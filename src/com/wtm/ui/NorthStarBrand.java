package com.wtm.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical North Star identity service.
 *
 * IMPORTANT: the symbol and primary lockup are no longer approximated with
 * Java2D geometry.  They are loaded from the approved raster reference assets
 * supplied for North Star so the ring, three-point path, focus star, horizon
 * and metallic treatment remain exactly consistent everywhere in the app.
 */
public final class NorthStarBrand {
    private static final Map<Integer,ImageIcon> SYMBOL_CACHE=
            new ConcurrentHashMap<>();
    private static final Map<Integer,ImageIcon> LOCKUP_CACHE=
            new ConcurrentHashMap<>();
    private static final Map<Integer,ImageIcon> ICON_CACHE=
            new ConcurrentHashMap<>();

    private static final BufferedImage SYMBOL_SOURCE=
            load("/brand/northstar_symbol.png");
    private static final BufferedImage LOCKUP_SOURCE=
            load("/brand/northstar_primary_lockup.png");

    private NorthStarBrand(){}

    public static final String PRODUCT_NAME="NORTH STAR";
    public static final String TAGLINE="OPERATIONS INTELLIGENCE";

    public static ImageIcon symbol(int size){
        int px=Math.max(16,size);
        return SYMBOL_CACHE.computeIfAbsent(px,key->scaleSquare(SYMBOL_SOURCE,key));
    }

    /** Exact approved vertical primary logo, including wordmark/tagline. */
    public static ImageIcon primaryLockup(int width){
        int px=Math.max(120,width);
        return LOCKUP_CACHE.computeIfAbsent(px,key->scaleWidth(LOCKUP_SOURCE,key));
    }

    /**
     * App/window icon: exact approved symbol placed on a polished rounded North
     * Star surface.  The symbol itself is never redrawn or altered.
     */
    public static ImageIcon appIcon(int size){
        int px=Math.max(32,size);
        return ICON_CACHE.computeIfAbsent(px,NorthStarBrand::composeAppIcon);
    }

    private static ImageIcon composeAppIcon(int size){
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            quality(g);
            int arc=Math.max(18,size/5);

            Shape rounded=new RoundRectangle2D.Double(
                    1,1,size-2,size-2,arc,arc);
            g.setPaint(new GradientPaint(
                    0,0,new Color(9,18,29),
                    size,size,new Color(1,6,11)));
            g.fill(rounded);

            g.setColor(new Color(72,86,104,150));
            g.setStroke(new BasicStroke(Math.max(1f,size/120f)));
            g.draw(rounded);

            int d=(int)Math.round(size*.84);
            ImageIcon symbol=symbol(d);
            int x=(size-symbol.getIconWidth())/2;
            int y=(size-symbol.getIconHeight())/2;
            g.drawImage(symbol.getImage(),x,y,null);
        }finally{
            g.dispose();
        }
        return new ImageIcon(out);
    }

    private static ImageIcon scaleSquare(BufferedImage source,int size){
        if(source==null)return new ImageIcon(new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB));

        double ratio=Math.min(
                size/(double)source.getWidth(),
                size/(double)source.getHeight());
        int w=Math.max(1,(int)Math.round(source.getWidth()*ratio));
        int h=Math.max(1,(int)Math.round(source.getHeight()*ratio));

        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            quality(g);
            g.drawImage(source,(size-w)/2,(size-h)/2,w,h,null);
        }finally{
            g.dispose();
        }
        return new ImageIcon(out);
    }

    private static ImageIcon scaleWidth(BufferedImage source,int width){
        if(source==null)return new ImageIcon();
        double ratio=width/(double)source.getWidth();
        int height=Math.max(1,(int)Math.round(source.getHeight()*ratio));
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            quality(g);
            g.drawImage(source,0,0,width,height,null);
        }finally{
            g.dispose();
        }
        return new ImageIcon(out);
    }

    private static BufferedImage load(String resource){
        try(InputStream in=NorthStarBrand.class.getResourceAsStream(resource)){
            if(in==null)throw new IllegalStateException(
                    "Missing North Star brand resource: "+resource);
            return ImageIO.read(in);
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
    }
}
