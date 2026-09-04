import com.wtm.ui.StartupPresentationLayout;

import java.awt.*;

/** Headless regression coverage for the bounded startup/login geometry policy. */
public final class StartupPresentationLayoutSmokeTest {
    private StartupPresentationLayoutSmokeTest(){}

    public static void main(String[] args){
        verify(new Rectangle(0,0,1672,941),1672,941);
        verify(new Rectangle(0,0,1920,1040),1920,1080);
        verify(new Rectangle(40,30,1280,720),1920,1080);
        System.out.println("STARTUP_PRESENTATION_LAYOUT_SMOKE_OK");
    }

    private static void verify(Rectangle usable,int sourceW,int sourceH){
        StartupPresentationLayout.Geometry geometry=
                StartupPresentationLayout.fit(usable,sourceW,sourceH);
        Rectangle bounds=geometry.windowBounds();
        Dimension artwork=geometry.artworkSize();

        require(bounds.width>0&&bounds.height>0,"non-positive window geometry");
        require(bounds.width<=960,"startup surface escaped manageable width");
        require(bounds.x>=usable.x&&bounds.y>=usable.y,"startup surface begins outside work area");
        require(bounds.x+bounds.width<=usable.x+usable.width,"startup surface exceeds work-area width");
        require(bounds.y+bounds.height<=usable.y+usable.height,"startup surface exceeds work-area height");
        require(artwork.width==bounds.width,"artwork/window widths diverged");
        require(artwork.height<bounds.height,"login area was not reserved below artwork");

        double expected=sourceW/(double)sourceH;
        double actual=artwork.width/(double)artwork.height;
        require(Math.abs(expected-actual)<.01,"artwork aspect ratio changed");
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}
