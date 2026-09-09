package com.wtm.ui;

import com.wtm.security.UserAccount;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Startup-only authentication splash. The login always uses North Star branding;
 * the user's saved application theme is installed only after authentication.
 */
public final class StartupLoginDialog extends JDialog {
    private final LoginFormPanel form;
    private final FadingPanel reveal;
    private UserAccount authenticated;
    private Timer revealTimer;

    private StartupLoginDialog(
            Window owner,
            String message,
            String suggestedUsername
    ){
        super(owner,"North Star Sign In",ModalityType.APPLICATION_MODAL);
        Theme.setActive(AppTheme.NORTH_STAR.id());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setUndecorated(true);

        BufferedImage artwork=NorthStarBrand.primaryArtwork();
        Rectangle usable=LoginSplashLayout.usableBounds(owner);
        LoginSplashLayout.Geometry geometry=LoginSplashLayout.fit(
                usable,artwork.getWidth(),artwork.getHeight());
        Rectangle bounds=geometry.windowBounds();

        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(Color.BLACK);
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.accent(),1,true),
                BorderFactory.createEmptyBorder(1,1,1,1)
        ));

        StartupArtworkPanel artworkPanel=new StartupArtworkPanel(artwork);
        artworkPanel.setPreferredSize(geometry.artworkSize());
        shell.add(artworkPanel,BorderLayout.NORTH);

        JSeparator separator=new JSeparator();
        separator.setForeground(Theme.accent());

        JPanel formHost=new JPanel(new GridBagLayout());
        formHost.setBackground(Theme.panel());
        form=new LoginFormPanel(
                this,message,suggestedUsername,
                new Insets(14,24,14,24),
                account->{authenticated=account;dispose();}
        );
        form.setPreferredSize(new Dimension(
                Math.min(520,Math.max(430,bounds.width-120)),
                Math.max(270,geometry.loginHeight()-18)
        ));
        formHost.add(form);

        JPanel lower=new JPanel(new BorderLayout());
        lower.setBackground(Theme.panel());
        lower.add(separator,BorderLayout.NORTH);
        lower.add(formHost,BorderLayout.CENTER);

        reveal=new FadingPanel(new BorderLayout());
        reveal.setBackground(Theme.panel());
        reveal.add(lower,BorderLayout.CENTER);
        reveal.setProgress(0f);
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
                SwingUtilities.invokeLater(StartupLoginDialog.this::startReveal);
            }
        });
    }

    public static UserAccount authenticate(
            Window owner,
            String message,
            String suggestedUsername
    ){
        StartupLoginDialog dialog=new StartupLoginDialog(
                owner,message,suggestedUsername);
        dialog.setVisible(true);
        return dialog.authenticated;
    }

    private void startReveal(){
        final long started=System.nanoTime();
        revealTimer=new Timer(LoginRevealPolicy.TIMER_DELAY_MILLIS,e->{
            double elapsed=(System.nanoTime()-started)/1_000_000.0;
            float progress=LoginRevealPolicy.revealProgress(elapsed);
            reveal.setProgress(progress);
            if(progress>=1f){
                ((Timer)e.getSource()).stop();
                reveal.setProgress(1f);
                SwingUtilities.invokeLater(form::focusInitial);
            }
        });
        revealTimer.setCoalesce(true);
        revealTimer.start();
    }

    @Override public void dispose(){
        if(revealTimer!=null)revealTimer.stop();
        form.stop();
        super.dispose();
    }

    private static final class FadingPanel extends JPanel {
        private float progress=1f;
        private FadingPanel(LayoutManager layout){
            super(layout);
            setDoubleBuffered(true);
        }
        private void setProgress(float value){
            progress=Math.max(0f,Math.min(1f,value));
            repaint();
        }
        @Override public void paint(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setComposite(AlphaComposite.SrcOver.derive(progress));
                g.translate(0,LoginRevealPolicy.verticalOffset(progress));
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
            setDoubleBuffered(true);
        }
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                drawContained(g,artwork,getWidth(),getHeight());
            }finally{g.dispose();}
        }
        private static void drawContained(Graphics2D g,BufferedImage image,int width,int height){
            double scale=Math.min(
                    width/(double)image.getWidth(),
                    height/(double)image.getHeight());
            int w=Math.max(1,(int)Math.round(image.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(image.getHeight()*scale));
            g.drawImage(
                    image,
                    (width-w)/2,(height-h)/2,
                    (width-w)/2+w,(height-h)/2+h,
                    0,0,image.getWidth(),image.getHeight(),
                    null
            );
        }
    }
}
