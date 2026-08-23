package com.wtm.truck;

import java.time.LocalDateTime;
import java.util.UUID;

/** One physical Trak-4 GPS device owned by the operation. */
public final class Trak4Tracker {
    public String id=UUID.randomUUID().toString();
    public String deviceId="";
    public String label="";
    public String physicalTrailer="";
    public Double latitude;
    public Double longitude;
    public LocalDateTime lastReport;
    public Integer batteryPercent;
    public boolean active=true;
    public String source="MANUAL";

    public Trak4Tracker copy(){
        Trak4Tracker t=new Trak4Tracker();
        t.id=id;t.deviceId=deviceId;t.label=label;t.physicalTrailer=physicalTrailer;
        t.latitude=latitude;t.longitude=longitude;t.lastReport=lastReport;
        t.batteryPercent=batteryPercent;t.active=active;t.source=source;
        return t;
    }
    public boolean hasCoordinates(){
        return latitude!=null&&longitude!=null
                &&Double.isFinite(latitude)&&Double.isFinite(longitude);
    }
}
