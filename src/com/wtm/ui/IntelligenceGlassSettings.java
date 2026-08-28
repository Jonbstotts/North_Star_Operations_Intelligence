package com.wtm.ui;

import java.util.prefs.Preferences;

/**
 * Persisted appearance preference for NorthStar Intelligence surfaces.
 *
 * <p>This is intentionally a passive settings holder. It performs no polling,
 * Swing-tree scanning, or workspace mutation.</p>
 */
public final class IntelligenceGlassSettings {
    private static final Preferences PREFS =
            Preferences.userRoot().node("northstar-operations-intelligence/ui");

    private IntelligenceGlassSettings() {}

    public static boolean enabled() {
        return PREFS.getBoolean("glassSurfaces", true);
    }

    public static void setEnabled(boolean enabled) {
        PREFS.putBoolean("glassSurfaces", enabled);
    }
}
