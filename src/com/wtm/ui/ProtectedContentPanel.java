package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * Security wrapper for Settings pages that must not be readable before
 * administrator authentication.
 *
 * While locked, the real Swing component remains in memory but is not directly
 * displayed. A heavily blurred snapshot is rendered under an opaque privacy
 * veil and lock message. The combination intentionally favors concealment over
 * preserving detail: text, numeric values, and credential fields should not be
 * recoverable by simply looking around the login dialog.
 */
public final class ProtectedContentPanel extends JPanel {
    private final JComponent protectedContent;
    private final PrivacySurface privacySurface=new PrivacySurface();

    private final CardLayout cards=new CardLayout();
    private boolean unlocked=false;

    public ProtectedContentPanel(JComponent protectedContent){
        this.protectedContent=protectedContent;

        setLayout(cards);
        add(privacySurface,"LOCKED");
        add(protectedContent,"CONTENT");
        cards.show(this,"LOCKED");
    }

    public void lock(){
        unlocked=false;
        privacySurface.invalidateSnapshot();
        cards.show(this,"LOCKED");
        revalidate();
        repaint();
    }

    public void unlock(){
        unlocked=true;
        cards.show(this,"CONTENT");
        revalidate();
        repaint();
    }

    public boolean isUnlocked(){
        return unlocked;
    }

    /**
     * Refreshes the privacy snapshot while keeping the real content concealed.
     * This is useful immediately before displaying an authentication prompt.
     */
    public void refreshPrivacySurface(){
        privacySurface.invalidateSnapshot();
        privacySurface.repaint();
    }

    private final class PrivacySurface extends JPanel {
        private BufferedImage blurredSnapshot;
        private Dimension snapshotSize;

        PrivacySurface(){
            setOpaque(true);
        }

        void invalidateSnapshot(){
            blurredSnapshot=null;
            snapshotSize=null;
        }

        @Override
        protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);

            int width=getWidth();
            int height=getHeight();
            if(width<=0||height<=0)return;

            if(blurredSnapshot==null
                    ||snapshotSize==null
                    ||snapshotSize.width!=width
                    ||snapshotSize.height!=height){
                blurredSnapshot=createPrivacySnapshot(width,height);
                snapshotSize=new Dimension(width,height);
            }

            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            if(blurredSnapshot!=null)
                g.drawImage(blurredSnapshot,0,0,width,height,null);

            /*
             * The translucent veil is deliberately strong. Blur supplies the
             * visual context while the veil guarantees ordinary text/numbers
             * cannot remain legible behind the authentication dialog.
             */
            Color base=currentThemePanel();
            g.setColor(new Color(
                    base.getRed(),
                    base.getGreen(),
                    base.getBlue(),
                    218
            ));
            g.fillRect(0,0,width,height);

            paintLockMessage(g,width,height);
            g.dispose();
        }

        private BufferedImage createPrivacySnapshot(int width,int height){
            /*
             * Render at quarter resolution before blurring. Besides being much
             * cheaper than full-resolution convolution, this destroys fine
             * text detail before the blur kernel is even applied.
             */
            int sampleW=Math.max(1,width/4);
            int sampleH=Math.max(1,height/4);

            BufferedImage sample=new BufferedImage(
                    sampleW,
                    sampleH,
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D sg=sample.createGraphics();
            sg.scale(sampleW/(double)Math.max(1,width),
                    sampleH/(double)Math.max(1,height));

            protectedContent.setSize(width,height);
            protectedContent.doLayout();
            protectedContent.printAll(sg);
            sg.dispose();

            float[] kernelData=new float[81];
            for(int i=0;i<kernelData.length;i++)
                kernelData[i]=1f/kernelData.length;

            ConvolveOp blur=new ConvolveOp(
                    new Kernel(9,9,kernelData),
                    ConvolveOp.EDGE_NO_OP,
                    null
            );

            BufferedImage pass1=new BufferedImage(
                    sampleW,
                    sampleH,
                    BufferedImage.TYPE_INT_ARGB
            );
            BufferedImage pass2=new BufferedImage(
                    sampleW,
                    sampleH,
                    BufferedImage.TYPE_INT_ARGB
            );

            blur.filter(sample,pass1);
            blur.filter(pass1,pass2);
            return pass2;
        }

        private void paintLockMessage(Graphics2D g,int width,int height){
            AppTheme theme=currentTheme();

            int boxW=Math.min(460,Math.max(300,width-80));
            int boxH=128;
            int x=(width-boxW)/2;
            int y=(height-boxH)/2;

            g.setColor(new Color(
                    theme.panel2().getRed(),
                    theme.panel2().getGreen(),
                    theme.panel2().getBlue(),
                    242
            ));
            g.fillRoundRect(x,y,boxW,boxH,24,24);

            g.setColor(theme.border());
            g.setStroke(new BasicStroke(1.3f));
            g.drawRoundRect(x,y,boxW,boxH,24,24);

            // Vector padlock.
            int lockX=x+34;
            int lockY=y+34;

            g.setColor(theme.accent());
            g.setStroke(new BasicStroke(
                    4f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            g.drawArc(lockX,lockY-10,34,34,0,180);
            g.fillRoundRect(lockX-3,lockY+7,40,34,8,8);

            g.setColor(bestText(theme.accent()));
            g.fillOval(lockX+14,lockY+18,7,7);
            g.fillRect(lockX+16,lockY+23,3,9);

            g.setColor(theme.text());
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,19));
            g.drawString("Protected API settings",x+96,y+48);

            g.setColor(theme.muted());
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
            g.drawString(
                    "Administrator authentication is required to view this content.",
                    x+96,
                    y+76
            );
            g.drawString(
                    "Sensitive information remains concealed while locked.",
                    x+96,
                    y+98
            );
        }

        private AppTheme currentTheme(){
            Window window=SwingUtilities.getWindowAncestor(
                    ProtectedContentPanel.this
            );

            if(window instanceof SettingsDialog settings)
                return settings.activeThemeForProtectedContent();

            return AppTheme.FLATLAF_DARK;
        }

        private Color currentThemePanel(){
            return currentTheme().bg();
        }

        private Color bestText(Color background){
            double luminance=(
                    0.299*background.getRed()
                    +0.587*background.getGreen()
                    +0.114*background.getBlue()
            )/255.0;

            return luminance>.62?Color.BLACK:Color.WHITE;
        }
    }
}
