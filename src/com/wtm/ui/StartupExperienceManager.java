package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.security.AuditService;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
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
 * Frames are decoded off the Swing event thread, buffered, and presented from
 * their container timestamps. The EDT never blocks on video decoding and no
 * fixed frame-rate assumption is made. The selected movie's final full-quality
 * frame is also prepared as the startup-login poster, guaranteeing a seamless
 * movie-to-logo handoff without maintaining a second manually synchronized
 * still image.
 */
public final class StartupExperienceManager {
    private static final int FRAME_BUFFER_CAPACITY=36;
    private static final int INITIAL_BUFFER_FRAMES=18;
    private static final int REBUFFER_FRAMES=6;

    private static volatile BufferedImage preparedPoster;
    private static volatile Path preparedVideo;

    private StartupExperienceManager(){}

    public enum Exit { COMPLETED, SKIPPED, FAILED }

    public record IntroResult(
            Exit exit,
            BufferedImage poster,
            Rectangle loginBounds
    ){}

    /**
     * Prepares the exact final movie frame while normal application loading is
     * already occurring. This keeps Skip Intro and no-intro startup immediate.
     */
    public static void preparePoster(AppConfig config){
        Path video=resolveVideo(config);
        if(video==null){
            preparedPoster=null;
            preparedVideo=null;
            return;
        }
        if(video.equals(preparedVideo)&&preparedPoster!=null)return;

        try{
            BufferedImage poster=decodeFinalFrame(video);
            if(poster!=null){
                preparedPoster=poster;
                preparedVideo=video;
            }
        }catch(Throwable ex){
            preparedPoster=null;
            preparedVideo=video;
            AuditService.record(
                    "Startup poster preparation failed: "+ex.getClass().getSimpleName());
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

    private static Path resolveVideo(AppConfig config){
        if(config==null||config.startupVideoAsset==null||config.startupVideoAsset.isBlank())return null;
        return MediaService.resolve(MediaCategory.STARTUP_MEDIA,config.startupVideoAsset);
    }

    private static BufferedImage decodeFinalFrame(Path video)throws Exception{
        SeekableByteChannel channel=null;
        try{
            channel=NIOUtils.readableChannel(video.toFile());
            FrameGrab grab=FrameGrab.createFrameGrab(channel);
            DemuxerTrackMeta meta=grab.getVideoTrack().getMeta();
            int total=meta==null?0:meta.getTotalFrames();
            if(total>0)grab.seekToFramePrecise(Math.max(0,total-1));
            else if(meta!=null&&meta.getTotalDuration()>0)
                grab.seekToSecondPrecise(Math.max(0,meta.getTotalDuration()-.05));
            Picture picture=grab.getNativeFrame();
            return picture==null?null:AWTUtil.toBufferedImage(picture);
        }finally{
            NIOUtils.closeQuietly(channel);
        }
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

        private volatile boolean stopRequested;
        private volatile boolean decodingComplete;
        private volatile Throwable decodeFailure;
        private volatile double firstTimestamp=Double.NaN;
        private volatile double lastPresentedEnd=Double.NaN;
        private volatile BufferedImage lastFullFrame;
        private volatile BufferedImage poster;
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
            JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,10));
            controls.setOpaque(false);
            JButton skip=new JButton("Skip Intro");
            skip.setFocusable(false);
            skip.addActionListener(e->finish(Exit.SKIPPED));
            controls.add(skip);
            videoPanel.add(controls,BorderLayout.SOUTH);
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
            Thread decoder=new Thread(this::decodeLoop,"northstar-startup-video-decoder");
            decoder.setDaemon(true);
            decoder.start();
        }

        private void decodeLoop(){
            SeekableByteChannel channel=null;
            try{
                channel=NIOUtils.readableChannel(video.toFile());
                FrameGrab grab=FrameGrab.createFrameGrab(channel);
                while(!stopRequested){
                    PictureWithMetadata decoded=grab.getNativeFrameWithMetadata();
                    if(decoded==null)break;
                    BufferedImage full=AWTUtil.toBufferedImage(decoded.getPicture());
                    lastFullFrame=full;
                    BufferedImage display=scaleContained(full,targetSize.width,targetSize.height);
                    double timestamp=Math.max(0,decoded.getTimestamp());
                    double duration=Math.max(.001,decoded.getDuration());
                    if(Double.isNaN(firstTimestamp))firstTimestamp=timestamp;
                    frames.put(new VideoFrame(display,timestamp,duration));

                    if(frames.size()==1)
                        SwingUtilities.invokeLater(()->{
                            VideoFrame first=frames.peek();
                            if(first!=null)videoPanel.setFrame(first.image());
                        });
                    if(frames.size()>=INITIAL_BUFFER_FRAMES)
                        SwingUtilities.invokeLater(this::startPlaybackIfReady);
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
                if(lastFullFrame!=null){
                    poster=lastFullFrame;
                    preparedPoster=lastFullFrame;
                    preparedVideo=video;
                }
                SwingUtilities.invokeLater(this::startPlaybackIfReady);
            }
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
            setVisible(false);
            dispose();
            completion.accept(new IntroResult(exit,poster,new Rectangle(loginBounds)));
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
            double scale=Math.min(width/(double)source.getWidth(),height/(double)source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(source.getHeight()*scale));
            g.drawImage(source,(width-w)/2,(height-h)/2,w,h,null);
        }finally{g.dispose();}
        return out;
    }

    private static final class VideoPanel extends JPanel {
        private volatile BufferedImage frame;
        VideoPanel(){setBackground(Color.BLACK);setOpaque(true);}
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
