package com.wtm.truck;

import java.time.LocalDateTime;
import java.util.UUID;

/** One carrier-reported tracking checkpoint retained for history/playback. */
public final class ShipmentTrackingEvent {
    public enum MovementMode {
        UNKNOWN,GROUND,AIR,RAIL,SEA;
        public static MovementMode parse(String value){
            if(value==null)return UNKNOWN;
            String v=value.trim().toUpperCase();
            if(v.contains("AIR")||v.contains("FLIGHT"))return AIR;
            if(v.contains("GROUND")||v.contains("TRUCK")||v.contains("ROAD"))
                return GROUND;
            if(v.contains("RAIL"))return RAIL;
            if(v.contains("SEA")||v.contains("OCEAN"))return SEA;
            return UNKNOWN;
        }
    }

    public String id=UUID.randomUUID().toString();
    public String shipmentId="";
    public String trackingNumber="";
    public String provider="";
    public String eventCode="";
    public String eventDescription="";
    public LocalDateTime eventTime;
    public String city="";
    public String state="";
    public String country="";
    public Double latitude;
    public Double longitude;
    /** PROVIDER_GPS, PROVIDER_PLACE, GEOCODED_PLACE, or UNKNOWN. */
    public String locationConfidence="UNKNOWN";
    public MovementMode movementMode=MovementMode.UNKNOWN;
    public String rawStatus="";

    public ShipmentTrackingEvent copy(){
        ShipmentTrackingEvent e=new ShipmentTrackingEvent();
        e.id=id;e.shipmentId=shipmentId;e.trackingNumber=trackingNumber;
        e.provider=provider;e.eventCode=eventCode;
        e.eventDescription=eventDescription;e.eventTime=eventTime;
        e.city=city;e.state=state;e.country=country;
        e.latitude=latitude;e.longitude=longitude;
        e.locationConfidence=locationConfidence;
        e.movementMode=movementMode;e.rawStatus=rawStatus;
        return e;
    }

    public boolean hasCoordinates(){
        return latitude!=null&&longitude!=null
                &&Double.isFinite(latitude)&&Double.isFinite(longitude);
    }

    public String placeLabel(){
        StringBuilder b=new StringBuilder();
        if(city!=null&&!city.isBlank())b.append(city);
        if(state!=null&&!state.isBlank()){
            if(b.length()>0)b.append(", ");
            b.append(state);
        }
        if(country!=null&&!country.isBlank()&&b.length()==0)b.append(country);
        return b.toString();
    }
}
