package com.wtm.modular.ui;

import com.wtm.modular.core.ModuleRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canonical event-driven workspace/module lifecycle.
 *
 * Structural injectors are run only at safe workspace boundaries: initial
 * window installation, full workspace-root replacement, and direct route
 * mounting into the workspace content host. Ordinary nested component events
 * perform visual normalization only, preventing the historical EDT rebuild
 * storms while keeping dynamic routes synchronized.
 */
public final class ModuleUiCoordinatorLifecycle {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> RESTORING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final String ROUTE_GUARD = "northstar.sidebar.visualGuard";

    private static Object coordinator;
    private static Method install;
    private static Method apply;
    private static Set<JFrame> installed;
    private static Field routeButtonsField;
    private static Field activeRouteField;
    private static Field workspaceHostField;

    private ModuleUiCoordinatorLifecycle() {}

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        resolveCoordinator();
        if (coordinator == null) return;

        if (coordinator instanceof ModuleRegistry.Listener listener) {
            ModuleRegistry.get().addListener(listener);
        }

        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame) installFrame(frame, false);
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we
                    && we.getID() == WindowEvent.WINDOW_OPENED
                    && we.getWindow() instanceof JFrame frame) {
                installFrame(frame, true);
                return;
            }

            if (event instanceof ContainerEvent ce
                    && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component added = ce.getChild();
                normalizeAddedComponent(added);

                JFrame frame = workspaceFrame(added);
                if (frame == null) return;

                if (isDirectWorkspaceRootReplacement(frame, ce.getContainer())) {
                    restoreWorkspaceSidebar(frame);
                    adaptCurrentWorkspaceRoute(frame);
                } else if (isWorkspaceHostAdd(frame, ce.getContainer())) {
                    adaptCurrentWorkspaceRoute(frame);
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    }

    private static void resolveCoordinator() {
        try {
            Class<?> type = Class.forName("com.wtm.modular.ui.ModuleUiCoordinator");
            Field instance = type.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            coordinator = instance.get(null);

            install = type.getDeclaredMethod("install", JFrame.class);
            install.setAccessible(true);
            apply = type.getDeclaredMethod("apply", JFrame.class);
            apply.setAccessible(true);

            Field installedField = type.getDeclaredField("installed");
            installedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<JFrame> set = (Set<JFrame>) installedField.get(coordinator);
            installed = set;

            Class<?> workspace = Class.forName("com.wtm.ui.OperationsWorkspaceFrame");
            routeButtonsField = workspace.getDeclaredField("sidebarRouteButtons");
            routeButtonsField.setAccessible(true);
            activeRouteField = workspace.getDeclaredField("activeWorkspaceRoute");
            activeRouteField.setAccessible(true);
            workspaceHostField = workspace.getDeclaredField("workspaceContentHost");
            workspaceHostField.setAccessible(true);
        } catch (Exception ex) {
            coordinator = null;
            System.err.println("NorthStar module lifecycle initialization failed: " + ex.getMessage());
        }
    }

    private static void installFrame(JFrame frame, boolean firstOpen) {
        if (!isWorkspaceFrame(frame)) return;
        if (coordinator == null || install == null || apply == null || installed == null) return;

        JComponent previousGlass = null;
        boolean previousGlassVisible = false;
        JPanel preparation = null;
        try {
            normalizeSidebarVisualState(frame);

            if (firstOpen) {
                Component glass = frame.getGlassPane();
                if (glass instanceof JComponent jc) {
                    previousGlass = jc;
                    previousGlassVisible = jc.isVisible();
                }
                preparation = preparationPane();
                frame.setGlassPane(preparation);
                preparation.setVisible(true);
            }

            if (!installed.contains(frame)) {
                install.invoke(coordinator, frame);
                installed.add(frame);
            }

            restoreWorkspaceSidebar(frame);
            adaptCurrentWorkspaceRoute(frame);

            frame.getRootPane().revalidate();
            frame.validate();
            frame.doLayout();
            frame.repaint();
        } catch (Exception ex) {
            System.err.println("NorthStar module sidebar initialization failed: " + ex.getMessage());
        } finally {
            if (firstOpen && preparation != null) {
                JPanel finalPreparation = preparation;
                JComponent finalPreviousGlass = previousGlass;
                boolean finalPreviousVisible = previousGlassVisible;
                SwingUtilities.invokeLater(() -> {
                    restoreWorkspaceSidebar(frame);
                    adaptCurrentWorkspaceRoute(frame);
                    normalizeSidebarVisualState(frame);
                    SwingUtilities.invokeLater(() -> {
                        restoreWorkspaceSidebar(frame);
                        adaptCurrentWorkspaceRoute(frame);
                        normalizeSidebarVisualState(frame);
                        finalPreparation.setVisible(false);
                        if (finalPreviousGlass != null && finalPreviousGlass != finalPreparation) {
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

    private static JPanel preparationPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(true);
        pane.setBackground(com.wtm.ui.Theme.bg());
        JLabel status = new JLabel("Preparing Operations Workspace...");
        status.setForeground(com.wtm.ui.Theme.muted());
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        pane.add(status);
        return pane;
    }

    private static void restoreWorkspaceSidebar(JFrame frame) {
        if (!isWorkspaceFrame(frame) || Boolean.TRUE.equals(RESTORING.get())) return;
        RESTORING.set(Boolean.TRUE);
        try {
            ensureModulesRoute(frame);
            if (coordinator != null && apply != null) apply.invoke(coordinator, frame);

            invokePrivateStatic("com.wtm.app.AiEnabledMain", "injectSidebar", frame);
            invokePrivateStatic("com.wtm.ui.DataIngestionInjector", "inject", frame);
            invokePrivateStatic("com.wtm.modular.ui.ShowcaseModuleInjector", "adapt", frame);

            ensureModulesRoute(frame);
            normalizeSidebarVisualState(frame);
        } catch (Exception ignored) {
            normalizeSidebarVisualState(frame);
        } finally {
            RESTORING.set(Boolean.FALSE);
        }
    }

    private static void ensureModulesRoute(JFrame frame) {
        if (coordinator == null || install == null || routeButtonsField == null) return;
        try {
            Object value = routeButtonsField.get(frame);
            if (!(value instanceof Map<?, ?> map)) return;

            boolean present = map.keySet().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .anyMatch(key -> "modules".equalsIgnoreCase(key));
            if (!present) {
                install.invoke(coordinator, frame);
                if (installed != null) installed.add(frame);
            }
        } catch (Exception ex) {
            System.err.println("NorthStar Modules route restore failed: " + ex.getMessage());
        }
    }

    private static void adaptCurrentWorkspaceRoute(JFrame frame) {
        if (!isWorkspaceFrame(frame)) return;
        String active = activeRoute(frame).toLowerCase(Locale.ROOT);

        if (active.isBlank() || active.contains("dashboard")) {
            invokePrivateStatic("com.wtm.app.AiEnabledMain", "injectDashboard", frame);
        }

        if (active.contains("location") || active.contains("route")) {
            invokePrivateStatic("com.wtm.ui.LocationsNetworkInjector", "adapt", frame);
        }

        ensureModulesRoute(frame);
        normalizeSidebarVisualState(frame);
    }

    private static void invokePrivateStatic(String className, String methodName, JFrame frame) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, JFrame.class);
            method.setAccessible(true);
            method.invoke(null, frame);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void normalizeSidebarVisualState(JFrame frame) {
        if (frame == null) return;
        String active = activeRoute(frame);
        if (routeButtonsField != null) {
            try {
                Object value = routeButtonsField.get(frame);
                if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getValue() instanceof JButton button) {
                            String route = entry.getKey() == null ? "" : entry.getKey().toString();
                            normalizeRouteButton(button, route.equalsIgnoreCase(active));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        normalizeAddedComponent(frame.getContentPane());
    }

    private static String activeRoute(JFrame frame) {
        if (activeRouteField == null) return "";
        try {
            Object value = activeRouteField.get(frame);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void normalizeAddedComponent(Component component) {
        if (component == null) return;
        if (component instanceof JButton button && isSidebarRouteCandidate(button)) {
            boolean active = Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.active"));
            normalizeRouteButton(button, active);
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) normalizeAddedComponent(child);
        }
    }

    private static boolean isSidebarRouteCandidate(JButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.route"))) return true;
        if (button.getClass().getName().contains("OperationsWorkspaceFrame$RoundedSidebarButton")) return true;
        String text = button.getText();
        if (text == null) return false;
        String normalized = text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return normalized.contains("data collection")
                || normalized.contains("northstar intelligence")
                || normalized.equals("modules")
                || normalized.contains("media library");
    }

    private static void normalizeRouteButton(JButton button, boolean active) {
        button.putClientProperty("northstar.ui.skip", Boolean.TRUE);
        button.putClientProperty("northstar.sidebar.route", Boolean.TRUE);
        button.putClientProperty("northstar.sidebar.active", active ? Boolean.TRUE : Boolean.FALSE);
        button.setEnabled(true);
        button.setForeground(active ? Color.WHITE : com.wtm.ui.Theme.text());
        button.setRolloverEnabled(false);
        ButtonModel model = button.getModel();
        model.setRollover(false);
        model.setArmed(false);
        model.setPressed(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        button.setBorderPainted(false);
        installVisualGuard(button);
        button.repaint();
    }

    private static void installVisualGuard(JButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty(ROUTE_GUARD))) return;
        button.putClientProperty(ROUTE_GUARD, Boolean.TRUE);
        PropertyChangeListener listener = (PropertyChangeEvent event) -> {
            String name = event.getPropertyName();
            if (!"foreground".equals(name) && !"northstar.sidebar.active".equals(name)) return;
            boolean active = Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.active"));
            Color desired = active ? Color.WHITE : com.wtm.ui.Theme.text();
            if (!desired.equals(button.getForeground())) button.setForeground(desired);
        };
        button.addPropertyChangeListener(listener);
    }

    private static JFrame workspaceFrame(Component component) {
        if (component == null) return null;
        Window window = SwingUtilities.getWindowAncestor(component);
        return window instanceof JFrame frame && isWorkspaceFrame(frame) ? frame : null;
    }

    private static boolean isDirectWorkspaceRootReplacement(JFrame frame, Container container) {
        if (frame == null || container == null) return false;
        return container == frame.getRootPane()
                || container == frame.getLayeredPane()
                || container == frame.getContentPane();
    }

    private static boolean isWorkspaceHostAdd(JFrame frame, Container container) {
        if (frame == null || container == null || workspaceHostField == null) return false;
        try {
            return workspaceHostField.get(frame) == container;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isWorkspaceFrame(JFrame frame) {
        return frame != null
                && frame.isDisplayable()
                && "com.wtm.ui.OperationsWorkspaceFrame".equals(frame.getClass().getName());
    }
}
