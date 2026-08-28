package com.wtm.modular.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;

/**
 * Cosmetic-only normalization for the dynamically injected Modules route.
 *
 * The guard never rebuilds the sidebar and never schedules delayed passes.
 * Newly inserted route components are normalized synchronously, while a newly
 * opened workspace receives one bounded initial scan.
 */
public final class SidebarModulesGlyphGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static boolean installed;

    private SidebarModulesGlyphGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        AWTEventListener listener = event -> {
            if (event instanceof ContainerEvent ce
                    && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component added = ce.getChild();
                Window workspace = added == null
                        ? null
                        : SwingUtilities.getWindowAncestor(added);
                if (isWorkspace(workspace)) normalizeAddedComponent(added);
                return;
            }

            if (event instanceof WindowEvent we
                    && we.getID() == WindowEvent.WINDOW_OPENED
                    && isWorkspace(we.getWindow())) {
                normalizeModules((Container) we.getWindow());
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(
                listener,
                AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK
        );

        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isDisplayable()) {
                normalizeModules((Container) window);
            }
        }
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static void normalizeAddedComponent(Component component) {
        if (component == null) return;
        if (component instanceof AbstractButton button && isModulesText(button.getText())) {
            normalizeButton(button);
            return;
        }
        if (component instanceof JLabel label && isModulesText(label.getText())) {
            label.setText("▦  Modules");
            return;
        }
        if (component instanceof Container child) normalizeModules(child);
    }

    private static void normalizeModules(Container root) {
        if (root == null) return;
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button && isModulesText(button.getText())) {
                normalizeButton(button);
            } else if (component instanceof JLabel label && isModulesText(label.getText())) {
                label.setText("▦  Modules");
            }

            if (component instanceof Container child) normalizeModules(child);
        }
    }

    private static boolean isModulesText(String text) {
        if (text == null) return false;
        String normalized = text.replace("▦", "").trim();
        return "Modules".equalsIgnoreCase(normalized);
    }

    private static void normalizeButton(AbstractButton button) {
        button.setText("▦  Modules");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.putClientProperty("northstar.sidebar.route", Boolean.TRUE);
        button.putClientProperty("northstar.ui.skip", Boolean.TRUE);
    }
}
