package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Applies only North Star application-specific presentation. FlatLaf owns
 * standard Swing controls, popups, fields, tabs, tables, lists and scrollbars.
 */
public final class ThemeStyler {
    private ThemeStyler(){}

    public static void apply(Component component,AppTheme theme){
        if(component==null)return;
        AppTheme resolved=theme==null?Theme.active():theme;
        ThemeManager.refresh(component);
        applyApplicationPresentation(component,resolved);
    }

    private static void applyApplicationPresentation(Component component,AppTheme theme){
        if(component instanceof JDialog dialog && dialog.getRootPane()!=null){
            dialog.getRootPane().putClientProperty(
                    "apple.awt.windowAppearance",
                    theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        }

        if(component instanceof JButton button){
            if(isSidebarRouteButton(button)){
                button.setFocusPainted(false);
                button.setFocusable(false);
                button.setOpaque(false);
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setBorder(new EmptyBorder(9,12,9,12));
            }else if(Boolean.TRUE.equals(button.getClientProperty("primaryAction"))){
                button.setBackground(Theme.accent());
                button.setForeground(readableText(Theme.accent()));
            }
        }else if(component instanceof RoundedPanel rounded){
            rounded.setBackground(Theme.panel());
            rounded.putClientProperty("outlineColor",Theme.border());
        }else if(component instanceof JScrollPane scroll){
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.getHorizontalScrollBar().setUnitIncrement(18);
        }

        if(component instanceof Container container)
            for(Component child:container.getComponents())
                applyApplicationPresentation(child,theme);
    }

    private static boolean isSidebarRouteButton(JButton button){
        return Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.route"));
    }

    private static Color readableText(Color background){
        if(background==null)return Color.WHITE;
        double luminance=(0.2126*background.getRed())
                +(0.7152*background.getGreen())
                +(0.0722*background.getBlue());
        return luminance>150?new Color(20,22,24):Color.WHITE;
    }
}
