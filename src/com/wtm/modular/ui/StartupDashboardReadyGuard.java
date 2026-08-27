package com.wtm.modular.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Condition-driven Dashboard readiness guard.
 *
 * The native workspace intentionally queues its first Dashboard route with
 * invokeLater().  During post-login transition and Settings Save & Apply, later
 * workspace lifecycle work can replace that first tree.  The visible symptom
 * is a blank Dashboard that repairs itself as soon as the user navigates away
 * and back.
 *
 * This guard mirrors that successful manual recovery, but only when the
 * Dashboard is genuinely unhealthy.  It watches startup/save for a bounded
 * period, verifies that dashboardBody is attached beneath workspaceContentHost,
 * and calls showDashboardRoute() only if that invariant is broken.  Once the
 * native Dashboard is healthy it performs cosmetic/data refresh plus dynamic
 * Intelligence/Music attachment and stops.  Ordinary navigation is never
 * monitored.
 */
public final class StartupDashboardReadyGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static final String MINI_MARK = "northstar.music.mini";
    private static final WeakHashMap<Window, Timer> WATCHES = new WeakHashMap<>();
    private static boolean installed;

    private StartupDashboardReadyGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we
                    && we.getID() == WindowEvent.WINDOW_OPENED
                    && isWorkspace(we.getWindow())) {
                startWatch(we.getWindow());
                return;
            }

            if (event instanceof ActionEvent ae
                    && ae.getSource() instanceof AbstractButton button
                    && "Save & Apply".equalsIgnoreCase(clean(button.getText()))) {
                Window workspace = findWorkspace(SwingUtilities.getWindowAncestor(button));
                if (workspace != null) SwingUtilities.invokeLater(() -> startWatch(workspace));
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isShowing()) startWatch(window);
        }
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static Window findWorkspace(Window source) {
        if (isWorkspace(source)) return source;
        for (Window owner = source == null ? null : source.getOwner();
             owner != null;
             owner = owner.getOwner()) {
            if (isWorkspace(owner)) return owner;
        }
        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isDisplayable()) return window;
        }
        return null;
    }

    private static void startWatch(Window window) {
        if (!(window instanceof JFrame frame)) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> startWatch(window));
            return;
        }

        Timer old = WATCHES.remove(window);
        if (old != null) old.stop();

        final long deadline = System.currentTimeMillis() + 6500L;
        final int[] repairs = {0};
        final int[] healthyPasses = {0};

        Timer watch = new Timer(140, null);
        watch.addActionListener(e -> {
            if (!frame.isDisplayable() || !frame.isShowing()) {
                watch.stop();
                WATCHES.remove(window);
                return;
            }

            String route = activeRoute(frame);
            if (!"Dashboard".equalsIgnoreCase(route)) {
                if (System.currentTimeMillis() >= deadline) {
                    watch.stop();
                    WATCHES.remove(window);
                }
                return;
            }

            if (!dashboardHealthy(frame)) {
                healthyPasses[0] = 0;
                if (repairs[0] < 3) {
                    repairs[0]++;
                    invoke(frame, "showDashboardRoute");
                }
            } else {
                syncDashboard(frame);
                healthyPasses[0]++;
                // Require two consecutive healthy observations so a later
                // post-login/settings lifecycle replacement cannot win the race.
                if (healthyPasses[0] >= 2) {
                    watch.stop();
                    WATCHES.remove(window);
                    return;
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                // Final safe attempt only if the Dashboard is still broken.
                if (!dashboardHealthy(frame) && repairs[0] < 4) {
                    invoke(frame, "showDashboardRoute");
                    syncDashboard(frame);
                }
                watch.stop();
                WATCHES.remove(window);
            }
        });
        watch.setInitialDelay(120);
        watch.setRepeats(true);
        WATCHES.put(window, watch);
        watch.start();
    }

    private static boolean dashboardHealthy(JFrame frame) {
        Object bodyValue = field(frame, "dashboardBody");
        Object hostValue = field(frame, "workspaceContentHost");
        if (!(bodyValue instanceof JPanel body) || !(hostValue instanceof JPanel host)) return false;
        if (body.getParent() == null || host.getComponentCount() == 0) return false;
        if (!javax.swing.SwingUtilities.isDescendingFrom(body, host)) return false;
        // Native Dashboard always contains summary + spacer + module grid.
        return body.getComponentCount() >= 3 && body.getWidth() > 0 && body.getHeight() > 0;
    }

    private static void syncDashboard(JFrame frame) {
        invoke(frame, "refreshVisibleModules");
        invokeStaticFrame("com.wtm.app.AiEnabledMain", "injectDashboard", frame);

        if (musicDashboardEnabled()) {
            invokeStaticWindow("com.wtm.modular.ui.MusicModuleGuard", "injectDashboardPlayer", frame);
        } else {
            removeMarked((Container) frame, MINI_MARK);
        }

        Object body = field(frame, "dashboardBody");
        Object host = field(frame, "workspaceContentHost");
        if (body instanceof JComponent c) c.revalidate();
        if (host instanceof JComponent c) c.revalidate();
        frame.getRootPane().revalidate();
        frame.validate();
        if (body instanceof JComponent c) c.repaint();
        if (host instanceof JComponent c) c.repaint();
        frame.repaint();
    }

    private static boolean musicDashboardEnabled() {
        try {
            Class<?> serviceClass = Class.forName("com.wtm.modular.ui.MusicModuleGuard$MusicService");
            Method instance = serviceClass.getDeclaredMethod("instance");
            instance.setAccessible(true);
            Object service = instance.invoke(null);
            Field settingsField = serviceClass.getDeclaredField("settings");
            settingsField.setAccessible(true);
            Object settings = settingsField.get(service);
            Field dashboard = settings.getClass().getDeclaredField("dashboardPlayer");
            dashboard.setAccessible(true);
            return dashboard.getBoolean(settings);
        } catch (ReflectiveOperationException ex) {
            return true;
        }
    }

    private static void removeMarked(Container root, String marker) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComponent jc
                    && Boolean.TRUE.equals(jc.getClientProperty(marker))
                    && component.getParent() != null) {
                Container parent = component.getParent();
                parent.remove(component);
                parent.revalidate();
                parent.repaint();
                return;
            }
            if (component instanceof Container child) removeMarked(child, marker);
        }
    }

    private static String activeRoute(JFrame frame) {
        Object value = field(frame, "activeWorkspaceRoute");
        return value == null ? "" : value.toString();
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invokeStaticFrame(String className, String methodName, JFrame frame) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, JFrame.class);
            method.setAccessible(true);
            method.invoke(null, frame);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invokeStaticWindow(String className, String methodName, Window window) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, Window.class);
            method.setAccessible(true);
            method.invoke(null, window);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
