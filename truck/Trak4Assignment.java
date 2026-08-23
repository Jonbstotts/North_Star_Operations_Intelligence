package com.wtm.truck;

import java.time.*;
import java.util.UUID;

/**
 * Historical association of a reusable physical tracker with the temporary
 * DVIEW trailer/shipment identity used for one shipping day.
 */
public final class Trak4Assignment {
    public String id=UUID.randomUUID().toString();
    public String trackerId="";
    public String deviceId="";
    public String physicalTrailer="";
    public String dailyTrailerId="";
    public String outboundShipmentId="";
    public LocalDate assignmentDate=LocalDate.now();
    public LocalDateTime assignedAt=LocalDateTime.now();
    public LocalDateTime releasedAt;
    public boolean active=true;
    public String notes="";

    public Trak4Assignment copy(){
        Trak4Assignment a=new Trak4Assignment();
        a.id=id;a.trackerId=trackerId;a.deviceId=deviceId;
        a.physicalTrailer=physicalTrailer;a.dailyTrailerId=dailyTrailerId;
        a.outboundShipmentId=outboundShipmentId;a.assignmentDate=assignmentDate;
        a.assignedAt=assignedAt;a.releasedAt=releasedAt;
        a.active=active;a.notes=notes;return a;
    }
}
