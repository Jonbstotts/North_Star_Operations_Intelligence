package com.wtm.ui;

import java.awt.Component;

/** Applies the current NorthStar theme to a newly injected component tree. */
public final class NorthStarThemeCompliance {
    private NorthStarThemeCompliance(){}
    public static void apply(Component component){
        if(component!=null)ThemeStyler.apply(component,Theme.active());
    }
}
