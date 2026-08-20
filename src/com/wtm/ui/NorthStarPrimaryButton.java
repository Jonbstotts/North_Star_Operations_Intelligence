package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Polished North Star blue-gradient primary action button. */
public final class NorthStarPrimaryButton extends JButton {
    private boolean hover;
    private boolean pressed;

    public NorthStarPrimaryButton(String text){
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(Color.WHITE);
        setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(380,52));

        addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){hover=true;repaint();}
            @Override public void mouseExited(MouseEvent e){hover=false;pressed=false;repaint();}
            @Override public void mousePressed(MouseEvent e){pressed=true;repaint();}
            @Override public void mouseReleased(MouseEvent e){pressed=false;repaint();}
        });
    }

    @Override protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Color base=Theme.accent();
        Color top=hover?base.brighter():base;
        Color bottom=hover?base:base.darker();
        if(pressed){
            top=top.darker(); bottom=bottom.darker();
        }

        g2.setPaint(new GradientPaint(0,0,top,0,getHeight(),bottom));
        g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);

        Color accent=Theme.accent();
        g2.setColor(new Color(
                accent.getRed(),
                accent.getGreen(),
                accent.getBlue(),
                hover?210:135
        ));
        g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);

        if(hover){
            g2.setColor(new Color(255,255,255,18));
            g2.fillRoundRect(2,2,getWidth()-4,getHeight()/2,14,14);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
