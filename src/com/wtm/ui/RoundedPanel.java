package com.wtm.ui;

import javax.swing.*;
import java.awt.*;

/** Standard NorthStar rounded card. Glass Surfaces is intentionally theme-agnostic. */
public class RoundedPanel extends JPanel {
    private final int radius;

    public RoundedPanel(int radius) {
        this.radius = radius;
        setOpaque(false);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = Math.max(0, getWidth() - 1);
        int h = Math.max(0, getHeight() - 1);
        Color base = getBackground() == null ? Theme.panel() : getBackground();

        if (IntelligenceGlassSettings.enabled()) {
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 205));
            g.fillRoundRect(0, 0, w, h, radius, radius);

            g.setPaint(new GradientPaint(0, 0,
                    new Color(255, 255, 255, 26),
                    0, Math.max(1, h / 2),
                    new Color(255, 255, 255, 0)));
            g.fillRoundRect(1, 1, Math.max(0, w - 1), Math.max(1, h / 2), radius, radius);

            g.setPaint(new GradientPaint(0, Math.max(1, h / 2),
                    new Color(Theme.accent().getRed(), Theme.accent().getGreen(), Theme.accent().getBlue(), 0),
                    w, h,
                    new Color(Theme.accent().getRed(), Theme.accent().getGreen(), Theme.accent().getBlue(), 16)));
            g.fillRoundRect(1, 1, Math.max(0, w - 1), Math.max(0, h - 1), radius, radius);
        } else {
            g.setColor(base);
            g.fillRoundRect(0, 0, w, h, radius, radius);
        }

        Object outline = getClientProperty("outlineColor");
        Color border = outline instanceof Color c ? c : Theme.border();
        if (IntelligenceGlassSettings.enabled()) {
            border = new Color(border.getRed(), border.getGreen(), border.getBlue(), 225);
        }
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(0, 0, w, h, radius, radius);
        g.dispose();
        super.paintComponent(g0);
    }
}
