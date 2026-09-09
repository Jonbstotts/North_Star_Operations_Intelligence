package com.wtm.ui;

import java.awt.*;

/** Pure geometry policy for the branded startup sign-in surface. */
public final class LoginSplashLayout {
    private static final int MAX_WIDTH=920;
    private static final int MIN_WIDTH=660;
    private static final int DESIRED_LOGIN_HEIGHT=330;
    private static final int MIN_LOGIN_HEIGHT=300;

    private LoginSplashLayout(){}

    public record Geometry(
            Rectangle windowBounds,
            Dimension artworkSize,
            int loginHeight
    ){}

    /** Fits the complete logo + authentication surface inside the usable area. */
    public static Geometry fit(Rectangle usable,int sourceWidth,int sourceHeight){
        Rectangle area=usable==null?new Rectangle(0,0,1440,900):new Rectangle(usable);
        int sw=Math.max(1,sourceWidth);
        int sh=Math.max(1,sourceHeight);
        double aspect=sw/(double)sh;

        int maxW=Math.max(1,(int)Math.floor(area.width*.82));
        int maxH=Math.max(1,(int)Math.floor(area.height*.88));
        int width=Math.min(MAX_WIDTH,maxW);
        if(width<MIN_WIDTH&&maxW>=MIN_WIDTH)width=MIN_WIDTH;
        width=Math.max(1,Math.min(width,maxW));

        int loginHeight=Math.min(
                DESIRED_LOGIN_HEIGHT,
                Math.max(MIN_LOGIN_HEIGHT,(int)Math.round(maxH*.38))
        );
        loginHeight=Math.min(loginHeight,Math.max(1,maxH/2));

        int artworkHeight=Math.max(1,(int)Math.round(width/aspect));
        if(artworkHeight+loginHeight>maxH){
            artworkHeight=Math.max(1,maxH-loginHeight);
            width=Math.min(maxW,Math.max(1,(int)Math.round(artworkHeight*aspect)));
            artworkHeight=Math.max(1,(int)Math.round(width/aspect));
        }

        int totalHeight=Math.min(maxH,artworkHeight+loginHeight);
        loginHeight=Math.max(1,totalHeight-artworkHeight);
        int x=area.x+Math.max(0,(area.width-width)/2);
        int y=area.y+Math.max(0,(area.height-totalHeight)/2);

        return new Geometry(
                new Rectangle(x,y,width,totalHeight),
                new Dimension(width,artworkHeight),
                loginHeight
        );
    }

    /** Resolves the monitor work area nearest the supplied owner. */
    public static Rectangle usableBounds(Window owner){
        GraphicsConfiguration gc=owner==null?null:owner.getGraphicsConfiguration();
        if(gc==null){
            GraphicsEnvironment ge=GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device=ge.getDefaultScreenDevice();
            gc=device==null?null:device.getDefaultConfiguration();
        }
        if(gc==null)return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        Rectangle bounds=new Rectangle(gc.getBounds());
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x+insets.left,
                bounds.y+insets.top,
                Math.max(1,bounds.width-insets.left-insets.right),
                Math.max(1,bounds.height-insets.top-insets.bottom)
        );
    }
}
