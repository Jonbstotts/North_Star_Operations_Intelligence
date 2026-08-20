package com.wtm.ui;

import java.awt.*;

/**
 * Central theme facade used throughout the dashboard.
 *
 * The active preset is changed when configuration is applied. Existing widget
 * code can continue using Theme.accent()/danger()/etc., while surfaces come
 * from the selected AppTheme.
 */
public final class Theme {
    private Theme() {}

    private static volatile AppTheme active=AppTheme.NORTH_STAR;

    public static void setActive(String id){ active=AppTheme.fromId(id); }
    public static AppTheme active(){return active;}

    public static Color bg(){return active.bg();}
    public static Color panel(){return active.panel();}
    public static Color panel2(){return active.panel2();}
    public static Color border(){return active.border();}
    public static Color text(){return active.text();}
    public static Color muted(){return active.muted();}
    public static Color accent(){return active.accent();}

    // Compatibility overloads retained for older components/source extensions.
    public static Color bg(boolean dark){return dark?AppTheme.DARK.bg():AppTheme.LIGHT.bg();}
    public static Color panel(boolean dark){return dark?AppTheme.DARK.panel():AppTheme.LIGHT.panel();}
    public static Color panel2(boolean dark){return dark?AppTheme.DARK.panel2():AppTheme.LIGHT.panel2();}
    public static Color border(boolean dark){return dark?AppTheme.DARK.border():AppTheme.LIGHT.border();}
    public static Color text(boolean dark){return dark?AppTheme.DARK.text():AppTheme.LIGHT.text();}
    public static Color muted(boolean dark){return dark?AppTheme.DARK.muted():AppTheme.LIGHT.muted();}

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
