package com.wtm.ui.foundation;

import com.wtm.ui.Theme;
import com.wtm.ui.ThemeStyler;
import java.awt.Component;
import javax.swing.JComponent;

/** Shared entry point for the NorthStar UI Foundation runtime. */
public final class UiFoundation {
    public static final String SKIP="northstar.ui.skip";
    private UiFoundation(){}

    public static void apply(Component component){
        if(component==null)return;
        if(component instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty(SKIP)))return;
        ThemeStyler.apply(component,Theme.active());
    }
}
