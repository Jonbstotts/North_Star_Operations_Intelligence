package com.wtm.modular.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Event-driven recovery guard for dynamic NorthStar workspace extensions.
 *
 * Recovery is intentionally limited to real workspace lifecycle boundaries:
 * initial window open and Settings Save & Apply. Ordinary module navigation
 * must never trigger a structural sidebar reinstall; doing so causes the
 * visible "jump" that earlier stabilization builds exhibited.
 */
public final class WorkspaceUiRecoveryGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static final String SETTINGS_CLASS = "com.wtm.ui.SettingsDialog";
    private static final String PREF_NODE = "com/wtm/northstar/workspace";
    private static final String PREF_INTELLIGENCE = "dashboard.intelligence.enabled";
    private static final String MODULE_TOKEN = "NORTHSTAR_INTELLIGENCE";
    private static final String CHECKBOX_MARKER = "northstar.intelligence.workspace.checkbox";
    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);
    private static final Map<Window, javax.swing.Timer> DEBOUNCE = new WeakHashMap<>();
    private static boolean started;

    private WorkspaceUiRecoveryGuard() {}

    public static synchronized void install() {
        if (started) return;
        started = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we && we.getID() == WindowEvent.WINDOW_OPENED) {
                schedule(we.getWindow(), 80);
                return;
            }
            if (event instanceof ActionEvent ae && ae.getSource() instanceof AbstractButton button) {
                String text = button.getText() == null ? "" : button.getText().trim();
                if ("Save & Apply".equalsIgnoreCase(text)) {
                    Window window = SwingUtilities.getWindowAncestor(button);
                    if (isNorthStarWindow(window)) {
                        SwingUtilities.invokeLater(() -> {
                            persistIntelligenceSelection(window);
                            Window target = isWorkspace(window) ? window : window.getOwner();
                            schedule(target, 220);
                        });
                    }
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isNorthStarWindow(window)) schedule(window, 40);
        }
    }

    private static boolean isNorthStarWindow(Window window) {
        return isWorkspace(window) || isSettings(window);
    }

    private static boolean isWorkspace(Window window) {
        return window != null && WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static boolean isSettings(Window window) {
        return window != null && SETTINGS_CLASS.equals(window.getClass().getName());
    }

    private static void schedule(Window window, int delayMs) {
        if (!isNorthStarWindow(window)) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> schedule(window, delayMs));
            return;
        }
        javax.swing.Timer timer = DEBOUNCE.get(window);
        if (timer == null) {
            timer = new javax.swing.Timer(Math.max(50, delayMs), e -> repair(window));
            timer.setRepeats(false);
            DEBOUNCE.put(window, timer);
        }
        timer.setInitialDelay(Math.max(50, delayMs));
        timer.restart();
    }

    private static void repair(Window window) {
        if (window == null || !window.isDisplayable()) return;
        if (isSettings(window)) {
            injectWorkspaceIntelligenceCheckbox(window);
            return;
        }
        if (isWorkspace(window) && window instanceof JFrame frame) {
            injectWorkspaceIntelligenceCheckbox(frame);
            restoreDynamicSidebar(frame);
            restoreDashboardIntelligence(frame);
        }
    }

    private static void restoreDynamicSidebar(JFrame frame) {
        try {
            Class<?> coordinatorType = Class.forName("com.wtm.modular.ui.ModuleUiCoordinator");
            Field instanceField = coordinatorType.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object coordinator = instanceField.get(null);

            Field installedField = coordinatorType.getDeclaredField("installed");
            installedField.setAccessible(true);
            Object raw = installedField.get(coordinator);
            if (raw instanceof Set<?> set) {
                @SuppressWarnings("unchecked") Set<Object> mutable = (Set<Object>) set;
                mutable.remove(frame);
            }

            Method install = coordinatorType.getDeclaredMethod("install", JFrame.class);
            install.setAccessible(true);
            install.invoke(coordinator, frame);

            Method apply = coordinatorType.getDeclaredMethod("apply", JFrame.class);
            apply.setAccessible(true);
            apply.invoke(coordinator, frame);
        } catch (ReflectiveOperationException ignored) {
        }

        invokeFrameMethod("com.wtm.app.AiEnabledMain", "injectSidebar", frame);
        invokeFrameMethod("com.wtm.ui.DataIngestionInjector", "inject", frame);
        normalizeSidebar(frame);
    }

    private static void restoreDashboardIntelligence(JFrame frame) {
        boolean enabled = PREFS.getBoolean(PREF_INTELLIGENCE, true);
        setWorkspaceModuleToken(frame, enabled);

        if (enabled) {
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                invokeFrameMethod("com.wtm.app.AiEnabledMain", "injectDashboard", frame);
                frame.revalidate();
                frame.repaint();
            }));
        } else {
            removeNamedComponent(frame, "northstar.ai.compact");
        }
    }

    private static void injectWorkspaceIntelligenceCheckbox(Window settings) {
        if (!(settings instanceof Container root)) return;
        if (findMarkedCheckBox(root) != null) return;

        JCheckBox weather = findCheckBox(root, "Local Weather");
        JCheckBox operations = findCheckBox(root, "Operations Snapshot");
        if (weather == null || operations == null) return;

        Container target = commonParent(weather, operations);
        if (!(target instanceof JPanel panel)) return;

        JCheckBox intelligence = new JCheckBox("NorthStar Intelligence");
        intelligence.putClientProperty(CHECKBOX_MARKER, Boolean.TRUE);
        intelligence.setSelected(PREFS.getBoolean(PREF_INTELLIGENCE, true));
        intelligence.setOpaque(false);
        intelligence.addActionListener(e -> {
            boolean enabled = intelligence.isSelected();
            PREFS.putBoolean(PREF_INTELLIGENCE, enabled);
            setSettingsModuleToken(settings, enabled);
        });

        panel.add(intelligence);
        panel.revalidate();
        panel.repaint();
        setSettingsModuleToken(settings, intelligence.isSelected());
    }

    private static void persistIntelligenceSelection(Window settings) {
        JCheckBox checkbox = settings instanceof Container c ? findMarkedCheckBox(c) : null;
        boolean enabled = checkbox == null
                ? PREFS.getBoolean(PREF_INTELLIGENCE, true)
                : checkbox.isSelected();
        PREFS.putBoolean(PREF_INTELLIGENCE, enabled);
        setSettingsModuleToken(settings, enabled);

        Object cfg = configObject(settings);
        if (cfg != null) {
            try {
                Class<?> service = Class.forName("com.wtm.config.ConfigService");
                Method save = service.getDeclaredMethod("save", cfg.getClass());
                save.setAccessible(true);
                save.invoke(null, cfg);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void setSettingsModuleToken(Window settings, boolean enabled) {
        setModuleToken(configObject(settings), enabled);
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

    private static void setWorkspaceModuleToken(JFrame frame, boolean enabled) {
        Object cfg = fieldValue(frame, "config");
        setModuleToken(cfg, enabled);
    }

    private static void setModuleToken(Object cfg, boolean enabled) {
        if (cfg == null) return;
        try {
            Field modulesField = cfg.getClass().getField("workspaceModules");
            Object raw = modulesField.get(cfg);
            if (!(raw instanceof List<?> list)) return;
            @SuppressWarnings("unchecked") List<Object> modules = (List<Object>) list;
            modules.removeIf(value -> MODULE_TOKEN.equalsIgnoreCase(String.valueOf(value)));
            if (enabled) modules.add(MODULE_TOKEN);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void normalizeSidebar(JFrame frame) {
        Object raw = fieldValue(frame, "sidebarRouteButtons");
        String active = String.valueOf(fieldValue(frame, "activeWorkspaceRoute"));
        if (!(raw instanceof Map<?, ?> map)) return;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof JButton button)) continue;
            String route = entry.getKey() == null ? "" : entry.getKey().toString();
            boolean selected = route.equalsIgnoreCase(active);

            button.putClientProperty("northstar.sidebar.active", selected);
            button.setEnabled(true);
            button.setFocusable(false);
            button.setFocusPainted(false);

            if ("Modules".equalsIgnoreCase(route)) {
                normalizeModulesRoute(button);
            }

            if (!selected) button.getModel().setRollover(false);
            button.repaint();
        }
    }

    /**
     * Modules is injected by the modular coordinator rather than by the base
     * workspace. Give it exactly the same geometry and leading-glyph treatment
     * as the native sidebar routes so it never changes width/indentation after
     * selection and cannot cause a sidebar layout jump.
     */
    private static void normalizeModulesRoute(JButton button) {
        button.setText("▦  Modules");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.putClientProperty("northstar.sidebar.route", Boolean.TRUE);
        button.putClientProperty("northstar.ui.skip", Boolean.TRUE);
    }

    private static void removeNamedComponent(Container root, String name) {
        Component found = findByName(root, name);
        if (found == null || found.getParent() == null) return;
        Container parent = found.getParent();
        parent.remove(found);
        parent.revalidate();
        parent.repaint();
    }

    private static Component findByName(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) {
                Component found = findByName(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox findMarkedCheckBox(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box
                    && Boolean.TRUE.equals(box.getClientProperty(CHECKBOX_MARKER))) return box;
            if (component instanceof Container child) {
                JCheckBox found = findMarkedCheckBox(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box && text.equalsIgnoreCase(box.getText())) return box;
            if (component instanceof Container child) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Container commonParent(Component a, Component b) {
        Set<Container> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container p = a.getParent(); p != null; p = p.getParent()) ancestors.add(p);
        for (Container p = b.getParent(); p != null; p = p.getParent()) {
            if (ancestors.contains(p)) return p;
        }
        return null;
    }

    private static Object fieldValue(Object instance, String fieldName) {
        if (instance == null) return null;
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void invokeFrameMethod(String className, String methodName, JFrame frame) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, JFrame.class);
            method.setAccessible(true);
            method.invoke(null, frame);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
