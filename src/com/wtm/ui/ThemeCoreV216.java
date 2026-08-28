package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Shared NorthStar theme core.
 *
 * ThemeCore is deliberately passive: callers hand it a concrete component
 * tree and it synchronizes dialog presentation. Window lifecycle ownership
 * belongs to UiFoundationRuntime rather than a second global listener here.
 */
public final class ThemeCoreV216 {
    private ThemeCoreV216(){}

    /** Called synchronously for the root passed to ThemeStyler. */
    public static void applyImmediate(Component component){
        if(component instanceof JDialog dialog)polishDialog(dialog);
    }

    private static void polishDialog(JDialog dialog){
        if(dialog==null||!dialog.isDisplayable())return;
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        root.putClientProperty("apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        root.setOpaque(true);
        root.setBackground(theme.bg());
        root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){
            root.getLayeredPane().setOpaque(true);
            root.getLayeredPane().setBackground(theme.bg());
        }
        if(dialog.getContentPane() instanceof JComponent content){
            content.setOpaque(true);
            content.setBackground(theme.bg());
            content.setBorder(BorderFactory.createEmptyBorder());
        }
        dialog.setBackground(theme.bg());
        themeDialogTree(dialog,theme);
        dialog.revalidate();
        dialog.repaint();
    }

    private static void themeDialogTree(Component component,AppTheme theme){
        if(component instanceof JOptionPane pane){
            pane.setOpaque(true);
            pane.setBackground(theme.bg());
            pane.setForeground(theme.text());
            pane.setBorder(new EmptyBorder(14,16,12,16));
        }else if(component instanceof JComboBox<?> box){
            box.setOpaque(true);
            box.setBackground(theme.panel2());
            box.setForeground(theme.text());
            if(!(box.getUI() instanceof ThemedComboBoxUI))
                box.setUI(new ThemedComboBoxUI(theme));
        }else if(component instanceof JPanel panel){
            if(panel.getClass()==JPanel.class){
                panel.setOpaque(true);
                panel.setBackground(theme.bg());
            }
        }else if(component instanceof JViewport viewport){
            viewport.setOpaque(true);
            viewport.setBackground(theme.bg());
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())themeDialogTree(child,theme);
    }
}
