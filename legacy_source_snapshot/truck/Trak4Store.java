package com.wtm.truck;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * North Star-owned physical GPS registry and assignment history.
 * This remains usable if Penske or another transportation provider changes.
 */
public final class Trak4Store {
    private static final Trak4Store INSTANCE=new Trak4Store();
    private final List<Trak4Tracker> trackers=new ArrayList<>();
    private final List<Trak4Assignment> assignments=new ArrayList<>();
    private boolean loaded;

    private Trak4Store(){}
    public static Trak4Store get(){return INSTANCE;}

    public synchronized List<Trak4Tracker> trackers(){
        load();return trackers.stream().map(Trak4Tracker::copy).toList();
    }
    public synchronized List<Trak4Assignment> assignments(){
        load();return assignments.stream()
                .sorted(Comparator.comparing(
                        (Trak4Assignment a)->a.assignedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(Trak4Assignment::copy).toList();
    }
    public synchronized List<Trak4Assignment> activeAssignments(){
        load();return assignments.stream().filter(a->a.active)
                .map(Trak4Assignment::copy).toList();
    }

    public synchronized void upsertTracker(Trak4Tracker source){
        load();Trak4Tracker t=source.copy();
        int index=-1;
        for(int i=0;i<trackers.size();i++){
            Trak4Tracker existing=trackers.get(i);
            if(existing.id.equals(t.id)
                    ||(!t.deviceId.isBlank()
                    &&existing.deviceId.equalsIgnoreCase(t.deviceId))){
                index=i;break;
            }
        }
        if(index>=0)trackers.set(index,t);else trackers.add(t);
        saveTrackers();
    }

    public synchronized void assign(Trak4Assignment source){
        load();
        for(Trak4Assignment existing:assignments){
            if(existing.active&&(existing.trackerId.equals(source.trackerId)
                    ||(!source.deviceId.isBlank()
                    &&existing.deviceId.equalsIgnoreCase(source.deviceId)))){
                existing.active=false;existing.releasedAt=LocalDateTime.now();
            }
        }
        Trak4Assignment a=source.copy();
        a.active=true;a.assignedAt=LocalDateTime.now();
        assignments.add(a);saveAssignments();
    }

    public synchronized void release(String id){
        load();
        for(Trak4Assignment a:assignments)
            if(a.id.equals(id)){a.active=false;a.releasedAt=LocalDateTime.now();}
        saveAssignments();
    }

    /** Push current assigned GPS into the matching shipment/load summary. */
    public synchronized int applyAssignmentsToShipments(){
        load();
        TruckTrackingStore shipmentStore=TruckTrackingStore.get();
        List<TruckShipment> shipments=new ArrayList<>(shipmentStore.all());
        int changed=0;
        for(Trak4Assignment a:assignments){
            if(!a.active)continue;
            Trak4Tracker tracker=trackers.stream()
                    .filter(t->t.id.equals(a.trackerId)).findFirst().orElse(null);
            if(tracker==null||!tracker.hasCoordinates())continue;
            for(TruckShipment s:shipments){
                boolean match=!a.outboundShipmentId.isBlank()
                        &&a.outboundShipmentId.equalsIgnoreCase(s.outboundShipmentId);
                if(!match&&!a.dailyTrailerId.isBlank())
                    match=a.dailyTrailerId.equalsIgnoreCase(s.trailerNumber);
                if(!match)continue;
                s.carrier=TruckCarrier.STARHUB;
                s.latitude=tracker.latitude;s.longitude=tracker.longitude;
                s.currentLocation="Trak-4 GPS • "+tracker.label;
                s.providerStatus="TRAK4_GPS";
                if(tracker.lastReport!=null)s.lastUpdated=tracker.lastReport;
                changed++;
            }
        }
        if(changed>0)shipmentStore.replaceAll(shipments);
        return changed;
    }

    private Path trackersFile(){
        return ConfigService.appDataDir().resolve("trak4-trackers.csv");
    }
    private Path assignmentsFile(){
        return ConfigService.appDataDir().resolve("trak4-assignments.csv");
    }

    private void load(){
        if(loaded)return;loaded=true;loadTrackers();loadAssignments();
    }
    private void loadTrackers(){
        Path p=trackersFile();if(!Files.exists(p))return;
        try{
            List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);
            for(int row=1;row<lines.size();row++){
                String[] v=lines.get(row).split(",",-1);if(v.length<10)continue;
                Trak4Tracker t=new Trak4Tracker();
                t.id=v[0];t.deviceId=v[1];t.label=v[2];t.physicalTrailer=v[3];
                t.latitude=d(v[4]);t.longitude=d(v[5]);t.lastReport=dt(v[6]);
                t.batteryPercent=i(v[7]);t.active=Boolean.parseBoolean(v[8]);
                t.source=v[9];trackers.add(t);
            }
        }catch(Exception ignored){}
    }
    private void loadAssignments(){
        Path p=assignmentsFile();if(!Files.exists(p))return;
        try{
            List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);
            for(int row=1;row<lines.size();row++){
                String[] v=lines.get(row).split(",",-1);if(v.length<11)continue;
                Trak4Assignment a=new Trak4Assignment();
                a.id=v[0];a.trackerId=v[1];a.deviceId=v[2];
                a.physicalTrailer=v[3];a.dailyTrailerId=v[4];
                a.outboundShipmentId=v[5];
                try{a.assignmentDate=LocalDate.parse(v[6]);}catch(Exception ignored){}
                a.assignedAt=dt(v[7]);a.releasedAt=dt(v[8]);
                a.active=Boolean.parseBoolean(v[9]);a.notes=v[10];
                assignments.add(a);
            }
        }catch(Exception ignored){}
    }

    private void saveTrackers(){
        List<String> lines=new ArrayList<>();
        lines.add("id,deviceId,label,physicalTrailer,latitude,longitude,lastReport,batteryPercent,active,source");
        for(Trak4Tracker t:trackers)
            lines.add(String.join(",",safe(t.id),safe(t.deviceId),safe(t.label),
                    safe(t.physicalTrailer),safe(t.latitude),safe(t.longitude),
                    safe(t.lastReport),safe(t.batteryPercent),
                    String.valueOf(t.active),safe(t.source)));
        write(trackersFile(),lines);
    }
    private void saveAssignments(){
        List<String> lines=new ArrayList<>();
        lines.add("id,trackerId,deviceId,physicalTrailer,dailyTrailerId,outboundShipmentId,assignmentDate,assignedAt,releasedAt,active,notes");
        for(Trak4Assignment a:assignments)
            lines.add(String.join(",",safe(a.id),safe(a.trackerId),
                    safe(a.deviceId),safe(a.physicalTrailer),
                    safe(a.dailyTrailerId),safe(a.outboundShipmentId),
                    safe(a.assignmentDate),safe(a.assignedAt),safe(a.releasedAt),
                    String.valueOf(a.active),safe(a.notes)));
        write(assignmentsFile(),lines);
    }
    private static void write(Path p,List<String> lines){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            Files.write(p,lines,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            SecureFiles.restrictFile(p);
        }catch(Exception ex){throw new RuntimeException("Unable to save Trak-4 data.",ex);}
    }
    private static String safe(Object v){
        return v==null?"":String.valueOf(v).replace(","," ");
    }
    private static Double d(String v){
        try{return v.isBlank()?null:Double.parseDouble(v);}catch(Exception e){return null;}
    }
    private static Integer i(String v){
        try{return v.isBlank()?null:Integer.parseInt(v);}catch(Exception e){return null;}
    }
    private static LocalDateTime dt(String v){
        try{return v.isBlank()?null:LocalDateTime.parse(v);}catch(Exception e){return null;}
    }
}
