package com.wtm.truck;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Durable detailed carrier-event history stored separately from shipment rows. */
public final class ShipmentEventStore {
    private static final ShipmentEventStore INSTANCE=new ShipmentEventStore();
    private final List<ShipmentTrackingEvent> events=new ArrayList<>();
    private boolean loaded;

    private ShipmentEventStore(){}
    public static ShipmentEventStore get(){return INSTANCE;}
    private Path file(){
        return ConfigService.appDataDir().resolve("shipment-tracking-events.csv");
    }

    public synchronized List<ShipmentTrackingEvent> forShipment(String id){
        load();
        return events.stream().filter(e->Objects.equals(e.shipmentId,id))
                .sorted(Comparator.comparing(
                        (ShipmentTrackingEvent e)->e.eventTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ShipmentTrackingEvent::copy).toList();
    }

    public synchronized void replaceProviderEvents(
            String shipmentId,String tracking,String provider,
            Collection<ShipmentTrackingEvent> replacement){
        load();
        events.removeIf(e->Objects.equals(e.shipmentId,shipmentId)
                &&provider.equalsIgnoreCase(e.provider)
                &&tracking.equalsIgnoreCase(e.trackingNumber));
        for(ShipmentTrackingEvent source:replacement){
            ShipmentTrackingEvent e=source.copy();
            e.shipmentId=shipmentId;e.trackingNumber=tracking;e.provider=provider;
            events.add(e);
        }
        save();
    }

    private void load(){
        if(loaded)return;
        loaded=true;
        Path p=file();if(!Files.exists(p))return;
        try{
            List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);
            for(int row=1;row<lines.size();row++){
                List<String> v=parse(lines.get(row));
                if(v.size()<15)continue;
                ShipmentTrackingEvent e=new ShipmentTrackingEvent();
                e.id=v.get(0);e.shipmentId=v.get(1);e.trackingNumber=v.get(2);
                e.provider=v.get(3);e.eventCode=v.get(4);
                e.eventDescription=v.get(5);e.eventTime=time(v.get(6));
                e.city=v.get(7);e.state=v.get(8);e.country=v.get(9);
                e.latitude=number(v.get(10));e.longitude=number(v.get(11));
                e.locationConfidence=v.get(12);
                e.movementMode=ShipmentTrackingEvent.MovementMode.parse(v.get(13));
                e.rawStatus=v.get(14);
                events.add(e);
            }
        }catch(Exception ex){
            System.err.println("Carrier event history could not be loaded.");
        }
    }

    private void save(){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            Path p=file();
            List<String> lines=new ArrayList<>();
            lines.add("id,shipmentId,trackingNumber,provider,eventCode,"
                    +"eventDescription,eventTime,city,state,country,latitude,"
                    +"longitude,locationConfidence,movementMode,rawStatus");
            for(ShipmentTrackingEvent e:events)
                lines.add(String.join(",",
                        csv(e.id),csv(e.shipmentId),csv(e.trackingNumber),
                        csv(e.provider),csv(e.eventCode),
                        csv(e.eventDescription),csv(e.eventTime),csv(e.city),
                        csv(e.state),csv(e.country),csv(e.latitude),
                        csv(e.longitude),csv(e.locationConfidence),
                        csv(e.movementMode.name()),csv(e.rawStatus)));
            Files.write(p,lines,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            SecureFiles.restrictFile(p);
        }catch(Exception ex){
            throw new RuntimeException("Unable to save carrier-event history.",ex);
        }
    }

    private static LocalDateTime time(String v){
        try{return v.isBlank()?null:OffsetDateTime.parse(v).toLocalDateTime();}
        catch(Exception ignored){}
        try{return v.isBlank()?null:LocalDateTime.parse(v);}
        catch(Exception ignored){}
        return null;
    }
    private static Double number(String v){
        try{return v.isBlank()?null:Double.parseDouble(v);}
        catch(Exception ex){return null;}
    }
    private static String csv(Object value){
        String s=value==null?"":String.valueOf(value);
        if(s.contains("\""))s=s.replace("\"","\"\"");
        return s.contains(",")||s.contains("\"")||s.contains("\n")
                ?"\""+s+"\"":s;
    }
    private static List<String> parse(String line){
        List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();
        boolean q=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"'){
                if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){
                    b.append('"');i++;
                }else q=!q;
            }else if(c==','&&!q){out.add(b.toString());b.setLength(0);}
            else b.append(c);
        }
        out.add(b.toString());return out;
    }
}
