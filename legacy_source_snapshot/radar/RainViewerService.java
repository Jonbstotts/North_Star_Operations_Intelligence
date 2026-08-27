package com.wtm.radar;

import com.wtm.model.RadarFrame;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;
import java.util.*;

/** Retrieves RainViewer radar timeline metadata; map tiles are fetched separately by TileMapPanel. */
public final class RainViewerService {
    private final HttpService http;
    public RainViewerService(HttpService http) { this.http = http; }

    public RadarFrame latest() throws Exception {
        Map<String,Object> root = MiniJson.obj(MiniJson.parse(http.getText("https://api.rainviewer.com/public/weather-maps.json")));
        Map<String,Object> radar = MiniJson.obj(root.get("radar"));
        List<Object> past = MiniJson.arr(radar.get("past"));
        if (past.isEmpty()) return null;
        Map<String,Object> f = MiniJson.obj(past.get(past.size()-1));
        return new RadarFrame(((Number)f.get("time")).longValue(), MiniJson.str(f.get("path")));
    }
}
