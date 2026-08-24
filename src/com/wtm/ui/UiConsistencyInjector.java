package com.wtm.ui;
import javax.swing.*;import javax.swing.border.LineBorder;import java.awt.*;
public final class UiConsistencyInjector {
 private UiConsistencyInjector(){}
 public static void start(){javax.swing.Timer timer=new javax.swing.Timer(500,e->scan());timer.setInitialDelay(300);timer.start();}
 private static void scan(){for(Window w:Window.getWindows())if(w.isDisplayable()){if(w instanceof JDialog)NorthStarThemeCompliance.apply(w);walk(w);}}
 private static void walk(Container c){for(Component x:c.getComponents()){if(x instanceof JTabbedPane t)NorthStarTabs.refreshHeaders(t);if(x instanceof JScrollPane sp){sp.getViewport().setBackground(Theme.panel());sp.setBorder(new LineBorder(Theme.border()));}if(x instanceof Container ct)walk(ct);}}
}
