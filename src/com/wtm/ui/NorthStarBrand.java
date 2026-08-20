package com.wtm.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.*;
import javax.swing.Icon;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical North Star identity service.
 *
 * All branding is sourced from the full-resolution approved user artwork.
 * Smaller instances are generated directly from those originals with a
 * multi-step high-quality downscale. No already-scaled image is ever reused as
 * a source, which avoids the cumulative softness/pixelation seen previously.
 */
public final class NorthStarBrand {
    private static final Map<Integer,Icon> SYMBOL_CACHE=
            new ConcurrentHashMap<>();
    private static final Map<Integer,ImageIcon> LOCKUP_CACHE=
            new ConcurrentHashMap<>();
    private static final Map<Integer,ImageIcon> ICON_CACHE=
            new ConcurrentHashMap<>();

    private static final BufferedImage PRIMARY_SOURCE=
            load("/brand/northstar_primary_logo_exact.png");
    private static final BufferedImage SPLASH_SOURCE=
            load("/brand/northstar_splash_exact.png");
    private static final BufferedImage APP_ICON_SOURCE=
            load("/brand/northstar_app_icon_exact.png");

    private NorthStarBrand(){}

    public static final String PRODUCT_NAME="NORTH STAR";
    public static final String TAGLINE="OPERATIONS INTELLIGENCE";

    /** Compact identity mark used in the workspace/header. */
    public static Icon symbol(int size){
        int px=Math.max(16,size);
        return SYMBOL_CACHE.computeIfAbsent(
                px,key->new VectorNorthStarIcon(key));
    }

    /** Exact approved vertical primary logo, including wordmark/tagline. */
    public static ImageIcon primaryLockup(int width){
        int px=Math.max(120,width);
        return LOCKUP_CACHE.computeIfAbsent(px,key->{
            double ratio=key/(double)PRIMARY_SOURCE.getWidth();
            int height=Math.max(1,(int)Math.round(
                    PRIMARY_SOURCE.getHeight()*ratio));
            return new ImageIcon(scaleHighQuality(
                    PRIMARY_SOURCE,key,height));
        });
    }

    /**
     * Standalone app icon rendered from vector geometry at the requested
     * resolution. This removes the white lower-edge artifact in the legacy
     * raster and keeps Dock/taskbar icons crisp at every OS-requested size.
     */
    public static ImageIcon appIcon(int size){
        int px=Math.max(16,size);
        return ICON_CACHE.computeIfAbsent(
                px,key->new ImageIcon(renderVectorIcon(key)));
    }

    /** Full approved splash artwork used by the startup screen. */
    public static BufferedImage splashArtwork(){
        return SPLASH_SOURCE;
    }


    private static final class VectorNorthStarIcon implements Icon {
        private final int size;
        private VectorNorthStarIcon(int size){ this.size=size; }
        @Override public int getIconWidth(){ return size; }
        @Override public int getIconHeight(){ return size; }
        @Override public void paintIcon(Component c,Graphics graphics,int x,int y){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.translate(x,y);
                paintVectorMark(g,size);
            }finally{
                g.dispose();
            }
        }
    }

    private static BufferedImage renderVectorIcon(int size){
        BufferedImage image=new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try{ paintVectorMark(g,size); }
        finally{ g.dispose(); }
        return image;
    }

    /**
     * Clean North Star symbol: dark rounded field, subtle blue north-point
     * glow and a geometry-drawn three-point star/ring. There is deliberately
     * no raster border or white lower strip.
     */
    private static void paintVectorMark(Graphics2D g,int size){
        quality(g);
        float s=size;

        int arc=Math.max(6,Math.round(s*.24f));
        g.setColor(new Color(10,15,23,255));
        g.fillRoundRect(0,0,size-1,size-1,arc,arc);

        // restrained blue north-star glow
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Float(s*.50f,s*.20f),
                s*.42f,
                new float[]{0f,.35f,1f},
                new Color[]{
                        new Color(40,145,255,150),
                        new Color(18,92,190,55),
                        new Color(0,0,0,0)
                });
        g.setPaint(glow);
        g.fillRoundRect(1,1,size-3,size-3,arc,arc);

        float cx=s*.50f, cy=s*.53f;
        float radius=s*.315f;
        g.setColor(new Color(222,229,237));
        g.setStroke(new BasicStroke(
                Math.max(1.2f,s*.025f),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(
                cx-radius,cy-radius,radius*2,radius*2));

        // three-point star spokes and tapered blades
        double[] angles={-Math.PI/2, Math.PI/6, Math.PI*5/6};
        for(double a:angles){
            float ex=cx+(float)Math.cos(a)*radius*.91f;
            float ey=cy+(float)Math.sin(a)*radius*.91f;
            g.draw(new Line2D.Float(cx,cy,ex,ey));

            double perp=a+Math.PI/2;
            float bx=cx+(float)Math.cos(a)*s*.055f;
            float by=cy+(float)Math.sin(a)*s*.055f;
            float half=s*.025f;
            Path2D blade=new Path2D.Float();
            blade.moveTo(ex,ey);
            blade.lineTo(
                    bx+(float)Math.cos(perp)*half,
                    by+(float)Math.sin(perp)*half);
            blade.lineTo(
                    bx-(float)Math.cos(perp)*half,
                    by-(float)Math.sin(perp)*half);
            blade.closePath();
            g.fill(blade);
        }

        // bright north-star flare
        g.setColor(new Color(235,247,255,215));
        g.setStroke(new BasicStroke(Math.max(1f,s*.014f)));
        float flareY=cy-radius*.92f;
        g.draw(new Line2D.Float(cx,flareY-s*.09f,cx,flareY+s*.09f));
        g.draw(new Line2D.Float(cx-s*.07f,flareY,cx+s*.07f,flareY));

        // subtle neutral outline only; never a white bottom border.
        g.setColor(new Color(91,105,122,210));
        g.setStroke(new BasicStroke(Math.max(1f,s*.012f)));
        g.drawRoundRect(0,0,size-1,size-1,arc,arc);
    }

    /**
     * Paints directly from the full-resolution canonical artwork at the
     * requested logical Swing size. On Retina/HiDPI displays the Graphics2D
     * transform supplies additional device pixels, so the source is never
     * limited to a pre-rendered 42/48 px bitmap.
     */
    private static final class HighDpiIcon implements Icon {
        private final BufferedImage source;
        private final int logicalSize;

        private HighDpiIcon(BufferedImage source,int logicalSize){
            this.source=source;
            this.logicalSize=logicalSize;
        }

        @Override public int getIconWidth(){ return logicalSize; }
        @Override public int getIconHeight(){ return logicalSize; }

        @Override public void paintIcon(
                Component c,Graphics graphics,int x,int y
        ){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                quality(g);
                g.drawImage(
                        source,
                        x,y,
                        x+logicalSize,y+logicalSize,
                        0,0,source.getWidth(),source.getHeight(),
                        null
                );
            }finally{
                g.dispose();
            }
        }
    }

    private static ImageIcon containedIcon(
            BufferedImage source,
            int width,
            int height
    ){
        double ratio=Math.min(
                width/(double)source.getWidth(),
                height/(double)source.getHeight());
        int w=Math.max(1,(int)Math.round(source.getWidth()*ratio));
        int h=Math.max(1,(int)Math.round(source.getHeight()*ratio));

        BufferedImage scaled=scaleHighQuality(source,w,h);
        BufferedImage out=new BufferedImage(
                width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            quality(g);
            g.drawImage(
                    scaled,
                    (width-w)/2,
                    (height-h)/2,
                    null
            );
        }finally{
            g.dispose();
        }
        return new ImageIcon(out);
    }

    /**
     * Progressive downscale improves tiny 16–64 px identity marks compared
     * with a single large bicubic resize, especially on macOS Retina screens.
     */
    private static BufferedImage scaleHighQuality(
            BufferedImage source,
            int targetW,
            int targetH
    ){
        if(source.getWidth()==targetW&&source.getHeight()==targetH)
            return source;

        BufferedImage current=source;
        int w=current.getWidth();
        int h=current.getHeight();

        while(w/2>=targetW&&h/2>=targetH){
            w=Math.max(targetW,w/2);
            h=Math.max(targetH,h/2);
            current=resize(current,w,h);
        }

        if(current.getWidth()!=targetW||current.getHeight()!=targetH)
            current=resize(current,targetW,targetH);

        return current;
    }

    private static BufferedImage resize(
            BufferedImage source,
            int width,
            int height
    ){
        BufferedImage out=new BufferedImage(
                width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            quality(g);
            g.drawImage(source,0,0,width,height,null);
        }finally{
            g.dispose();
        }
        return out;
    }

    private static BufferedImage load(String resource){
        try(InputStream in=NorthStarBrand.class.getResourceAsStream(resource)){
            if(in==null)throw new IllegalStateException(
                    "Missing North Star brand resource: "+resource);
            BufferedImage image=ImageIO.read(in);
            if(image==null)throw new IllegalStateException(
                    "Unreadable North Star brand resource: "+resource);
            return image;
        }catch(Exception ex){
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void quality(Graphics2D g){
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }
}
