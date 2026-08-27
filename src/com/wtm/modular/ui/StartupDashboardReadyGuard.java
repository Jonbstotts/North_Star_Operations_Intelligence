package com.wtm.modular.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stabilizes the Dashboard after the workspace becomes visible and after a
 * Settings Save & Apply completes.
 *
 * IMPORTANT: this guard never calls showDashboardRoute(). The previous
 * implementation rebuilt the Dashboard shortly after the native workspace had
 * already mounted it. That second structural rebuild raced the post-login
 * transition and Settings rebuild lifecycle, producing the blank Dashboard
 * observed until the user navigated away and back.
 *
 * This version only refreshes/validates the already-mounted Dashboard and asks
 * dynamic Dashboard extensions (NorthStar Intelligence and Music) to attach to
 * that stable tree. It is intentionally limited to window-open and Save & Apply
 * lifecycle boundaries; ordinary navigation/component creation is untouched.
 */
public final class StartupDashboardReadyGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static final Map<Window, Timer[]> PASSES = new WeakHashMap<>();
    private static boolean installed;

    private StartupDashboardReadyGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we
                    && we.getID() == WindowEvent.WINDOW_OPENED
                    && isWorkspace(we.getWindow())) {
                schedulePasses(we.getWindow());
                return;
            }

            if (event instanceof ActionEvent ae
                    && ae.getSource() instanceof AbstractButton button
                    && "Save & Apply".equalsIgnoreCase(clean(button.getText()))) {
                Window workspace = findWorkspace(SwingUtilities.getWindowAncestor(button));
                if (workspace != null) {
                    // Let Settings finish config persistence/buildUi first.
                    SwingUtilities.invokeLater(() -> schedulePasses(workspace));
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isShowing()) schedulePasses(window);
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

    private static void schedulePasses(Window window) {
        if (!(window instanceof JFrame frame)) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> schedulePasses(window));
            return;
        }

        Timer[] previous = PASSES.remove(window);
        if (previous != null) for (Timer timer : previous) if (timer != null) timer.stop();

        // Multiple bounded passes cover macOS post-login/window geometry and
        // the asynchronous Settings buildUi() completion without polling.
        int[] delays = {180, 420, 850};
        Timer[] timers = new Timer[delays.length];
        for (int i = 0; i < delays.length; i++) {
            Timer timer = new Timer(delays[i], e -> {
                ((Timer)e.getSource()).stop();
                stabilize(frame);
            });
            timer.setRepeats(false);
            timers[i] = timer;
            timer.start();
        }
        PASSES.put(window, timers);
    }

    private static void stabilize(JFrame frame) {
        if (!frame.isDisplayable() || !frame.isShowing()) return;
        if (!"Dashboard".equalsIgnoreCase(activeRoute(frame))) return;

        Object body = field(frame, "dashboardBody");
        Object host = field(frame, "workspaceContentHost");
        if (!(body instanceof JComponent dashboard)
                || !(host instanceof JComponent workspaceHost)
                || dashboard.getParent() == null) {
            // Native buildUi/showDashboardRoute may still be queued. A later
            // bounded pass will pick it up; do not create a competing tree.
            return;
        }

        invoke(frame, "refreshVisibleModules");
        invokeStaticFrame("com.wtm.app.AiEnabledMain", "injectDashboard", frame);
        invokeStaticWindow("com.wtm.modular.ui.MusicModuleGuard", "installWorkspace", frame);

        dashboard.revalidate();
        workspaceHost.revalidate();
        frame.getRootPane().revalidate();
        frame.validate();
        dashboard.repaint();
        workspaceHost.repaint();
        frame.repaint();
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
