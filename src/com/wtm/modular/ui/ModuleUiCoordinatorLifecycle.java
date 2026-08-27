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
 * v2.1.19 keeps sidebar visual ownership lightweight. Container-add events are
 * allowed to normalize the component that was added, but they MUST NOT rebuild
 * or re-inject the workspace sidebar. Main Showcase and other settings pages
 * create large component trees; restoring the entire sidebar for every child
 * addition caused an O(n^2)-style EDT storm and made navigation appear frozen.
 */
public final class ModuleUiCoordinatorLifecycle {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> RESTORING=ThreadLocal.withInitial(()->Boolean.FALSE);
    private static Object coordinator;
    private static Method install;
    private static Method apply;
    private static Set<JFrame> installed;
    private static Field routeButtonsField;
    private static Field activeRouteField;

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
                // Visual normalization only. Never call restoreWorkspaceSidebar()
                // from component construction; doing so recursively re-ran module
                // injectors while Main Showcase/Settings were still being built.
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

            Class<?> workspace=Class.forName("com.wtm.ui.OperationsWorkspaceFrame");
            routeButtonsField=workspace.getDeclaredField("sidebarRouteButtons");
            routeButtonsField.setAccessible(true);
            activeRouteField=workspace.getDeclaredField("activeWorkspaceRoute");
            activeRouteField.setAccessible(true);
        }catch(Exception ex){
            coordinator=null;
            System.err.println("NorthStar module lifecycle initialization failed: "+ex.getMessage());
        }
    }

    private static void installFrame(JFrame frame,boolean firstOpen){
        if(!isWorkspaceFrame(frame))return;
        if(coordinator==null||install==null||apply==null||installed==null)return;

        JComponent previousGlass=null;
        boolean previousGlassVisible=false;
        JPanel preparation=null;
        try{
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
            restoreWorkspaceSidebar(frame);

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

    /**
     * Full route injection is intentionally restricted to workspace installation.
     * It is never called from a child COMPONENT_ADDED event.
     */
    private static void restoreWorkspaceSidebar(JFrame frame){
        if(!isWorkspaceFrame(frame)||Boolean.TRUE.equals(RESTORING.get()))return;
        RESTORING.set(Boolean.TRUE);
        try{
            if(coordinator!=null&&apply!=null)apply.invoke(coordinator,frame);
            invokePrivateStatic("com.wtm.app.AiEnabledMain","injectSidebar",frame);
            invokePrivateStatic("com.wtm.ui.DataIngestionInjector","inject",frame);
            invokePrivateStatic("com.wtm.modular.ui.ShowcaseModuleInjector","adapt",frame);
            normalizeSidebarVisualState(frame);
        }catch(Exception ignored){
            normalizeSidebarVisualState(frame);
        }finally{
            RESTORING.set(Boolean.FALSE);
        }
    }

    private static void invokePrivateStatic(String className,String methodName,JFrame frame){
        try{
            Class<?> type=Class.forName(className);
            Method method=type.getDeclaredMethod(methodName,JFrame.class);
            method.setAccessible(true);
            method.invoke(null,frame);
        }catch(ReflectiveOperationException ignored){}
    }

    private static void normalizeSidebarVisualState(JFrame frame){
        if(frame==null)return;
        String active=activeRoute(frame);
        if(routeButtonsField!=null){
            try{
                Object value=routeButtonsField.get(frame);
                if(value instanceof Map<?,?> map){
                    for(Map.Entry<?,?> entry:map.entrySet()){
                        if(entry.getValue() instanceof JButton button){
                            String route=entry.getKey()==null?"":entry.getKey().toString();
                            normalizeRouteButton(button,route.equalsIgnoreCase(active));
                        }
                    }
                }
            }catch(Exception ignored){}
        }
        normalizeAddedComponent(frame.getContentPane());
    }

    private static String activeRoute(JFrame frame){
        if(activeRouteField==null)return "";
        try{
            Object value=activeRouteField.get(frame);
            return value==null?"":value.toString();
        }catch(Exception ignored){return "";}
    }

    private static void normalizeAddedComponent(Component component){
        if(component==null)return;
        if(component instanceof JButton button&&isSidebarRouteCandidate(button)){
            boolean active=Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.active"));
            normalizeRouteButton(button,active);
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
                ||normalized.contains("northstar intelligence")
                ||normalized.contains("media library");
    }

    private static void normalizeRouteButton(JButton button,boolean active){
        button.putClientProperty("northstar.ui.skip",Boolean.TRUE);
        button.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
        button.putClientProperty("northstar.sidebar.active",active?Boolean.TRUE:Boolean.FALSE);
        button.setEnabled(true);
        button.setForeground(active?Color.WHITE:com.wtm.ui.Theme.text());
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

    private static boolean isWorkspaceFrame(JFrame frame){
        return frame!=null&&frame.isDisplayable()
                &&"com.wtm.ui.OperationsWorkspaceFrame".equals(frame.getClass().getName());
    }
}
