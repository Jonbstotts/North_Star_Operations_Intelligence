package com.wtm.modular.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;

/**
 * Ensures the initial Dashboard is rebuilt once after the workspace window is
 * actually displayable. OperationsWorkspaceFrame creates its first dashboard
 * from an invokeLater during construction, which can run before startup/login
 * presentation has fully exposed and laid out the frame. A later route switch
 * naturally rebuilds the dashboard and was masking that timing race.
 *
 * This guard is intentionally startup-only. It does not listen to ordinary
 * component additions and therefore cannot reintroduce the historical sidebar
 * rebuild/jump or module navigation freeze.
 */
public final class StartupDashboardReadyGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static boolean installed;

    private StartupDashboardReadyGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        AWTEventListener listener = event -> {
            if (!(event instanceof WindowEvent we)
                    || we.getID() != WindowEvent.WINDOW_OPENED
                    || !isWorkspace(we.getWindow())) return;
            scheduleReadyPass(we.getWindow());
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isShowing()) scheduleReadyPass(window);
        }
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static void scheduleReadyPass(Window window) {
        if (!(window instanceof JFrame frame)) return;
        javax.swing.Timer timer = new javax.swing.Timer(140, e -> {
            ((javax.swing.Timer) e.getSource()).stop();
            if (!frame.isDisplayable() || !frame.isShowing()) return;
            if (!"Dashboard".equalsIgnoreCase(activeRoute(frame))) return;

            // Rebuild once now that the frame has real display geometry.
            invoke(frame, "showDashboardRoute");

            // Let the native dashboard mount, then restore dynamic additions.
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                invokeStaticFrame("com.wtm.app.AiEnabledMain", "injectDashboard", frame);
                invokeStaticWindow("com.wtm.modular.ui.MusicModuleGuard", "installWorkspace", frame);
                frame.getRootPane().revalidate();
                frame.validate();
                frame.repaint();
            }));
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static String activeRoute(JFrame frame) {
        try {
            var field = frame.getClass().getDeclaredField("activeWorkspaceRoute");
            field.setAccessible(true);
            Object value = field.get(frame);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException ex) {
            return "";
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
}
