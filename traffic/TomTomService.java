package com.wtm.traffic;

import com.wtm.model.*;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** TomTom routing helper. Traffic map tiles are drawn directly by TileMapPanel. */
public final class TomTomService {
    private final HttpService http;
    public TomTomService(HttpService http) { this.http = http; }

    /**
     * Returns the actual roadway geometry from TomTom calculateRoute.
     * Used by historical truck playback; dashboard traffic only needs summaries.
     */
    public List<Location> fetchRouteGeometry(
            Location origin,
            Location destination,
            String key
    ) throws Exception {
        if(key==null||key.isBlank())
            throw new IllegalStateException(
                    "TomTom API key is required for roadway playback.");

        String path=origin.latitude()+","+origin.longitude()+":"
                +destination.latitude()+","+destination.longitude();
        String url="https://api.tomtom.com/routing/1/calculateRoute/"
                +path
                +"/json?traffic=false&travelMode=truck"
                +"&routeRepresentation=polyline"
                +"&key="+URLEncoder.encode(key,StandardCharsets.UTF_8);

        Map<String,Object> root=MiniJson.obj(MiniJson.parse(http.getText(url)));
        List<Object> routes=MiniJson.arr(root.get("routes"));
        if(routes.isEmpty())
            throw new IllegalStateException("No roadway route returned.");

        Map<String,Object> route=MiniJson.obj(routes.get(0));
        List<Location> points=new ArrayList<>();

        for(Object legObject:MiniJson.arr(route.get("legs"))){
            Map<String,Object> leg=MiniJson.obj(legObject);
            for(Object pointObject:MiniJson.arr(leg.get("points"))){
                Map<String,Object> point=MiniJson.obj(pointObject);
                double latitude=MiniJson.num(point.get("latitude"));
                double longitude=MiniJson.num(point.get("longitude"));
                if(!Double.isFinite(latitude)||!Double.isFinite(longitude))
                    continue;

                if(!points.isEmpty()){
                    Location previous=points.get(points.size()-1);
                    if(Math.abs(previous.latitude()-latitude)<1e-9
                            &&Math.abs(previous.longitude()-longitude)<1e-9)
                        continue;
                }
                points.add(new Location("route",latitude,longitude));
            }
        }

        if(points.size()<2)
            throw new IllegalStateException(
                    "TomTom returned no usable roadway geometry.");
        return List.copyOf(points);
    }

    public RouteStatus fetchRoute(RouteConfig route, String key) throws Exception {
        if (key == null || key.isBlank()) return new RouteStatus(route.name(), -1, -1, 0, "TRAFFIC KEY REQUIRED", Instant.now());
        Location a=route.origin(), b=route.destination();
        String path = a.latitude()+","+a.longitude()+":"+b.latitude()+","+b.longitude();
        String url = "https://api.tomtom.com/routing/1/calculateRoute/" + path + "/json?traffic=true&travelMode=car&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        Map<String,Object> root = MiniJson.obj(MiniJson.parse(http.getText(url)));
        List<Object> routes = MiniJson.arr(root.get("routes"));
        if (routes.isEmpty()) throw new IllegalStateException("No route returned");
        Map<String,Object> summary = MiniJson.obj(MiniJson.obj(routes.get(0)).get("summary"));
        int sec = MiniJson.integer(summary.get("travelTimeInSeconds"));
        int delaySec = summary.containsKey("trafficDelayInSeconds") ? MiniJson.integer(summary.get("trafficDelayInSeconds")) : 0;
        int travel = (int)Math.ceil(sec/60.0);
        int delay = (int)Math.ceil(delaySec/60.0);
        int normal = Math.max(0, travel-delay);
        String status = delay >= 20 ? "SEVERE" : delay >= 11 ? "HEAVY" : delay >= 5 ? "MODERATE" : "NORMAL";
        return new RouteStatus(route.name(), travel, normal, delay, status, Instant.now());
    }
}
