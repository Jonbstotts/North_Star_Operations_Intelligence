package com.wtm.ui;

import com.wtm.security.UserAccount;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Startup-only authentication shell. The identity artwork stays stable at the
 * top while the login form can reveal beneath it after a completed intro.
 * Protected Settings re-authentication continues to use UserLoginDialog.
 */
public final class StartupLoginDialog extends JDialog {
    private static final int REVEAL_MILLIS=480;

    private final LoginFormPanel form;
    private final FadingPanel reveal;
    private UserAccount authenticated;
    private Timer revealTimer;

    private StartupLoginDialog(
            Window owner,
            String message,
            AppTheme requestedTheme,
            String suggestedUsername,
            BufferedImage artwork,
            Rectangle requestedBounds,
            boolean animateForm
    ){
        super(owner,"North Star Sign In",ModalityType.APPLICATION_MODAL);
        Theme.setActive(requestedTheme==null?AppTheme.NORTH_STAR.id():requestedTheme.id());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        getRootPane().putClientProperty("apple.awt.fullWindowContent",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible",Boolean.FALSE);

        int sourceW=artwork==null?16:artwork.getWidth();
        int sourceH=artwork==null?9:artwork.getHeight();
        Rectangle usable=StartupPresentationLayout.usableBounds(owner);
        StartupPresentationLayout.Geometry geometry=StartupPresentationLayout.fit(
                usable,sourceW,sourceH);
        Rectangle bounds=requestedBounds==null
                ?geometry.windowBounds()
                :constrainBounds(requestedBounds,usable);

        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(Color.BLACK);
        shell.setBorder(BorderFactory.createLineBorder(Theme.border(),1,true));

        int artworkHeight=requestedBounds==null
                ?geometry.artworkSize().height
                :Math.max(1,(int)Math.round(bounds.width*(sourceH/(double)sourceW)));
        artworkHeight=Math.min(artworkHeight,Math.max(1,bounds.height-220));
        StartupArtworkPanel artworkPanel=new StartupArtworkPanel(artwork);
        artworkPanel.setPreferredSize(new Dimension(bounds.width,artworkHeight));
        shell.add(artworkPanel,BorderLayout.NORTH);

        JPanel formHost=new JPanel(new GridBagLayout());
        formHost.setBackground(Theme.panel());
        form=new LoginFormPanel(
                this,message,suggestedUsername,
                new Insets(12,22,10,22),
                account->{authenticated=account;dispose();}
        );
        form.setPreferredSize(new Dimension(
                Math.min(520,Math.max(420,bounds.width-100)),
                Math.max(300,bounds.height-artworkHeight)
        ));
        formHost.add(form);

        reveal=new FadingPanel(new BorderLayout());
        reveal.setBackground(Theme.panel());
        reveal.add(formHost,BorderLayout.CENTER);
        reveal.setAlpha(animateForm?0f:1f);
        shell.add(reveal,BorderLayout.CENTER);
        setContentPane(shell);

        getRootPane().setDefaultButton(form.defaultButton());
        getRootPane().registerKeyboardAction(
                e->dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        setBounds(bounds);
        ApplicationBrand.applyWindowIcon(this);

        addWindowListener(new WindowAdapter(){
            @Override public void windowOpened(WindowEvent e){
                if(animateForm)startReveal();
                else SwingUtilities.invokeLater(form::focusInitial);
            }
        });
    }

    public static UserAccount authenticate(
            Window owner,
            String message,
            AppTheme theme,
            String suggestedUsername,
            BufferedImage artwork,
            Rectangle requestedBounds,
            boolean animateForm
    ){
        StartupLoginDialog dialog=new StartupLoginDialog(
                owner,message,theme,suggestedUsername,artwork,requestedBounds,animateForm);
        dialog.setVisible(true);
        return dialog.authenticated;
    }

    private void startReveal(){
        final long started=System.nanoTime();
        revealTimer=new Timer(16,e->{
            double elapsed=(System.nanoTime()-started)/1_000_000.0;
            float t=(float)Math.max(0,Math.min(1,elapsed/REVEAL_MILLIS));
            float eased=t*t*(3f-2f*t);
            reveal.setAlpha(eased);
            if(t>=1f){
                ((Timer)e.getSource()).stop();
                reveal.setAlpha(1f);
                SwingUtilities.invokeLater(form::focusInitial);
            }
        });
        revealTimer.setCoalesce(true);
        revealTimer.start();
    }

    private static Rectangle constrainBounds(Rectangle requested,Rectangle usable){
        int width=Math.min(requested.width,usable.width);
        int height=Math.min(requested.height,usable.height);
        int x=Math.max(usable.x,Math.min(requested.x,usable.x+usable.width-width));
        int y=Math.max(usable.y,Math.min(requested.y,usable.y+usable.height-height));
        return new Rectangle(x,y,width,height);
    }

    @Override public void dispose(){
        if(revealTimer!=null)revealTimer.stop();
        form.stop();
        super.dispose();
    }

    private static final class FadingPanel extends JPanel {
        private float alpha=1f;
        private FadingPanel(LayoutManager layout){super(layout);}
        private void setAlpha(float value){
            alpha=Math.max(0f,Math.min(1f,value));
            repaint();
        }
        @Override public void paint(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                super.paint(g);
            }finally{g.dispose();}
        }
    }

    private static final class StartupArtworkPanel extends JPanel {
        private final BufferedImage artwork;
        private StartupArtworkPanel(BufferedImage artwork){
            this.artwork=artwork;
            setBackground(Color.BLACK);
            setOpaque(true);
        }
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                if(artwork!=null){
                    drawContained(g,artwork,getWidth(),getHeight());
                }else{
                    Image fallback=NorthStarBrand.primaryLockup(
                            Math.max(320,Math.min(600,getWidth()/2))).getImage();
                    int iw=fallback.getWidth(null),ih=fallback.getHeight(null);
                    if(iw>0&&ih>0){
                        double scale=Math.min(getWidth()*.62/iw,getHeight()*.86/ih);
                        int w=Math.max(1,(int)Math.round(iw*scale));
                        int h=Math.max(1,(int)Math.round(ih*scale));
                        g.drawImage(fallback,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);
                    }
                }
            }finally{g.dispose();}
        }
        private static void drawContained(Graphics2D g,BufferedImage image,int width,int height){
            double scale=Math.min(
                    width/(double)image.getWidth(),
                    height/(double)image.getHeight());
            int w=Math.max(1,(int)Math.round(image.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(image.getHeight()*scale));
            g.drawImage(image,(width-w)/2,(height-h)/2,w,h,null);
        }
    }
}
