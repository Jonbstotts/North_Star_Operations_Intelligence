package com.wtm.ui.foundation;

import java.awt.*;
import java.awt.event.*;

/** Synchronous, event-driven UI Foundation runtime; no post-paint polling. */
public final class UiFoundationRuntime {
    private static volatile boolean started;
    private UiFoundationRuntime(){}
    public static synchronized void start(){
        if(started)return;
        started=true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof ContainerEvent ce&&ce.getID()==ContainerEvent.COMPONENT_ADDED)
                UiFoundation.apply(ce.getChild());
            else if(event instanceof WindowEvent we&&we.getID()==WindowEvent.WINDOW_OPENED)
                UiFoundation.apply(we.getWindow());
        },AWTEvent.CONTAINER_EVENT_MASK|AWTEvent.WINDOW_EVENT_MASK);
        for(Window w:Window.getWindows())if(w!=null&&w.isDisplayable())UiFoundation.apply(w);
    }
}
