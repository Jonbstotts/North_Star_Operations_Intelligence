package com.wtm.firstparty;
public record TrackerTrip(String id,String deviceId,String trailerId,String shipmentId,String destinationId,String state,String assignedAt,String scheduledArrival,String estimatedArrival,int delayMinutes,String lastUpdated,String notes){}
