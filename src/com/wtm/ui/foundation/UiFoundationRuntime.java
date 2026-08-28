package com.wtm.ui.foundation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Window-boundary UI Foundation runtime.
 *
 * The foundation is applied once when a Swing window opens, plus once to any
 * displayable windows that already exist when the runtime starts. Component
 * additions are deliberately not observed: route/page constructors and
 * ThemeStyler own styling for content mounted after a window is open.
 */
public final class UiFoundationRuntime {
    private static volatile boolean started;

    private UiFoundationRuntime(){}

    public static synchronized void start(){
        if(started)return;
        started=true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED){
                applyWindow(we.getWindow());
            }
        },AWTEvent.WINDOW_EVENT_MASK);

        for(Window window:Window.getWindows())
            if(window!=null&&window.isDisplayable())applyWindow(window);
    }

    private static void applyWindow(Window window){
        if(window==null)return;
        markSidebarRoutes(window);
        UiFoundation.apply(window);
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
