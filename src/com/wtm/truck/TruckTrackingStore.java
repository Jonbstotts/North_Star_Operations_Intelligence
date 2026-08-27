package com.wtm.truck;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

/**
 * Small local shipment repository.
 *
 * Current and archived records live in one compact CSV so historical shipment
 * data remains available without cluttering the Current view. The store is
 * synchronized because map painting, refresh tasks, imports, and UI actions can
 * touch it from different threads.
 */
public final class TruckTrackingStore {
    private static final TruckTrackingStore INSTANCE=new TruckTrackingStore();
    private static final String FILE_NAME="truck-tracking.csv";
    private static final DateTimeFormatter DT=DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final List<TruckShipment> shipments=new ArrayList<>();
    private boolean loaded;

    private TruckTrackingStore(){}

    public static TruckTrackingStore get(){return INSTANCE;}

    private Path file(){
        return ConfigService.appDataDir().resolve(FILE_NAME);
    }

    public synchronized List<TruckShipment> all(){
        ensureLoaded();
        return shipments.stream().map(TruckShipment::copy).toList();
    }

    public synchronized List<TruckShipment> current(int historyDays){
        ensureLoaded();
        LocalDateTime cutoff=LocalDateTime.now()
                .minusDays(Math.max(0,historyDays));
        return shipments.stream()
                .filter(s->!s.archived)
                .filter(s->!s.status.closed()
                        ||s.lastUpdated==null
                        ||!s.lastUpdated.isBefore(cutoff))
                .sorted(Comparator.comparing(
                        (TruckShipment s)->Optional.ofNullable(
                                s.projectedArrival()).orElse(
                                LocalDateTime.MAX)))
                .map(TruckShipment::copy)
                .toList();
    }

    public synchronized List<TruckShipment> visibleOnMap(
            boolean fedex,
            boolean starhub,
            int deliveredDays
    ){
        ensureLoaded();
        LocalDateTime deliveredCutoff=LocalDateTime.now()
                .minusDays(Math.max(0,deliveredDays));

        return shipments.stream()
                .filter(s->!s.archived)
                .filter(s->switch(s.carrier){
                    case FEDEX->fedex;
                    case STARHUB->starhub;
                    default->false;
                })
                .filter(s->{
                    if(!s.status.closed())return true;
                    LocalDateTime closed=s.deliveredAt!=null
                            ?s.deliveredAt:s.lastUpdated;
                    return closed!=null&&!closed.isBefore(deliveredCutoff);
                })
                .map(TruckShipment::copy)
                .toList();
    }

    public synchronized TruckShipment find(String id){
        ensureLoaded();
        return shipments.stream()
                .filter(s->Objects.equals(s.id,id))
                .findFirst()
                .map(TruckShipment::copy)
                .orElse(null);
    }

    public synchronized void upsert(TruckShipment shipment){
        ensureLoaded();
        TruckShipment safe=shipment.copy();
        if(safe.id==null||safe.id.isBlank())
            safe.id=UUID.randomUUID().toString();
        safe.lastUpdated=LocalDateTime.now();

        int index=-1;
        for(int i=0;i<shipments.size();i++){
            if(Objects.equals(shipments.get(i).id,safe.id)){
                index=i;break;
            }
        }
        if(index>=0)shipments.set(index,safe);
        else shipments.add(safe);
        save();
    }

    public synchronized void replaceAll(Collection<TruckShipment> records){
        ensureLoaded();
        shipments.clear();
        for(TruckShipment s:records)shipments.add(s.copy());
        save();
    }

    public synchronized int archiveClosedOlderThan(int days){
        ensureLoaded();
        LocalDateTime cutoff=LocalDateTime.now().minusDays(Math.max(0,days));
        int changed=0;
        for(TruckShipment s:shipments){
            LocalDateTime t=s.deliveredAt!=null?s.deliveredAt:s.lastUpdated;
            if(s.status.closed()&&!s.archived&&t!=null&&t.isBefore(cutoff)){
                s.archived=true;
                changed++;
            }
        }
        if(changed>0)save();
        return changed;
    }

    public synchronized int deleteWhere(Predicate<TruckShipment> predicate){
        ensureLoaded();
        int before=shipments.size();
        shipments.removeIf(predicate);
        int removed=before-shipments.size();
        if(removed>0)save();
        return removed;
    }

    public synchronized TruckTrackingStats stats(){
        ensureLoaded();
        List<TruckShipment> active=shipments.stream()
                .filter(TruckShipment::active).toList();

        int delayed=(int)active.stream().filter(
                s->s.status==TruckStatus.DELAYED||s.totalDelayMinutes()>0
        ).count();
        int weather=(int)active.stream().filter(
                s->s.weatherDelayMinutes>0).count();
        int traffic=(int)active.stream().filter(
                s->s.trafficDelayMinutes>0).count();

        TruckShipment next=active.stream()
                .filter(s->s.projectedArrival()!=null)
                .min(Comparator.comparing(TruckShipment::projectedArrival))
                .orElse(null);

        return new TruckTrackingStats(
                active.size(),delayed,weather,traffic,
                next==null?null:next.projectedArrival(),
                next==null?"":next.carrier.display(),
                next==null?"":next.trackingNumber
        );
    }

    public synchronized int importCsv(Path path) throws IOException{
        ensureLoaded();
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);
        if(lines.isEmpty())return 0;

        List<String> header=parseCsvLine(lines.get(0));
        Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<header.size();i++)
            columns.put(normalize(header.get(i)),i);

        if(isIbmTrailerTrackingSchema(columns))
            return importIbmTrailerTracking(lines,columns);

        int imported=0;
        for(int row=1;row<lines.size();row++){
            if(lines.get(row).isBlank())continue;
            List<String> values=parseCsvLine(lines.get(row));

            TruckShipment shipment=new TruckShipment();
            shipment.id=value(values,columns,"id","");
            if(shipment.id.isBlank())shipment.id=UUID.randomUUID().toString();
            shipment.carrier=TruckCarrier.parse(
                    first(values,columns,"carrier","provider"));
            shipment.trackingNumber=first(
                    values,columns,"trackingnumber",
                    "loadnumber","tracking");
            shipment.proNumber=first(values,columns,"pronumber","pro");
            shipment.trailerNumber=first(
                    values,columns,"trailernumber","trailer");
            shipment.outboundShipmentId=first(
                    values,columns,"outboundshipmentid","outboundshipment");
            shipment.customerBk=first(
                    values,columns,"customerbk","customer");
            shipment.shipIds=first(
                    values,columns,"shipids","shipidbk","shipid");
            shipment.sourceSystem=first(
                    values,columns,"sourcesystem","source");
            shipment.shippedDate=parseDate(
                    first(values,columns,"shippeddate","shipdate"));
            shipment.routeName=first(values,columns,"routename","route");
            shipment.origin=first(values,columns,"origin");
            shipment.destination=first(values,columns,"destination");
            shipment.status=TruckStatus.parse(
                    first(values,columns,"status"));
            shipment.currentLocation=first(
                    values,columns,"currentlocation","location");
            shipment.latitude=parseDouble(
                    first(values,columns,"latitude","lat"));
            shipment.longitude=parseDouble(
                    first(values,columns,"longitude","lon","lng"));
            shipment.scheduledArrival=parseDateTime(
                    first(values,columns,"scheduledarrival","scheduledeta"));
            shipment.estimatedArrival=parseDateTime(
                    first(values,columns,"estimatedarrival","eta"));
            shipment.trafficDelayMinutes=parseInt(
                    first(values,columns,"trafficdelayminutes",
                            "trafficdelay"));
            shipment.weatherDelayMinutes=parseInt(
                    first(values,columns,"weatherdelayminutes",
                            "weatherdelay"));
            shipment.deliveredAt=parseDateTime(
                    first(values,columns,"deliveredat","deliverytime"));
            shipment.notes=first(values,columns,"notes");
            shipment.providerStatus=first(
                    values,columns,"providerstatus","carrierstatus");
            shipment.archived=parseBoolean(first(values,columns,"archived"));

            int existing=findIndexByNaturalKey(shipment);
            shipment.lastUpdated=LocalDateTime.now();
            if(existing>=0){
                shipment.id=shipments.get(existing).id;
                shipments.set(existing,shipment);
            }else{
                shipments.add(shipment);
            }
            imported++;
        }
        save();
        return imported;
    }

    public synchronized void exportCsv(Path path,List<TruckShipment> records)
            throws IOException{
        Files.createDirectories(path.toAbsolutePath().getParent());
        try(BufferedWriter out=Files.newBufferedWriter(
                path,StandardCharsets.UTF_8)){
            out.write("id,carrier,trackingNumber,proNumber,trailerNumber,"
                    +"outboundShipmentId,customerBk,shipIds,sourceSystem,"
                    +"shippedDate,routeName,origin,destination,"
                    +"status,currentLocation,latitude,longitude,"
                    +"scheduledArrival,estimatedArrival,trafficDelayMinutes,"
                    +"weatherDelayMinutes,lastUpdated,deliveredAt,providerStatus,"
                    +"archived,notes");
            out.newLine();
            for(TruckShipment s:records){
                out.write(String.join(",",
                        csv(s.id),
                        csv(s.carrier.name()),
                        csv(s.trackingNumber),
                        csv(s.proNumber),
                        csv(s.trailerNumber),
                        csv(s.outboundShipmentId),
                        csv(s.customerBk),
                        csv(s.shipIds),
                        csv(s.sourceSystem),
                        csv(s.shippedDate),
                        csv(s.routeName),
                        csv(s.origin),
                        csv(s.destination),
                        csv(s.status.name()),
                        csv(s.currentLocation),
                        csv(s.latitude),
                        csv(s.longitude),
                        csv(s.scheduledArrival),
                        csv(s.estimatedArrival),
                        csv(s.trafficDelayMinutes),
                        csv(s.weatherDelayMinutes),
                        csv(s.lastUpdated),
                        csv(s.deliveredAt),
                        csv(s.providerStatus),
                        csv(s.archived),
                        csv(s.notes)
                ));
                out.newLine();
            }
        }
    }

    private int findIndexByNaturalKey(TruckShipment candidate){
        for(int i=0;i<shipments.size();i++){
            TruckShipment current=shipments.get(i);
            if(candidate.id!=null&&!candidate.id.isBlank()
                    &&candidate.id.equals(current.id))return i;
            if(candidate.outboundShipmentId!=null
                    &&!candidate.outboundShipmentId.isBlank()){
                if(candidate.outboundShipmentId.equalsIgnoreCase(
                        current.outboundShipmentId))
                    return i;
                // An outbound shipment ID is the strongest shipment-grain key.
                // Do not merge two outbound loads merely because they share a
                // master PRO or parcel tracking number.
                continue;
            }
            if(candidate.proNumber!=null&&!candidate.proNumber.isBlank()
                    &&candidate.proNumber.equalsIgnoreCase(current.proNumber))
                return i;
            if(candidate.carrier==current.carrier
                    &&candidate.trackingNumber!=null
                    &&!candidate.trackingNumber.isBlank()
                    &&candidate.trackingNumber.equalsIgnoreCase(
                            current.trackingNumber))
                return i;
        }
        return -1;
    }

    private void ensureLoaded(){
        if(loaded)return;
        loaded=true;
        shipments.clear();
        Path path=file();
        if(!Files.exists(path))return;
        try{
            List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);
            if(lines.isEmpty())return;
            List<String> header=parseCsvLine(lines.get(0));
            Map<String,Integer> columns=new HashMap<>();
            for(int i=0;i<header.size();i++)
                columns.put(normalize(header.get(i)),i);
            for(int row=1;row<lines.size();row++){
                if(lines.get(row).isBlank())continue;
                List<String> values=parseCsvLine(lines.get(row));
                TruckShipment s=new TruckShipment();
                s.id=first(values,columns,"id");
                if(s.id.isBlank())s.id=UUID.randomUUID().toString();
                s.carrier=TruckCarrier.parse(first(values,columns,"carrier"));
                s.trackingNumber=first(values,columns,"trackingnumber");
                s.proNumber=first(values,columns,"pronumber");
                s.trailerNumber=first(values,columns,"trailernumber");
                s.outboundShipmentId=first(values,columns,"outboundshipmentid");
                s.customerBk=first(values,columns,"customerbk");
                s.shipIds=first(values,columns,"shipids");
                s.sourceSystem=first(values,columns,"sourcesystem");
                s.shippedDate=parseDate(first(values,columns,"shippeddate"));
                s.routeName=first(values,columns,"routename");
                s.origin=first(values,columns,"origin");
                s.destination=first(values,columns,"destination");
                s.status=TruckStatus.parse(first(values,columns,"status"));
                s.currentLocation=first(values,columns,"currentlocation");
                s.latitude=parseDouble(first(values,columns,"latitude"));
                s.longitude=parseDouble(first(values,columns,"longitude"));
                s.scheduledArrival=parseDateTime(first(
                        values,columns,"scheduledarrival"));
                s.estimatedArrival=parseDateTime(first(
                        values,columns,"estimatedarrival"));
                s.trafficDelayMinutes=parseInt(first(
                        values,columns,"trafficdelayminutes"));
                s.weatherDelayMinutes=parseInt(first(
                        values,columns,"weatherdelayminutes"));
                s.lastUpdated=parseDateTime(first(values,columns,"lastupdated"));
                if(s.lastUpdated==null)s.lastUpdated=LocalDateTime.now();
                s.deliveredAt=parseDateTime(first(values,columns,"deliveredat"));
                s.providerStatus=first(values,columns,"providerstatus");
                s.archived=parseBoolean(first(values,columns,"archived"));
                s.notes=first(values,columns,"notes");
                shipments.add(s);
            }
        }catch(Exception ex){
            System.err.println("Truck tracking history could not be loaded.");
        }
    }

    private static boolean isIbmTrailerTrackingSchema(
            Map<String,Integer> columns){
        return columns.containsKey("shippeddate")
                &&columns.containsKey("trailer")
                &&columns.containsKey("shipidbk")
                &&columns.containsKey("outboundshipmentid");
    }

    /**
     * Imports IBM/DVIEW TrailerInfoFromTrackingNumber extracts.
     *
     * The source is line/Ship-ID grain, while Truck Tracking is shipment/load
     * grain. Rows are consolidated by OUTBOUNDSHIPMENTID so one outbound load
     * is not displayed repeatedly because it contains multiple Ship IDs.
     */
    private int importIbmTrailerTracking(
            List<String> lines,
            Map<String,Integer> columns
    ){
        LinkedHashMap<String,IbmAggregate> groups=new LinkedHashMap<>();

        for(int row=1;row<lines.size();row++){
            if(lines.get(row).isBlank())continue;
            List<String> values=parseCsvLine(lines.get(row));

            String outbound=first(values,columns,"outboundshipmentid");
            String trailer=first(values,columns,"trailer");
            String pro=cleanIdentifier(first(values,columns,"pronumber"));
            String tracking=cleanIdentifier(
                    first(values,columns,"trackingnumber"));
            String shipId=first(values,columns,"shipidbk");
            String customer=first(values,columns,"customerbk");
            LocalDate shipped=parseDate(first(values,columns,"shippeddate"));

            String key=!outbound.isBlank()
                    ?"OUTBOUND:"+outbound
                    :!pro.isBlank()
                        ?"PRO:"+pro
                        :!tracking.isBlank()
                            ?"TRACK:"+tracking
                            :"TRAILER:"+trailer+":"+shipped;

            IbmAggregate aggregate=groups.computeIfAbsent(
                    key,k->new IbmAggregate());
            if(aggregate.outboundShipmentId.isBlank())
                aggregate.outboundShipmentId=outbound;
            if(aggregate.trailerNumber.isBlank())
                aggregate.trailerNumber=trailer;
            if(aggregate.customerBk.isBlank())
                aggregate.customerBk=customer;
            if(aggregate.shippedDate==null)
                aggregate.shippedDate=shipped;
            if(!pro.isBlank())aggregate.proNumbers.add(pro);
            if(!tracking.isBlank())aggregate.trackingNumbers.add(tracking);
            if(!shipId.isBlank())aggregate.shipIds.add(shipId);
        }

        int imported=0;
        for(IbmAggregate aggregate:groups.values()){
            TruckShipment shipment=new TruckShipment();
            shipment.id=!aggregate.outboundShipmentId.isBlank()
                    ?"IBM:"+aggregate.outboundShipmentId
                    :UUID.randomUUID().toString();
            shipment.outboundShipmentId=aggregate.outboundShipmentId;
            shipment.trailerNumber=aggregate.trailerNumber;
            shipment.customerBk=aggregate.customerBk;
            shipment.shippedDate=aggregate.shippedDate;
            shipment.proNumber=String.join(";",aggregate.proNumbers);
            shipment.trackingNumber=String.join(
                    ";",aggregate.trackingNumbers);
            shipment.shipIds=String.join(";",aggregate.shipIds);
            shipment.sourceSystem="IBM_DVIEW_TRAILER_TRACKING";

            shipment.carrier=(!shipment.proNumber.isBlank()
                    ||!shipment.trackingNumber.isBlank())
                    ?TruckCarrier.FEDEX:TruckCarrier.OTHER;

            shipment.routeName=!shipment.trailerNumber.isBlank()
                    ?"Trailer "+shipment.trailerNumber
                    :"Outbound "+shipment.outboundShipmentId;
            shipment.status=TruckStatus.PLANNED;
            shipment.providerStatus="IBM shipment reference";
            shipment.notes="Imported from IBM/DVIEW TrailerInfoFromTrackingNumber"
                    +" | "+aggregate.shipIds.size()+" Ship ID(s)";

            if(shipment.shippedDate!=null
                    &&shipment.shippedDate.isBefore(
                            LocalDate.now().minusDays(30))){
                shipment.archived=true;
            }

            int existing=findIndexByNaturalKey(shipment);
            shipment.lastUpdated=LocalDateTime.now();
            if(existing>=0){
                TruckShipment previous=shipments.get(existing);
                shipment.status=previous.status;
                shipment.currentLocation=previous.currentLocation;
                shipment.latitude=previous.latitude;
                shipment.longitude=previous.longitude;
                shipment.scheduledArrival=previous.scheduledArrival;
                shipment.estimatedArrival=previous.estimatedArrival;
                shipment.trafficDelayMinutes=previous.trafficDelayMinutes;
                shipment.weatherDelayMinutes=previous.weatherDelayMinutes;
                shipment.deliveredAt=previous.deliveredAt;
                shipment.providerStatus=previous.providerStatus==null
                        ||previous.providerStatus.isBlank()
                        ?shipment.providerStatus:previous.providerStatus;
                shipment.archived=previous.archived||shipment.archived;
                shipment.id=previous.id;
                shipments.set(existing,shipment);
            }else{
                shipments.add(shipment);
            }
            imported++;
        }

        save();
        return imported;
    }

    private static String cleanIdentifier(String value){
        if(value==null)return "";
        String v=value.trim();
        if(v.matches("\\d+\\.0"))
            return v.substring(0,v.length()-2);
        return v;
    }

    private static LocalDate parseDate(String value){
        if(value==null||value.isBlank())return null;
        try{return LocalDate.parse(value.trim());}
        catch(Exception ignored){return null;}
    }

    private static final class IbmAggregate{
        String outboundShipmentId="";
        String trailerNumber="";
        String customerBk="";
        LocalDate shippedDate;
        final LinkedHashSet<String> proNumbers=new LinkedHashSet<>();
        final LinkedHashSet<String> trackingNumbers=new LinkedHashSet<>();
        final LinkedHashSet<String> shipIds=new LinkedHashSet<>();
    }

    private void save(){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            Path target=file();
            Path temp=target.resolveSibling(target.getFileName()+".tmp");
            exportCsv(temp,shipments);
            try{
                Files.move(temp,target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
            SecureFiles.restrictFile(target);
        }catch(Exception ex){
            throw new RuntimeException(
                    "Unable to save truck tracking history.",ex);
        }
    }

    private static String normalize(String value){
        return value==null?"":value.trim().toLowerCase()
                .replace("_","").replace("-","").replace(" ","");
    }

    private static String first(
            List<String> values,Map<String,Integer> columns,String...keys){
        for(String key:keys){
            Integer index=columns.get(normalize(key));
            if(index!=null&&index>=0&&index<values.size()){
                String v=values.get(index).trim();
                if(!v.isBlank())return v;
            }
        }
        return "";
    }

    private static String value(
            List<String> values,Map<String,Integer> columns,
            String key,String fallback){
        String v=first(values,columns,key);
        return v.isBlank()?fallback:v;
    }

    private static Double parseDouble(String value){
        try{return value==null||value.isBlank()
                ?null:Double.parseDouble(value.trim());}
        catch(Exception ex){return null;}
    }

    private static int parseInt(String value){
        try{return value==null||value.isBlank()
                ?0:(int)Math.round(Double.parseDouble(value.trim()));}
        catch(Exception ex){return 0;}
    }

    private static boolean parseBoolean(String value){
        if(value==null)return false;
        String v=value.trim().toLowerCase();
        return v.equals("true")||v.equals("yes")||v.equals("y")||v.equals("1");
    }

    private static LocalDateTime parseDateTime(String value){
        if(value==null||value.isBlank())return null;
        String v=value.trim();
        try{return LocalDateTime.parse(v,DT);}catch(Exception ignored){}
        try{return OffsetDateTime.parse(v).toLocalDateTime();}
        catch(Exception ignored){}
        try{return LocalDate.parse(v).atStartOfDay();}
        catch(Exception ignored){}
        return null;
    }

    private static String csv(Object value){
        String s=value==null?"":String.valueOf(value);
        if(s.indexOf('"')>=0)s=s.replace("\"","\"\"");
        if(s.indexOf(',')>=0||s.indexOf('"')>=0
                ||s.indexOf('\n')>=0||s.indexOf('\r')>=0)
            return "\""+s+"\"";
        return s;
    }

    private static List<String> parseCsvLine(String line){
        List<String> values=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"'){
                if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){
                    current.append('"');i++;
                }else quoted=!quoted;
            }else if(c==','&&!quoted){
                values.add(current.toString());
                current.setLength(0);
            }else current.append(c);
        }
        values.add(current.toString());
        return values;
    }
}
