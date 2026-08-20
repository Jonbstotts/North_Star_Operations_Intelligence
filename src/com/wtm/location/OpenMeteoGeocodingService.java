package com.wtm.location;

import com.wtm.model.LocationSearchResult;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Keyless place-name geocoding through Open-Meteo's Geocoding API.
 *
 * This service is intended for cities, towns, municipalities, and named places.
 * Manual coordinates remain available when a site needs a very specific point
 * that the place-name database does not contain.
 */
public final class OpenMeteoGeocodingService {
    private final HttpService http;

    public OpenMeteoGeocodingService(HttpService http){
        this.http=http;
    }

    public List<LocationSearchResult> search(String query) throws Exception {
        String q=query==null?"":query.trim();
        if(q.isBlank()) return List.of();

        String url="https://geocoding-api.open-meteo.com/v1/search"
                +"?name="+URLEncoder.encode(q,StandardCharsets.UTF_8)
                +"&count=15&language=en&format=json";

        Object parsed=MiniJson.parse(http.getText(url));
        if(!(parsed instanceof Map<?,?>)) return List.of();

        Map<String,Object> root=MiniJson.obj(parsed);
        Object raw=root.get("results");
        if(!(raw instanceof List<?> list)) return List.of();

        List<LocationSearchResult> out=new ArrayList<>();
        for(Object item:list){
            if(!(item instanceof Map<?,?>)) continue;
            Map<String,Object> m=MiniJson.obj(item);

            String name=MiniJson.str(m.get("name"));
            if(name.isBlank() || m.get("latitude")==null || m.get("longitude")==null) continue;

            long population=0;
            Object pop=m.get("population");
            if(pop instanceof Number n) population=n.longValue();

            out.add(new LocationSearchResult(
                    name,
                    MiniJson.num(m.get("latitude")),
                    MiniJson.num(m.get("longitude")),
                    MiniJson.str(m.get("admin1")),
                    MiniJson.str(m.get("country")),
                    MiniJson.str(m.get("timezone")),
                    population,
                    "Open-Meteo / GeoNames"
            ));
        }
        return out;
    }
}
