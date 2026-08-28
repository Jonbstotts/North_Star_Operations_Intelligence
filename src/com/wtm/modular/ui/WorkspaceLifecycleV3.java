package com.wtm.modular.ui;

import com.wtm.app.AiEnabledMain;
import com.wtm.config.AppConfig;
import com.wtm.ui.OperationsWorkspaceFrame;
import com.wtm.ui.SettingsDialog;

import javax.swing.*;
import java.util.prefs.Preferences;

/**
 * Explicit coordinator for optional workspace additions that live outside
 * the base OperationsWorkspaceFrame source.
 *
 * <p>No global AWT listener, window scan, polling timer, reflection, or
 * component-added watcher is used. Canonical source owners call these
 * methods at concrete construction, dashboard-mount, and settings-save
 * boundaries.</p>
 */
public final class WorkspaceLifecycleV3 {
    private static final String MUSIC_MINI_MARKER = "northstar.music.mini";
    private static final String INTELLIGENCE_CHECK = "northstar.intelligence.workspace.checkbox";
    private static final String MUSIC_CHECK = "northstar.music.workspace.checkbox";
    private static final String INTELLIGENCE_TOKEN = "NORTHSTAR_INTELLIGENCE";
    private static final String INTELLIGENCE_PREF = "dashboard.intelligence.enabled";

    private static final Preferences WORKSPACE_PREFS =
            Preferences.userRoot().node("com/wtm/northstar/workspace");
    private static boolean bootstrapped;

    private WorkspaceLifecycleV3() {}

    /** Loads provider settings before the first workspace is constructed. */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        loadMusicSettings();
    }

    /** Called by OperationsWorkspaceFrame after its canonical UI is built. */
    public static void initializeWorkspace(OperationsWorkspaceFrame workspace) {
        if (workspace == null) return;
        AiEnabledMain.injectSidebar(workspace);
        MusicModuleGuard.installWorkspace(workspace);
        syncDynamicDashboard(workspace);
        workspace.getRootPane().revalidate();
        workspace.validate();
        workspace.repaint();
    }

    /** Called whenever the canonical Dashboard route has just been mounted. */
    public static void dashboardMounted(OperationsWorkspaceFrame workspace) {
        if (workspace == null) return;
        syncDynamicDashboard(workspace);
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
        else workspace.removeDashboardExtension("northstar.ai.compact");

        MusicModuleGuard.installWorkspace(workspace);
        if (!musicDashboardEnabled()) workspace.removeDashboardExtension(MUSIC_MINI_MARKER);

        workspace.getRootPane().revalidate();
        workspace.validate();
        workspace.repaint();
    }

    private static boolean intelligenceEnabled() {
        return WORKSPACE_PREFS.getBoolean(INTELLIGENCE_PREF, true);
    }

    private static void injectWorkspaceToggles(SettingsDialog owner) {
        JCheckBox intelligence=owner.registerWorkspaceModuleToggle(
                INTELLIGENCE_CHECK,"NorthStar Intelligence",intelligenceEnabled());
        if(intelligence!=null)
            setModuleToken(owner.workspaceConfigForExtensions(),
                    INTELLIGENCE_TOKEN,intelligence.isSelected());

        owner.registerWorkspaceModuleToggle(
                MUSIC_CHECK,"Music Compact Player",musicDashboardEnabled());
    }

    private static void persistWorkspaceToggles(SettingsDialog owner) {
        JCheckBox intelligence=owner.workspaceModuleToggle(INTELLIGENCE_CHECK);
        if(intelligence!=null){
            WORKSPACE_PREFS.putBoolean(INTELLIGENCE_PREF,intelligence.isSelected());
            setModuleToken(owner.workspaceConfigForExtensions(),
                    INTELLIGENCE_TOKEN,intelligence.isSelected());
        }

        JCheckBox music=owner.workspaceModuleToggle(MUSIC_CHECK);
        if(music!=null)setMusicDashboardEnabled(music.isSelected());
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


}
