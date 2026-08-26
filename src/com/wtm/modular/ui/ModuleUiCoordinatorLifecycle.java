package com.wtm.modular.ui;

import com.wtm.modular.core.ModuleRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-driven module/sidebar lifecycle.
 *
 * The former ModuleUiCoordinator.start() used a 350 ms polling timer with a
 * 700 ms initial delay. That made injected sidebar routes visibly appear after
 * the Operations Workspace was already on screen. This bridge reuses the
 * proven coordinator implementation but invokes it synchronously when the
 * workspace window opens, and registers it as the module-registry listener,
 * without starting the legacy polling timer.
 */
public final class ModuleUiCoordinatorLifecycle {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static Object coordinator;
    private static Method install;
    private static Method apply;
    private static Set<JFrame> installed;

    private ModuleUiCoordinatorLifecycle(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        resolveCoordinator();
        if(coordinator==null)return;

        if(coordinator instanceof ModuleRegistry.Listener listener)
            ModuleRegistry.get().addListener(listener);

        for(Window window:Window.getWindows())
            if(window instanceof JFrame frame)installFrame(frame);

        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED
                    &&we.getWindow() instanceof JFrame frame){
                installFrame(frame);
            }
        },AWTEvent.WINDOW_EVENT_MASK);
    }

    private static void resolveCoordinator(){
        try{
            Class<?> type=Class.forName("com.wtm.modular.ui.ModuleUiCoordinator");
            Field instance=type.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            coordinator=instance.get(null);

            install=type.getDeclaredMethod("install",JFrame.class);
            install.setAccessible(true);
            apply=type.getDeclaredMethod("apply",JFrame.class);
            apply.setAccessible(true);

            Field installedField=type.getDeclaredField("installed");
            installedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<JFrame> set=(Set<JFrame>)installedField.get(coordinator);
            installed=set;
        }catch(Exception ex){
            coordinator=null;
            System.err.println("NorthStar module lifecycle initialization failed: "+ex.getMessage());
        }
    }

    private static void installFrame(JFrame frame){
        if(frame==null||!frame.isDisplayable())return;
        if(!"com.wtm.ui.OperationsWorkspaceFrame".equals(frame.getClass().getName()))return;
        if(coordinator==null||install==null||apply==null||installed==null)return;
        try{
            if(!installed.contains(frame)){
                install.invoke(coordinator,frame);
                installed.add(frame);
            }
            apply.invoke(coordinator,frame);
            frame.validate();
            frame.repaint();
        }catch(Exception ex){
            System.err.println("NorthStar module sidebar initialization failed: "+ex.getMessage());
        }
    }
}
