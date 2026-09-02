package com.wtm.ui;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;

import javax.swing.*;
import java.awt.*;

/** Single owner for installing the Swing look-and-feel. */
public final class ThemeManager {
    private ThemeManager(){}

    /*
     * Older builds wrote the North Star overlay through UIManager.put(). Those
     * values live in Swing's developer-default table and survive subsequent
     * look-and-feel installs, which caused North Star colors to leak into every
     * FlatLaf theme. Clear only the keys North Star historically owned before
     * each install, then apply branded values to the current LAF defaults.
     */
    private static final String[] BRAND_KEYS={
            "Component.accentColor",
            "Component.focusColor",
            "TabbedPane.underlineColor",
            "ProgressBar.foreground",
            "TextComponent.selectionBackground",
            "Table.selectionBackground",
            "List.selectionBackground",
            "Panel.background",
            "Label.foreground"
    };

    public static void install(AppTheme theme){
        AppTheme resolved=theme==null?AppTheme.NORTH_STAR:theme;
        clearLegacyBrandOverrides();

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

        if(!installed){
            clearLegacyBrandOverrides();
            FlatDarkLaf.setup();
        }
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

    private static void clearLegacyBrandOverrides(){
        for(String key:BRAND_KEYS)
            UIManager.put(key,null);
    }

    /**
     * Brand/holiday overlays belong to the currently installed look-and-feel.
     * Writing directly to its defaults makes the override disappear naturally
     * when another FlatLaf is installed instead of contaminating future themes.
     */
    private static void applyBrandOverlay(AppTheme theme){
        UIDefaults defaults=UIManager.getLookAndFeelDefaults();
        defaults.put("Component.accentColor",theme.accent());
        defaults.put("Component.focusColor",theme.accent());
        defaults.put("TabbedPane.underlineColor",theme.accent());
        defaults.put("ProgressBar.foreground",theme.accent());
        defaults.put("TextComponent.selectionBackground",theme.accent());
        defaults.put("Table.selectionBackground",theme.accent());
        defaults.put("List.selectionBackground",theme.accent());
        defaults.put("Panel.background",theme.bg());
        defaults.put("Label.foreground",theme.text());
    }
}
