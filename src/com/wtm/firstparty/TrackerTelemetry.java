package com.wtm.firstparty;
public record TrackerTelemetry(String deviceId,String timestamp,double latitude,double longitude,double speedMph,double batteryPct,int signal,String source){}
