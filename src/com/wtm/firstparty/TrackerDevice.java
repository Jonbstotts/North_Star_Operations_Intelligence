package com.wtm.firstparty;
public record TrackerDevice(String id,String hardwareId,String imei,String label,String status,double batteryPct,int signal,String firmware,double latitude,double longitude,String lastSeen,String notes){
 public String display(){return label==null||label.isBlank()?id:label+" ("+id+")";}
}
