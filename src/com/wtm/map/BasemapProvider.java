package com.wtm.map;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Owns base-map provider identity, endpoint construction, attribution, cache
 * identity, and optional local presentation transforms.
 *
 * OpenStreetMap Standard is the zero-cost development default. TileMapPanel
 * composes map layers but does not know vendor URLs or vendor-specific rules,
 * so a contracted or self-hosted production provider can be added without
 * rewriting the renderer.
 */
public enum BasemapProvider {
    OPENSTREETMAP(
            "OPENSTREETMAP",
            "OpenStreetMap Standard (no key)",
            "© OpenStreetMap contributors",
            "osm-standard-v1"
    );

    private final String id;
    private final String displayName;
    private final String attribution;
    private final String cacheNamespace;

    BasemapProvider(
            String id,
            String displayName,
            String attribution,
            String cacheNamespace
    ){
        this.id=id;
        this.displayName=displayName;
        this.attribution=attribution;
        this.cacheNamespace=cacheNamespace;
    }

    public String id(){return id;}
    public String displayName(){return displayName;}
    public String attribution(){return attribution;}
    public String cacheNamespace(){return cacheNamespace;}

    public String tileUrl(int zoom,int x,int y){
        return switch(this){
            case OPENSTREETMAP -> "https://tile.openstreetmap.org/"
                    +zoom+"/"+x+"/"+y+".png";
        };
    }

    /**
     * OSM publishes one standard raster style. North Star derives its dark
     * dashboard presentation locally so dark mode does not depend on a second
     * unauthenticated tile service. The raw provider tile remains unchanged in
     * the persistent cache.
     */
    public BufferedImage renderTile(BufferedImage source,boolean darkMode){
        if(source==null||!darkMode)return source;

        BufferedImage out=new BufferedImage(
                source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);

        for(int y=0;y<source.getHeight();y++){
            for(int x=0;x<source.getWidth();x++){
                int argb=source.getRGB(x,y);
                int alpha=(argb>>>24)&0xff;
                int red=(argb>>>16)&0xff;
                int green=(argb>>>8)&0xff;
                int blue=argb&0xff;

                double luminance=(0.2126*red+0.7152*green+0.0722*blue)/255.0;
                int gray=(int)Math.round(luminance*255.0);
                int base=(int)Math.round(20.0+(1.0-luminance)*145.0);

                int r=clamp(base+(int)Math.round((red-gray)*0.18));
                int g=clamp(base+3+(int)Math.round((green-gray)*0.18));
                int b=clamp(base+8+(int)Math.round((blue-gray)*0.22));

                out.setRGB(x,y,(alpha<<24)|(r<<16)|(g<<8)|b);
            }
        }
        return out;
    }

    public static BasemapProvider fromId(String value){
        String normalized=value==null?"":value.trim().toUpperCase(Locale.ROOT);
        for(BasemapProvider provider:values())
            if(provider.id.equals(normalized))return provider;
        return OPENSTREETMAP;
    }

    @Override public String toString(){return displayName;}

    private static int clamp(int value){
        return Math.max(0,Math.min(255,value));
    }
}
