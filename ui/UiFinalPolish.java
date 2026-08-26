package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Final, idempotent UI stabilization pass for dynamic/legacy surfaces that are
 * created after the normal ThemeStyler traversal (notably injected Truck
 * Tracking tabs and platform-created dialogs).
 */
public final class UiFinalPolish {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";
    private static final String TRUCK_CLASS="com.wtm.ui.TruckTrackingPanel";
    private static final String PLAYBACK_TITLE="Playback / Live Map";

    private UiFinalPolish(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        Runnable install=()->{
            Timer timer=new Timer(650,e->scan());
            timer.setInitialDelay(80);
            timer.start();
            scan();
        };
        if(SwingUtilities.isEventDispatchThread())install.run();
        else SwingUtilities.invokeLater(install);
    }

    private static void scan(){
        for(Window window:Window.getWindows()){
            if(window==null||!window.isDisplayable())continue;

            if(window instanceof JDialog dialog)
                polishDialog(dialog);

            if(window instanceof Container container){
                polishEmployeeSplits(container,false);
                polishTruckTracking(container);
                polishAppearanceScroll(container);
            }
        }
    }

    private static void polishDialog(JDialog dialog){
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String marker=theme.id();
        if(marker.equals(root.getClientProperty("northstar.finalPolish.theme")))return;

        root.putClientProperty("northstar.finalPolish.theme",marker);
        root.putClientProperty(
                "apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        dialog.setBackground(theme.bg());
        if(dialog.getContentPane()!=null)
            dialog.getContentPane().setBackground(theme.bg());
        ThemeStyler.apply(dialog,theme);
        dialog.revalidate();
        dialog.repaint();
    }

    private static void polishEmployeeSplits(Component component,boolean inEmployees){
        boolean employeeRoot=inEmployees||EMPLOYEE_CLASS.equals(component.getClass().getName());

        if(employeeRoot&&component instanceof JSplitPane split){
            if(split.getDividerSize()!=0)split.setDividerSize(0);
            split.setBorder(null);
            split.setContinuousLayout(true);
            split.setOpaque(false);
            split.setBackground(Theme.bg());
        }

        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishEmployeeSplits(child,employeeRoot);
        }
    }

    private static void polishTruckTracking(Component component){
        if(TRUCK_CLASS.equals(component.getClass().getName())&&component instanceof Container root){
            JTabbedPane tabs=findTabbedPane(root);
            if(tabs!=null)polishPlaybackTab(tabs);
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishTruckTracking(child);
        }
    }

    private static void polishPlaybackTab(JTabbedPane tabs){
        for(int i=0;i<tabs.getTabCount();i++){
            String title=tabs.getTitleAt(i);
            if(title==null||!title.equalsIgnoreCase(PLAYBACK_TITLE))continue;

            Component current=tabs.getComponentAt(i);
            if(current instanceof JComponent jc&&Boolean.TRUE.equals(
                    jc.getClientProperty("northstar.playback.scroll.fixed")))return;

            JSplitPane split=findSplit(current);
            if(split==null)return;

            Component controls=split.getTopComponent();
            Component map=split.getBottomComponent();
            if(controls==null||map==null)return;

            split.setTopComponent(null);
            split.setBottomComponent(null);

            JPanel stack=new JPanel();
            stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
            stack.setBackground(Theme.bg());
            stack.setBorder(new EmptyBorder(0,0,18,0));

            normalizeFullWidth(controls);
            normalizeFullWidth(map);

            Dimension controlPref=controls.getPreferredSize();
            int controlsHeight=Math.max(360,controlPref==null?360:controlPref.height);
            controls.setPreferredSize(new Dimension(
                    Math.max(900,controlPref==null?900:controlPref.width),controlsHeight));
            controls.setMaximumSize(new Dimension(Integer.MAX_VALUE,controlsHeight));

            Dimension mapPref=map.getPreferredSize();
            int mapHeight=Math.max(500,mapPref==null?500:mapPref.height);
            map.setPreferredSize(new Dimension(
                    Math.max(900,mapPref==null?900:mapPref.width),mapHeight));
            map.setMinimumSize(new Dimension(360,360));
            map.setMaximumSize(new Dimension(Integer.MAX_VALUE,mapHeight));

            stack.add(controls);
            stack.add(Box.createVerticalStrut(12));
            stack.add(map);

            JScrollPane scroll=new JScrollPane(
                    stack,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(Theme.bg());
            scroll.getVerticalScrollBar().setUnitIncrement(22);
            scroll.getVerticalScrollBar().setBlockIncrement(120);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);

            ThemeStyler.apply(scroll,Theme.active());
            scroll.setBorder(null);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);
            tabs.setComponentAt(i,scroll);
            tabs.revalidate();
            tabs.repaint();
            return;
        }
    }

    private static void normalizeFullWidth(Component component){
        if(component instanceof JComponent jc){
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension pref=jc.getPreferredSize();
            int h=pref==null?1:Math.max(1,pref.height);
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE,h));
        }
    }

    private static JTabbedPane findTabbedPane(Container root){
        for(Component child:root.getComponents()){
            if(child instanceof JTabbedPane tabs)return tabs;
            if(child instanceof Container nested){
                JTabbedPane found=findTabbedPane(nested);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static JSplitPane findSplit(Component component){
        if(component instanceof JSplitPane split)return split;
        if(component instanceof Container container){
            for(Component child:container.getComponents()){
                JSplitPane found=findSplit(child);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static void polishAppearanceScroll(Component component){
        if(component instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view!=null&&containsText(view,"Appearance & Display")){
                if(!Boolean.TRUE.equals(scroll.getClientProperty("northstar.appearance.bottomSafe"))){
                    scroll.putClientProperty("northstar.appearance.bottomSafe",Boolean.TRUE);
                    scroll.setViewportBorder(new EmptyBorder(0,0,52,0));
                    scroll.getVerticalScrollBar().setUnitIncrement(20);
                    scroll.getVerticalScrollBar().setBlockIncrement(120);
                    scroll.revalidate();
                }
            }
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishAppearanceScroll(child);
        }
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel label&&label.getText()!=null
                &&stripHtml(label.getText()).contains(needle))return true;
        if(component instanceof AbstractButton button&&button.getText()!=null
                &&button.getText().contains(needle))return true;
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                if(containsText(child,needle))return true;
        }
        return false;
    }

    private static String stripHtml(String text){
        return text.replaceAll("<[^>]+>"," ");
    }
}
