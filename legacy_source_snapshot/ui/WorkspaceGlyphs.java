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
    private static final Map<String, Icon> RENDER_CACHE=
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
                ignored->new HighDpiGlyphIcon(
                        tintFullResolution(source,color),
                        size
                )
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

    private static BufferedImage tintFullResolution(
            BufferedImage source,
            Color tint
    ){
        BufferedImage out=new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        int rgb=tint.getRGB()&0x00FFFFFF;
        for(int y=0;y<source.getHeight();y++){
            for(int x=0;x<source.getWidth();x++){
                int argb=source.getRGB(x,y);
                int alpha=(argb>>>24)&0xFF;
                if(alpha==0)continue;

                int r=(argb>>>16)&0xFF;
                int g=(argb>>>8)&0xFF;
                int b=argb&0xFF;
                int luminance=(r*299+g*587+b*114)/1000;
                int effectiveAlpha=(alpha*(255-luminance))/255;
                if(effectiveAlpha<alpha/8)
                    effectiveAlpha=alpha;

                out.setRGB(x,y,(effectiveAlpha<<24)|rgb);
            }
        }
        return out;
    }

    /**
     * Retina/HiDPI-safe glyph. Swing receives logical dimensions, while the
     * full-resolution source is painted directly through the device transform.
     * This avoids creating a 40 px bitmap that macOS later enlarges.
     */
    private static final class HighDpiGlyphIcon implements Icon {
        private final BufferedImage source;
        private final int logicalSize;

        private HighDpiGlyphIcon(BufferedImage source,int logicalSize){
            this.source=source;
            this.logicalSize=logicalSize;
        }

        @Override public int getIconWidth(){ return logicalSize; }
        @Override public int getIconHeight(){ return logicalSize; }

        @Override public void paintIcon(
                Component component,
                Graphics graphics,
                int x,
                int y
        ){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
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
}
