package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Lightweight branded startup window.
 *
 * It displays while configuration and local account state are initialized,
 * then closes before any authentication/setup dialog appears.
 */
public final class NorthStarSplashScreen extends JWindow {
    private final JProgressBar progress=new JProgressBar(0,100);
    private final JLabel status=new JLabel(
            "Initializing operations intelligence...",
            SwingConstants.CENTER
    );

    public NorthStarSplashScreen(){
        NorthStarBackdropPanel root=new NorthStarBackdropPanel();
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(28,34,26,34));

        JPanel center=new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));

        NorthStarBrandLockup logo=new NorthStarBrandLockup(
                NorthStarBrandLockup.Layout.VERTICAL,
                210,
                42,
                true
        );
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel version=new JLabel(
                "SECURE OPERATIONS DISPLAY",
                SwingConstants.CENTER
        );
        version.setForeground(Theme.muted());
        version.setFont(
                new Font(Font.SANS_SERIF,Font.BOLD,11)
                        .deriveFont(11f)
        );
        version.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalGlue());
        center.add(logo);
        center.add(Box.createVerticalStrut(2));
        center.add(version);
        center.add(Box.createVerticalGlue());

        JPanel bottom=new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));

        progress.setValue(8);
        progress.setStringPainted(false);
        progress.setForeground(Theme.accent());
        progress.setBackground(Theme.panel2());
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(420,6));
        progress.setMaximumSize(new Dimension(420,6));
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);

        status.setForeground(Theme.muted());
        status.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
        status.setAlignmentX(Component.CENTER_ALIGNMENT);

        bottom.add(progress);
        bottom.add(Box.createVerticalStrut(12));
        bottom.add(status);

        root.add(center,BorderLayout.CENTER);
        root.add(bottom,BorderLayout.SOUTH);
        setContentPane(root);

        setSize(580,650);
        setLocationRelativeTo(null);
        ApplicationBrand.applyWindowIcon(this);
    }

    public void updateProgress(int value,String message){
        Runnable update=()->{
            progress.setValue(Math.max(0,Math.min(100,value)));
            if(message!=null&&!message.isBlank())
                status.setText(message);
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
}
