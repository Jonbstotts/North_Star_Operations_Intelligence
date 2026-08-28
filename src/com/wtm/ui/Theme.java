package com.wtm.ui;

import java.awt.*;

/** Central semantic-color facade backed by the installed FlatLaf. */
public final class Theme {
    private Theme() {}

    private static volatile AppTheme active=AppTheme.NORTH_STAR;

    public static synchronized void setActive(String id){
        active=AppTheme.fromId(id);
        ThemeManager.install(active);
    }
    public static AppTheme active(){return active;}

    public static Color bg(){return ThemeManager.uiColor("Panel.background",active.bg());}
    public static Color panel(){return ThemeManager.uiColor("Panel.background",active.panel());}
    public static Color panel2(){return ThemeManager.uiColor("TextField.background",active.panel2());}
    public static Color border(){return ThemeManager.uiColor("Component.borderColor",active.border());}
    public static Color text(){return ThemeManager.uiColor("Label.foreground",active.text());}
    public static Color muted(){return ThemeManager.uiColor("Label.disabledForeground",active.muted());}
    public static Color accent(){return ThemeManager.uiColor("Component.accentColor",active.accent());}

    public static Color bg(boolean dark){return dark?AppTheme.FLATLAF_DARK.bg():AppTheme.FLATLAF_LIGHT.bg();}
    public static Color panel(boolean dark){return dark?AppTheme.FLATLAF_DARK.panel():AppTheme.FLATLAF_LIGHT.panel();}
    public static Color panel2(boolean dark){return dark?AppTheme.FLATLAF_DARK.panel2():AppTheme.FLATLAF_LIGHT.panel2();}
    public static Color border(boolean dark){return dark?AppTheme.FLATLAF_DARK.border():AppTheme.FLATLAF_LIGHT.border();}
    public static Color text(boolean dark){return dark?AppTheme.FLATLAF_DARK.text():AppTheme.FLATLAF_LIGHT.text();}
    public static Color muted(boolean dark){return dark?AppTheme.FLATLAF_DARK.muted():AppTheme.FLATLAF_LIGHT.muted();}

    public static Color good(){ return new Color(54,177,91); }
    public static Color warn(){ return new Color(242,177,30); }
    public static Color danger(){ return new Color(229,72,77); }
    public static Color sun(){ return new Color(255,183,0); }
    public static Color cloud(){ return new Color(179,215,232); }
    public static Color rain(){ return new Color(60,151,229); }

    public static String hex(Color c){
        return String.format("#%02x%02x%02x",c.getRed(),c.getGreen(),c.getBlue());
    }
}
