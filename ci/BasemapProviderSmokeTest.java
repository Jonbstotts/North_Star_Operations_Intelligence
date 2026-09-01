import com.wtm.config.AppConfig;
import com.wtm.map.BasemapProvider;

import java.awt.image.BufferedImage;

public final class BasemapProviderSmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private static int luminance(int argb){
        int r=(argb>>>16)&0xff;
        int g=(argb>>>8)&0xff;
        int b=argb&0xff;
        return (int)Math.round(0.2126*r+0.7152*g+0.0722*b);
    }

    public static void main(String[] args){
        AppConfig defaults=new AppConfig();
        require("OPENSTREETMAP".equals(defaults.basemapProvider),
                "OpenStreetMap must remain the zero-cost development default");

        BasemapProvider provider=BasemapProvider.fromId(defaults.basemapProvider);
        require(provider==BasemapProvider.OPENSTREETMAP,
                "default provider did not resolve to OpenStreetMap");
        require("https://tile.openstreetmap.org/9/123/456.png".equals(
                        provider.tileUrl(9,123,456)),
                "OpenStreetMap endpoint does not match the canonical tile URL");
        require(provider.attribution().contains("OpenStreetMap contributors"),
                "visible OpenStreetMap attribution is missing");
        require(BasemapProvider.fromId("unknown")==BasemapProvider.OPENSTREETMAP,
                "unknown provider IDs must fail safely to the free default");

        BufferedImage sample=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        sample.setRGB(0,0,0xffffffff);
        sample.setRGB(1,0,0xff000000);
        BufferedImage dark=provider.renderTile(sample,true);
        require(dark!=sample,"dark rendering must not mutate the cached source tile");
        require(dark.getWidth()==2&&dark.getHeight()==1,
                "dark rendering changed tile dimensions");
        require(luminance(dark.getRGB(0,0))<70,
                "light map background did not become dark enough");
        require(luminance(dark.getRGB(1,0))>luminance(dark.getRGB(0,0))+70,
                "dark source labels/features did not become legible on dark background");
        require(provider.renderTile(sample,false)==sample,
                "light mode should preserve the raw provider tile");

        System.out.println("BASEMAP_PROVIDER_SMOKE_OK");
    }
}
