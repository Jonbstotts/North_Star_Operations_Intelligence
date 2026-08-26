package com.wtm.ui.foundation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Synchronous, event-driven UI Foundation runtime; no post-paint polling.
 *
 * Workspace sidebar routes are marked as foundation-exempt before the shared
 * styler sees them. Their active/inactive appearance is owned by the workspace
 * route painter, not NorthStarButtonUI.
 */
public final class UiFoundationRuntime {
    private static volatile boolean started;

    private UiFoundationRuntime(){}

    public static synchronized void start(){
        if(started)return;
        started=true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof ContainerEvent ce
                    &&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                Component child=ce.getChild();
                markSidebarRoutes(child);
                UiFoundation.apply(child);
            }else if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED){
                Window window=we.getWindow();
                markSidebarRoutes(window);
                UiFoundation.apply(window);
            }
        },AWTEvent.CONTAINER_EVENT_MASK|AWTEvent.WINDOW_EVENT_MASK);

        for(Window window:Window.getWindows()){
            if(window!=null&&window.isDisplayable()){
                markSidebarRoutes(window);
                UiFoundation.apply(window);
            }
        }
    }

    private static void markSidebarRoutes(Component component){
        if(component==null)return;

        if(component instanceof JButton button&&isSidebarRoute(button)){
            button.putClientProperty(UiFoundation.SKIP,Boolean.TRUE);
            button.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
        }

        if(component instanceof Container container)
            for(Component child:container.getComponents())markSidebarRoutes(child);
    }

    private static boolean isSidebarRoute(JButton button){
        if(Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.route")))return true;
        if(button.getClass().getName().contains("OperationsWorkspaceFrame$RoundedSidebarButton"))return true;

        String text=button.getText();
        if(text==null)return false;
        String normalized=text.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
        return normalized.contains("data collection")
                ||normalized.contains("northstar intelligence");
    }
}
