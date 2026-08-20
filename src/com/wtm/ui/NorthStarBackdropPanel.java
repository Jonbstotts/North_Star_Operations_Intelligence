package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Scalable North Star background treatment used by splash/login/setup surfaces.
 *
 * All decoration is vector-painted so it remains crisp at different DPI
 * settings without requiring additional raster artwork.
 */
public class NorthStarBackdropPanel extends JPanel {
    public NorthStarBackdropPanel(){
        setOpaque(true);
        setBackground(Theme.bg());
    }

    @Override
    protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        int w=getWidth();
        int h=getHeight();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Deep navy vertical gradient.
        g.setPaint(new GradientPaint(
                0,0,Theme.panel(),
                0,h,Theme.bg()
        ));
        g.fillRect(0,0,w,h);

        // Subtle blue glow behind the center brand mark.
        float radius=Math.max(w,h)*0.72f;
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Float(w*.5f,h*.34f),
                radius,
                new float[]{0f,.38f,1f},
                new Color[]{
                        new Color(
                                Theme.accent().getRed(),
                                Theme.accent().getGreen(),
                                Theme.accent().getBlue(),
                                BrandIdentity.northStar()?28:58
                        ),
                        new Color(
                                Theme.panel().getRed(),
                                Theme.panel().getGreen(),
                                Theme.panel().getBlue(),
                                24
                        ),
                        new Color(
                                Theme.bg().getRed(),
                                Theme.bg().getGreen(),
                                Theme.bg().getBlue(),
                                0
                        )
                }
        );
        g.setPaint(glow);
        g.fillRect(0,0,w,h);

        if(!BrandIdentity.northStar()){
            // North Star retains its subtle radar/intelligence backdrop.
            g.setStroke(new BasicStroke(1.2f));
            for(int i=0;i<4;i++){
                int diameter=220+i*115;
                int x=w/2-diameter/2;
                int y=(int)(h*.32)-diameter/2;
                Color accent=Theme.accent();
                g.setColor(new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue(),
                        Math.max(6,18-i*2)
                ));
                g.drawArc(x,y,diameter,diameter,194,152);
            }

            Color accent=Theme.accent();
            g.setColor(new Color(
                    accent.getRed(),
                    accent.getGreen(),
                    accent.getBlue(),
                    20
            ));
            g.drawLine(
                    (int)(w*.08),
                    (int)(h*.58),
                    (int)(w*.92),
                    (int)(h*.58)
            );
        }else{
            /*
             * North Star reference screens use a clean field behind the mark.
             * Keep only a soft center glow; construction/radar/horizon lines
             * are intentionally omitted so they cannot intersect the logo.
             */
            Color accent=Theme.accent();
            RadialGradientPaint focus=new RadialGradientPaint(
                    new Point2D.Float(w*.5f,h*.31f),
                    Math.max(120f,Math.min(w,h)*.42f),
                    new float[]{0f,.5f,1f},
                    new Color[]{
                            new Color(
                                    accent.getRed(),
                                    accent.getGreen(),
                                    accent.getBlue(),
                                    18
                            ),
                            new Color(
                                    accent.getRed(),
                                    accent.getGreen(),
                                    accent.getBlue(),
                                    6
                            ),
                            new Color(
                                    accent.getRed(),
                                    accent.getGreen(),
                                    accent.getBlue(),
                                    0
                            )
                    }
            );
            g.setPaint(focus);
            g.fillRect(0,0,w,h);
        }

        g.dispose();
    }
}
