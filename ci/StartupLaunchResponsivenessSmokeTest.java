import com.wtm.config.AppConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.ui.StartupExperienceManager;

import java.nio.file.*;
import java.util.Comparator;

/** Ensures legacy intro poster migration can never block application launch. */
public final class StartupLaunchResponsivenessSmokeTest {
    private StartupLaunchResponsivenessSmokeTest(){}

    public static void main(String[] args) throws Exception {
        String previousHome=System.getProperty("user.home");
        Path home=Files.createTempDirectory("northstar-startup-launch-smoke-");
        try{
            System.setProperty("user.home",home.toString());
            Path startup=MediaService.directory(MediaCategory.STARTUP_MEDIA);
            Files.createDirectories(startup);
            Path video=startup.resolve("legacy-intro.mp4");
            Files.write(video,new byte[]{0,1,2,3,4,5});

            AppConfig config=new AppConfig();
            config.startupExperience="INTRO_VIDEO";
            config.startupVideoAsset=video.getFileName().toString();

            long started=System.nanoTime();
            StartupExperienceManager.preparePoster(config);
            long elapsedMillis=(System.nanoTime()-started)/1_000_000L;

            if(elapsedMillis>750L)
                throw new AssertionError(
                        "startup poster preparation blocked for "+elapsedMillis+" ms");

            System.out.println("STARTUP_LAUNCH_RESPONSIVENESS_SMOKE_OK "+elapsedMillis+"ms");
        }finally{
            if(previousHome==null)System.clearProperty("user.home");
            else System.setProperty("user.home",previousHome);
            // Give the daemon migration worker a moment to fail on the deliberately
            // invalid fixture before removing its temporary managed directory.
            Thread.sleep(150L);
            if(Files.exists(home)){
                try(var paths=Files.walk(home)){
                    paths.sorted(Comparator.reverseOrder()).forEach(path->{
                        try{Files.deleteIfExists(path);}catch(Exception ignored){}
                    });
                }
            }
        }
    }
}
