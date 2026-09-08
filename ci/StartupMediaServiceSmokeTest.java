import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.media.StartupMediaService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

public final class StartupMediaServiceSmokeTest {
    private StartupMediaServiceSmokeTest(){}

    public static void main(String[] args) throws Exception {
        String previousHome=System.getProperty("user.home");
        Path home=Files.createTempDirectory("northstar-startup-media-smoke-");
        try{
            System.setProperty("user.home",home.toString());
            Path startup=MediaService.directory(MediaCategory.STARTUP_MEDIA);
            Files.createDirectories(startup);

            Path video=startup.resolve("intro.mp4");
            Files.write(video,new byte[]{0,1,2,3});
            long base=System.currentTimeMillis()-10_000L;
            Files.setLastModifiedTime(video,FileTime.fromMillis(base));

            Path cache=startup.resolve(".poster-cache");
            Files.createDirectories(cache);
            Path poster=cache.resolve("intro.mp4.poster.png");
            BufferedImage expected=new BufferedImage(3,2,BufferedImage.TYPE_INT_ARGB);
            expected.setRGB(0,0,0xff102030);
            expected.setRGB(1,0,0xff405060);
            expected.setRGB(2,1,0xff90a0b0);
            if(!ImageIO.write(expected,"png",poster.toFile()))
                throw new AssertionError("PNG writer unavailable");
            Files.setLastModifiedTime(poster,FileTime.fromMillis(base+5_000L));

            BufferedImage loaded=StartupMediaService.cachedPosterFor(video);
            require(loaded!=null,"fresh poster cache was not loaded");
            require(loaded.getWidth()==3&&loaded.getHeight()==2,
                    "cached poster dimensions changed");
            require(loaded.getRGB(0,0)==expected.getRGB(0,0)
                            &&loaded.getRGB(1,0)==expected.getRGB(1,0)
                            &&loaded.getRGB(2,1)==expected.getRGB(2,1),
                    "cached poster pixels changed");

            // Startup cache reads must never decode stale legacy video bytes.
            // Invalid source bytes therefore return null immediately here rather
            // than throwing from JCodec on the application-launch path.
            Files.setLastModifiedTime(video,FileTime.fromMillis(base+10_000L));
            BufferedImage stale=StartupMediaService.cachedPosterFor(video);
            require(stale==null,"stale poster cache was treated as fresh");

            boolean migrationDecodeAttempted=false;
            try{
                StartupMediaService.ensurePosterFor(video);
            }catch(Exception expectedFailure){
                migrationDecodeAttempted=true;
            }
            require(migrationDecodeAttempted,
                    "background/import poster generation did not revalidate source media");

            Path outside=home.resolve("outside.mp4");
            Files.write(outside,new byte[]{1,2,3});
            boolean outsideRejected=false;
            try{
                StartupMediaService.cachedPosterFor(outside);
            }catch(Exception expectedFailure){
                outsideRejected=true;
            }
            require(outsideRejected,"startup poster cache accepted unmanaged media");

            System.out.println("STARTUP_MEDIA_SERVICE_SMOKE_OK");
        }finally{
            if(previousHome==null)System.clearProperty("user.home");
            else System.setProperty("user.home",previousHome);
            if(Files.exists(home)){
                try(var paths=Files.walk(home)){
                    paths.sorted(Comparator.reverseOrder()).forEach(path->{
                        try{Files.deleteIfExists(path);}catch(Exception ignored){}
                    });
                }
            }
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}
