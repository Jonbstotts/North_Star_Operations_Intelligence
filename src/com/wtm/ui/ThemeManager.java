package com.wtm.ui;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;

import javax.swing.*;
import java.awt.*;

/** Single owner for installing the Swing look-and-feel. */
public final class ThemeManager {
    private ThemeManager(){}

    public static void install(AppTheme theme){
        AppTheme resolved=theme==null?AppTheme.NORTH_STAR:theme;
        boolean installed=switch(resolved){
            case FLATLAF_LIGHT->FlatLightLaf.setup();
            case FLATLAF_DARK->FlatDarkLaf.setup();
            case FLATLAF_INTELLIJ->FlatIntelliJLaf.setup();
            case FLATLAF_DARCULA->FlatDarculaLaf.setup();
            case ARC->FlatArcIJTheme.setup();
            case ARC_DARK->FlatArcDarkIJTheme.setup();
            case CARBON->FlatCarbonIJTheme.setup();
            case COBALT_2->FlatCobalt2IJTheme.setup();
            case DRACULA->FlatDraculaIJTheme.setup();
            case NORD->FlatNordIJTheme.setup();
            case ONE_DARK->FlatOneDarkIJTheme.setup();
            case SOLARIZED_LIGHT->FlatSolarizedLightIJTheme.setup();
            case SOLARIZED_DARK->FlatSolarizedDarkIJTheme.setup();
            case GRADIANTO_MIDNIGHT->FlatGradiantoMidnightBlueIJTheme.setup();
            case GRADIANTO_NATURE->FlatGradiantoNatureGreenIJTheme.setup();
            case ARC_DARK_ORANGE->FlatArcDarkOrangeIJTheme.setup();
            case HIGH_CONTRAST->FlatHighContrastIJTheme.setup();
            case CHRISTMAS->FlatArcDarkIJTheme.setup();
            case HALLOWEEN->FlatDarkPurpleIJTheme.setup();
            case THANKSGIVING->FlatArcDarkOrangeIJTheme.setup();
            case INDEPENDENCE->FlatCobalt2IJTheme.setup();
            case VALENTINE->FlatLightFlatIJTheme.setup();
            case ST_PATRICKS->FlatGradiantoNatureGreenIJTheme.setup();
            case WINTER_FROST->FlatCyanLightIJTheme.setup();
            case NORTH_STAR->FlatDarculaLaf.setup();
        };
        if(!installed)FlatDarkLaf.setup();
        if(resolved.branded())applyBrandOverlay(resolved);
    }

    public static void refresh(Component root){
        if(root==null)return;
        if(root instanceof JComponent component)SwingUtilities.updateComponentTreeUI(component);
        else if(root instanceof Window window)SwingUtilities.updateComponentTreeUI(window);
    }

    public static Color uiColor(String key,Color fallback){
        Color value=UIManager.getColor(key);
        return value==null?fallback:value;
    }

    private static void applyBrandOverlay(AppTheme theme){
        UIManager.put("Component.accentColor",theme.accent());
        UIManager.put("Component.focusColor",theme.accent());
        UIManager.put("TabbedPane.underlineColor",theme.accent());
        UIManager.put("ProgressBar.foreground",theme.accent());
        UIManager.put("TextComponent.selectionBackground",theme.accent());
        UIManager.put("Table.selectionBackground",theme.accent());
        UIManager.put("List.selectionBackground",theme.accent());
        UIManager.put("Panel.background",theme.bg());
        UIManager.put("Label.foreground",theme.text());
    }
}
