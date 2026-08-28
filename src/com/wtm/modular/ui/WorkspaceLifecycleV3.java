package com.wtm.modular.ui;

import com.wtm.app.AiEnabledMain;
import com.wtm.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/**
 * Single lifecycle owner for dynamic workspace additions that live outside the
 * base OperationsWorkspaceFrame source.
 *
 * <p>Structural work (dynamic sidebar routes, Intelligence and Music dashboard
 * additions) runs only at real lifecycle boundaries: first workspace
 * activation and Settings Save & Apply. Generic component-add lifecycle
 * hooks are deliberately excluded.</p>
 *
 * <p>WINDOW_OPENED is intentionally not a structural workspace boundary. Swing
 * can publish it while the base frame still has queued construction work. The
 * first WINDOW_ACTIVATED event is later, unique per workspace, and guarantees
 * we synchronize the already-mounted canonical dashboard only once.</p>
 */
public final class WorkspaceLifecycleV3 {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static final String SETTINGS_CLASS = "com.wtm.ui.SettingsDialog";
    private static final String MUSIC_MINI_MARKER = "northstar.music.mini";
    private static final String INTELLIGENCE_CHECK = "northstar.intelligence.workspace.checkbox";
    private static final String MUSIC_CHECK = "northstar.music.workspace.checkbox";
    private static final String INTELLIGENCE_TOKEN = "NORTHSTAR_INTELLIGENCE";
    private static final String INTELLIGENCE_PREF = "dashboard.intelligence.enabled";

    private static final Preferences WORKSPACE_PREFS =
            Preferences.userRoot().node("com/wtm/northstar/workspace");
    private static final Set<Window> ACTIVATED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean installed;

    private WorkspaceLifecycleV3() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        loadMusicSettings();

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we) {
                Window window = we.getWindow();
                if (we.getID() == WindowEvent.WINDOW_OPENED) {
                    if (isSettings(window)) SwingUtilities.invokeLater(() -> injectWorkspaceToggles(window));
                    return;
                }
                if (we.getID() == WindowEvent.WINDOW_ACTIVATED && isWorkspace(window)) {
                    if (ACTIVATED.add(window)) activateWorkspace(window);
                    return;
                }
            }

            if (event instanceof ActionEvent ae && ae.getSource() instanceof AbstractButton button) {
                String text = clean(button.getText());
                if ("Save & Apply".equalsIgnoreCase(text)) {
                    Window source = SwingUtilities.getWindowAncestor(button);
                    persistWorkspaceToggles(source);
                    Window workspace = findWorkspace(source);
                    if (workspace != null) {
                        SwingUtilities.invokeLater(() -> refreshWorkspaceBoundary(workspace));
                    }
                    return;
                }

                if (text.endsWith("Dashboard") || "Dashboard".equalsIgnoreCase(text)) {
                    Window workspace = findWorkspace(SwingUtilities.getWindowAncestor(button));
                    if (workspace != null) {
                        SwingUtilities.invokeLater(() -> syncDynamicDashboard(workspace));
                    }
                    return;
                }

                if (text.contains("Data Collection")
                        || text.contains("Gmail")
                        || text.contains("DataPath")) {
                    Window workspace = findWorkspace(SwingUtilities.getWindowAncestor(button));
                    if (workspace != null) SwingUtilities.invokeLater(() -> TrustedEmailUiGuard.apply(workspace));
                }
            }

        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isSettings(window)) injectWorkspaceToggles(window);
            if (isWorkspace(window) && window.isActive() && ACTIVATED.add(window)) activateWorkspace(window);
        }
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static boolean isSettings(Window window) {
        return window != null && SETTINGS_CLASS.equals(window.getClass().getName());
    }

    private static Window findWorkspace(Window source) {
        for (Window current = source; current != null; current = current.getOwner()) {
            if (isWorkspace(current)) return current;
        }
        for (Window window : Window.getWindows()) {
            if (isWorkspace(window) && window.isDisplayable()) return window;
        }
        return null;
    }

    /**
     * First workspace activation is the synchronous structural startup boundary.
     * No delayed timers or health-watchdog behavior is permitted here.
     */
    private static void activateWorkspace(Window window) {
        if (!(window instanceof JFrame frame)) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> activateWorkspace(window));
            return;
        }
        if (!frame.isDisplayable()) return;

        AiEnabledMain.injectSidebar(frame);
        MusicModuleGuard.installWorkspace(window);
        syncDynamicDashboard(frame);
        TrustedEmailUiGuard.apply(frame);
        frame.getRootPane().revalidate();
        frame.validate();
        frame.repaint();
    }

    /**
     * Settings Save & Apply is the other structural boundary. The base workspace
     * owns its own rebuild; after that event we synchronize source-backed
     * modular additions once, without a delayed corrective remount.
     */
    private static void refreshWorkspaceBoundary(Window window) {
        if (!(window instanceof JFrame frame) || !frame.isDisplayable()) return;
        AiEnabledMain.injectSidebar(frame);
        MusicModuleGuard.installWorkspace(window);
        syncDynamicDashboard(frame);
        TrustedEmailUiGuard.apply(frame);
        frame.getRootPane().revalidate();
        frame.validate();
        frame.repaint();
    }

    private static void syncDynamicDashboard(Window workspace) {
        if (!(workspace instanceof JFrame frame) || !frame.isDisplayable()) return;
        if (!"Dashboard".equalsIgnoreCase(activeRoute(frame))) return;

        if (intelligenceEnabled()) AiEnabledMain.injectDashboard(frame);
        else removeNamed((Container) frame, "northstar.ai.compact");

        MusicModuleGuard.installWorkspace(workspace);
        if (!musicDashboardEnabled()) removeMarked((Container) frame, MUSIC_MINI_MARKER);

        frame.getRootPane().revalidate();
        frame.validate();
        frame.repaint();
    }

    private static String activeRoute(JFrame frame) {
        Object value = fieldValue(frame, "activeWorkspaceRoute");
        return value == null ? "" : value.toString();
    }

    private static boolean intelligenceEnabled() {
        return WORKSPACE_PREFS.getBoolean(INTELLIGENCE_PREF, true);
    }

    private static void injectWorkspaceToggles(Window owner) {
        if (!(owner instanceof Container root) || !owner.isDisplayable()) return;
        JCheckBox weather = findCheckBox(root, "Local Weather");
        JCheckBox operations = findCheckBox(root, "Operations Snapshot");
        if (weather == null || operations == null) return;

        Container common = commonParent(weather, operations);
        if (!(common instanceof JPanel panel)) return;

        JCheckBox intelligence = findMarkedCheckBox(panel, INTELLIGENCE_CHECK);
        if (intelligence == null) {
            intelligence = new JCheckBox("NorthStar Intelligence");
            intelligence.putClientProperty(INTELLIGENCE_CHECK, Boolean.TRUE);
            intelligence.setOpaque(false);
            intelligence.setForeground(Theme.text());
            intelligence.setSelected(intelligenceEnabled());
            intelligence.addActionListener(e -> {
                boolean enabled = ((JCheckBox)e.getSource()).isSelected();
                WORKSPACE_PREFS.putBoolean(INTELLIGENCE_PREF, enabled);
                setModuleToken(configObject(owner), INTELLIGENCE_TOKEN, enabled);
            });
            panel.add(intelligence);
        }
        setModuleToken(configObject(owner), INTELLIGENCE_TOKEN, intelligence.isSelected());

        JCheckBox music = findMarkedCheckBox(panel, MUSIC_CHECK);
        if (music == null) {
            music = new JCheckBox("Music Compact Player");
            music.putClientProperty(MUSIC_CHECK, Boolean.TRUE);
            music.setOpaque(false);
            music.setForeground(Theme.text());
            music.setSelected(musicDashboardEnabled());
            music.addActionListener(e -> setMusicDashboardEnabled(((JCheckBox)e.getSource()).isSelected()));
            panel.add(music);
        } else {
            music.setSelected(musicDashboardEnabled());
        }

        panel.revalidate();
        panel.repaint();
    }

    private static void persistWorkspaceToggles(Window owner) {
        if (!(owner instanceof Container root)) return;

        JCheckBox intelligence = findMarkedCheckBox(root, INTELLIGENCE_CHECK);
        if (intelligence != null) {
            WORKSPACE_PREFS.putBoolean(INTELLIGENCE_PREF, intelligence.isSelected());
            setModuleToken(configObject(owner), INTELLIGENCE_TOKEN, intelligence.isSelected());
        }

        JCheckBox music = findMarkedCheckBox(root, MUSIC_CHECK);
        if (music != null) setMusicDashboardEnabled(music.isSelected());
    }

    private static Object configObject(Window window) {
        if (window == null) return null;
        Object cfg = fieldValue(window, "cfg");
        if (cfg != null) return cfg;
        cfg = fieldValue(window, "config");
        if (cfg != null) return cfg;
        Object embedded = fieldValue(window, "embeddedSettingsSession");
        return fieldValue(embedded, "cfg");
    }

    @SuppressWarnings("unchecked")
    private static void setModuleToken(Object cfg, String token, boolean enabled) {
        if (cfg == null) return;
        try {
            Field modulesField = cfg.getClass().getField("workspaceModules");
            Object raw = modulesField.get(cfg);
            if (!(raw instanceof List<?> list)) return;
            List<Object> modules = (List<Object>) list;
            modules.removeIf(value -> token.equalsIgnoreCase(String.valueOf(value)));
            if (enabled) modules.add(token);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void loadMusicSettings() {
        MusicModuleGuard.loadSettings();
        restrictMusicStorage();
    }

    private static boolean musicDashboardEnabled() {
        return MusicModuleGuard.dashboardPlayerEnabled();
    }

    private static void setMusicDashboardEnabled(boolean enabled) {
        MusicModuleGuard.setDashboardPlayerEnabled(enabled);
        restrictMusicStorage();
    }

    private static void restrictMusicStorage() {
        try {
            java.nio.file.Path directory = java.nio.file.Paths.get(System.getProperty("user.home"), ".northstar");
            com.wtm.util.SecureFiles.ensurePrivateDirectory(directory);
            java.nio.file.Path file = directory.resolve("music.properties");
            if (java.nio.file.Files.isRegularFile(file)) com.wtm.util.SecureFiles.restrictFile(file);
        } catch (Exception ignored) {
        }
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box && text.equalsIgnoreCase(clean(box.getText()))) return box;
            if (component instanceof Container child) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox findMarkedCheckBox(Container root, String marker) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box && Boolean.TRUE.equals(box.getClientProperty(marker))) return box;
            if (component instanceof Container child) {
                JCheckBox found = findMarkedCheckBox(child, marker);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Container commonParent(Component a, Component b) {
        Set<Container> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container p = a.getParent(); p != null; p = p.getParent()) ancestors.add(p);
        for (Container p = b.getParent(); p != null; p = p.getParent()) if (ancestors.contains(p)) return p;
        return null;
    }

    private static void removeMarked(Container root, String marker) {
        Component found = findMarked(root, marker);
        if (found == null || found.getParent() == null) return;
        Container parent = found.getParent();
        parent.remove(found);
        parent.revalidate();
        parent.repaint();
    }

    private static Component findMarked(Container root, String marker) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty(marker))) return component;
            if (component instanceof Container child) {
                Component found = findMarked(child, marker);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void removeNamed(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName()) && component.getParent() != null) {
                Container parent = component.getParent();
                parent.remove(component);
                parent.revalidate();
                parent.repaint();
                return;
            }
            if (component instanceof Container child) removeNamed(child, name);
        }
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("^[^A-Za-z0-9]+", "").trim();
    }
}
