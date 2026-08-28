package com.wtm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Idempotent pre-paint normalization for dynamic/legacy NorthStar surfaces.
 *
 * This class is intentionally presentation-only. It may normalize component
 * styling, but it must never reach into feature-private state, mutate feature
 * layout ownership, or change persisted/runtime configuration during paint.
 */
public final class UiFinalPolish {
    private UiFinalPolish(){}

    static void prepareBeforePaint(Component component){
        if(component==null)return;
        polishUiFoundation(component);
    }

    private static void polishUiFoundation(Component component){
        if(component instanceof Container container&&containsText(container,"NorthStar UI Foundation")){
            normalizeFoundationCombos(container);
            return;
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishUiFoundation(child);
    }

    private static void normalizeFoundationCombos(Component component){
        if(component instanceof JComboBox<?> box){
            AppTheme theme=Theme.active();
            if(!(box.getUI() instanceof ThemedComboBoxUI))box.setUI(new ThemedComboBoxUI(theme));
            box.setBackground(theme.panel2());
            box.setForeground(theme.text());
            Dimension pref=box.getPreferredSize();
            int width=Math.max(160,pref==null?160:pref.width);
            box.setPreferredSize(new Dimension(width,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMinimumSize(new Dimension(120,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE,ThemedComboBoxUI.CONTROL_HEIGHT));
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())normalizeFoundationCombos(child);
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel label&&label.getText()!=null&&stripHtml(label.getText()).contains(needle))return true;
        if(component instanceof AbstractButton button&&button.getText()!=null&&button.getText().contains(needle))return true;
        if(component instanceof Container container)
            for(Component child:container.getComponents())if(containsText(child,needle))return true;
        return false;
    }

    private static String stripHtml(String text){
        return text.replaceAll("<[^>]+>"," ");
    }
}
