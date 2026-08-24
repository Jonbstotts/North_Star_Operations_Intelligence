package com.wtm.ui;
import javax.swing.*; import java.awt.*;
public final class GlassSurfacePanel extends JPanel {
 private final int radius;
 public GlassSurfacePanel(int radius){this.radius=radius;setOpaque(false);}
 @Override protected void paintComponent(Graphics g0){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int w=getWidth()-1,h=getHeight()-1;Color base=Theme.panel();if(IntelligenceGlassSettings.enabled()){g.setColor(new Color(base.getRed(),base.getGreen(),base.getBlue(),218));g.fillRoundRect(0,0,w,h,radius,radius);g.setPaint(new GradientPaint(0,0,new Color(255,255,255,24),0,Math.max(1,h/2),new Color(255,255,255,0)));g.fillRoundRect(1,1,w-1,Math.max(1,h/2),radius,radius);}else{g.setColor(base);g.fillRoundRect(0,0,w,h,radius,radius);}g.setColor(new Color(Theme.border().getRed(),Theme.border().getGreen(),Theme.border().getBlue(),220));g.drawRoundRect(0,0,w,h,radius,radius);g.dispose();super.paintComponent(g0);}
}
