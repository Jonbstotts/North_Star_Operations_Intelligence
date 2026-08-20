package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Startup window using the exact full-resolution approved splash artwork.
 * The progress track in the supplied artwork is covered by the live progress
 * indicator so startup remains functional without altering the visual design.
 */
public final class NorthStarSplashScreen extends JWindow {
    private final SplashArtworkPanel artwork=new SplashArtworkPanel();

    public NorthStarSplashScreen(){
        setContentPane(artwork);

        // The uploaded splash is a Retina-scale portrait asset. Displaying it
        // at roughly half resolution keeps the logo razor sharp while fitting
        // common desktop displays.
        setSize(470,835);
        setLocationRelativeTo(null);
        ApplicationBrand.applyWindowIcon(this);
    }

    public void updateProgress(int value,String message){
        Runnable update=()->{
            artwork.progress=Math.max(0,Math.min(100,value));
            artwork.repaint();
        };
        if(SwingUtilities.isEventDispatchThread())update.run();
        else SwingUtilities.invokeLater(update);
    }

    public void closeSplash(){
        Runnable close=()->{
            setVisible(false);
            dispose();
        };
        if(SwingUtilities.isEventDispatchThread())close.run();
        else SwingUtilities.invokeLater(close);
    }

    private static final class SplashArtworkPanel extends JPanel{
        private final BufferedImage source=NorthStarBrand.splashArtwork();
        private int progress=8;

        private SplashArtworkPanel(){
            setOpaque(true);
            setBackground(Color.BLACK);
        }

        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                int w=getWidth();
                int h=getHeight();
                g.drawImage(source,0,0,w,h,null);

                // Match the position of the progress track in the supplied
                // splash reference. Drawing an opaque track first fully covers
                // the reference image's example progress value.
                int x=(int)Math.round(w*.115);
                int trackW=(int)Math.round(w*.77);
                int trackH=Math.max(5,(int)Math.round(h*.008));
                int y=(int)Math.round(h*.907);

                g.setColor(new Color(18,32,48));
                g.fillRoundRect(x,y,trackW,trackH,trackH,trackH);

                int fill=(int)Math.round(trackW*(progress/100.0));
                if(fill>0){
                    GradientPaint blue=new GradientPaint(
                            x,y,new Color(13,110,253),
                            x+fill,y,new Color(0,174,255));
                    g.setPaint(blue);
                    g.fillRoundRect(x,y,fill,trackH,trackH,trackH);
                }
            }finally{
                g.dispose();
            }
        }
    }
}
