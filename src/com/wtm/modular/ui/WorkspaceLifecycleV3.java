package com.wtm.modular.ui;

import com.wtm.app.AiEnabledMain;
import com.wtm.config.AppConfig;
import com.wtm.ui.OperationsWorkspaceFrame;
import com.wtm.ui.SettingsDialog;
import com.wtm.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Explicit coordinator for optional workspace additions that live outside
 * the base OperationsWorkspaceFrame source.
 *
 * <p>No global AWT listener, window scan, polling timer, reflection, or
 * component-added watcher is used. Canonical source owners call these
 * methods at concrete construction, route-mount, dashboard-mount, and
 * settings-save boundaries.</p>
 */
public final class WorkspaceLifecycleV3 {
    private static final String MUSIC_MINI_MARKER = "northstar.music.mini";
    private static final String INTELLIGENCE_CHECK = "northstar.intelligence.workspace.checkbox";
    private static final String MUSIC_CHECK = "northstar.music.workspace.checkbox";
    private static final String INTELLIGENCE_TOKEN = "NORTHSTAR_INTELLIGENCE";
    private static final String INTELLIGENCE_PREF = "dashboard.intelligence.enabled";

    private static final Preferences WORKSPACE_PREFS =
            Preferences.userRoot().node("com/wtm/northstar/workspace");
    private static boolean installed;

    private WorkspaceLifecycleV3() {}

    /** Loads provider settings before the first workspace is constructed. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        loadMusicSettings();
    }

    /** Called by OperationsWorkspaceFrame after its canonical UI is built. */
    public static void initializeWorkspace(OperationsWorkspaceFrame workspace) {
        if (workspace == null) return;
        AiEnabledMain.injectSidebar(workspace);
        MusicModuleGuard.installWorkspace(workspace);
        syncDynamicDashboard(workspace);
        TrustedEmailUiGuard.apply(workspace);
        workspace.getRootPane().revalidate();
        workspace.validate();
        workspace.repaint();
    }

    /** Called whenever the canonical Dashboard route has just been mounted. */
    public static void dashboardMounted(OperationsWorkspaceFrame workspace) {
        if (workspace == null) return;
        syncDynamicDashboard(workspace);
    }

    /** Called after a non-Dashboard route is mounted. */
    public static void routeMounted(OperationsWorkspaceFrame workspace) {
        if (workspace == null) return;
        TrustedEmailUiGuard.apply(workspace);
    }

    /** Called after Settings has built all authorized workspace controls. */
    public static void settingsReady(SettingsDialog settings) {
        if (settings == null) return;
        injectWorkspaceToggles(settings);
    }

    /** Persists extension-owned Settings controls before ConfigService.save(). */
    public static void persistSettings(SettingsDialog settings) {
        if (settings == null) return;
        persistWorkspaceToggles(settings);
    }

    /** Reinstalls dynamic routes after Settings has rebuilt its owning workspace. */
    public static void settingsApplied(SettingsDialog settings) {
        if (settings != null && settings.getOwner() instanceof OperationsWorkspaceFrame workspace)
            initializeWorkspace(workspace);
    }

    private static void syncDynamicDashboard(OperationsWorkspaceFrame workspace) {
        if (!"Dashboard".equalsIgnoreCase(workspace.activeWorkspaceRouteName())) return;

        if (intelligenceEnabled()) AiEnabledMain.injectDashboard(workspace);
        else removeNamed(workspace, "northstar.ai.compact");

        MusicModuleGuard.installWorkspace(workspace);
        if (!musicDashboardEnabled()) removeMarked(workspace, MUSIC_MINI_MARKER);

        workspace.getRootPane().revalidate();
        workspace.validate();
        workspace.repaint();
    }

    private static boolean intelligenceEnabled() {
        return WORKSPACE_PREFS.getBoolean(INTELLIGENCE_PREF, true);
    }

    private static void injectWorkspaceToggles(SettingsDialog owner) {
        JCheckBox weather = findCheckBox(owner, "Local Weather");
        JCheckBox operations = findCheckBox(owner, "Operations Snapshot");
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
            panel.add(intelligence);
        }
        setModuleToken(owner.workspaceConfigForExtensions(),
                INTELLIGENCE_TOKEN, intelligence.isSelected());

        JCheckBox music = findMarkedCheckBox(panel, MUSIC_CHECK);
        if (music == null) {
            music = new JCheckBox("Music Compact Player");
            music.putClientProperty(MUSIC_CHECK, Boolean.TRUE);
            music.setOpaque(false);
            music.setForeground(Theme.text());
            music.setSelected(musicDashboardEnabled());
            panel.add(music);
        } else {
            music.setSelected(musicDashboardEnabled());
        }

        panel.revalidate();
        panel.repaint();
    }

    private static void persistWorkspaceToggles(SettingsDialog owner) {
        JCheckBox intelligence = findMarkedCheckBox(owner, INTELLIGENCE_CHECK);
        if (intelligence != null) {
            WORKSPACE_PREFS.putBoolean(INTELLIGENCE_PREF, intelligence.isSelected());
            setModuleToken(owner.workspaceConfigForExtensions(),
                    INTELLIGENCE_TOKEN, intelligence.isSelected());
        }

        JCheckBox music = findMarkedCheckBox(owner, MUSIC_CHECK);
        if (music != null) setMusicDashboardEnabled(music.isSelected());
    }

    private static void setModuleToken(AppConfig cfg, String token, boolean enabled) {
        if (cfg == null) return;
        cfg.workspaceModules.removeIf(value -> token.equalsIgnoreCase(String.valueOf(value)));
        if (enabled) cfg.workspaceModules.add(token);
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
            java.nio.file.Path directory = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".northstar");
            com.wtm.util.SecureFiles.ensurePrivateDirectory(directory);
            java.nio.file.Path file = directory.resolve("music.properties");
            if (java.nio.file.Files.isRegularFile(file))
                com.wtm.util.SecureFiles.restrictFile(file);
        } catch (Exception ignored) {
        }
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box
                    && text.equalsIgnoreCase(clean(box.getText()))) return box;
            if (component instanceof Container child) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox findMarkedCheckBox(Container root, String marker) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box
                    && Boolean.TRUE.equals(box.getClientProperty(marker))) return box;
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
        for (Container p = b.getParent(); p != null; p = p.getParent())
            if (ancestors.contains(p)) return p;
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
            if (component instanceof JComponent jc
                    && Boolean.TRUE.equals(jc.getClientProperty(marker))) return component;
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

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("^[^A-Za-z0-9]+", "").trim();
    }
}
