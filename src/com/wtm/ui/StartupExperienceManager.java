package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.media.StartupMediaService;
import com.wtm.media.StartupPlaybackCacheService;
import com.wtm.security.AuditService;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.scale.AWTUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Source-owned startup movie presentation.
 *
 * H.264 decoding is never expected to keep pace with the presentation clock.
 * StartupPlaybackCacheService prepares display-ready lossless frames once and
 * normal playback reads those frames sequentially. This keeps the visible
 * animation fluid while retaining JCodec as the portable source decoder.
 */
public final class StartupExperienceManager {
    private static final int FRAME_BUFFER_CAPACITY=54;
    private static final int INITIAL_BUFFER_FRAMES=24;
    private static final int REBUFFER_FRAMES=12;

    private static volatile BufferedImage preparedPoster;
    private static volatile Path preparedVideo;
    private static volatile Path posterMigrationVideo;

    private StartupExperienceManager(){}

    public enum Exit { COMPLETED, SKIPPED, FAILED }

    public record IntroResult(
            Exit exit,
            BufferedImage poster,
            Rectangle loginBounds,
            Window handoffWindow
    ){}

    /**
     * Reads only the lossless resting-frame cache on the startup critical path.
     * If a non-video startup uses an older imported movie without a poster, that
     * poster can still migrate in the background. Intro playback builds both its
     * playback cache and final poster together when either is missing.
     */
    public static void preparePoster(AppConfig config){
        Path video=resolveVideo(config);
        if(video==null){
            preparedPoster=null;
            preparedVideo=null;
            posterMigrationVideo=null;
            return;
        }
        if(video.equals(preparedVideo)&&preparedPoster!=null)return;

        preparedVideo=video;
        try{
            BufferedImage cached=StartupMediaService.cachedPosterFor(video);
            preparedPoster=cached;
            boolean intro="INTRO_VIDEO".equalsIgnoreCase(config.startupExperience);
            if(cached==null&&!intro)startPosterMigration(video);
        }catch(Throwable ex){
            preparedPoster=null;
            AuditService.record(
                    "Startup poster cache read failed: "+ex.getClass().getSimpleName());
        }
    }

    public static BufferedImage preparedPoster(AppConfig config){
        Path video=resolveVideo(config);
        return video!=null&&video.equals(preparedVideo)?preparedPoster:null;
    }

    public static boolean playIntroIfConfigured(
            AppConfig config,
            Consumer<IntroResult> completion
    ){
        if(config==null||!"INTRO_VIDEO".equalsIgnoreCase(config.startupExperience))return false;
        Path video=resolveVideo(config);
        if(video==null)return false;

        BufferedImage poster=preparedPoster(config);
        IntroWindow window=new IntroWindow(
                video,
                poster,
                completion==null?result->{}:completion
        );
        window.setVisible(true);
        window.start();
        return true;
    }

    /** Releases the frozen final-frame window after the login shell is painted. */
    public static void releaseHandoff(Window window){
        if(window==null)return;
        Runnable release=()->{
            if(window.isDisplayable()){
                window.setVisible(false);
                window.dispose();
            }
        };
        if(SwingUtilities.isEventDispatchThread())release.run();
        else SwingUtilities.invokeLater(release);
    }

    private static void startPosterMigration(Path video){
        if(video==null||video.equals(posterMigrationVideo))return;
        posterMigrationVideo=video;
        Thread worker=new Thread(()->{
            try{
                BufferedImage generated=StartupMediaService.ensurePosterFor(video);
                if(generated!=null&&video.equals(preparedVideo))
                    preparedPoster=generated;
            }catch(Throwable ex){
                AuditService.record(
                        "Startup poster background migration failed: "+ex.getClass().getSimpleName());
            }finally{
                if(video.equals(posterMigrationVideo))posterMigrationVideo=null;
            }
        },"northstar-startup-poster-migration");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static Path resolveVideo(AppConfig config){
        if(config==null||config.startupVideoAsset==null||config.startupVideoAsset.isBlank())return null;
        return MediaService.resolve(MediaCategory.STARTUP_MEDIA,config.startupVideoAsset);
    }

    private static final class IntroWindow extends JWindow {
        private final Path video;
        private final Consumer<IntroResult> completion;
        private final VideoPanel videoPanel=new VideoPanel();
        private final BlockingQueue<VideoFrame> frames=
                new ArrayBlockingQueue<>(FRAME_BUFFER_CAPACITY);
        private final AtomicBoolean finished=new AtomicBoolean(false);
        private final AtomicBoolean playbackStarted=new AtomicBoolean(false);
        private final Rectangle loginBounds;
        private final Dimension targetSize;
        private final Timer displayTimer;
        private final JPanel preparationPanel=new JPanel(new BorderLayout(8,0));
        private final JLabel preparationLabel=new JLabel("Preparing startup animation…");
        private final JProgressBar preparationProgress=new JProgressBar(0,100);
        private final JPanel footer=new JPanel(new BorderLayout(10,0));

        private volatile boolean stopRequested;
        private volatile boolean decodingComplete;
        private volatile boolean reachedEndOfStream;
        private volatile Throwable decodeFailure;
        private volatile double firstTimestamp=Double.NaN;
        private volatile double lastPresentedEnd=Double.NaN;
        private volatile BufferedImage lastFullFrame;
        private volatile BufferedImage lastDisplayFrame;
        private volatile BufferedImage poster;
        private volatile Thread sourceThread;
        private long playbackBaseNanos;
        private boolean buffering;
        private long bufferStartedNanos;

        IntroWindow(
                Path video,
                BufferedImage prepared,
                Consumer<IntroResult> completion
        ){
            this.video=Objects.requireNonNull(video);
            this.poster=prepared;
            this.completion=completion;

            int sourceW=prepared==null?16:prepared.getWidth();
            int sourceH=prepared==null?9:prepared.getHeight();
            StartupPresentationLayout.Geometry geometry=StartupPresentationLayout.fit(
                    StartupPresentationLayout.usableBounds(null),sourceW,sourceH);
            loginBounds=geometry.windowBounds();
            targetSize=geometry.artworkSize();

            setBackground(Color.BLACK);
            setAlwaysOnTop(true);
            setBounds(
                    loginBounds.x,loginBounds.y,
                    targetSize.width,targetSize.height
            );

            videoPanel.setLayout(new BorderLayout());
            if(prepared!=null)
                videoPanel.setFrame(scaleContained(prepared,targetSize.width,targetSize.height));

            preparationPanel.setOpaque(false);
            preparationLabel.setForeground(new Color(205,221,235));
            preparationLabel.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
            preparationProgress.setPreferredSize(new Dimension(150,7));
            preparationProgress.setBorderPainted(false);
            preparationProgress.setStringPainted(false);
            preparationPanel.add(preparationLabel,BorderLayout.WEST);
            preparationPanel.add(preparationProgress,BorderLayout.CENTER);
            preparationPanel.setVisible(false);

            footer.setOpaque(false);
            footer.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
            footer.add(preparationPanel,BorderLayout.CENTER);
            JButton skip=new JButton("Skip Intro");
            skip.setFocusable(false);
            skip.addActionListener(e->finish(Exit.SKIPPED));
            footer.add(skip,BorderLayout.EAST);
            videoPanel.add(footer,BorderLayout.SOUTH);
            setContentPane(videoPanel);

            getRootPane().registerKeyboardAction(
                    e->finish(Exit.SKIPPED),
                    KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
            ApplicationBrand.applyWindowIcon(this);

            displayTimer=new Timer(8,e->presentDueFrame());
            displayTimer.setCoalesce(true);
        }

        void start(){
            sourceThread=new Thread(
                    this::prepareAndLoadPlayback,
                    "northstar-startup-playback-source");
            sourceThread.setDaemon(true);
            sourceThread.setPriority(Math.min(Thread.MAX_PRIORITY,Thread.NORM_PRIORITY+1));
            sourceThread.start();
        }

        private void prepareAndLoadPlayback(){
            try{
                StartupPlaybackCacheService.Cache cache=
                        StartupPlaybackCacheService.openFresh(video,targetSize);
                if(cache==null){
                    showPreparation(true);
                    cache=StartupPlaybackCacheService.ensure(
                            video,targetSize,this::updatePreparationProgress);
                    if(stopRequested)return;
                    try{
                        BufferedImage cachedPoster=StartupMediaService.cachedPosterFor(video);
                        if(cachedPoster!=null){
                            poster=cachedPoster;
                            preparedPoster=cachedPoster;
                            preparedVideo=video;
                        }
                    }catch(Exception ignored){}
                    showPreparation(false);
                }
                if(stopRequested)return;
                loadCachedFrames(cache);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }catch(Throwable cacheFailure){
                if(stopRequested)return;
                AuditService.record(
                        "Startup playback cache unavailable; using direct decoder: "
                                +cacheFailure.getClass().getSimpleName());
                showPreparation(false);
                decodeDirectly();
            }
        }

        private void loadCachedFrames(StartupPlaybackCacheService.Cache cache)
                throws Exception {
            try(StartupPlaybackCacheService.Reader reader=
                        StartupPlaybackCacheService.openReader(cache)){
                StartupPlaybackCacheService.Frame cached;
                while(!stopRequested&&(cached=reader.next())!=null){
                    BufferedImage image=cached.image();
                    lastDisplayFrame=image;
                    enqueueFrame(new VideoFrame(
                            image,cached.timestamp(),cached.duration()));
                }
                if(!stopRequested)reachedEndOfStream=true;
            }finally{
                decodingComplete=true;
                SwingUtilities.invokeLater(this::startPlaybackIfReady);
            }
        }

        /** Fallback only; normal playback should use the display-ready cache. */
        private void decodeDirectly(){
            SeekableByteChannel channel=null;
            try{
                channel=NIOUtils.readableChannel(video.toFile());
                FrameGrab grab=FrameGrab.createFrameGrab(channel);
                while(!stopRequested){
                    PictureWithMetadata decoded=grab.getNativeFrameWithMetadata();
                    if(decoded==null){
                        reachedEndOfStream=true;
                        break;
                    }
                    BufferedImage full=AWTUtil.toBufferedImage(decoded.getPicture());
                    lastFullFrame=full;
                    BufferedImage display=scaleContained(full,targetSize.width,targetSize.height);
                    lastDisplayFrame=display;
                    double timestamp=Math.max(0,decoded.getTimestamp());
                    double duration=Math.max(.001,decoded.getDuration());
                    enqueueFrame(new VideoFrame(display,timestamp,duration));
                }
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }catch(Throwable ex){
                decodeFailure=ex;
                AuditService.record(
                        "Startup intro playback failed: "+ex.getClass().getSimpleName());
            }finally{
                NIOUtils.closeQuietly(channel);
                decodingComplete=true;
                if(reachedEndOfStream&&lastFullFrame!=null&&decodeFailure==null){
                    poster=lastFullFrame;
                    preparedPoster=lastFullFrame;
                    preparedVideo=video;
                    StartupMediaService.cachePoster(video,lastFullFrame);
                }
                SwingUtilities.invokeLater(this::startPlaybackIfReady);
            }
        }

        private void enqueueFrame(VideoFrame frame) throws InterruptedException {
            if(Double.isNaN(firstTimestamp))firstTimestamp=frame.timestamp();
            frames.put(frame);
            if(frames.size()==1)
                SwingUtilities.invokeLater(()->{
                    VideoFrame first=frames.peek();
                    if(first!=null)videoPanel.setFrame(first.image());
                });
            if(frames.size()>=INITIAL_BUFFER_FRAMES)
                SwingUtilities.invokeLater(this::startPlaybackIfReady);
        }

        private void startPlaybackIfReady(){
            if(stopRequested||finished.get()||playbackStarted.get())return;
            if(frames.isEmpty()){
                if(decodingComplete)finish(Exit.FAILED);
                return;
            }
            if(!decodingComplete&&frames.size()<INITIAL_BUFFER_FRAMES)return;
            if(!playbackStarted.compareAndSet(false,true))return;
            playbackBaseNanos=System.nanoTime();
            displayTimer.start();
            presentDueFrame();
        }

        private void presentDueFrame(){
            if(stopRequested||finished.get())return;
            long now=System.nanoTime();

            if(buffering){
                if(!decodingComplete&&frames.size()<REBUFFER_FRAMES)return;
                playbackBaseNanos+=now-bufferStartedNanos;
                buffering=false;
            }

            double start=Double.isNaN(firstTimestamp)?0:firstTimestamp;
            double mediaTime=start+(now-playbackBaseNanos)/1_000_000_000.0;
            VideoFrame due=null;
            while(true){
                VideoFrame next=frames.peek();
                if(next==null||next.timestamp()>mediaTime+.004)break;
                due=frames.poll();
            }
            if(due!=null){
                videoPanel.setFrame(due.image());
                lastDisplayFrame=due.image();
                lastPresentedEnd=due.timestamp()+due.duration();
            }

            if(frames.isEmpty()){
                if(decodingComplete){
                    if(Double.isNaN(lastPresentedEnd)||mediaTime>=lastPresentedEnd)
                        finish(decodeFailure==null?Exit.COMPLETED:Exit.FAILED);
                }else if(!buffering){
                    buffering=true;
                    bufferStartedNanos=now;
                }
            }
        }

        private void finish(Exit exit){
            if(!SwingUtilities.isEventDispatchThread()){
                SwingUtilities.invokeLater(()->finish(exit));
                return;
            }
            if(!finished.compareAndSet(false,true))return;
            stopRequested=true;
            displayTimer.stop();
            Thread active=sourceThread;
            if(active!=null)active.interrupt();

            BufferedImage resting=poster;
            if(resting==null&&video.equals(preparedVideo))resting=preparedPoster;
            if(resting==null){
                try{resting=StartupMediaService.cachedPosterFor(video);}
                catch(Exception ignored){}
            }
            if(resting==null&&exit==Exit.COMPLETED)resting=lastFullFrame;

            if(exit==Exit.FAILED){
                setVisible(false);
                dispose();
                completion.accept(new IntroResult(
                        exit,resting,new Rectangle(loginBounds),null));
                return;
            }

            BufferedImage displayResting=resting==null
                    ?lastDisplayFrame
                    :scaleContained(resting,targetSize.width,targetSize.height);
            if(displayResting!=null){
                videoPanel.setFrame(displayResting);
                videoPanel.paintImmediately(0,0,videoPanel.getWidth(),videoPanel.getHeight());
            }
            footer.setVisible(false);
            setAlwaysOnTop(false);
            completion.accept(new IntroResult(
                    exit,resting,new Rectangle(loginBounds),this));
        }

        private void showPreparation(boolean visible){
            SwingUtilities.invokeLater(()->{
                if(stopRequested)return;
                preparationPanel.setVisible(visible);
                if(visible)preparationProgress.setValue(0);
                footer.revalidate();
                footer.repaint();
            });
        }

        private void updatePreparationProgress(int value){
            SwingUtilities.invokeLater(()->{
                if(!stopRequested)
                    preparationProgress.setValue(Math.max(0,Math.min(100,value)));
            });
        }
    }

    private record VideoFrame(BufferedImage image,double timestamp,double duration){}

    private static BufferedImage scaleContained(BufferedImage source,int width,int height){
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try{
            g.setColor(Color.BLACK);
            g.fillRect(0,0,width,height);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            double scale=Math.min(width/(double)source.getWidth(),height/(double)source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(source.getHeight()*scale));
            g.drawImage(source,(width-w)/2,(height-h)/2,w,h,null);
        }finally{g.dispose();}
        return out;
    }

    private static final class VideoPanel extends JPanel {
        private volatile BufferedImage frame;
        VideoPanel(){
            setBackground(Color.BLACK);
            setOpaque(true);
            setDoubleBuffered(true);
        }
        void setFrame(BufferedImage next){frame=next;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            BufferedImage image=frame;
            if(image==null)return;
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(image,0,0,getWidth(),getHeight(),null);
            }finally{g.dispose();}
        }
    }
}
