package com.wtm.ui.foundation;

import java.awt.*;
import java.awt.event.WindowEvent;

/**
 * Window-boundary UI Foundation runtime.
 *
 * The foundation is applied once when a Swing window opens, plus once to any
 * displayable windows that already exist when the runtime starts. Component
 * additions and route discovery are deliberately not observed: canonical
 * sidebar buttons identify themselves and ThemeStyler owns their presentation.
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
        if(window!=null)UiFoundation.apply(window);
    }
}
