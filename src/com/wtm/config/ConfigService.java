package com.wtm.config;

import com.wtm.model.Location;
import com.wtm.model.RouteConfig;
import com.wtm.model.SportsConfig;
import com.wtm.model.CelebrationConfig;
import com.wtm.model.OperationEvent;
import com.wtm.model.OperationType;
import com.wtm.model.OperationsKpiConfig;
import com.wtm.ui.AppTheme;
import com.wtm.ui.OrientedImageLoader;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.util.SecureFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;

/** Loads and saves human-readable .properties configuration in the user's home directory. */
public final class ConfigService {
    private static final String APP_DIR = ".northstar-operations-intelligence";
    private static final String LEGACY_APP_DIR = ".weather-traffic-monitor";
    private static final String FILE_NAME = "config.properties";

    private ConfigService() {}

    public static Path appDataDir() {
        Path home=Path.of(System.getProperty("user.home"));
        Path target=home.resolve(APP_DIR);
        migrateLegacyDataIfNeeded(home.resolve(LEGACY_APP_DIR),target);
        return target;
    }

    /**
     * One-time product split migration. The legacy shared application folder is
     * copied, never moved, so ORIVUE Classic and North Star can diverge safely.
     */
    private static void migrateLegacyDataIfNeeded(Path legacy,Path target){
        if(Files.exists(target)||!Files.isDirectory(legacy))return;

        try{
            Files.createDirectories(target);
            try(var paths=Files.walk(legacy)){
                paths.forEach(source->{
                    try{
                        Path relative=legacy.relativize(source);
                        Path destination=target.resolve(relative);
                        if(Files.isDirectory(source)){
                            Files.createDirectories(destination);
                        }else if(Files.isRegularFile(source)){
                            Files.createDirectories(destination.getParent());
                            Files.copy(
                                    source,
                                    destination,
                                    StandardCopyOption.COPY_ATTRIBUTES
                            );
                        }
                    }catch(Exception ignored){}
                });
            }
        }catch(Exception ignored){
            // A failed migration must not prevent the application from starting.
        }
    }

    /**
     * North Star remains the product identity, but the user-selected visual
     * palette is available during splash/login startup.
     */
    public static String peekThemeId(){
        try{
            Path file=appDataDir().resolve(FILE_NAME);
            if(!Files.exists(file))return "NORTH_STAR";
            Properties p=new Properties();
            try(InputStream in=Files.newInputStream(file)){p.load(in);}
            return p.getProperty("themeId","NORTH_STAR");
        }catch(Exception ex){
            return "NORTH_STAR";
        }
    }

    public static AppConfig load() {
        AppConfig cfg = new AppConfig();
        try {
            SecureFiles.ensurePrivateDirectory(appDataDir());
            MediaService.ensureDirectories();
            Path file = appDataDir().resolve(FILE_NAME);
            if (!Files.exists(file)) {
                save(cfg);
                return cfg;
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }

            cfg.fullscreen = bool(p, "fullscreen", cfg.fullscreen);
            cfg.darkMode = bool(p, "darkMode", cfg.darkMode);
            cfg.themeId=p.getProperty("themeId",cfg.themeId);
            AppTheme selectedTheme=AppTheme.fromId(cfg.themeId);
            cfg.darkMode=selectedTheme.dark();

            cfg.showHeader = bool(p, "showHeader", cfg.showHeader);
            cfg.showTicker = bool(p, "showTicker", cfg.showTicker);

            cfg.workspaceModules.clear();
            String defaultModules="WEATHER,TRAFFIC_MAP,UPCOMING_EVENTS,TEAM_CELEBRATIONS,OPERATIONS_SNAPSHOT";
            for(String module:p.getProperty("workspaceModules",defaultModules).split(",")){
                String value=module.trim().toUpperCase();
                if(!value.isBlank()&&!cfg.workspaceModules.contains(value))
                    cfg.workspaceModules.add(value);
            }
            if(cfg.workspaceModules.isEmpty())
                cfg.workspaceModules.addAll(java.util.List.of(
                        "WEATHER","TRAFFIC_MAP","UPCOMING_EVENTS",
                        "TEAM_CELEBRATIONS","OPERATIONS_SNAPSHOT"));

            String intelligenceSetting=p.getProperty("workspace.intelligence.enabled");
            if(intelligenceSetting!=null){
                cfg.workspaceIntelligenceEnabled=Boolean.parseBoolean(intelligenceSetting.trim());
            }else if(cfg.workspaceModules.stream().anyMatch(
                    value->"NORTHSTAR_INTELLIGENCE".equalsIgnoreCase(value))){
                cfg.workspaceIntelligenceEnabled=true;
            }else{
                // One-time compatibility fallback for builds that stored this toggle
                // only in java.util.prefs. New saves persist it in config.properties.
                cfg.workspaceIntelligenceEnabled=java.util.prefs.Preferences.userRoot()
                        .node("com/wtm/northstar/workspace")
                        .getBoolean("dashboard.intelligence.enabled",true);
            }
            cfg.workspaceModules.removeIf(
                    value->"NORTHSTAR_INTELLIGENCE".equalsIgnoreCase(value));

            cfg.workspaceInfoStripEnabled=bool(
                    p,"workspace.infoStrip.enabled",true);
            cfg.workspaceInfoBlockCount=Math.max(
                    2,
                    Math.min(
                            6,
                            integer(p,"workspace.infoStrip.count",4)
                    )
            );
            /*
             * v5.0.3 retires implicit page cycling. Existing installs move to
             * STATIC unless they have explicitly saved one of the new movement
             * modes. Continuous ticker is available as the recommended overflow
             * behavior from Workspace Setup.
             */
            cfg.workspaceInfoMovementMode=p.getProperty(
                    "workspace.infoStrip.movementMode",
                    "STATIC"
            ).trim().toUpperCase();

            if(!java.util.Set.of("STATIC","PAGED","TICKER")
                    .contains(cfg.workspaceInfoMovementMode))
                cfg.workspaceInfoMovementMode="STATIC";

            cfg.workspaceInfoAutoScroll=
                    "PAGED".equals(cfg.workspaceInfoMovementMode);

            cfg.workspaceInfoScrollSeconds=Math.max(
                    5,
                    Math.min(
                            60,
                            integer(p,"workspace.infoStrip.scrollSeconds",10)
                    )
            );

            cfg.workspaceInfoTickerPixelsPerSecond=Math.max(
                    8,
                    Math.min(
                            120,
                            integer(
                                    p,
                                    "workspace.infoStrip.tickerPixelsPerSecond",
                                    28
                            )
                    )
            );

            cfg.operationsKpis.clear();
            int kpiCount=safeCount(p,"workspace.kpis.count",5,24);
            AppConfig kpiDefaults=new AppConfig();
            for(int i=0;i<kpiCount;i++){
                OperationsKpiConfig fallback=i<kpiDefaults.operationsKpis.size()
                        ?kpiDefaults.operationsKpis.get(i)
                        :new OperationsKpiConfig(
                                "kpi_"+i,"KPI "+(i+1),0,Double.NaN,"",true,true,"MANUAL");
                String prefix="workspace.kpi."+i+".";
                String targetText=p.getProperty(prefix+"target",
                        Double.toString(fallback.targetValue()));
                double target;
                try{target=Double.parseDouble(targetText);}
                catch(Exception ex){target=fallback.targetValue();}
                double current;
                try{current=Double.parseDouble(p.getProperty(
                        prefix+"current",Double.toString(fallback.currentValue())));}
                catch(Exception ex){current=fallback.currentValue();}
                cfg.operationsKpis.add(new OperationsKpiConfig(
                        p.getProperty(prefix+"id",fallback.id()),
                        p.getProperty(prefix+"label",fallback.label()),
                        current,target,
                        p.getProperty(prefix+"unit",fallback.unit()),
                        bool(p,prefix+"higherIsBetter",fallback.higherIsBetter()),
                        bool(p,prefix+"enabled",fallback.enabled()),
                        p.getProperty(prefix+"dataSource",fallback.dataSourceId())
                ));
            }
            if(cfg.operationsKpis.isEmpty())
                cfg.operationsKpis.addAll(kpiDefaults.operationsKpis);

            cfg.loginRequiredOnStartup = bool(
                    p,"loginRequiredOnStartup",false);
            cfg.protectApiSettings = bool(
                    p,"protectApiSettings",false);
            cfg.showRadar = bool(p, "showRadar", cfg.showRadar);
            cfg.showTraffic = bool(p, "showTraffic", cfg.showTraffic);
            cfg.showAlertsOnMap = bool(p, "showAlertsOnMap", cfg.showAlertsOnMap);
            cfg.headerText = p.getProperty("headerText", cfg.headerText);
            cfg.tickerText = p.getProperty("tickerText", cfg.tickerText);
            // Provider selection is ordinary site configuration; API secrets are
            // loaded from credentials.properties below. Keep the legacy TomTom
            // property only as a one-time migration path from releases <=1.5.1.
            String legacyTomTomKey = p.getProperty("tomTomApiKey", "").trim();
            cfg.weatherProvider = p.getProperty("weatherProvider", cfg.weatherProvider).trim();
            cfg.alertProvider = p.getProperty("alertProvider", cfg.alertProvider).trim();
            cfg.radarProvider = p.getProperty("radarProvider", cfg.radarProvider).trim();
            cfg.trafficProvider = p.getProperty("trafficProvider", cfg.trafficProvider).trim();
            cfg.sportsProvider = p.getProperty("sportsProvider", cfg.sportsProvider).trim();
            cfg.sportsPremiumLiveScores = bool(p, "sportsPremiumLiveScores", false);
            cfg.nwsUserAgent = p.getProperty("nwsUserAgent", cfg.nwsUserAgent).trim();
            cfg.weatherRefreshMinutes = integer(p, "weatherRefreshMinutes", 10);
            cfg.alertRefreshMinutes = integer(p, "alertRefreshMinutes", 2);
            cfg.radarRefreshMinutes = integer(p, "radarRefreshMinutes", 5);
            cfg.trafficRefreshMinutes = integer(p, "trafficRefreshMinutes", 5);
            // Upcoming schedules do not need live-score polling frequency.
            // Existing installations using the old 2/5/10-minute score cadence
            // migrate to a conservative 30-minute schedule refresh.
            int savedSportsRefresh=integer(p,"sportsRefreshMinutes",30);
            cfg.sportsRefreshMinutes=savedSportsRefresh<15?30:savedSportsRefresh;

            cfg.callInMode=p.getProperty("callInMode","LOCAL_TEST")
                    .trim().toUpperCase();
            if(!java.util.Set.of("OFF","LOCAL_TEST","TWILIO_WEBHOOK")
                    .contains(cfg.callInMode))
                cfg.callInMode="LOCAL_TEST";
            cfg.callInEnabled=bool(p,"callInEnabled",false);
            cfg.callInWebhookPort=Math.max(
                    1024,
                    Math.min(65535,integer(p,"callInWebhookPort",8787))
            );
            cfg.callInPublicBaseUrl=p.getProperty(
                    "callInPublicBaseUrl","").trim();
            cfg.callInTwilioFromNumber=p.getProperty(
                    "callInTwilioFromNumber","").trim();
            cfg.callInSmsRecipients=p.getProperty(
                    "callInSmsRecipients","").trim();
            cfg.callInEmailRecipients=p.getProperty(
                    "callInEmailRecipients","").trim();
            cfg.callInSmsNotifications=bool(
                    p,"callInSmsNotifications",false);
            cfg.callInEmailNotifications=bool(
                    p,"callInEmailNotifications",false);
            cfg.callInEmailFrom=p.getProperty(
                    "callInEmailFrom","").trim();

            cfg.liveSevereWeatherMode = bool(p, "liveSevereWeatherMode", false);
            cfg.automaticSevereWeatherMode = bool(p, "automaticSevereWeatherMode", true);
            cfg.autoDisableSevereWeatherMode = bool(p, "autoDisableSevereWeatherMode", true);
            cfg.mainShowcaseMediaEnabled = bool(p, "mainShowcaseMediaEnabled", false);
            cfg.mainShowcaseIntervalSeconds = Math.max(5, Math.min(600,
                    integer(p, "mainShowcaseIntervalSeconds", 30)));
            cfg.severeWeatherMapPriority = bool(p, "severeWeatherMapPriority", true);
            cfg.themeOverlayEffects = bool(p, "themeOverlayEffects", true);
            cfg.overlayIntensity = p.getProperty("overlayIntensity", "LOW").trim().toUpperCase();
            cfg.overlayPerformanceMode = p.getProperty(
                    "overlayPerformanceMode","AUTOMATIC").trim().toUpperCase();
            cfg.celebrationsEnabled = bool(p, "celebrationsEnabled", true);
            cfg.operationsAnnouncementsEnabled =
                    bool(p,"operationsAnnouncementsEnabled",true);
            cfg.operationsDefaultLeadDays =
                    Math.max(0,integer(p,"operationsDefaultLeadDays",14));

            try{
                cfg.normalOperatingStart=LocalTime.parse(
                        p.getProperty("normalOperatingStart","07:30"));
            }catch(Exception ignored){}

            try{
                cfg.normalOperatingEnd=LocalTime.parse(
                        p.getProperty("normalOperatingEnd","16:00"));
            }catch(Exception ignored){}

            cfg.normalOperatingDays.clear();
            String normalDays=p.getProperty(
                    "normalOperatingDays","MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
            for(String day:normalDays.split(",")){
                try{cfg.normalOperatingDays.add(DayOfWeek.valueOf(day.trim()));}
                catch(Exception ignored){}
            }
            if(cfg.normalOperatingDays.isEmpty()){
                cfg.normalOperatingDays.addAll(java.util.List.of(
                        DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,DayOfWeek.FRIDAY));
            }
            cfg.visibleWidgetCount = Math.max(6, Math.min(12, integer(p, "visibleWidgetCount", cfg.visibleWidgetCount)));
            cfg.mapWidthPercent = Math.max(55, Math.min(75, integer(p, "mapWidthPercent", cfg.mapWidthPercent)));
            String legacyMediaDirectory=p.getProperty("mediaDirectory","").trim();
            boolean legacyAnnouncementConfigurationFound=!legacyMediaDirectory.isBlank();
            boolean legacyAnnouncementsMigrated=legacyMediaDirectory.isBlank()
                    ||migrateLegacyAnnouncementDirectory(legacyMediaDirectory);

            cfg.primary = readLocation(p, "primary", cfg.primary);
            cfg.monitored.clear();
            int mc = safeCount(p,"monitored.count",3,200);
            for (int i = 0; i < mc; i++) {
                Location fallback = i < 3 ? new AppConfig().monitored.get(i) : cfg.primary;
                cfg.monitored.add(readLocation(p, "monitored." + i, fallback));
            }

            cfg.routes.clear();
            int rc = safeCount(p,"routes.count",3,200);
            for (int i = 0; i < rc; i++) {
                String prefix = "route." + i;
                String name = p.getProperty(prefix + ".name", "Route " + (i + 1));
                Location dest = readLocation(p, prefix + ".destination", cfg.primary);
                cfg.routes.add(new RouteConfig(name, cfg.primary, dest));
            }

            cfg.sports.clear();
            int sc = safeCount(p,"sports.count",2,100);
            AppConfig defaultsForSports = new AppConfig();
            for (int i = 0; i < sc; i++) {
                SportsConfig fallback = i < defaultsForSports.sports.size()
                        ? defaultsForSports.sports.get(i)
                        : new SportsConfig("Sports " + (i + 1), "American Football", "", "", "", true);
                String prefix="sports."+i;
                cfg.sports.add(new SportsConfig(
                        p.getProperty(prefix+".name", fallback.name()),
                        p.getProperty(prefix+".sport", fallback.sport()),
                        p.getProperty(prefix+".leagueId", fallback.leagueId()),
                        p.getProperty(prefix+".teamId", fallback.teamId()),
                        p.getProperty(prefix+".teamName", fallback.teamName()),
                        bool(p, prefix+".showLogos", fallback.showLogos())
                ));
            }

            cfg.celebrations.clear();
            boolean legacyPhotoConfigurationFound=false;
            boolean everyLegacyPhotoMigrated=true;
            int cc=safeCount(p,"celebrations.count",0,500);
            for(int i=0;i<cc;i++){
                String prefix="celebration."+i;
                String hire=p.getProperty(prefix+".hireDate","").trim();
                LocalDate hireDate=null;
                if(!hire.isBlank()){
                    try{hireDate=LocalDate.parse(hire);}catch(Exception ignored){}
                }
                String photoAsset=p.getProperty(prefix+".photoAsset","").trim();
                String legacyPhoto=p.getProperty(prefix+".photoPath","").trim();
                if(photoAsset.isBlank()&&!legacyPhoto.isBlank()){
                    legacyPhotoConfigurationFound=true;
                    photoAsset=migrateLegacyEmployeePhoto(legacyPhoto);
                    if(photoAsset.isBlank())everyLegacyPhotoMigrated=false;
                }

                cfg.celebrations.add(new CelebrationConfig(
                        p.getProperty(prefix+".name","Team Member"),
                        integer(p,prefix+".birthdayMonth",0),
                        integer(p,prefix+".birthdayDay",0),
                        hireDate,
                        photoAsset,
                        bool(p,prefix+".showBirthday",true),
                        bool(p,prefix+".showAnniversary",true),
                        integer(p,prefix+".employeeOfMonthYear",0),
                        integer(p,prefix+".employeeOfMonthMonth",0),
                        bool(p,prefix+".celebrationEffect",true),
                        bool(p,prefix+".enabled",true)
                ));
            }

            cfg.operationEvents.clear();
            int operationCount=safeCount(p,"operations.count",0,500);
            for(int i=0;i<operationCount;i++){
                String prefix="operation."+i;

                LocalDate start=null;
                LocalDate end=null;
                LocalTime startTime=null;
                LocalTime endTime=null;

                try{start=LocalDate.parse(p.getProperty(prefix+".startDate",""));}
                catch(Exception ignored){}
                try{end=LocalDate.parse(p.getProperty(prefix+".endDate",""));}
                catch(Exception ignored){}

                if(start!=null && end==null)end=start;

                try{
                    String value=p.getProperty(prefix+".startTime","").trim();
                    if(!value.isBlank())startTime=LocalTime.parse(value);
                }catch(Exception ignored){}

                try{
                    String value=p.getProperty(prefix+".endTime","").trim();
                    if(!value.isBlank())endTime=LocalTime.parse(value);
                }catch(Exception ignored){}

                if(start!=null && end!=null){
                    try{
                        cfg.operationEvents.add(new OperationEvent(
                                p.getProperty(prefix+".name","Operations Event"),
                                start,
                                end,
                                OperationType.from(
                                        p.getProperty(prefix+".type","MODIFIED_HOURS")),
                                startTime,
                                endTime,
                                Math.max(0,integer(p,prefix+".leadDays",0)),
                                bool(p,prefix+".enabled",true)
                        ));
                    }catch(Exception ignored){}
                }
            }

            cfg.widgetTypes.clear();
            int wc = safeCount(p,"widgets.count",6,12);
            for (int i = 0; i < wc; i++) cfg.widgetTypes.add(p.getProperty("widget." + i, "STATUS"));

            ApiCredentialService.loadInto(cfg);
            if((cfg.tomTomApiKey==null||cfg.tomTomApiKey.isBlank())
                    &&!legacyTomTomKey.isBlank()){
                cfg.tomTomApiKey=legacyTomTomKey;
                ApiCredentialService.saveFrom(cfg);

                /*
                 * Re-save immediately so the legacy plaintext key is removed
                 * from config.properties instead of waiting for the next manual
                 * Settings save.
                 */
                save(cfg);
            }

            if((legacyPhotoConfigurationFound&&everyLegacyPhotoMigrated)
                    ||(legacyAnnouncementConfigurationFound&&legacyAnnouncementsMigrated)){
                // Rewrite once so obsolete absolute-path media properties disappear.
                save(cfg);
            }
        } catch (Exception ex) {
            System.err.println(
                    "Configuration could not be loaded completely; safe defaults were used."
            );
        }
        return cfg;
    }

    public static void save(AppConfig cfg) {
        try {
            SecureFiles.ensurePrivateDirectory(appDataDir());
            MediaService.ensureDirectories();
            Properties p = new Properties();
            p.setProperty("fullscreen", Boolean.toString(cfg.fullscreen));
            p.setProperty("themeId", cfg.themeId);
            p.setProperty("northStarPreviewV501","true");
            p.setProperty("automaticHolidayThemes",Boolean.toString(cfg.automaticHolidayThemes));
            p.setProperty("darkMode", Boolean.toString(cfg.darkMode));
            p.setProperty("showHeader", Boolean.toString(cfg.showHeader));
            p.setProperty("showTicker", Boolean.toString(cfg.showTicker));
            p.setProperty("workspaceModules",String.join(",",cfg.workspaceModules));
            p.setProperty("workspace.intelligence.enabled",
                    Boolean.toString(cfg.workspaceIntelligenceEnabled));
            p.setProperty(
                    "workspace.infoStrip.enabled",
                    Boolean.toString(cfg.workspaceInfoStripEnabled));
            p.setProperty(
                    "workspace.infoStrip.count",
                    Integer.toString(cfg.workspaceInfoBlockCount));
            p.setProperty(
                    "workspace.infoStrip.movementMode",
                    cfg.workspaceInfoMovementMode);
            p.setProperty(
                    "workspace.infoStrip.autoScroll",
                    Boolean.toString(
                            "PAGED".equalsIgnoreCase(
                                    cfg.workspaceInfoMovementMode)));
            p.setProperty(
                    "workspace.infoStrip.scrollSeconds",
                    Integer.toString(cfg.workspaceInfoScrollSeconds));
            p.setProperty(
                    "workspace.infoStrip.tickerPixelsPerSecond",
                    Integer.toString(
                            cfg.workspaceInfoTickerPixelsPerSecond));
            p.setProperty("workspace.kpis.count",Integer.toString(cfg.operationsKpis.size()));
            for(int i=0;i<cfg.operationsKpis.size();i++){
                OperationsKpiConfig kpi=cfg.operationsKpis.get(i);
                String prefix="workspace.kpi."+i+".";
                p.setProperty(prefix+"id",kpi.id());
                p.setProperty(prefix+"label",kpi.label());
                p.setProperty(prefix+"current",Double.toString(kpi.currentValue()));
                p.setProperty(prefix+"target",Double.toString(kpi.targetValue()));
                p.setProperty(prefix+"unit",kpi.unit()==null?"":kpi.unit());
                p.setProperty(prefix+"higherIsBetter",Boolean.toString(kpi.higherIsBetter()));
                p.setProperty(prefix+"enabled",Boolean.toString(kpi.enabled()));
                p.setProperty(prefix+"dataSource",kpi.dataSourceId()==null?"MANUAL":kpi.dataSourceId());
            }
            p.setProperty(
                    "loginRequiredOnStartup",
                    Boolean.toString(cfg.loginRequiredOnStartup));
            p.setProperty(
                    "protectApiSettings",
                    Boolean.toString(cfg.protectApiSettings));
            p.setProperty("showRadar", Boolean.toString(cfg.showRadar));
            p.setProperty("showTraffic", Boolean.toString(cfg.showTraffic));
            p.setProperty("showAlertsOnMap", Boolean.toString(cfg.showAlertsOnMap));
            p.setProperty("liveSevereWeatherMode", Boolean.toString(cfg.liveSevereWeatherMode));
            p.setProperty("automaticSevereWeatherMode", Boolean.toString(cfg.automaticSevereWeatherMode));
            p.setProperty("autoDisableSevereWeatherMode", Boolean.toString(cfg.autoDisableSevereWeatherMode));
            p.setProperty("mainShowcaseMediaEnabled", Boolean.toString(cfg.mainShowcaseMediaEnabled));
            p.setProperty("mainShowcaseIntervalSeconds", Integer.toString(cfg.mainShowcaseIntervalSeconds));
            p.setProperty("severeWeatherMapPriority", Boolean.toString(cfg.severeWeatherMapPriority));
            p.setProperty("themeOverlayEffects", Boolean.toString(cfg.themeOverlayEffects));
            p.setProperty("overlayIntensity", cfg.overlayIntensity);
            p.setProperty("overlayPerformanceMode", cfg.overlayPerformanceMode);
            p.setProperty("celebrationsEnabled", Boolean.toString(cfg.celebrationsEnabled));
            p.setProperty(
                    "operationsAnnouncementsEnabled",
                    Boolean.toString(cfg.operationsAnnouncementsEnabled));
            p.setProperty(
                    "operationsDefaultLeadDays",
                    Integer.toString(cfg.operationsDefaultLeadDays));
            p.setProperty(
                    "normalOperatingStart",
                    cfg.normalOperatingStart.toString());
            p.setProperty(
                    "normalOperatingEnd",
                    cfg.normalOperatingEnd.toString());
            p.setProperty(
                    "normalOperatingDays",
                    cfg.normalOperatingDays.stream()
                            .map(Enum::name)
                            .sorted()
                            .reduce((a,b)->a+","+b)
                            .orElse(""));

            p.setProperty("headerText", cfg.headerText);
            p.setProperty("tickerText", cfg.tickerText);
            p.setProperty("weatherProvider", cfg.weatherProvider);
            p.setProperty("alertProvider", cfg.alertProvider);
            p.setProperty("radarProvider", cfg.radarProvider);
            p.setProperty("trafficProvider", cfg.trafficProvider);
            p.setProperty("sportsProvider", cfg.sportsProvider);
            p.setProperty("sportsPremiumLiveScores", Boolean.toString(cfg.sportsPremiumLiveScores));
            p.setProperty("nwsUserAgent", cfg.nwsUserAgent);
            p.setProperty("weatherRefreshMinutes", Integer.toString(cfg.weatherRefreshMinutes));
            p.setProperty("alertRefreshMinutes", Integer.toString(cfg.alertRefreshMinutes));
            p.setProperty("radarRefreshMinutes", Integer.toString(cfg.radarRefreshMinutes));
            p.setProperty("trafficRefreshMinutes", Integer.toString(cfg.trafficRefreshMinutes));
            p.setProperty("sportsRefreshMinutes", Integer.toString(cfg.sportsRefreshMinutes));
            p.setProperty("callInMode",cfg.callInMode);
            p.setProperty("callInEnabled",Boolean.toString(cfg.callInEnabled));
            p.setProperty("callInWebhookPort",Integer.toString(cfg.callInWebhookPort));
            p.setProperty("callInPublicBaseUrl",cfg.callInPublicBaseUrl);
            p.setProperty("callInTwilioFromNumber",cfg.callInTwilioFromNumber);
            p.setProperty("callInSmsRecipients",cfg.callInSmsRecipients);
            p.setProperty("callInEmailRecipients",cfg.callInEmailRecipients);
            p.setProperty("callInSmsNotifications",Boolean.toString(cfg.callInSmsNotifications));
            p.setProperty("callInEmailNotifications",Boolean.toString(cfg.callInEmailNotifications));
            p.setProperty("callInEmailFrom",cfg.callInEmailFrom);
            p.setProperty("visibleWidgetCount", Integer.toString(cfg.visibleWidgetCount));
            p.setProperty("mapWidthPercent", Integer.toString(cfg.mapWidthPercent));
            writeLocation(p, "primary", cfg.primary);

            p.setProperty("monitored.count", Integer.toString(cfg.monitored.size()));
            for (int i = 0; i < cfg.monitored.size(); i++) writeLocation(p, "monitored." + i, cfg.monitored.get(i));

            p.setProperty("routes.count", Integer.toString(cfg.routes.size()));
            for (int i = 0; i < cfg.routes.size(); i++) {
                RouteConfig r = cfg.routes.get(i);
                p.setProperty("route." + i + ".name", r.name());
                writeLocation(p, "route." + i + ".destination", r.destination());
            }

            p.setProperty("sports.count", Integer.toString(cfg.sports.size()));
            for (int i = 0; i < cfg.sports.size(); i++) {
                SportsConfig sport = cfg.sports.get(i);
                String prefix="sports."+i;
                p.setProperty(prefix+".name", sport.name());
                p.setProperty(prefix+".sport", sport.sport());
                p.setProperty(prefix+".leagueId", sport.leagueId());
                p.setProperty(prefix+".teamId", sport.teamId());
                p.setProperty(prefix+".teamName", sport.teamName());
                p.setProperty(prefix+".showLogos", Boolean.toString(sport.showLogos()));
            }

            p.setProperty("celebrations.count",Integer.toString(cfg.celebrations.size()));
            for(int i=0;i<cfg.celebrations.size();i++){
                CelebrationConfig c=cfg.celebrations.get(i);
                String prefix="celebration."+i;
                p.setProperty(prefix+".name",c.name()==null?"":c.name());
                p.setProperty(prefix+".birthdayMonth",Integer.toString(c.birthdayMonth()));
                p.setProperty(prefix+".birthdayDay",Integer.toString(c.birthdayDay()));
                p.setProperty(prefix+".hireDate",c.hireDate()==null?"":c.hireDate().toString());
                p.setProperty(prefix+".photoAsset",c.photoAsset()==null?"":c.photoAsset());
                p.setProperty(prefix+".showBirthday",Boolean.toString(c.showBirthday()));
                p.setProperty(prefix+".showAnniversary",Boolean.toString(c.showAnniversary()));
                p.setProperty(prefix+".employeeOfMonthYear",Integer.toString(c.employeeOfMonthYear()));
                p.setProperty(prefix+".employeeOfMonthMonth",Integer.toString(c.employeeOfMonthMonth()));
                p.setProperty(prefix+".celebrationEffect",Boolean.toString(c.celebrationEffect()));
                p.setProperty(prefix+".enabled",Boolean.toString(c.enabled()));
            }

            p.setProperty(
                    "operations.count",
                    Integer.toString(cfg.operationEvents.size()));

            for(int i=0;i<cfg.operationEvents.size();i++){
                OperationEvent event=cfg.operationEvents.get(i);
                String prefix="operation."+i;

                p.setProperty(prefix+".name",event.name()==null?"":event.name());
                p.setProperty(prefix+".startDate",event.startDate().toString());
                p.setProperty(prefix+".endDate",event.endDate().toString());
                p.setProperty(prefix+".type",event.type().name());
                p.setProperty(
                        prefix+".startTime",
                        event.startTime()==null?"":event.startTime().toString());
                p.setProperty(
                        prefix+".endTime",
                        event.endTime()==null?"":event.endTime().toString());
                p.setProperty(prefix+".leadDays",Integer.toString(event.leadDays()));
                p.setProperty(prefix+".enabled",Boolean.toString(event.enabled()));
            }

            p.setProperty("widgets.count", Integer.toString(cfg.widgetTypes.size()));
            for (int i = 0; i < cfg.widgetTypes.size(); i++) p.setProperty("widget." + i, cfg.widgetTypes.get(i));

            /*
             * Write configuration atomically so a power interruption cannot
             * leave a truncated properties file on a 24/7 Raspberry Pi.
             * The file also receives owner-only POSIX permissions where
             * supported because it can contain employee names and local paths.
             */
            SecureFiles.storePropertiesAtomic(
                    appDataDir().resolve(FILE_NAME),
                    p,
                    "North Star Operations configuration"
            );

            ApiCredentialService.saveFrom(cfg);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to save configuration", ex);
        }
    }

    /** Migrates the pre-Media-Library announcement folder once. */
    private static boolean migrateLegacyAnnouncementDirectory(String legacyValue){
        final Path sourceDirectory;
        try{sourceDirectory=Path.of(legacyValue);}
        catch(Exception ex){return false;}

        if(!Files.isDirectory(sourceDirectory))return false;

        try(var stream=Files.list(sourceDirectory)){
            for(Path source:stream.filter(Files::isRegularFile).toList()){
                String name=source.getFileName().toString().toLowerCase(Locale.ROOT);
                if(!(name.endsWith(".png")||name.endsWith(".jpg")
                        ||name.endsWith(".jpeg")||name.endsWith(".gif")))
                    continue;

                try{
                    if(OrientedImageLoader.load(source)==null)continue;
                    Path directory=MediaService.directory(MediaCategory.ANNOUNCEMENTS);
                    SecureFiles.ensurePrivateDirectory(directory);

                    Path target=directory.resolve(source.getFileName());
                    if(Files.exists(target))continue;
                    Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES);
                    SecureFiles.restrictFile(target);
                }catch(Exception ex){
                    System.err.println("Legacy announcement media could not be migrated.");
                    return false;
                }
            }
            return true;
        }catch(Exception ex){
            return false;
        }
    }

    /**
     * One-time migration from pre-v3.1.2 absolute employee-photo paths into
     * the managed Employee Photos library. New configuration stores only the
     * managed filename, making employee records portable between machines.
     */
    private static String migrateLegacyEmployeePhoto(String legacyValue){
        if(legacyValue==null||legacyValue.isBlank())return "";

        // If an older config already happens to contain a managed filename,
        // preserve it without copying again.
        Path managed=MediaService.resolve(
                MediaCategory.EMPLOYEE_PHOTOS,
                Path.of(legacyValue).getFileName().toString()
        );
        if(managed!=null)return managed.getFileName().toString();

        final Path source;
        try{ source=Path.of(legacyValue); }
        catch(Exception ex){ return ""; }

        if(!Files.isRegularFile(source)||!Files.isReadable(source))return "";

        try{
            if(OrientedImageLoader.load(source)==null)return "";

            Path directory=MediaService.directory(MediaCategory.EMPLOYEE_PHOTOS);
            SecureFiles.ensurePrivateDirectory(directory);

            String original=source.getFileName().toString();
            int dot=original.lastIndexOf('.');
            String extension=dot>=0?original.substring(dot).toLowerCase(Locale.ROOT):".jpg";
            if(!Set.of(".png",".jpg",".jpeg",".gif").contains(extension))
                extension=".jpg";

            String base=(dot>0?original.substring(0,dot):original)
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]+","-")
                    .replaceAll("^-+|-+$","");
            if(base.isBlank())base="employee-photo";

            Path target=directory.resolve(base+extension);
            int suffix=2;
            while(Files.exists(target)){
                target=directory.resolve(base+"-"+suffix+extension);
                suffix++;
            }

            Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES);
            SecureFiles.restrictFile(target);
            return target.getFileName().toString();
        }catch(Exception ex){
            System.err.println("Legacy employee photo could not be migrated.");
            return "";
        }
    }

    private static int safeCount(
            Properties properties,
            String key,
            int defaultValue,
            int maximum
    ){
        int value=integer(properties,key,defaultValue);
        return Math.max(0,Math.min(maximum,value));
    }

    private static boolean bool(Properties p, String k, boolean d) { return Boolean.parseBoolean(p.getProperty(k, Boolean.toString(d))); }
    private static int integer(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k, Integer.toString(d))); } catch (Exception e) { return d; } }
    private static Location readLocation(Properties p, String prefix, Location d) {
        String name = p.getProperty(prefix + ".name", d.name());
        double lat = number(p.getProperty(prefix + ".lat"), d.latitude());
        double lon = number(p.getProperty(prefix + ".lon"), d.longitude());
        return new Location(name, lat, lon);
    }
    private static void writeLocation(Properties p, String prefix, Location l) {
        p.setProperty(prefix + ".name", l.name());
        p.setProperty(prefix + ".lat", Double.toString(l.latitude()));
        p.setProperty(prefix + ".lon", Double.toString(l.longitude()));
    }
    private static double number(String v, double d) { try { return v == null ? d : Double.parseDouble(v); } catch (Exception e) { return d; } }
}
