package com.wtm.modular.ui;

import com.wtm.ui.Theme;
import com.wtm.ui.ThemedComboBoxUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/**
 * Small compatibility/polish layer for the v2.1.30 Music & Audio workspace.
 *
 * This guard deliberately does not replace the base workspace.  It repairs
 * controls that are mounted dynamically after Workspace Setup and Music &
 * Audio are opened, keeps the dashboard music-player preference synchronized,
 * and forces the music page to size to the available viewport on macOS.
 */
public final class MusicWorkspacePolishGuard {
    private static final String WORKSPACE_CLASS = "com.wtm.ui.OperationsWorkspaceFrame";
    private static final String SETTINGS_CLASS = "com.wtm.ui.SettingsDialog";
    private static final String MUSIC_PANEL_CLASS = "com.wtm.modular.ui.MusicModuleGuard$MusicPanel";

    private static final String INTELLIGENCE_MARKER = "northstar.intelligence.workspace.checkbox";
    private static final String MUSIC_MARKER = "northstar.music.workspace.checkbox";
    private static final String MUSIC_MINI_MARKER = "northstar.music.mini";

    private static final Preferences WORKSPACE_PREFS =
            Preferences.userRoot().node("com/wtm/northstar/workspace");
    private static final String INTELLIGENCE_PREF = "dashboard.intelligence.enabled";

    private static final WeakHashMap<Window, Timer> PENDING = new WeakHashMap<>();
    private static final Set<JScrollPane> RESIZED =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean installed;

    private MusicWorkspacePolishGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent we && we.getID() == WindowEvent.WINDOW_OPENED) {
                schedule(we.getWindow(), 80);
                return;
            }

            if (event instanceof ContainerEvent ce && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component child = ce.getChild();
                Window window = child == null ? null : SwingUtilities.getWindowAncestor(child);
                if (isNorthStarWindow(window)) schedule(window, 70);
                polishMusicTree(child);
                return;
            }

            if (event instanceof ActionEvent ae && ae.getSource() instanceof AbstractButton button) {
                if ("Save & Apply".equalsIgnoreCase(clean(button.getText()))) {
                    Window window = SwingUtilities.getWindowAncestor(button);
                    Window workspace = findWorkspace(window);
                    if (workspace != null) schedule(workspace, 260);
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (isNorthStarWindow(window)) schedule(window, 40);
        }
    }

    private static boolean isNorthStarWindow(Window window) {
        return window != null && (WORKSPACE_CLASS.equals(window.getClass().getName())
                || SETTINGS_CLASS.equals(window.getClass().getName()));
    }

    private static void schedule(Window window, int delay) {
        if (window == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> schedule(window, delay));
            return;
        }
        Timer timer = PENDING.get(window);
        if (timer == null) {
            timer = new Timer(Math.max(50, delay), e -> repair(window));
            timer.setRepeats(false);
            PENDING.put(window, timer);
        }
        timer.setInitialDelay(Math.max(50, delay));
        timer.restart();
    }

    private static void repair(Window window) {
        if (window == null || !window.isDisplayable()) return;
        if (window instanceof Container root) {
            injectWorkspaceSetupToggles(window, root);
            polishMusicTree(root);
        }

        Window workspace = findWorkspace(window);
        if (workspace != null) {
            if (musicDashboardEnabled()) invokeMusicDashboardInjection(workspace);
            else removeMusicMiniPlayer((Container) workspace);
        }
    }

    /** Adds both dynamic dashboard modules alongside the native workspace toggles. */
    private static void injectWorkspaceSetupToggles(Window owner, Container root) {
        JCheckBox weather = findCheckBox(root, "Local Weather");
        JCheckBox operations = findCheckBox(root, "Operations Snapshot");
        if (weather == null || operations == null) return;

        Container common = commonParent(weather, operations);
        if (!(common instanceof JPanel panel)) return;

        JCheckBox intelligence = findMarkedCheckBox(panel, INTELLIGENCE_MARKER);
        if (intelligence == null) {
            intelligence = new JCheckBox("NorthStar Intelligence");
            intelligence.putClientProperty(INTELLIGENCE_MARKER, Boolean.TRUE);
            intelligence.setOpaque(false);
            intelligence.setForeground(Theme.text());
            intelligence.setSelected(WORKSPACE_PREFS.getBoolean(INTELLIGENCE_PREF, true));
            intelligence.addActionListener(e -> {
                JCheckBox source = (JCheckBox) e.getSource();
                WORKSPACE_PREFS.putBoolean(INTELLIGENCE_PREF, source.isSelected());
                Window workspace = findWorkspace(owner);
                if (workspace instanceof JFrame frame) {
                    if (source.isSelected()) invokeAiDashboard(frame);
                    else removeNamedComponent(frame, "northstar.ai.compact");
                }
            });
            panel.add(intelligence);
        }

        JCheckBox music = findMarkedCheckBox(panel, MUSIC_MARKER);
        if (music == null) {
            music = new JCheckBox("Music Compact Player");
            music.putClientProperty(MUSIC_MARKER, Boolean.TRUE);
            music.setOpaque(false);
            music.setForeground(Theme.text());
            music.setSelected(musicDashboardEnabled());
            music.addActionListener(e -> {
                JCheckBox source = (JCheckBox) e.getSource();
                setMusicDashboardEnabled(source.isSelected());
                Window workspace = findWorkspace(owner);
                if (workspace != null) {
                    if (source.isSelected()) invokeMusicDashboardInjection(workspace);
                    else removeMusicMiniPlayer((Container) workspace);
                }
            });
            panel.add(music);
        } else {
            music.setSelected(musicDashboardEnabled());
        }

        panel.revalidate();
        panel.repaint();
    }

    /** Makes the Music route viewport-width and normalizes its combo boxes. */
    private static void polishMusicTree(Component component) {
        if (component == null) return;

        if (component instanceof JComboBox<?> combo
                && SwingUtilities.getAncestorOfClassName(MUSIC_PANEL_CLASS, combo) != null) {
            applyThemedCombo(combo);
        }

        if (component instanceof Container container) {
            if (MUSIC_PANEL_CLASS.equals(container.getClass().getName())) {
                JScrollPane scroll = firstScrollPane(container);
                if (scroll != null) constrainMusicScrollPane(scroll);
                applyMusicCombos(container);
            }
            for (Component child : container.getComponents()) polishMusicTree(child);
        }
    }

    private static void constrainMusicScrollPane(JScrollPane scroll) {
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getHorizontalScrollBar().setEnabled(false);
        scroll.getViewport().setBackground(Theme.bg());

        Runnable fit = () -> {
            Component view = scroll.getViewport().getView();
            if (!(view instanceof JComponent jc)) return;
            int width = scroll.getViewport().getExtentSize().width;
            if (width <= 0) width = Math.max(640, scroll.getWidth() - 20);
            Dimension pref = jc.getPreferredSize();
            int height = pref == null ? 800 : Math.max(pref.height, 720);
            jc.setMinimumSize(new Dimension(0, height));
            jc.setPreferredSize(new Dimension(Math.max(620, width), height));
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            jc.revalidate();
        };

        fit.run();
        if (RESIZED.add(scroll)) {
            scroll.getViewport().addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    SwingUtilities.invokeLater(fit);
                }
            });
        }
    }

    private static void applyMusicCombos(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComboBox<?> combo) applyThemedCombo(combo);
            if (child instanceof Container container) applyMusicCombos(container);
        }
    }

    private static void applyThemedCombo(JComboBox<?> combo) {
        combo.setUI(new ThemedComboBoxUI(Theme.active()));
        combo.setBackground(Theme.panel2());
        combo.setForeground(Theme.text());
        combo.setBorder(BorderFactory.createLineBorder(Theme.border()));
        combo.setMinimumSize(new Dimension(120, ThemedComboBoxUI.CONTROL_HEIGHT));
        combo.setPreferredSize(new Dimension(Math.max(150, combo.getPreferredSize().width),
                ThemedComboBoxUI.CONTROL_HEIGHT));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, ThemedComboBoxUI.CONTROL_HEIGHT));
    }

    private static boolean musicDashboardEnabled() {
        try {
            Object service = musicService();
            if (service == null) return true;
            Field settingsField = service.getClass().getDeclaredField("settings");
            settingsField.setAccessible(true);
            Object settings = settingsField.get(service);
            Field dashboard = settings.getClass().getDeclaredField("dashboardPlayer");
            dashboard.setAccessible(true);
            return dashboard.getBoolean(settings);
        } catch (ReflectiveOperationException ex) {
            return true;
        }
    }

    private static void setMusicDashboardEnabled(boolean enabled) {
        try {
            Object service = musicService();
            if (service == null) return;
            Field settingsField = service.getClass().getDeclaredField("settings");
            settingsField.setAccessible(true);
            Object settings = settingsField.get(service);
            Field dashboard = settings.getClass().getDeclaredField("dashboardPlayer");
            dashboard.setAccessible(true);
            dashboard.setBoolean(settings, enabled);
            Method save = service.getClass().getDeclaredMethod("save");
            save.setAccessible(true);
            save.invoke(service);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object musicService() throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName("com.wtm.modular.ui.MusicModuleGuard$MusicService");
        Method instance = serviceClass.getDeclaredMethod("instance");
        instance.setAccessible(true);
        return instance.invoke(null);
    }

    private static void invokeMusicDashboardInjection(Window workspace) {
        try {
            Class<?> type = Class.forName("com.wtm.modular.ui.MusicModuleGuard");
            Method method = type.getDeclaredMethod("injectDashboardPlayer", Window.class);
            method.setAccessible(true);
            method.invoke(null, workspace);
            workspace.revalidate();
            workspace.repaint();
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invokeAiDashboard(JFrame frame) {
        try {
            Class<?> type = Class.forName("com.wtm.app.AiEnabledMain");
            Method method = type.getDeclaredMethod("injectDashboard", JFrame.class);
            method.setAccessible(true);
            method.invoke(null, frame);
            frame.revalidate();
            frame.repaint();
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Window findWorkspace(Window source) {
        for (Window current = source; current != null; current = current.getOwner()) {
            if (WORKSPACE_CLASS.equals(current.getClass().getName())) return current;
        }
        for (Window window : Window.getWindows()) {
            if (WORKSPACE_CLASS.equals(window.getClass().getName()) && window.isDisplayable()) return window;
        }
        return null;
    }

    private static JScrollPane firstScrollPane(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JScrollPane scroll) return scroll;
            if (component instanceof Container child) {
                JScrollPane found = firstScrollPane(child);
                if (found != null) return found;
            }
        }
        return null;
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
        for (Container p = b.getParent(); p != null; p = p.getParent()) {
            if (ancestors.contains(p)) return p;
        }
        return null;
    }

    private static void removeMusicMiniPlayer(Container root) {
        Component found = findMusicMini(root);
        if (found == null || found.getParent() == null) return;
        Container parent = found.getParent();
        parent.remove(found);
        parent.revalidate();
        parent.repaint();
    }

    private static Component findMusicMini(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComponent jc
                    && Boolean.TRUE.equals(jc.getClientProperty(MUSIC_MINI_MARKER))) return component;
            if (component instanceof Container child) {
                Component found = findMusicMini(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void removeNamedComponent(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName()) && component.getParent() != null) {
                Container parent = component.getParent();
                parent.remove(component);
                parent.revalidate();
                parent.repaint();
                return;
            }
            if (component instanceof Container child) removeNamedComponent(child, name);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    /** Java 21/Swing helper without depending on package-private UI internals. */
    private static final class SwingUtilities {
        private SwingUtilities() {}

        static void invokeLater(Runnable runnable) { javax.swing.SwingUtilities.invokeLater(runnable); }
        static boolean isEventDispatchThread() { return javax.swing.SwingUtilities.isEventDispatchThread(); }
        static Window getWindowAncestor(Component component) {
            return component == null ? null : javax.swing.SwingUtilities.getWindowAncestor(component);
        }
        static Container getAncestorOfClassName(String className, Component component) {
            for (Container p = component == null ? null : component.getParent(); p != null; p = p.getParent()) {
                if (className.equals(p.getClass().getName())) return p;
            }
            return null;
        }
    }
}
