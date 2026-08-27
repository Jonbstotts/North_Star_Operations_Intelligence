package com.wtm.modular.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

/**
 * Cosmetic-only guard for the dynamically injected Modules sidebar route.
 *
 * The modular coordinator may recreate the Modules component during a Settings
 * Save & Apply rebuild. That recreation can briefly restore the legacy plain
 * "Modules" text. This guard never rebuilds the sidebar; it only reapplies the
 * canonical glyph/text/geometry after real workspace lifecycle boundaries.
 */
public final class SidebarModulesGlyphGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static boolean installed;

    private SidebarModulesGlyphGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        AWTEventListener listener = event -> {
            if (event instanceof WindowEvent we && we.getID() == WindowEvent.WINDOW_OPENED) {
                Window window = we.getWindow();
                if (isWorkspace(window)) stabilize(window);
                return;
            }

            if (event instanceof ActionEvent ae && ae.getSource() instanceof AbstractButton button) {
                String text = button.getText() == null ? "" : button.getText().trim();
                if (!"Save & Apply".equalsIgnoreCase(text)) return;

                Window sourceWindow = SwingUtilities.getWindowAncestor(button);
                Window workspace = findWorkspace(sourceWindow);
                if (workspace != null) stabilize(workspace);
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(
                listener,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK
        );

        for (Window window : Window.getWindows()) {
            if (isWorkspace(window)) stabilize(window);
        }
    }

    private static Window findWorkspace(Window sourceWindow) {
        if (isWorkspace(sourceWindow)) return sourceWindow;
        for (Window owner = sourceWindow == null ? null : sourceWindow.getOwner();
             owner != null;
             owner = owner.getOwner()) {
            if (isWorkspace(owner)) return owner;
        }
        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isDisplayable()) return window;
        }
        return null;
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    /**
     * Run several cosmetic passes because Save & Apply replaces the workspace
     * tree and the modular coordinator may insert Modules on a later EDT turn.
     * No structural injector/coordinator calls are made here.
     */
    private static void stabilize(Window window) {
        if (window == null) return;
        runPass(window);
        SwingUtilities.invokeLater(() -> runPass(window));

        Timer t1 = new Timer(80, e -> runPass(window));
        t1.setRepeats(false);
        t1.start();

        Timer t2 = new Timer(220, e -> runPass(window));
        t2.setRepeats(false);
        t2.start();
    }

    private static void runPass(Window window) {
        if (!(window instanceof Container root) || !window.isDisplayable()) return;
        normalizeModules(root);
        root.revalidate();
        root.repaint();
    }

    private static void normalizeModules(Container root) {
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
