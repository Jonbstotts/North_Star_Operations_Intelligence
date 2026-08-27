package com.wtm.truck;

import com.wtm.location.OpenMeteoGeocodingService;
import com.wtm.model.LocationSearchResult;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * FedEx Basic Integrated Visibility adapter for both parcel and Freight LTL.
 * FedEx Freight PRO numbers are master tracking numbers and use the same Track
 * API as FedEx Express/Ground tracking numbers. Detailed scan events are stored
 * as confirmed checkpoints for trace-back and map playback.
 */
public final class FedExTrackingService {
    public record Result(
            String trackingNumber,String status,
            LocalDateTime estimatedDelivery,LocalDateTime deliveredAt,
            List<ShipmentTrackingEvent> events){}

    private final HttpService http;
    private final OpenMeteoGeocodingService geocoder;

    public FedExTrackingService(HttpService http){
        this.http=http;this.geocoder=new OpenMeteoGeocodingService(http);
    }

    public List<Result> track(
            Collection<String> trackingNumbers,String clientId,
            String clientSecret,boolean sandbox) throws Exception {
        List<String> numbers=trackingNumbers.stream()
                .filter(Objects::nonNull).map(String::trim)
                .filter(s->!s.isBlank()).distinct().limit(30).toList();
        if(numbers.isEmpty())return List.of();

        String base=sandbox
                ?"https://apis-sandbox.fedex.com":"https://apis.fedex.com";
        String token=oauth(base,clientId,clientSecret);

        StringBuilder json=new StringBuilder(
                "{\"includeDetailedScans\":true,\"trackingInfo\":[");
        for(int i=0;i<numbers.size();i++){
            if(i>0)json.append(',');
            json.append("{\"trackingNumberInfo\":{\"trackingNumber\":\"")
                    .append(escape(numbers.get(i))).append("\"}}");
        }
        json.append("]}");

        String response=http.postJson(
                base+"/track/v1/trackingnumbers",json.toString(),
                Map.of("Authorization","Bearer "+token,
                        "X-locale","en_US"));
        List<Result> parsed=parse(response);
        if(parsed.isEmpty())
            throw new IllegalStateException(
                    "FedEx returned no tracking results for the supplied identifier(s). "
                    +"Confirm Production/Test environment and FedEx API project credentials.");
        return parsed;
    }

    private String oauth(
            String base,String clientId,String clientSecret) throws Exception {
        if(clientId==null||clientId.isBlank()
                ||clientSecret==null||clientSecret.isBlank())
            throw new IllegalStateException(
                    "FedEx API Key/Client ID and Secret Key are required.");
        String form="grant_type=client_credentials"
                +"&client_id="+URLEncoder.encode(clientId,StandardCharsets.UTF_8)
                +"&client_secret="+URLEncoder.encode(
                        clientSecret,StandardCharsets.UTF_8);
        Map<String,Object> root=map(MiniJson.parse(
                http.postForm(base+"/oauth/token",form,Map.of())));
        String token=MiniJson.str(root.get("access_token"));
        if(token.isBlank())
            throw new IllegalStateException("FedEx OAuth token was not returned.");
        return token;
    }

    private List<Result> parse(String json){
        Map<String,Object> root=map(MiniJson.parse(json));
        Map<String,Object> output=map(root.get("output"));
        List<Result> results=new ArrayList<>();

        for(Object completeRaw:list(output.get("completeTrackResults"))){
            Map<String,Object> complete=map(completeRaw);
            String topNumber=MiniJson.str(complete.get("trackingNumber"));
            for(Object trackRaw:list(complete.get("trackResults"))){
                Map<String,Object> track=map(trackRaw);
                String number=MiniJson.str(
                        map(track.get("trackingNumberInfo")).get("trackingNumber"));
                if(number.isBlank())number=topNumber;
                ShipmentTrackingEvent.MovementMode fallback=
                        defaultMovementMode(track);
                results.add(new Result(
                        number,readStatus(track),readEta(track),
                        readDelivered(track),parseEvents(track,number,fallback)));
            }
        }
        return List.copyOf(results);
    }

    private List<ShipmentTrackingEvent> parseEvents(
            Map<String,Object> track,String tracking,
            ShipmentTrackingEvent.MovementMode fallback){
        List<ShipmentTrackingEvent> events=new ArrayList<>();
        for(Object raw:list(track.get("scanEvents"))){
            Map<String,Object> scan=map(raw);
            ShipmentTrackingEvent e=new ShipmentTrackingEvent();
            e.provider="FEDEX";e.trackingNumber=tracking;
            e.eventCode=MiniJson.str(scan.get("eventType"));
            e.eventDescription=MiniJson.str(scan.get("eventDescription"));
            e.rawStatus=e.eventDescription;
            e.eventTime=time(MiniJson.str(scan.get("date")));

            Map<String,Object> loc=map(scan.get("scanLocation"));
            e.city=MiniJson.str(loc.get("city"));
            e.state=MiniJson.str(loc.get("stateOrProvinceCode"));
            e.country=MiniJson.str(loc.get("countryCode"));

            Double lat=number(loc.get("latitude"));
            Double lon=number(loc.get("longitude"));
            if(lat!=null&&lon!=null){
                e.latitude=lat;e.longitude=lon;
                e.locationConfidence="PROVIDER_GPS";
            }else if(!e.city.isBlank()){
                LocationSearchResult result=geocode(e.city,e.state,e.country);
                if(result!=null){
                    e.latitude=result.latitude();e.longitude=result.longitude();
                    e.locationConfidence="GEOCODED_PLACE";
                }else e.locationConfidence="PROVIDER_PLACE";
            }

            String mode=MiniJson.str(scan.get("transportationMode"));
            if(mode.isBlank())mode=MiniJson.str(scan.get("transportationType"));
            e.movementMode=ShipmentTrackingEvent.MovementMode.parse(mode);
            if(e.movementMode==ShipmentTrackingEvent.MovementMode.UNKNOWN)
                e.movementMode=fallback;
            events.add(e);
        }
        events.sort(Comparator.comparing(
                (ShipmentTrackingEvent e)->e.eventTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(events);
    }

    private static ShipmentTrackingEvent.MovementMode defaultMovementMode(
            Map<String,Object> track){
        Map<String,Object> service=map(track.get("serviceDetail"));
        String type=(MiniJson.str(service.get("type"))+" "
                +MiniJson.str(service.get("description"))).toUpperCase(Locale.ROOT);
        if(type.contains("FREIGHT")||type.contains("GROUND"))
            return ShipmentTrackingEvent.MovementMode.GROUND;
        if(type.contains("EXPRESS")||type.contains("OVERNIGHT")
                ||type.contains("2_DAY")||type.contains("2DAY")
                ||type.contains("INTERNATIONAL_PRIORITY")
                ||type.contains("INTERNATIONAL_FIRST"))
            return ShipmentTrackingEvent.MovementMode.AIR;
        return ShipmentTrackingEvent.MovementMode.UNKNOWN;
    }

    private LocationSearchResult geocode(String city,String state,String country){
        try{
            String q=city+(state.isBlank()?"":", "+state)
                    +(country.isBlank()?"":", "+country);
            List<LocationSearchResult> results=geocoder.search(q);
            if(results.isEmpty())results=geocoder.search(city);
            return results.isEmpty()?null:results.get(0);
        }catch(Exception ex){return null;}
    }

    private static String readStatus(Map<String,Object> track){
        Map<String,Object> latest=map(track.get("latestStatusDetail"));
        String s=MiniJson.str(latest.get("description"));
        if(!s.isBlank())return s;
        Object derived=track.get("derivedStatus");
        if(derived instanceof Map<?,?>){
            s=MiniJson.str(map(derived).get("statusByLocale"));
            if(!s.isBlank())return s;
        }
        return MiniJson.str(derived);
    }

    private static LocalDateTime readEta(Map<String,Object> track){
        Map<String,Object> outer=map(track.get("estimatedDeliveryTimeWindow"));
        Map<String,Object> window=map(outer.get("window"));
        LocalDateTime begin=time(MiniJson.str(window.get("begins")));
        LocalDateTime end=time(MiniJson.str(window.get("ends")));
        if(begin!=null&&end!=null)
            return begin.plusSeconds(Math.max(0,
                    Duration.between(begin,end).getSeconds()/2));
        if(end!=null)return end;if(begin!=null)return begin;
        for(Object raw:list(track.get("dateAndTimes"))){
            Map<String,Object> item=map(raw);
            if(MiniJson.str(item.get("type")).toUpperCase()
                    .contains("ESTIMATED_DELIVERY"))
                return time(MiniJson.str(item.get("dateTime")));
        }
        return null;
    }

    private static LocalDateTime readDelivered(Map<String,Object> track){
        for(Object raw:list(track.get("dateAndTimes"))){
            Map<String,Object> item=map(raw);
            if(MiniJson.str(item.get("type")).toUpperCase()
                    .contains("ACTUAL_DELIVERY"))
                return time(MiniJson.str(item.get("dateTime")));
        }
        return null;
    }

    private static LocalDateTime time(String value){
        if(value==null||value.isBlank())return null;
        try{return OffsetDateTime.parse(value).toLocalDateTime();}
        catch(Exception ignored){}
        try{return LocalDateTime.parse(value);}catch(Exception ignored){}
        return null;
    }
    private static Map<String,Object> map(Object v){
        return v instanceof Map<?,?>?MiniJson.obj(v):Map.of();
    }
    private static List<Object> list(Object v){
        return v instanceof List<?>?MiniJson.arr(v):List.of();
    }
    private static Double number(Object v){
        try{return v==null?null:MiniJson.num(v);}catch(Exception ex){return null;}
    }
    private static String escape(String v){
        return v.replace("\\","\\\\").replace("\"","\\\"");
    }
}
