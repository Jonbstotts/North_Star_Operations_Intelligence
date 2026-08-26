package com.wtm.modular.ui;

import com.wtm.modular.core.ModuleRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-driven module/sidebar lifecycle.
 *
 * v2.1.13 keeps the completed first-paint sidebar path and normalizes the
 * final sidebar visual state after module injection. Injected routes can carry
 * a real Swing border from their source button implementation; disabling only
 * rollover is therefore insufficient. All sidebar route buttons use the same
 * empty layout border, while active selection remains owned by the NorthStar
 * sidebar painter/client property.
 */
public final class ModuleUiCoordinatorLifecycle {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static Object coordinator;
    private static Method install;
    private static Method apply;
    private static Set<JFrame> installed;
    private static Field routeButtonsField;

    private ModuleUiCoordinatorLifecycle(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        resolveCoordinator();
        if(coordinator==null)return;

        if(coordinator instanceof ModuleRegistry.Listener listener)
            ModuleRegistry.get().addListener(listener);

        for(Window window:Window.getWindows())
            if(window instanceof JFrame frame)installFrame(frame,false);

        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED
                    &&we.getWindow() instanceof JFrame frame){
                installFrame(frame,true);
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

            routeButtonsField=Class.forName("com.wtm.ui.OperationsWorkspaceFrame")
                    .getDeclaredField("sidebarRouteButtons");
            routeButtonsField.setAccessible(true);
        }catch(Exception ex){
            coordinator=null;
            System.err.println("NorthStar module lifecycle initialization failed: "+ex.getMessage());
        }
    }

    private static void installFrame(JFrame frame,boolean firstOpen){
        if(frame==null||!frame.isDisplayable())return;
        if(!"com.wtm.ui.OperationsWorkspaceFrame".equals(frame.getClass().getName()))return;
        if(coordinator==null||install==null||apply==null||installed==null)return;

        JComponent previousGlass=null;
        boolean previousGlassVisible=false;
        JPanel preparation=null;
        try{
            if(firstOpen){
                Component glass=frame.getGlassPane();
                if(glass instanceof JComponent jc){
                    previousGlass=jc;
                    previousGlassVisible=jc.isVisible();
                }
                preparation=preparationPane();
                frame.setGlassPane(preparation);
                preparation.setVisible(true);
            }

            if(!installed.contains(frame)){
                install.invoke(coordinator,frame);
                installed.add(frame);
            }
            apply.invoke(coordinator,frame);
            normalizeSidebarVisualState(frame);

            frame.getRootPane().revalidate();
            frame.validate();
            frame.doLayout();
            frame.repaint();
        }catch(Exception ex){
            System.err.println("NorthStar module sidebar initialization failed: "+ex.getMessage());
        }finally{
            if(firstOpen&&preparation!=null){
                JPanel finalPreparation=preparation;
                JComponent finalPreviousGlass=previousGlass;
                boolean finalPreviousVisible=previousGlassVisible;
                SwingUtilities.invokeLater(()->{
                    normalizeSidebarVisualState(frame);
                    finalPreparation.setVisible(false);
                    if(finalPreviousGlass!=null&&finalPreviousGlass!=finalPreparation){
                        frame.setGlassPane(finalPreviousGlass);
                        finalPreviousGlass.setVisible(finalPreviousVisible);
                    }
                    frame.getRootPane().revalidate();
                    frame.repaint();
                });
            }
        }
    }

    private static JPanel preparationPane(){
        JPanel pane=new JPanel(new GridBagLayout());
        pane.setOpaque(true);
        pane.setBackground(com.wtm.ui.Theme.bg());
        JLabel status=new JLabel("Preparing Operations Workspace...");
        status.setForeground(com.wtm.ui.Theme.muted());
        status.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));
        pane.add(status);
        return pane;
    }

    /**
     * Remove stale rollover/focus state and real Swing borders from every route
     * button. The selected blue state is painted from northstar.sidebar.active,
     * so borderPainted is not part of route selection and must not remain as an
     * accidental inactive highlight on injected routes such as Intelligence.
     */
    private static void normalizeSidebarVisualState(JFrame frame){
        if(routeButtonsField==null)return;
        try{
            Object value=routeButtonsField.get(frame);
            if(!(value instanceof Map<?,?> map))return;
            for(Object item:map.values()){
                if(!(item instanceof JButton button))continue;
                button.setRolloverEnabled(false);
                button.getModel().setRollover(false);
                button.getModel().setArmed(false);
                button.getModel().setPressed(false);
                button.setFocusPainted(false);
                button.setFocusable(false);
                button.setBorder(new EmptyBorder(9,12,9,12));
                button.setBorderPainted(false);
                button.repaint();
            }
        }catch(Exception ignored){}
    }
}
