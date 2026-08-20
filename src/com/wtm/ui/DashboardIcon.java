package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Small vector icon set used by dashboard cards.
 *
 * Icons are drawn at runtime rather than loaded from image files, keeping the
 * application portable across macOS, Windows, Linux and Raspberry Pi.
 */
public final class DashboardIcon implements Icon {
    public enum Kind { SUN, PARTLY_CLOUDY, CLOUD, RAIN, STORM, CAR, ALERT, WIND, CLOCK, STATUS, MEDIA }

    private final Kind kind;
    private final int size;
    private final Color primary;

    public DashboardIcon(Kind kind, int size, Color primary){
        this.kind=kind;
        this.size=size;
        this.primary=primary;
    }

    @Override public int getIconWidth(){ return size; }
    @Override public int getIconHeight(){ return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y){
        Graphics2D g=(Graphics2D)g0.create();
        g.translate(x,y);
        double s=size/48.0;
        g.scale(s,s);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

        switch(kind){
            case SUN -> drawSun(g);
            case PARTLY_CLOUDY -> { drawSunAt(g,29,18,9); drawCloud(g,7,20); }
            case CLOUD -> drawCloud(g,5,15);
            case RAIN -> { drawCloud(g,5,10); drawRain(g); }
            case STORM -> { drawCloud(g,5,9); drawLightning(g); }
            case CAR -> drawCar(g);
            case ALERT -> drawAlert(g);
            case WIND -> drawWind(g);
            case CLOCK -> drawClock(g);
            case STATUS -> drawStatus(g);
            case MEDIA -> drawMedia(g);
        }
        g.dispose();
    }

    private void drawSun(Graphics2D g){ drawSunAt(g,24,24,10); }

    private void drawSunAt(Graphics2D g,int cx,int cy,int r){
        g.setColor(Theme.sun());
        g.fill(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            int x1=(int)(cx+Math.cos(a)*(r+4)), y1=(int)(cy+Math.sin(a)*(r+4));
            int x2=(int)(cx+Math.cos(a)*(r+10)),y2=(int)(cy+Math.sin(a)*(r+10));
            g.drawLine(x1,y1,x2,y2);
        }
    }

    private void drawCloud(Graphics2D g,int x,int y){
        g.setColor(Theme.cloud());
        g.fillRoundRect(x+7,y+10,29,15,12,12);
        g.fillOval(x+9,y+5,15,15);
        g.fillOval(x+19,y,19,19);
        g.fillOval(x+30,y+8,12,12);
    }

    private void drawRain(Graphics2D g){
        g.setColor(Theme.rain());
        g.drawLine(15,35,12,41);
        g.drawLine(25,35,22,41);
        g.drawLine(35,35,32,41);
    }

    private void drawLightning(Graphics2D g){
        g.setColor(Theme.sun());
        Path2D p=new Path2D.Double();
        p.moveTo(25,29);p.lineTo(18,39);p.lineTo(25,38);p.lineTo(21,47);p.lineTo(34,34);p.lineTo(27,35);p.closePath();
        g.fill(p);
    }

    private void drawCar(Graphics2D g){
        g.setColor(primary);
        Path2D body=new Path2D.Double();
        body.moveTo(8,28);body.lineTo(12,18);body.quadTo(14,14,19,14);
        body.lineTo(31,14);body.quadTo(36,14,38,19);body.lineTo(41,28);
        body.lineTo(41,36);body.lineTo(36,36);body.lineTo(35,32);body.lineTo(14,32);body.lineTo(13,36);body.lineTo(8,36);body.closePath();
        g.fill(body);
        g.setColor(new Color(255,255,255,205));
        g.fillRoundRect(16,18,17,7,3,3);
        g.setColor(primary.darker());
        g.fillOval(12,31,7,7);g.fillOval(31,31,7,7);
    }

    private void drawAlert(Graphics2D g){
        g.setColor(primary);
        Path2D p=new Path2D.Double();p.moveTo(24,5);p.lineTo(44,41);p.lineTo(4,41);p.closePath();g.fill(p);
        g.setColor(Color.WHITE);g.fillRoundRect(22,16,4,13,3,3);g.fillOval(22,33,4,4);
    }

    private void drawWind(Graphics2D g){
        /*
         * Classic weather-wind glyph: three horizontal gust lines terminate
         * in smooth curls, closely matching the familiar meteorological wind
         * symbol while remaining crisp at dashboard-card sizes.
         */
        g.setColor(primary);
        g.setStroke(new BasicStroke(
                3.4f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        // Upper gust: long line curling upward and back inward.
        Path2D top=new Path2D.Double();
        top.moveTo(5,18);
        top.lineTo(29,18);
        top.curveTo(35,18,38,14,38,10);
        top.curveTo(38,6,35,4,31,4);
        top.curveTo(27,4,25,7,25,10);
        top.curveTo(25,13,27,14,30,14);
        g.draw(top);

        // Center gust: longest line with a broad downward-returning curl.
        Path2D middle=new Path2D.Double();
        middle.moveTo(3,25);
        middle.lineTo(36,25);
        middle.curveTo(42,25,45,21,45,16);
        middle.curveTo(45,11,42,8,38,8);
        g.draw(middle);

        // Lower gust: shorter line curling down and back.
        Path2D bottom=new Path2D.Double();
        bottom.moveTo(6,32);
        bottom.lineTo(31,32);
        bottom.curveTo(36,32,39,35,39,39);
        bottom.curveTo(39,43,36,45,32,45);
        bottom.curveTo(28,45,26,42,26,39);
        bottom.curveTo(26,36,28,35,31,35);
        g.draw(bottom);
    }

    private void drawClock(Graphics2D g){
        g.setColor(primary);g.drawOval(7,7,34,34);g.drawLine(24,24,24,13);g.drawLine(24,24,33,28);
    }

    private void drawStatus(Graphics2D g){
        g.setColor(primary);g.fillOval(8,8,32,32);
        g.setColor(Color.WHITE);g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawLine(15,25,21,31);g.drawLine(21,31,34,17);
    }

    private void drawMedia(Graphics2D g){
        g.setColor(primary);g.fillRoundRect(6,9,36,29,5,5);
        g.setColor(Color.WHITE);
        Path2D p=new Path2D.Double();p.moveTo(20,17);p.lineTo(33,24);p.lineTo(20,31);p.closePath();g.fill(p);
    }

    public static Kind weatherKind(String condition){
        String c=condition==null?"":condition.toLowerCase();
        if(c.contains("thunder")) return Kind.STORM;
        if(c.contains("rain")||c.contains("drizzle")||c.contains("shower")) return Kind.RAIN;
        if(c.contains("partly")||c.contains("mostly clear")||c.contains("mostly sunny")) return Kind.PARTLY_CLOUDY;
        if(c.contains("cloud")||c.contains("overcast")||c.contains("fog")) return Kind.CLOUD;
        return Kind.SUN;
    }
}
