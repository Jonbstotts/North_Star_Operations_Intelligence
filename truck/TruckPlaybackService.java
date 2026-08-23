package com.wtm.truck;

import com.wtm.model.Location;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Non-destructive historical truck playback.
 *
 * ROAD_RECONSTRUCTION follows routing-provider road geometry.
 * STRAIGHT_FALLBACK exists only when road geometry cannot be calculated.
 * Movement is distance-based so unevenly spaced route points remain smooth.
 */
public final class TruckPlaybackService {
    public enum Mode {
        NONE("Not running"),
        ROAD_RECONSTRUCTION("Reconstructed roadway"),
        CARRIER_EVENTS("Carrier event checkpoints"),
        STRAIGHT_FALLBACK("Straight-line fallback");

        private final String label;
        Mode(String label){this.label=label;}
        public String label(){return label;}
    }

    private static final TruckPlaybackService INSTANCE=
            new TruckPlaybackService();
    private final CopyOnWriteArrayList<Runnable> listeners=
            new CopyOnWriteArrayList<>();

    private TruckShipment source;
    private List<Location> path=List.of();
    private List<Location> checkpoints=List.of();
    private double[] cumulativeMeters=new double[0];
    private double totalMeters;
    private int progress;
    private boolean enabled;
    private Mode mode=Mode.NONE;
    private String basisNote="";

    private TruckPlaybackService(){}
    public static TruckPlaybackService get(){return INSTANCE;}

    public synchronized void setRoadPath(
            TruckShipment source,
            List<Location> roadPath,
            List<Location> checkpoints,
            int progress,
            String basisNote
    ){
        this.source=source==null?null:source.copy();
        this.path=sanitize(roadPath);
        this.checkpoints=sanitize(checkpoints);
        this.progress=Math.max(0,Math.min(100,progress));
        this.basisNote=basisNote==null?"":basisNote;
        this.mode=this.path.size()>=2
                ?Mode.ROAD_RECONSTRUCTION:Mode.NONE;
        this.enabled=this.source!=null&&this.path.size()>=2;
        calculateDistances();
        fire();
    }

    public synchronized void setCarrierEvents(
            TruckShipment source,List<Location> checkpoints,
            int progress,String basisNote){
        this.source=source==null?null:source.copy();
        this.path=sanitize(checkpoints);
        this.checkpoints=this.path;
        this.progress=Math.max(0,Math.min(100,progress));
        this.basisNote=basisNote==null?"":basisNote;
        this.mode=this.path.size()>=2?Mode.CARRIER_EVENTS:Mode.NONE;
        this.enabled=this.source!=null&&this.path.size()>=2;
        calculateDistances();fire();
    }

    public synchronized void setStraightFallback(
            TruckShipment source,
            Location origin,
            Location destination,
            int progress,
            String basisNote
    ){
        if(source==null||origin==null||destination==null){
            clear();return;
        }
        this.source=source.copy();
        this.path=List.of(origin,destination);
        this.checkpoints=List.of(origin,destination);
        this.progress=Math.max(0,Math.min(100,progress));
        this.basisNote=basisNote==null?"":basisNote;
        this.mode=Mode.STRAIGHT_FALLBACK;
        this.enabled=true;
        calculateDistances();
        fire();
    }

    /** Compatibility with v1.5.2 callers. */
    public synchronized void set(
            TruckShipment source,
            Location origin,
            Location destination,
            int progress,
            boolean enabled
    ){
        if(!enabled){clear();return;}
        setStraightFallback(
                source,origin,destination,progress,"Legacy playback");
    }

    public synchronized void setProgress(int progress){
        this.progress=Math.max(0,Math.min(100,progress));
        fire();
    }
    public synchronized int progress(){return progress;}
    public synchronized boolean enabled(){return enabled;}
    public synchronized Mode mode(){return mode;}
    public synchronized String basisNote(){return basisNote;}
    public synchronized double totalMeters(){return totalMeters;}
    public synchronized List<Location> path(){return List.copyOf(path);}
    public synchronized List<Location> checkpoints(){
        return List.copyOf(checkpoints);
    }

    public synchronized void clear(){
        enabled=false;
        source=null;
        path=List.of();
        checkpoints=List.of();
        cumulativeMeters=new double[0];
        totalMeters=0;
        progress=0;
        mode=Mode.NONE;
        basisNote="";
        fire();
    }

    public synchronized TruckShipment overlay(){
        if(!enabled||source==null||path.size()<2)return null;
        Location position=positionAt(progress/100.0);
        if(position==null)return null;

        TruckShipment s=source.copy();
        s.status=TruckStatus.IN_TRANSIT;
        s.archived=false;
        s.latitude=position.latitude();
        s.longitude=position.longitude();
        s.currentLocation=mode.label()+" • "+progress+"%";
        s.providerStatus=switch(mode){
            case ROAD_RECONSTRUCTION->"ROAD_PLAYBACK";
            case CARRIER_EVENTS->"CARRIER_EVENT_PLAYBACK";
            default->"TEST_PLAYBACK";
        };

        LocalDateTime start=source.shippedDate==null
                ?LocalDateTime.now().minusHours(8)
                :source.shippedDate.atStartOfDay().plusHours(8);
        LocalDateTime end=source.deliveredAt!=null
                ?source.deliveredAt
                :(source.estimatedArrival!=null
                    ?source.estimatedArrival:start.plusHours(8));
        if(end.isBefore(start))end=start.plusHours(8);
        long seconds=Duration.between(start,end).getSeconds();
        s.lastUpdated=start.plusSeconds(
                Math.round(seconds*(progress/100.0)));
        s.estimatedArrival=end;
        return s;
    }

    private Location positionAt(double fraction){
        if(path.size()<2)return null;
        if(totalMeters<=0)
            return path.get(Math.min(
                    path.size()-1,
                    (int)Math.round(fraction*(path.size()-1))));

        double target=Math.max(0,Math.min(1,fraction))*totalMeters;
        int segment=1;
        while(segment<cumulativeMeters.length
                &&cumulativeMeters[segment]<target)
            segment++;

        if(segment>=path.size())return path.get(path.size()-1);

        double startDistance=cumulativeMeters[segment-1];
        double endDistance=cumulativeMeters[segment];
        double span=endDistance-startDistance;
        double local=span<=0?0:(target-startDistance)/span;

        Location a=path.get(segment-1);
        Location b=path.get(segment);
        return new Location(
                "playback",
                a.latitude()+(b.latitude()-a.latitude())*local,
                a.longitude()+(b.longitude()-a.longitude())*local
        );
    }

    private void calculateDistances(){
        cumulativeMeters=new double[path.size()];
        totalMeters=0;
        for(int i=1;i<path.size();i++){
            totalMeters+=haversineMeters(path.get(i-1),path.get(i));
            cumulativeMeters[i]=totalMeters;
        }
    }

    private static List<Location> sanitize(List<Location> source){
        if(source==null||source.isEmpty())return List.of();
        List<Location> out=new ArrayList<>();
        for(Location point:source){
            if(point==null
                    ||!Double.isFinite(point.latitude())
                    ||!Double.isFinite(point.longitude()))
                continue;
            if(!out.isEmpty()){
                Location previous=out.get(out.size()-1);
                if(Math.abs(previous.latitude()-point.latitude())<1e-9
                        &&Math.abs(previous.longitude()-point.longitude())<1e-9)
                    continue;
            }
            out.add(point);
        }
        return List.copyOf(out);
    }

    private static double haversineMeters(Location a,Location b){
        double r=6_371_000.0;
        double lat1=Math.toRadians(a.latitude());
        double lat2=Math.toRadians(b.latitude());
        double dLat=lat2-lat1;
        double dLon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.sin(dLat/2)*Math.sin(dLat/2)
                +Math.cos(lat1)*Math.cos(lat2)
                *Math.sin(dLon/2)*Math.sin(dLon/2);
        return r*2*Math.atan2(
                Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    }

    public void addListener(Runnable listener){
        if(listener!=null)listeners.add(listener);
    }
    public void removeListener(Runnable listener){listeners.remove(listener);}
    private void fire(){for(Runnable listener:listeners)listener.run();}
}
