package com.wtm.usage;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Installation-local accounting for outbound provider requests.
 *
 * Counts are persisted, but persistence is intentionally throttled. Previous
 * releases rewrote the properties file on every map tile request, which caused
 * unnecessary storage I/O on 24/7 installations and was especially undesirable
 * on host computer SD cards.
 */
public final class ApiUsageTracker {
    private static final ApiUsageTracker INSTANCE=new ApiUsageTracker();
    private static final String FILE="api-usage.properties";
    private static final long SAVE_INTERVAL_NANOS=
            Duration.ofSeconds(30).toNanos();

    private final Map<String,AtomicLong> counts=new ConcurrentHashMap<>();

    private String dayKey=currentDay();
    private String monthKey=currentMonth();
    private String minuteKey=currentMinute();

    private boolean dirty=false;
    private long lastSaveNanos=System.nanoTime();

    private ApiUsageTracker(){
        load();

        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        ()->{
                            synchronized(this){
                                saveQuietly(true);
                            }
                        },
                        "api-usage-final-save"
                )
        );
    }

    public static ApiUsageTracker get(){
        return INSTANCE;
    }

    /**
     * Records one outbound request. Classification is domain/path based so
     * provider adapters cannot accidentally forget usage accounting.
     */
    public synchronized void record(String url){
        rollPeriodsIfNeeded();

        String key=classify(url);
        if(key==null)return;

        increment("lifetime."+key);
        increment("day."+dayKey+"."+key);
        increment("month."+monthKey+"."+key);
        increment("minute."+minuteKey+"."+key);

        dirty=true;
        saveQuietly(false);
    }

    public synchronized List<ApiUsageRecord> snapshot(
            boolean openMeteoCustomer,
            boolean sportsPremium
    ){
        rollPeriodsIfNeeded();

        List<ApiUsageRecord> rows=new ArrayList<>();

        rows.add(row(
                "TomTom",
                "Traffic / map tiles",
                "TOMTOM_TILE",
                50_000,
                "day",
                "Configured local reference limit."
        ));
        rows.add(row(
                "TomTom",
                "Routing / other non-tile",
                "TOMTOM_NON_TILE",
                2_500,
                "day",
                "Configured local reference limit."
        ));

        if(openMeteoCustomer){
            rows.add(row(
                    "Open-Meteo",
                    "Weather / geocoding",
                    "OPEN_METEO",
                    -1,
                    "day",
                    "Customer endpoint usage is tracked locally."
            ));
        }else{
            rows.add(row(
                    "Open-Meteo",
                    "Weather / geocoding",
                    "OPEN_METEO",
                    10_000,
                    "day",
                    "Free-tier reference used by this installation."
            ));
        }

        rows.add(row(
                "National Weather Service",
                "Alerts / weather.gov",
                "NWS",
                -1,
                "day",
                "Tracked locally; no fixed quota is enforced by the application."
        ));

        rows.add(row(
                "RainViewer",
                "Radar metadata / tiles",
                "RAINVIEWER",
                -1,
                "day",
                "Tracked locally."
        ));

        rows.add(row(
                "TheSportsDB",
                "Sports API",
                "SPORTSDB",
                sportsPremium?100:30,
                "minute",
                "Local request-rate reference."
        ));

        rows.add(row(
                "Twilio",
                "Management SMS notifications",
                "TWILIO_MESSAGING",
                -1,
                "day",
                "Tracked locally; billing depends on the Twilio account."
        ));

        rows.add(row(
                "SendGrid",
                "Management email notifications",
                "SENDGRID",
                -1,
                "day",
                "Tracked locally; provider dashboard remains authoritative."
        ));

        return rows;
    }

    public synchronized long getLifetime(String classification){
        return value("lifetime."+classification);
    }

    public synchronized void resetLocalHistory(){
        counts.clear();
        dayKey=currentDay();
        monthKey=currentMonth();
        minuteKey=currentMinute();
        dirty=true;
        saveQuietly(true);
    }

    private ApiUsageRecord row(
            String provider,
            String category,
            String key,
            long limit,
            String period,
            String note
    ){
        long used=switch(period){
            case "minute" -> value("minute."+minuteKey+"."+key);
            case "month" -> value("month."+monthKey+"."+key);
            default -> value("day."+dayKey+"."+key);
        };

        return new ApiUsageRecord(
                provider,category,used,limit,period,note);
    }

    private String classify(String url){
        try{
            URI uri=URI.create(url);
            String host=Optional.ofNullable(uri.getHost())
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            String path=Optional.ofNullable(uri.getPath())
                    .orElse("")
                    .toLowerCase(Locale.ROOT);

            if(host.contains("tomtom")){
                return path.contains("/tile/")||path.contains("/tiles/")
                        ?"TOMTOM_TILE"
                        :"TOMTOM_NON_TILE";
            }
            if(host.contains("open-meteo.com"))return "OPEN_METEO";
            if(host.contains("weather.gov"))return "NWS";
            if(host.contains("rainviewer.com"))return "RAINVIEWER";
            if(host.contains("thesportsdb.com"))return "SPORTSDB";
            if(host.contains("api.twilio.com"))return "TWILIO_MESSAGING";
            if(host.contains("api.sendgrid.com"))return "SENDGRID";
        }catch(Exception ignored){
        }

        return null;
    }

    private void increment(String key){
        counts.computeIfAbsent(key,k->new AtomicLong()).incrementAndGet();
    }

    private long value(String key){
        AtomicLong value=counts.get(key);
        return value==null?0:value.get();
    }

    private void rollPeriodsIfNeeded(){
        dayKey=currentDay();
        monthKey=currentMonth();
        minuteKey=currentMinute();

        counts.keySet().removeIf(key->
                key.startsWith("day.")
                        &&!key.startsWith("day."+dayKey+".")
                ||key.startsWith("month.")
                        &&!key.startsWith("month."+monthKey+".")
                ||key.startsWith("minute.")
                        &&!key.startsWith("minute."+minuteKey+".")
        );
    }

    private void load(){
        Path file=ConfigService.appDataDir().resolve(FILE);
        if(!Files.exists(file))return;

        Properties properties=new Properties();

        try(InputStream in=new BufferedInputStream(Files.newInputStream(file))){
            properties.load(in);

            for(String name:properties.stringPropertyNames()){
                try{
                    counts.put(
                            name,
                            new AtomicLong(
                                    Long.parseLong(properties.getProperty(name))
                            )
                    );
                }catch(Exception ignored){
                }
            }
        }catch(Exception ex){
            System.err.println("API usage history could not be loaded.");
        }
    }

    private void saveQuietly(boolean force){
        if(!dirty)return;

        long now=System.nanoTime();
        if(!force&&now-lastSaveNanos<SAVE_INTERVAL_NANOS)return;

        try{
            Properties properties=new Properties();

            for(var entry:counts.entrySet()){
                properties.setProperty(
                        entry.getKey(),
                        Long.toString(entry.getValue().get())
                );
            }

            SecureFiles.storePropertiesAtomic(
                    ConfigService.appDataDir().resolve(FILE),
                    properties,
                    "North Star local API request accounting"
            );

            dirty=false;
            lastSaveNanos=now;
        }catch(Exception ex){
            // Leave dirty=true so a later request/shutdown can retry.
            System.err.println("API usage history could not be saved.");
        }
    }

    private static String currentDay(){
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String currentMonth(){
        return YearMonth.now().toString().replace("-","");
    }

    private static String currentMinute(){
        return LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        );
    }
}
