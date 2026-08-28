package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Shared NorthStar theme/layout core.
 *
 * ThemeCore is deliberately passive: callers hand it a concrete component
 * tree and it normalizes that tree synchronously. Window lifecycle ownership
 * belongs to UiFoundationRuntime rather than a second global listener here.
 */
public final class ThemeCoreV216 {
    private ThemeCoreV216(){}

    /** Called synchronously for the root passed to ThemeStyler. */
    public static void applyImmediate(Component component){
        if(component instanceof JDialog dialog)polishDialog(dialog);
        normalizeLocationsHeader(component);
    }

    /**
     * The workspace shell already supplies the Locations & Routes page title.
     * Hide only the legacy in-panel title that sits beside the explanatory
     * sentence; the explanatory copy remains visible.
     */
    private static void normalizeLocationsHeader(Component root){
        Container section=findContainerContainingText(root,
                "Manage the primary facility, monitoring locations and commute routes in one place.");
        if(section==null)return;
        JLabel duplicate=findLabel(section,"Locations & Routes");
        if(duplicate!=null){
            duplicate.setVisible(false);
            duplicate.setPreferredSize(new Dimension(0,0));
            duplicate.setMinimumSize(new Dimension(0,0));
            duplicate.setMaximumSize(new Dimension(0,0));
        }
    }

    private static Container findContainerContainingText(Component root,String text){
        if(root instanceof Container c){
            if(containsText(c,text)){
                for(Component child:c.getComponents()){
                    Container nested=findContainerContainingText(child,text);
                    if(nested!=null)return nested;
                }
                return c;
            }
        }
        return null;
    }

    private static JLabel findLabel(Component root,String text){
        if(root instanceof JLabel label&&text.equals(label.getText()))return label;
        if(root instanceof Container c)
            for(Component child:c.getComponents()){
                JLabel found=findLabel(child,text);
                if(found!=null)return found;
            }
        return null;
    }

    private static boolean containsText(Component root,String text){
        if(root instanceof JLabel label){
            String value=label.getText();
            if(value!=null){
                String plain=value.replace("<html>","").replace("</html>","");
                if(text.equals(plain)||plain.contains(text))return true;
            }
        }
        if(root instanceof Container c)
            for(Component child:c.getComponents())if(containsText(child,text))return true;
        return false;
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
