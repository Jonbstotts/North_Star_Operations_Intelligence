package com.wtm.firstparty;
import java.util.*;import java.util.prefs.Preferences;
public final class NetworkPinStyleRegistry{
 public static final String[] CATEGORIES={"Home / Origin","Logistics Center","Dealer","Carrier Terminal","Supplier","Cross-Dock / Consolidation Point","Service / Support Facility","Other / Temporary Destination"};
 public static final String[] STYLES={"Star","Circle","Square","Diamond","Triangle","Home","Dealer Dot"};
 private static final Preferences P=Preferences.userRoot().node("com/wtm/northstar/network-pin-styles");private static final Map<String,String>D=Map.of("Home / Origin","Home","Logistics Center","Star","Dealer","Dealer Dot","Carrier Terminal","Diamond","Supplier","Triangle","Cross-Dock / Consolidation Point","Square","Service / Support Facility","Circle","Other / Temporary Destination","Circle");
 public static String style(String c){return P.get(c,D.getOrDefault(c,"Circle"));}public static void set(String c,String s){P.put(c,s);}private NetworkPinStyleRegistry(){}
}
