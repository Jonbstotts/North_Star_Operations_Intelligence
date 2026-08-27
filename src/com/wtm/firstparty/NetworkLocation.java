package com.wtm.firstparty;
public record NetworkLocation(String id,String name,String category,String address,String externalId,double latitude,double longitude,int geofenceMeters,String notes,boolean active){
 public String display(){return name+" • "+category+(externalId==null||externalId.isBlank()?"":" • "+externalId);}
 public boolean hasCoordinates(){return Double.isFinite(latitude)&&Double.isFinite(longitude)&&Math.abs(latitude)<=90&&Math.abs(longitude)<=180&&(latitude!=0||longitude!=0);}
}
