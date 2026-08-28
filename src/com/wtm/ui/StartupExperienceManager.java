package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.security.AuditService;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Source-owned optional managed startup intro. Visual track only in v1. */
public final class StartupExperienceManager {
    private StartupExperienceManager(){}

    public static boolean playIntroIfConfigured(AppConfig config,Runnable completion){
        if(config==null||!"INTRO_VIDEO".equalsIgnoreCase(config.startupExperience))return false;
        Path video=MediaService.resolve(MediaCategory.STARTUP_MEDIA,config.startupVideoAsset);
        if(video==null)return false;
        IntroWindow window=new IntroWindow(video,completion==null?()->{}:completion);
        window.setVisible(true);
        window.start();
        return true;
    }

    private static final class IntroWindow extends JWindow {
        private final Path video;
        private final Runnable completion;
        private final VideoPanel videoPanel=new VideoPanel();
        private final AtomicBoolean finished=new AtomicBoolean(false);
        private volatile boolean stopRequested;

        IntroWindow(Path video,Runnable completion){
            this.video=video;this.completion=completion;
            setBackground(Color.BLACK);setAlwaysOnTop(true);
            setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
            JPanel root=new JPanel(new BorderLayout());root.setBackground(Color.BLACK);
            root.add(videoPanel,BorderLayout.CENTER);
            JButton skip=new JButton("Skip Intro");skip.addActionListener(e->finish());
            JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,16,12));
            controls.setOpaque(false);controls.add(skip);root.add(controls,BorderLayout.SOUTH);
            setContentPane(root);
            getRootPane().registerKeyboardAction(e->finish(),KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
            ApplicationBrand.applyWindowIcon(this);
        }

        void start(){Thread t=new Thread(this::decodeLoop,"northstar-startup-video");t.setDaemon(true);t.start();}

        private void decodeLoop(){
            SeekableByteChannel channel=null;
            try{
                channel=NIOUtils.readableChannel(video.toFile());
                FrameGrab grab=FrameGrab.createFrameGrab(channel);
                long next=System.nanoTime();
                while(!stopRequested){
                    Picture picture=grab.getNativeFrame();if(picture==null)break;
                    BufferedImage frame=AWTUtil.toBufferedImage(picture);
                    SwingUtilities.invokeAndWait(()->videoPanel.setFrame(frame));
                    next+=33_333_333L;
                    long remaining=next-System.nanoTime();
                    if(remaining>0)Thread.sleep(remaining/1_000_000L,(int)(remaining%1_000_000L));
                    else next=System.nanoTime();
                }
            }catch(Throwable ex){
                AuditService.record("Startup intro playback failed: "+ex.getClass().getSimpleName());
            }finally{
                NIOUtils.closeQuietly(channel);
                SwingUtilities.invokeLater(this::finish);
            }
        }

        private void finish(){
            if(!finished.compareAndSet(false,true))return;
            stopRequested=true;setVisible(false);dispose();completion.run();
        }
    }

    private static final class VideoPanel extends JPanel {
        private BufferedImage frame;
        VideoPanel(){setBackground(Color.BLACK);}
        void setFrame(BufferedImage next){frame=next;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);BufferedImage image=frame;if(image==null)return;
            double scale=Math.min(getWidth()/(double)image.getWidth(),getHeight()/(double)image.getHeight());
            int dw=Math.max(1,(int)Math.round(image.getWidth()*scale));
            int dh=Math.max(1,(int)Math.round(image.getHeight()*scale));
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image,(getWidth()-dw)/2,(getHeight()-dh)/2,dw,dh,null);
            }finally{g.dispose();}
        }
    }
}
