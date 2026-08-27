package com.wtm.truck;

import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;
import java.time.*;
import java.util.*;

/**
 * Trak-4 GPS-report adapter.
 *
 * Trak-4 publicly provides REST GPS reports and webhooks. The exact reports URL
 * and auth header remain configurable because account/API-version documentation
 * owns those values.
 */
public final class Trak4Service {
    private final HttpService http;
    public Trak4Service(HttpService http){this.http=http;}

    public List<Trak4Tracker> fetch(
            String reportUrl,String authHeader,String apiKey) throws Exception {
        if(reportUrl==null||reportUrl.isBlank())
            throw new IllegalStateException("Trak-4 GPS Reports URL is required.");
        String body=(apiKey==null||apiKey.isBlank())
                ?http.getText(reportUrl)
                :http.getTextWithHeader(reportUrl,
                    authHeader==null||authHeader.isBlank()
                            ?"Authorization":authHeader,apiKey);

        List<Map<String,Object>> records=new ArrayList<>();
        collect(MiniJson.parse(body),records);
        List<Trak4Tracker> out=new ArrayList<>();
        for(Map<String,Object> record:records){
            Double lat=num(first(record,"latitude","lat","gpsLatitude"));
            Double lon=num(first(record,"longitude","lon","lng","gpsLongitude"));
            String device=text(first(record,
                    "deviceId","deviceID","imei","serial","trackerId","unitId"));
            if(device.isBlank()||lat==null||lon==null)continue;
            Trak4Tracker t=new Trak4Tracker();
            t.deviceId=device;
            t.label=text(first(record,"name","label","deviceName"));
            if(t.label.isBlank())t.label=device;
            t.latitude=lat;t.longitude=lon;
            t.lastReport=time(first(record,
                    "timestamp","reportTime","gpsTime","dateTime","time"));
            t.batteryPercent=integer(first(record,
                    "batteryPercent","battery","batteryLevel"));
            t.source="TRAK4_REST";out.add(t);
        }
        return List.copyOf(out);
    }

    private static void collect(Object value,List<Map<String,Object>> out){
        if(value instanceof Map<?,?>){
            Map<String,Object> m=MiniJson.obj(value);
            if(first(m,"latitude","lat","gpsLatitude")!=null
                    &&first(m,"longitude","lon","lng","gpsLongitude")!=null)
                out.add(m);
            for(Object child:m.values())collect(child,out);
        }else if(value instanceof List<?>)
            for(Object child:MiniJson.arr(value))collect(child,out);
    }
    private static Object first(Map<String,Object> m,String... keys){
        for(String key:keys)if(m.containsKey(key))return m.get(key);
        return null;
    }
    private static String text(Object v){return v==null?"":String.valueOf(v);}
    private static Double num(Object v){
        try{return v==null?null:MiniJson.num(v);}catch(Exception e){return null;}
    }
    private static Integer integer(Object v){
        try{return v==null?null:MiniJson.integer(v);}catch(Exception e){return null;}
    }
    private static LocalDateTime time(Object v){
        if(v==null)return LocalDateTime.now();
        try{return OffsetDateTime.parse(String.valueOf(v)).toLocalDateTime();}
        catch(Exception ignored){}
        try{return LocalDateTime.parse(String.valueOf(v));}
        catch(Exception ignored){}
        return LocalDateTime.now();
    }
}
