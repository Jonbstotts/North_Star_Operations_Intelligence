package com.wtm.ui;

import java.awt.*;
import java.util.*;

/** Window/taskbar branding utilities for the standalone North Star product. */
public final class ApplicationBrand {
    private ApplicationBrand(){}

    public static void applyWindowIcon(Window window){
        if(window==null)return;
        try{
            java.util.List<Image> icons=new ArrayList<>();
            for(int size:new int[]{16,32,64,128,256,512})
                icons.add(NorthStarBrand.appIcon(size).getImage());
            window.setIconImages(icons);
        }catch(Exception ignored){}
    }

    public static void applyApplicationIcon(){
        try{
            Image image=NorthStarBrand.appIcon(512).getImage();
            Taskbar taskbar=Taskbar.getTaskbar();
            if(taskbar.isSupported(Taskbar.Feature.ICON_IMAGE))
                taskbar.setIconImage(image);
        }catch(Exception ignored){}
    }

    public static Color blend(Color a,Color b,float amount){
        float t=Math.max(0f,Math.min(1f,amount));
        return new Color(
                Math.round(a.getRed()+(b.getRed()-a.getRed())*t),
                Math.round(a.getGreen()+(b.getGreen()-a.getGreen())*t),
                Math.round(a.getBlue()+(b.getBlue()-a.getBlue())*t),
                Math.round(a.getAlpha()+(b.getAlpha()-a.getAlpha())*t)
        );
    }
}
