import com.wtm.ui.LoginSplashLayout;
import java.awt.*;

public final class LoginSplashLayoutSmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        int[][] screens={{1280,720},{1440,900},{1920,1080},{2560,1440}};
        for(int[] screen:screens){
            Rectangle usable=new Rectangle(0,0,screen[0],screen[1]);
            LoginSplashLayout.Geometry g=LoginSplashLayout.fit(usable,1672,941);
            require(usable.contains(g.windowBounds()),"login splash escaped usable bounds");
            require(g.windowBounds().width<=920,"login splash is wider than intended");
            require(g.artworkSize().width==g.windowBounds().width,
                    "artwork width does not track login splash width");
            require(g.loginHeight()>0,"login form has no reserved height");
            double sourceAspect=1672.0/941.0;
            double renderedAspect=g.artworkSize().width/(double)g.artworkSize().height;
            require(Math.abs(sourceAspect-renderedAspect)<0.02,
                    "primary logo aspect ratio was not preserved");
        }
        System.out.println("LOGIN_SPLASH_LAYOUT_SMOKE_OK");
    }
}
