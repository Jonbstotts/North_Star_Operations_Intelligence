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
 * v2.1.14 finalizes sidebar visual normalization for both core routes and
 * routes injected by later feature bootstraps (notably Data Collection and
 * NorthStar Intelligence). Those injected buttons are not guaranteed to live
 * in OperationsWorkspaceFrame.sidebarRouteButtons, so the previous map-only
 * cleanup could leave their native Swing border visible after startup.
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

                // Feature bootstraps add their routes with invokeLater(). Keep
                // the preparation layer up for two EDT turns, normalize again,
                // and only then reveal the finished sidebar. This is event-
                // driven (no timer/polling) and prevents a visible startup snap.
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
     * Normalize both registered route buttons and late-injected sidebar routes.
     * Active selection is painted by the NorthStar sidebar active property, not
     * by Swing's native border/rollover state.
     */
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

        // Data Collection and NorthStar Intelligence are injected by feature
        // bootstraps and may not be registered in sidebarRouteButtons. Find
        // those concrete route buttons in the live component tree as well.
        normalizeInjectedRoutes(frame.getContentPane());
    }

    private static void normalizeInjectedRoutes(Component component){
        if(component instanceof JButton button&&isInjectedSidebarRoute(button))
            normalizeRouteButton(button);
        if(component instanceof Container container)
            for(Component child:container.getComponents())normalizeInjectedRoutes(child);
    }

    private static boolean isInjectedSidebarRoute(JButton button){
        String text=button.getText();
        if(text==null)return false;
        String normalized=text.replaceAll("\\s+"," ").trim().toLowerCase();
        return normalized.contains("data collection")
                ||normalized.contains("northstar intelligence");
    }

    private static void normalizeRouteButton(JButton button){
        button.setRolloverEnabled(false);
        ButtonModel model=button.getModel();
        model.setRollover(false);
        model.setArmed(false);
        model.setPressed(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setBorder(new EmptyBorder(9,12,9,12));
        button.setBorderPainted(false);
        button.repaint();
    }
}
