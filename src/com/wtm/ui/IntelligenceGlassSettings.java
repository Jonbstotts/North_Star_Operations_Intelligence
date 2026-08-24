package com.wtm.ui;
import java.util.prefs.Preferences;
public final class IntelligenceGlassSettings {
 private static final Preferences P=Preferences.userRoot().node("northstar-operations-intelligence/ui");
 private IntelligenceGlassSettings(){}
 public static boolean enabled(){return P.getBoolean("glassSurfaces",true);}
 public static void setEnabled(boolean v){P.putBoolean("glassSurfaces",v);}
}
