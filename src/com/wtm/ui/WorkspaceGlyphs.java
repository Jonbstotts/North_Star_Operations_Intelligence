package com.wtm.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asset-backed glyph loader for Operations Calendar and Team Celebrations.
 *
 * Exact user-approved glyphs can be bundled under /glyphs without touching
 * dashboard layout/rendering code. PNG alpha is preserved and the visible
 * pixels are tinted to the active theme text color so the same artwork works
 * in North Star and holiday overlays.
 *
 * If an approved asset has not been installed yet, callers may fall back to
 * the existing Java2D semantic glyph renderer.
 */
public final class WorkspaceGlyphs {
    private static final Map<String, BufferedImage> SOURCE_CACHE=
            new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> RENDER_CACHE=
            new ConcurrentHashMap<>();

    private WorkspaceGlyphs(){}

    public static Icon icon(String key,int size,Color tint){
        if(key==null||key.isBlank()||size<8)return null;
        String normalized=key.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+","_");
        BufferedImage source=source(normalized);
        if(source==null)return null;

        Color color=tint==null?Color.WHITE:tint;
        String cacheKey=normalized+":"+size+":"+color.getRGB();
        return RENDER_CACHE.computeIfAbsent(
                cacheKey,
                ignored->new ImageIcon(render(source,size,color))
        );
    }

    private static BufferedImage source(String key){
        if(SOURCE_CACHE.containsKey(key))
            return SOURCE_CACHE.get(key);

        String path="/glyphs/"+key+".png";
        try(InputStream in=WorkspaceGlyphs.class.getResourceAsStream(path)){
            if(in==null)return null;
            BufferedImage image=ImageIO.read(in);
            if(image!=null)SOURCE_CACHE.put(key,image);
            return image;
        }catch(Exception ex){
            return null;
        }
    }

    private static BufferedImage render(
            BufferedImage source,
            int size,
            Color tint
    ){
        BufferedImage scaled=new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=scaled.createGraphics();
        try{
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            double scale=Math.min(
                    size/(double)source.getWidth(),
                    size/(double)source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(source.getHeight()*scale));
            int x=(size-w)/2;
            int y=(size-h)/2;
            g.drawImage(source,x,y,w,h,null);
        }finally{
            g.dispose();
        }

        int rgb=tint.getRGB()&0x00FFFFFF;
        for(int y=0;y<size;y++){
            for(int x=0;x<size;x++){
                int argb=scaled.getRGB(x,y);
                int alpha=(argb>>>24)&0xFF;
                if(alpha==0)continue;

                // Preserve source alpha/luminance antialiasing while replacing
                // its color with the active theme's approved glyph color.
                int r=(argb>>>16)&0xFF;
                int gg=(argb>>>8)&0xFF;
                int b=argb&0xFF;
                int luminance=(r*299+gg*587+b*114)/1000;
                int effectiveAlpha=(alpha*(255-luminance))/255;

                // Assets may be supplied white-on-transparent or
                // black-on-transparent. If black-pixel conversion produced
                // almost no alpha, keep the original source alpha instead.
                if(effectiveAlpha<alpha/8)
                    effectiveAlpha=alpha;

                scaled.setRGB(
                        x,y,(effectiveAlpha<<24)|rgb);
            }
        }
        return scaled;
    }
}
