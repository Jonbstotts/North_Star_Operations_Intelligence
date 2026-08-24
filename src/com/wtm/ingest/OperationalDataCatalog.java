package com.wtm.ingest;

import java.util.*;import java.util.prefs.Preferences;

public final class OperationalDataCatalog {
 public record Feed(String id,String title,String domain,String description,boolean showcaseDefault){}
 private static final List<Feed> FEEDS=List.of(
  new Feed("DAILY_PRODUCTIVITY","Daily LHY & LPH","Productivity & Throughput","Inbound/outbound hours, receipt/order lines, LPH and LHY. Partial current-day rows are kept but excluded from completed-day trend comparisons.",true),
  new Feed("HOURLY_PICKS","Hourly Picks by User","Productivity & Throughput","Hourly pick counts by associate and total throughput.",true),
  new Feed("HOURLY_BINNED","Hourly Binned Tasks by User","Productivity & Throughput","Hourly binned-task counts by associate and total throughput.",false),
  new Feed("FLOOR_DENIALS","Floor Denials","Exceptions & Quality","Denial events, shorted quantity, affected value, part and location concentration.",true),
  new Feed("TASK_DELETIONS","Deleted Move/Replenishment Tasks","Exceptions & Quality","Normal, consolidation, replenishment and hot-replenishment task deletion activity.",false),
  new Feed("TICKET_UNDOS","Undone Tickets","Exceptions & Quality","Inbound ticket undo events, quantities, parts, receipts, sources and users.",false),
  new Feed("AUDIT_ITEM","Who Touched Item","Activity Trace","Chronological item/process/user/barcode/location activity for investigation.",false),
  new Feed("AUDIT_LOCATION","Who Touched Location","Activity Trace","Chronological location activity for investigation.",false),
  new Feed("AUDIT_TASK","Who Touched Task","Activity Trace","Chronological task/barcode activity for investigation.",false),
  new Feed("EQUIPMENT_USAGE","Equipment Usage","Equipment & RF Activity","Historical equipment start/stop activity by user.",false),
  new Feed("RF_TERMINAL","RF Terminal Assignment","Equipment & RF Activity","Current/previous RF terminal association and last activity.",false),
  new Feed("DEALER_NETWORK","Dealer / Logistics Network","Logistics Network","Categorized dealer and logistics destination registry.",false),
  new Feed("TRACKER_TELEMETRY","Tracker Telemetry","Truck Tracking","First-party tracker GPS and device telemetry.",false));
 private static final Preferences P=Preferences.userRoot().node("com/wtm/northstar/showcase");
 private OperationalDataCatalog(){} public static List<Feed> feeds(){return FEEDS;}
 public static Feed feed(String id){for(Feed f:FEEDS)if(f.id().equals(id))return f;return null;}
 public static boolean showcase(String id){Feed f=feed(id);return P.getBoolean(id,f!=null&&f.showcaseDefault());}
 public static void setShowcase(String id,boolean v){P.putBoolean(id,v);}
 public static List<Feed> showcased(){return FEEDS.stream().filter(f->showcase(f.id())).toList();}
}
