package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared NorthStar theme/appearance lifecycle core.
 *
 * v2.1.7 removes timer-driven layout mutation. New workspace routes are
 * normalized synchronously as Swing adds them to the component tree, before
 * the next paint, so pages do not visibly grow after opening.
 */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> NORMALIZING=
            ThreadLocal.withInitial(()->Boolean.FALSE);
    private static final String LEGACY_DASHBOARD_TEXT="← Dashboard";

    private ThemeCoreV216(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;

        long mask=AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK;
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener(){
            @Override public void eventDispatched(AWTEvent event){
                if(Boolean.TRUE.equals(NORMALIZING.get()))return;

                if(event instanceof ContainerEvent ce
                        &&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                    normalizeBeforePaint(ce.getChild());
                    Container parent=ce.getContainer();
                    removeLegacyDashboardButtons(parent);
                    return;
                }

                if(event instanceof WindowEvent we
                        &&we.getID()==WindowEvent.WINDOW_OPENED
                        &&we.getWindow() instanceof JDialog dialog){
                    polishDialog(dialog);
                }
            }
        },mask);
    }

    public static void applyImmediate(Component component){
        normalizeBeforePaint(component);
    }

    static void normalizeBeforePaint(Component component){
        if(component==null||Boolean.TRUE.equals(NORMALIZING.get()))return;
        NORMALIZING.set(Boolean.TRUE);
        try{
            removeLegacyDashboardButtons(component);
            UiFinalPolish.prepareBeforePaint(component);
        }finally{
            NORMALIZING.set(Boolean.FALSE);
        }
    }

    private static void removeLegacyDashboardButtons(Component component){
        if(!(component instanceof Container container))return;
        Component[] children=container.getComponents();
        for(Component child:children){
            if(child instanceof JButton button
                    &&LEGACY_DASHBOARD_TEXT.equals(button.getText())){
                container.remove(child);
                continue;
            }
            removeLegacyDashboardButtons(child);
        }
    }

    private static void polishDialog(JDialog dialog){
        if(!dialog.isDisplayable())return;
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String title=dialog.getTitle()==null?"":dialog.getTitle();
        root.putClientProperty("apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        root.setOpaque(true);root.setBackground(theme.bg());
        root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){
            root.getLayeredPane().setOpaque(true);
            root.getLayeredPane().setBackground(theme.bg());
        }
        if(dialog.getContentPane() instanceof JComponent content){
            content.setOpaque(true);content.setBackground(theme.bg());
            content.setBorder(BorderFactory.createEmptyBorder());
        }
        dialog.setBackground(theme.bg());
        themeDialogTree(dialog,theme);
        if(isTargetForm(title)){
            boolean tracker=title.equalsIgnoreCase("Add NorthStar Tracker");
            fit(dialog,tracker?560:760,tracker?500:555);
        }
        dialog.validate();dialog.repaint();
    }

    private static boolean isTargetForm(String title){
        return title.equalsIgnoreCase("Add NorthStar Tracker")
                ||title.equalsIgnoreCase("Add Network Location")
                ||title.equalsIgnoreCase("Add Logistics Network Location");
    }

    private static void fit(JDialog dialog,int wantedW,int wantedH){
        GraphicsConfiguration gc=dialog.getGraphicsConfiguration();
        Rectangle bounds=gc==null
                ?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                :gc.getBounds();
        Insets insets=gc==null?new Insets(0,0,0,0)
                :Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW=Math.max(440,bounds.width-insets.left-insets.right-40);
        int maxH=Math.max(420,bounds.height-insets.top-insets.bottom-40);
        dialog.setMinimumSize(new Dimension(0,0));
        dialog.setSize(Math.min(wantedW,maxW),Math.min(wantedH,maxH));
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

    private static void themeDialogTree(Component component,AppTheme theme){
        if(component instanceof JOptionPane pane){
            pane.setOpaque(true);pane.setBackground(theme.bg());
            pane.setForeground(theme.text());
            pane.setBorder(new EmptyBorder(14,16,12,16));
        }else if(component instanceof JPanel panel){
            panel.setBackground(theme.bg());
            if(panel.getClass()==JPanel.class)panel.setOpaque(true);
        }else if(component instanceof JViewport viewport){
            viewport.setOpaque(true);viewport.setBackground(theme.bg());
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())
                themeDialogTree(child,theme);
    }
}
