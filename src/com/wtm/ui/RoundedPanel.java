package com.wtm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable rounded dashboard surface with a subtle outline.
 *
 * The component deliberately paints its own background so every dashboard card
 * has the same corner radius and border treatment in both light and dark mode.
 */
public class RoundedPanel extends JPanel {
    private final int radius;

    public RoundedPanel(int radius){
        this.radius=radius;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0){
        Graphics2D g=(Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(getBackground());
        g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,radius,radius);

        Object borderColor=getClientProperty("outlineColor");
        if(borderColor instanceof Color c){
            g.setColor(c);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius,radius);
        }

        g.dispose();
        super.paintComponent(g0);
    }
}
