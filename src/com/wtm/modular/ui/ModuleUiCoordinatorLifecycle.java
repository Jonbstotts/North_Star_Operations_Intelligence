package com.wtm.modular.ui;

import com.wtm.modular.core.ModuleRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-driven module/sidebar lifecycle.
 *
 * v2.1.17 establishes sidebar visual ownership before UiFoundationRuntime can
 * apply the shared NorthStarButtonUI delegate. The foundation engine correctly
 * styles ordinary application buttons, but sidebar routes use a dedicated
 * painter and must opt out through the northstar.ui.skip contract. Missing that
 * property was the final source of startup outlines and the all-routes-card
 * regression after Main Showcase Save & Apply rebuilt the workspace.
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
            }else if(event instanceof ContainerEvent ce
                    &&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                normalizeAddedComponent(ce.getChild());
            }
        },AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK);
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
            // Mark every existing route as foundation-exempt before any shared
            // window-level styling pass can see the Operations workspace.
            normalizeSidebarVisualState(frame);

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

    private static void normalizeSidebarVisualState(JFrame frame){
        if(frame==null)return;

        if(routeButtonsField!=null){
            try{
                Object value=routeButtonsField.get(frame);
                if(value instanceof Map<?,?> map){
                    for(Object item:map.values())
                        if(item instanceof JButton button)normalizeRouteButton(button);
                }
            }catch(Exception ignored){}
        }

        normalizeAddedComponent(frame.getContentPane());
    }

    private static void normalizeAddedComponent(Component component){
        if(component==null)return;
        if(component instanceof JButton button&&isSidebarRouteCandidate(button)){
            normalizeRouteButton(button);
            return;
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())normalizeAddedComponent(child);
    }

    private static boolean isSidebarRouteCandidate(JButton button){
        if(Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.route")))return true;
        if(button.getClass().getName().contains("OperationsWorkspaceFrame$RoundedSidebarButton"))return true;
        String text=button.getText();
        if(text==null)return false;
        String normalized=text.replaceAll("\\s+"," ").trim().toLowerCase();
        return normalized.contains("data collection")
                ||normalized.contains("northstar intelligence");
    }

    private static void normalizeRouteButton(JButton button){
        // UiFoundation.styleButton() checks northstar.ui.skip before installing
        // NorthStarButtonUI. This property is the critical ownership boundary.
        button.putClientProperty("northstar.ui.skip",Boolean.TRUE);
        button.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
        button.setRolloverEnabled(false);
        ButtonModel model=button.getModel();
        model.setRollover(false);
        model.setArmed(false);
        model.setPressed(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(9,12,9,12));
        button.setBorderPainted(false);
        button.repaint();
    }
}
