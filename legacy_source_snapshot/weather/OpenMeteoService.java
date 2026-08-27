package com.wtm.weather;

import com.wtm.model.*;
import com.wtm.config.AppConfig;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Retrieves current/hourly/daily weather from Open-Meteo (no API key required). */
public final class OpenMeteoService {
    private final HttpService http;
    public OpenMeteoService(HttpService http) { this.http = http; }

    public WeatherSnapshot fetch(Location loc, AppConfig cfg) throws Exception {
        boolean customer="OPEN_METEO_CUSTOMER".equalsIgnoreCase(cfg.weatherProvider);
        String host=customer ? "https://customer-api.open-meteo.com" : "https://api.open-meteo.com";
        String url = host + "/v1/forecast?latitude=" + loc.latitude() +
                "&longitude=" + loc.longitude() +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=7";
        if(customer){
            if(cfg.weatherApiKey==null || cfg.weatherApiKey.isBlank())
                throw new IllegalStateException("Open-Meteo Customer selected but API key is blank");
            url += "&apikey=" + URLEncoder.encode(cfg.weatherApiKey, StandardCharsets.UTF_8);
        }
        Map<String,Object> root = MiniJson.obj(MiniJson.parse(http.getText(url)));
        Map<String,Object> current = MiniJson.obj(root.get("current"));
        Map<String,Object> daily = MiniJson.obj(root.get("daily"));
        Map<String,Object> hourly = MiniJson.obj(root.get("hourly"));

        double temp = MiniJson.num(current.get("temperature_2m"));
        double feels = MiniJson.num(current.get("apparent_temperature"));
        double humidity=MiniJson.num(current.get("relative_humidity_2m"));
        double wind = MiniJson.num(current.get("wind_speed_10m"));
        double gust = MiniJson.num(current.get("wind_gusts_10m"));
        int code = MiniJson.integer(current.get("weather_code"));
        double high = firstNum(daily.get("temperature_2m_max"));
        double low = firstNum(daily.get("temperature_2m_min"));
        double precip = firstNum(daily.get("precipitation_probability_max"));

        List<Object> times = MiniJson.arr(hourly.get("time"));
        List<Object> temps = MiniJson.arr(hourly.get("temperature_2m"));
        List<Object> probs = MiniJson.arr(hourly.get("precipitation_probability"));
        List<Object> codes = MiniJson.arr(hourly.get("weather_code"));
        List<WeatherSnapshot.HourlyPoint> points = new ArrayList<>();
        int max = Math.min(48, Math.min(times.size(), temps.size()));
        for (int i = 0; i < max; i++) {
            points.add(new WeatherSnapshot.HourlyPoint(MiniJson.str(times.get(i)), MiniJson.num(temps.get(i)), MiniJson.num(probs.get(i)), MiniJson.integer(codes.get(i))));
        }
        List<Object> dailyTimes=MiniJson.arr(daily.get("time"));
        List<Object> dailyHighs=MiniJson.arr(daily.get("temperature_2m_max"));
        List<Object> dailyLows=MiniJson.arr(daily.get("temperature_2m_min"));
        List<Object> dailyPrecip=MiniJson.arr(daily.get("precipitation_probability_max"));
        List<Object> dailyCodes=MiniJson.arr(daily.get("weather_code"));

        List<WeatherSnapshot.DailyPoint> days=new ArrayList<>();
        int dayCount=Math.min(
                5,
                Math.min(
                        dailyTimes.size(),
                        Math.min(dailyHighs.size(),dailyLows.size())
                )
        );

        for(int i=0;i<dayCount;i++){
            double probability=i<dailyPrecip.size()
                    ?MiniJson.num(dailyPrecip.get(i))
                    :Double.NaN;
            int dayCode=i<dailyCodes.size()
                    ?MiniJson.integer(dailyCodes.get(i))
                    :code;

            days.add(new WeatherSnapshot.DailyPoint(
                    MiniJson.str(dailyTimes.get(i)),
                    MiniJson.num(dailyHighs.get(i)),
                    MiniJson.num(dailyLows.get(i)),
                    probability,
                    dayCode
            ));
        }

        return new WeatherSnapshot(
                loc.name(),
                temp,
                feels,
                high,
                low,
                precip,
                humidity,
                wind,
                gust,
                code,
                condition(code),
                Instant.now(),
                points,
                days
        );
    }

    /** Geocodes a user-entered place name using Open-Meteo's no-key geocoding endpoint. */
    public Location geocode(String name) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        Map<String,Object> root = MiniJson.obj(MiniJson.parse(http.getText(url)));
        Object r = root.get("results");
        if (!(r instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("Location not found: " + name);
        Map<String,Object> item = MiniJson.obj(list.get(0));
        String display = MiniJson.str(item.get("name"));
        String admin = MiniJson.str(item.get("admin1"));
        if (!admin.isBlank()) display += ", " + admin;
        return new Location(display, MiniJson.num(item.get("latitude")), MiniJson.num(item.get("longitude")));
    }

    private static double firstNum(Object o) { List<Object> a = MiniJson.arr(o); return a.isEmpty() ? Double.NaN : MiniJson.num(a.get(0)); }

    public static String condition(int c) {
        return switch (c) {
            case 0 -> "Clear"; case 1 -> "Mostly Clear"; case 2 -> "Partly Cloudy"; case 3 -> "Cloudy";
            case 45,48 -> "Fog"; case 51,53,55,56,57 -> "Drizzle"; case 61,63,65,66,67 -> "Rain";
            case 71,73,75,77,85,86 -> "Snow"; case 80,81,82 -> "Rain Showers";
            case 95 -> "Thunderstorms"; case 96,99 -> "Severe Storms"; default -> "Conditions";
        };
    }
}
