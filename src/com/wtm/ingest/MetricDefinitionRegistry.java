package com.wtm.ingest;

import java.util.*;

/** Central typed metric metadata used by KPI Showcase and NorthStar Intelligence. */
public final class MetricDefinitionRegistry {
 public enum Direction { HIGHER_IS_BETTER, LOWER_IS_BETTER, INFORMATIONAL }
 public record Metric(String id,String label,String unit,Double target,Direction direction,String sourceType,String sourceField,String description){}
 private static final List<Metric> METRICS=List.of(
  new Metric("LHY","LHY","LHY",17500d,Direction.HIGHER_IS_BETTER,"DAILY_PRODUCTIVITY","LHY","Labor-hour yield. Values above target are favorable."),
  new Metric("INBOUND_LPH","Inbound LPH","lines/hour",null,Direction.INFORMATIONAL,"DAILY_PRODUCTIVITY","Inbound LPH","Inbound lines processed per labor hour."),
  new Metric("OUTBOUND_LPH","Outbound LPH","lines/hour",null,Direction.INFORMATIONAL,"DAILY_PRODUCTIVITY","Outbound LPH","Outbound lines processed per labor hour."),
  new Metric("PICKS","Picks","picks",null,Direction.INFORMATIONAL,"HOURLY_PICKS","TOTAL","Picking throughput from the latest imported snapshot."),
  new Metric("BINNED_TASKS","Binned Tasks","tasks",null,Direction.INFORMATIONAL,"HOURLY_BINNED","TOTAL","Binned-task throughput from the latest imported snapshot."),
  new Metric("FLOOR_DENIALS","Floor Denials","events",0d,Direction.LOWER_IS_BETTER,"FLOOR_DENIALS","COUNT","Floor denial event count."),
  new Metric("FLOOR_DENIAL_VALUE","Floor Denial Value","USD",0d,Direction.LOWER_IS_BETTER,"FLOOR_DENIALS","Ext Value Shorted","Extended value represented by current floor-denial feed."),
  new Metric("TASK_DELETIONS","Task Deletions","events",0d,Direction.LOWER_IS_BETTER,"TASK_DELETIONS","COUNT","Deleted move/replenishment task rows."),
  new Metric("TICKET_UNDOS","Ticket Undos","events",0d,Direction.LOWER_IS_BETTER,"TICKET_UNDOS","COUNT","Ticket undo activity rows."));
 private MetricDefinitionRegistry(){}
 public static List<Metric> all(){return METRICS;}
 public static Metric byId(String id){for(Metric m:METRICS)if(m.id().equalsIgnoreCase(id))return m;return null;}
 public static List<Metric> forSource(String sourceType){return METRICS.stream().filter(m->m.sourceType().equalsIgnoreCase(sourceType)).toList();}
 public static String directionLabel(Direction d){return switch(d){case HIGHER_IS_BETTER->"Higher is better";case LOWER_IS_BETTER->"Lower is better";case INFORMATIONAL->"Informational";};}
}
