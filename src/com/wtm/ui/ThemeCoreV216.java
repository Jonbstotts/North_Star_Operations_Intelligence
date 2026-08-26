package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared NorthStar theme core.
 *
 * v2.1.11 keeps the v2.1.10 non-reentrant dialog lifecycle and adds a single,
 * synchronous layout normalization for the Modules > Organization card.
 */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final String MODULE_MANAGER_CLASS="com.wtm.modular.ui.ModuleManagerPanel";

    private ThemeCoreV216(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED
                    &&we.getWindow() instanceof JDialog dialog){
                polishDialog(dialog);
            }
        },AWTEvent.WINDOW_EVENT_MASK);
    }

    /** Called once for the root passed to ThemeStyler. */
    public static void applyImmediate(Component component){
        if(component instanceof JDialog dialog)polishDialog(dialog);
        alignModuleOrganization(component);
    }

    /**
     * GridBagLayout centers a grid when every row has weighty=0. The
     * Organization page intentionally uses fixed-height rows, so a final
     * weighty filler is added once to consume the remaining vertical space and
     * keep the form anchored neatly beneath the tabs.
     */
    private static void alignModuleOrganization(Component root){
        Component manager=findByClass(root,MODULE_MANAGER_CLASS);
        if(!(manager instanceof Container container))return;
        Container organization=findOrganizationGrid(container);
        if(!(organization instanceof JComponent jc))return;
        if(Boolean.TRUE.equals(jc.getClientProperty("northstar.organization.topAligned")))return;
        jc.putClientProperty("northstar.organization.topAligned",Boolean.TRUE);

        JPanel filler=new JPanel();
        filler.setOpaque(false);
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.gridy=99;
        c.gridwidth=2;
        c.weightx=1.0;
        c.weighty=1.0;
        c.fill=GridBagConstraints.BOTH;
        c.anchor=GridBagConstraints.NORTHWEST;
        organization.add(filler,c);
        organization.invalidate();
    }

    private static Container findOrganizationGrid(Container root){
        if(root.getLayout() instanceof GridBagLayout
                &&containsText(root,"Organization name")
                &&containsText(root,"Primary site/workspace")
                &&containsText(root,"Deployment profile"))return root;
        for(Component child:root.getComponents())
            if(child instanceof Container c){
                Container found=findOrganizationGrid(c);
                if(found!=null)return found;
            }
        return null;
    }

    private static Component findByClass(Component root,String className){
        if(root==null)return null;
        if(className.equals(root.getClass().getName()))return root;
        if(root instanceof Container c)
            for(Component child:c.getComponents()){
                Component found=findByClass(child,className);
                if(found!=null)return found;
            }
        return null;
    }

    private static boolean containsText(Component root,String text){
        if(root instanceof JLabel label&&text.equals(label.getText()))return true;
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
