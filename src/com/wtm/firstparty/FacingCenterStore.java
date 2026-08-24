package com.wtm.firstparty;
import java.util.*;import java.util.prefs.Preferences;
public final class FacingCenterStore{
 private static final Preferences P=Preferences.userRoot().node("com/wtm/northstar/facing-centers");
 public static String get(NetworkLocation n){if(n==null)return"";return P.get(key(n),"");}public static void set(NetworkLocation n,String v){if(n==null)return;String x=v==null?"":v.trim().toUpperCase(Locale.ROOT);if(x.isBlank())P.remove(key(n));else P.put(key(n),x);}public static String get(String externalId){if(externalId==null)return"";return P.get("ext."+externalId.trim().toUpperCase(Locale.ROOT),"");}
 private static String key(NetworkLocation n){String e=n.externalId()==null?"":n.externalId().trim().toUpperCase(Locale.ROOT);return e.isBlank()?"id."+n.id():"ext."+e;}private FacingCenterStore(){}
}
