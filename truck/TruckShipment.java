package com.wtm.truck;

import java.time.*;
import java.util.UUID;

/**
 * Carrier-neutral shipment/load record.
 *
 * The same model is intentionally used for FedEx Freight PRO numbers,
 * Mercedes StarHub/Penske loads, CSV/manual records, and future providers.
 */
public final class TruckShipment {
    public String id=UUID.randomUUID().toString();
    public TruckCarrier carrier=TruckCarrier.OTHER;
    public String trackingNumber="";
    /** FedEx Freight PRO number when present. */
    public String proNumber="";
    /** IBM/DVIEW trailer identifier. */
    public String trailerNumber="";
    /** IBM/DVIEW outbound shipment identifier. */
    public String outboundShipmentId="";
    /** IBM/DVIEW customer/dealer key. */
    public String customerBk="";
    /** One or more IBM/DVIEW Ship IDs, semicolon-delimited. */
    public String shipIds="";
    /** Source extract/system for traceability (for example IBM_DVIEW). */
    public String sourceSystem="";
    /** Shipment date from the source extract when supplied. */
    public LocalDate shippedDate;
    public String routeName="";
    public String origin="";
    public String destination="";
    public TruckStatus status=TruckStatus.PLANNED;
    public String currentLocation="";
    public Double latitude;
    public Double longitude;
    public LocalDateTime scheduledArrival;
    public LocalDateTime estimatedArrival;
    public int trafficDelayMinutes;
    public int weatherDelayMinutes;
    public LocalDateTime lastUpdated=LocalDateTime.now();
    public LocalDateTime deliveredAt;
    public String notes="";
    public String providerStatus="";
    public boolean archived;

    public TruckShipment copy(){
        TruckShipment s=new TruckShipment();
        s.id=id;
        s.carrier=carrier;
        s.trackingNumber=trackingNumber;
        s.proNumber=proNumber;
        s.trailerNumber=trailerNumber;
        s.outboundShipmentId=outboundShipmentId;
        s.customerBk=customerBk;
        s.shipIds=shipIds;
        s.sourceSystem=sourceSystem;
        s.shippedDate=shippedDate;
        s.routeName=routeName;
        s.origin=origin;
        s.destination=destination;
        s.status=status;
        s.currentLocation=currentLocation;
        s.latitude=latitude;
        s.longitude=longitude;
        s.scheduledArrival=scheduledArrival;
        s.estimatedArrival=estimatedArrival;
        s.trafficDelayMinutes=trafficDelayMinutes;
        s.weatherDelayMinutes=weatherDelayMinutes;
        s.lastUpdated=lastUpdated;
        s.deliveredAt=deliveredAt;
        s.notes=notes;
        s.providerStatus=providerStatus;
        s.archived=archived;
        return s;
    }

    public String primaryCarrierIdentifier(){
        if(trackingNumber!=null&&!trackingNumber.isBlank())return trackingNumber;
        if(proNumber!=null&&!proNumber.isBlank())return proNumber;
        if(outboundShipmentId!=null&&!outboundShipmentId.isBlank())
            return outboundShipmentId;
        if(trailerNumber!=null&&!trailerNumber.isBlank())return trailerNumber;
        return carrier.display();
    }

    public int totalDelayMinutes(){
        return Math.max(0,trafficDelayMinutes)+Math.max(0,weatherDelayMinutes);
    }

    public LocalDateTime projectedArrival(){
        if(estimatedArrival!=null)return estimatedArrival;
        if(scheduledArrival==null)return null;
        return scheduledArrival.plusMinutes(totalDelayMinutes());
    }

    public boolean hasCoordinates(){
        return latitude!=null&&longitude!=null
                &&Double.isFinite(latitude)&&Double.isFinite(longitude);
    }

    public boolean active(){
        return !archived&&!status.closed();
    }
}
