package com.wtm.alerts;

import com.wtm.model.*;
import com.wtm.config.AppConfig;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;
import java.time.Instant;
import java.util.*;

/** Pulls active National Weather Service alerts and parses GeoJSON polygons for the map. */
public final class NwsAlertService {
    private final HttpService http;
    public NwsAlertService(HttpService http) { this.http = http; }

    public List<WeatherAlert> fetch(Location loc, AppConfig cfg) throws Exception {
        String url = "https://api.weather.gov/alerts/active?point=" + loc.latitude() + "," + loc.longitude();
        Map<String,Object> root = MiniJson.obj(MiniJson.parse(http.getText(url, cfg.nwsUserAgent)));
        List<WeatherAlert> out = new ArrayList<>();
        Object features = root.get("features");
        if (!(features instanceof List<?> list)) return out;
        for (Object f : list) {
            Map<String,Object> feature = MiniJson.obj(f);
            Map<String,Object> p = MiniJson.obj(feature.get("properties"));
            String event = MiniJson.str(p.get("event"));
            String headline = MiniJson.str(p.get("headline"));
            String severity = MiniJson.str(p.get("severity"));
            String urgency = MiniJson.str(p.get("urgency"));
            String instruction = MiniJson.str(p.get("instruction"));
            Instant expires = parseInstant(p.get("expires"));
            List<List<double[]>> polygons = parseGeometry(feature.get("geometry"));
            out.add(new WeatherAlert(event, headline, severity, urgency, instruction, expires, polygons));
        }
        return out;
    }

    private static Instant parseInstant(Object o) { try { return o == null ? null : Instant.parse(String.valueOf(o)); } catch (Exception e) { return null; } }

    private static List<List<double[]>> parseGeometry(Object geometryObj) {
        List<List<double[]>> result = new ArrayList<>();
        if (!(geometryObj instanceof Map<?,?>)) return result;
        Map<String,Object> g = MiniJson.obj(geometryObj);
        String type = MiniJson.str(g.get("type"));
        Object coords = g.get("coordinates");
        try {
            if ("Polygon".equals(type)) parsePolygon(MiniJson.arr(coords), result);
            else if ("MultiPolygon".equals(type)) for (Object poly : MiniJson.arr(coords)) parsePolygon(MiniJson.arr(poly), result);
        } catch (Exception ignored) {}
        return result;
    }

    private static void parsePolygon(List<Object> rings, List<List<double[]>> result) {
        if (rings.isEmpty()) return;
        List<double[]> ring = new ArrayList<>();
        for (Object pt : MiniJson.arr(rings.get(0))) {
            List<Object> xy = MiniJson.arr(pt);
            if (xy.size() >= 2) ring.add(new double[]{MiniJson.num(xy.get(1)), MiniJson.num(xy.get(0))});
        }
        if (!ring.isEmpty()) result.add(ring);
    }
}
