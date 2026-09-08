import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.media.StartupPlaybackCacheService;
import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

/** Verifies real MP4 -> lossless display-cache preparation and invalidation. */
public final class StartupPlaybackCacheSmokeTest {
    private StartupPlaybackCacheSmokeTest(){}

    public static void main(String[] args) throws Exception {
        String previousHome=System.getProperty("user.home");
        Path home=Files.createTempDirectory("northstar-startup-playback-smoke-");
        try{
            System.setProperty("user.home",home.toString());
            Path startup=MediaService.directory(MediaCategory.STARTUP_MEDIA);
            Files.createDirectories(startup);
            Path video=startup.resolve("cache-smoke.mp4");

            AWTSequenceEncoder encoder=AWTSequenceEncoder.create30Fps(video.toFile());
            for(int i=0;i<18;i++){
                BufferedImage frame=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=frame.createGraphics();
                try{
                    g.setColor(new Color((i*13)%255,(i*29)%255,(i*47)%255));
                    g.fillRect(0,0,frame.getWidth(),frame.getHeight());
                    g.setColor(Color.WHITE);
                    g.fillOval(20+i*8,60,28,28);
                }finally{g.dispose();}
                encoder.encodeImage(frame);
            }
            encoder.finish();

            Dimension target=new Dimension(240,135);
            final int[] latestProgress={-1};
            StartupPlaybackCacheService.Cache cache=StartupPlaybackCacheService.ensure(
                    video,target,value->latestProgress[0]=value);
            require(cache!=null,"cache was not created");
            require(cache.width()==240&&cache.height()==135,"cache dimensions changed");
            require(cache.frameCount()>=15,"too many presentation frames were discarded");
            require(latestProgress[0]==100,"cache preparation did not report completion");

            StartupPlaybackCacheService.Cache fresh=
                    StartupPlaybackCacheService.openFresh(video,target);
            require(fresh!=null,"fresh cache was not reusable");

            int count=0;
            double lastTimestamp=-1;
            BufferedImage last=null;
            try(StartupPlaybackCacheService.Reader reader=
                        StartupPlaybackCacheService.openReader(fresh)){
                StartupPlaybackCacheService.Frame frame;
                while((frame=reader.next())!=null){
                    require(frame.image().getWidth()==240&&frame.image().getHeight()==135,
                            "cached frame dimensions changed");
                    require(frame.timestamp()>=lastTimestamp,
                            "cached frame timestamps are not monotonic");
                    lastTimestamp=frame.timestamp();
                    last=frame.image();
                    count++;
                }
            }
            require(count==fresh.frameCount(),"reader frame count does not match metadata");
            require(last!=null,"cache reader returned no frames");

            long changed=Files.getLastModifiedTime(video).toMillis()+5_000L;
            Files.setLastModifiedTime(video,FileTime.fromMillis(changed));
            require(StartupPlaybackCacheService.openFresh(video,target)==null,
                    "changed source video did not invalidate playback cache");

            System.out.println("STARTUP_PLAYBACK_CACHE_SMOKE_OK "+count+" frames");
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
