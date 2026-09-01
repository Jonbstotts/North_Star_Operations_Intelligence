package com.wtm.config;

import com.wtm.model.Location;
import com.wtm.model.RouteConfig;
import com.wtm.model.SportsConfig;
import com.wtm.model.CelebrationConfig;
import com.wtm.model.OperationEvent;
import com.wtm.model.OperationsKpiConfig;
import java.time.*;
import java.util.*;

/**
 * In-memory application configuration.
 *
 * Site-specific values live here instead of in dashboard classes so one build
 * can be reused at any facility. Pinned locations and commute routes are
 * deliberately dynamic; there is no hard-coded facility limit.
 */
public final class AppConfig {
    public boolean fullscreen = true;
    /**
     * Theme preset ID. darkMode is retained as a compatibility/map-rendering
     * flag and is synchronized from the chosen theme.
     */
    public String themeId = "NORTH_STAR";

    /**
     * When enabled, holiday/seasonal presets temporarily override themeId based
     * on the current local date. themeId remains the manual fallback.
     */
    public boolean automaticHolidayThemes = false;

    public boolean darkMode = true;
    public boolean showHeader = true;
    public boolean showTicker = true;

    /** NONE, STATIC_SPLASH, or INTRO_VIDEO. */
    public String startupExperience = "STATIC_SPLASH";

    /** Managed filename under MediaCategory.STARTUP_MEDIA; never an arbitrary path. */
    public String startupVideoAsset = "";


    /** Modular cards shown on the Operations Workspace home dashboard. */
    public final List<String> workspaceModules = new ArrayList<>(List.of(
            "WEATHER",
            "TRAFFIC_MAP",
            "UPCOMING_EVENTS",
            "TEAM_CELEBRATIONS",
            "OPERATIONS_SNAPSHOT"
    ));

    /** Source-owned toggle for the optional NorthStar Intelligence dashboard/route integration. */
    public boolean workspaceIntelligenceEnabled = true;

    /** Persisted 24-column dashboard tile geometry: x,y,width,height. */
    public final Map<String,String> workspaceDashboardLayout=new LinkedHashMap<>(Map.of(
            "_gridVersion","2",
            "WEATHER","0,0,6,6",
            "SHOWCASE","6,0,12,6",
            "UPCOMING_EVENTS","18,0,6,3",
            "TEAM_CELEBRATIONS","18,3,6,3",
            "INFORMATION","0,6,24,1",
            "OPERATIONS_SNAPSHOT","0,7,24,2"
    ));

    /**
     * Compact Information row placed between the primary workspace modules and
     * Operations Snapshot. Information Blocks is the single configuration
     * surface for item count, selected content, viewport size and movement.
     */
    public boolean workspaceInfoStripEnabled = true;
    public int workspaceInfoBlockCount = 4;

    /**
     * Information movement behavior:
     * STATIC = fixed first page
     * PAGED = page-by-page rotation
     * TICKER = continuous smooth horizontal movement of the whole strip
     */
    public String workspaceInfoMovementMode = "STATIC";

    /** Page interval used only by PAGED mode. */
    public int workspaceInfoScrollSeconds = 10;

    /** Continuous ticker speed in pixels per second. */
    public int workspaceInfoTickerPixelsPerSecond = 28;

    /**
     * Operations Snapshot movement behavior. This is intentionally independent
     * from the Information row because each is now its own dashboard-grid tile.
     * STATIC = fixed first viewport, PAGED = timed pages, TICKER = continuous.
     */
    public String workspaceKpiMovementMode = "STATIC";
    public int workspaceKpiVisibleCount = 8;
    public int workspaceKpiScrollSeconds = 10;
    public int workspaceKpiTickerPixelsPerSecond = 28;

    /** Configurable KPI cards for the Operations Snapshot module. */
    public final List<OperationsKpiConfig> operationsKpis = new ArrayList<>(List.of(
            new OperationsKpiConfig("lhy","LHY Performance",0,17500,"",true,true,"MANUAL"),
            new OperationsKpiConfig("lines","Lines Shipped",0,Double.NaN," lines",true,true,"MANUAL"),
            new OperationsKpiConfig("damages","Damages",0,0,"",false,true,"MANUAL"),
            new OperationsKpiConfig("floor_denials","Floor Denials",0,0,"",false,true,"MANUAL"),
            new OperationsKpiConfig("alerts","Active Alerts",0,0,"",false,true,"SYSTEM_ALERTS")
    ));

    /** Optional named-user login before the passive dashboard is shown. */
    public boolean loginRequiredOnStartup = false;

    /** Require password re-verification before authorized API tabs open. */
    public boolean protectApiSettings = false;
    public boolean showRadar = true;
    public boolean showTraffic = true;
    public boolean showAlertsOnMap = true;
    public String headerText = "OPERATIONS INTELLIGENCE DASHBOARD";
    public String tickerText = "Weather and traffic conditions refresh automatically throughout the day.";
    public int weatherRefreshMinutes = 10;
    public int alertRefreshMinutes = 2;
    public int radarRefreshMinutes = 5;
    public int trafficRefreshMinutes = 5;

    /**
     * High-frequency severe-weather monitoring mode.
     *
     * This does not alter TomTom traffic cadence. It temporarily overrides
     * weather/radar/NWS alert scheduler intervals while enabled.
     */
    public boolean liveSevereWeatherMode = false;

    /**
     * Allows NWS alert detection to automatically enter rapid severe-weather
     * polling without changing the user's manual live-mode preference.
     */
    public boolean automaticSevereWeatherMode = true;

    /**
     * Returns an automatically-triggered live state to normal after qualifying
     * severe-weather alerts clear.
     */
    public boolean autoDisableSevereWeatherMode = true;

    /** Supported weather adapter: OPEN_METEO_FREE or OPEN_METEO_CUSTOMER. */
    public String weatherProvider = "OPEN_METEO_FREE";

    /** Optional Open-Meteo customer-plan API key. Blank for free access. */
    public String weatherApiKey = "";

    /** Alert adapter. NWS is the currently installed U.S. severe-weather adapter. */
    public String alertProvider = "NWS";

    /** NWS identifies clients by User-Agent rather than a traditional API key. */
    public String nwsUserAgent = "NorthStarOperationsIntelligence/2.1.34 (workplace-display; contact=local-admin)";

    /** Radar adapter. RainViewer public radar is currently installed. */
    public String radarProvider = "RAINVIEWER";

    /** Traffic/routing adapter. TomTom is currently installed. */
    public String trafficProvider = "TOMTOM";

    /** TomTom API credential. Stored separately from normal site settings. */
    public String tomTomApiKey = "";

    /** Sports adapter. TheSportsDB is the first installed general-sports provider. */
    public String sportsProvider = "THESPORTSDB";

    /** TheSportsDB v1 free key is currently 123; replace with a premium key if purchased. */
    public String sportsApiKey = "123";

    /** Enables TheSportsDB premium v2 livescore endpoint when a premium key is supplied. */
    public boolean sportsPremiumLiveScores = false;

    /** Sports block refresh cadence. Two minutes is appropriate for premium live scores. */
    public int sportsRefreshMinutes = 5;

    // ---------------------------------------------------------------------
    // Employee call-in / management notification integration.
    // ---------------------------------------------------------------------

    /** OFF, LOCAL_TEST, or TWILIO_WEBHOOK. */
    public String callInMode = "LOCAL_TEST";

    /** Starts the embedded webhook listener when TWILIO_WEBHOOK is selected. */
    public boolean callInEnabled = false;

    /** Local listener port. A production reverse proxy/function should expose HTTPS. */
    public int callInWebhookPort = 8787;

    /** Exact public HTTPS base URL used by Twilio signature validation. */
    public String callInPublicBaseUrl = "";

    /** Twilio phone number used for outbound management SMS notifications. */
    public String callInTwilioFromNumber = "";

    /** Comma-separated management SMS destinations. */
    public String callInSmsRecipients = "";

    /** Comma-separated management email destinations. */
    public String callInEmailRecipients = "";

    public boolean callInSmsNotifications = false;
    public boolean callInEmailNotifications = false;

    /** Verified SendGrid sender address. */
    public String callInEmailFrom = "";

    /** Twilio Account SID. Stored in credentials.properties. */
    public String twilioAccountSid = "";

    /** Twilio Auth Token. Stored in credentials.properties. */
    public String twilioAuthToken = "";

    /** SendGrid API key. Stored in credentials.properties. */
    public String sendGridApiKey = "";

    /** Optional lightweight decorative overlays tied to themes. */
    public boolean themeOverlayEffects = true;

    /** LOW, MEDIUM, or HIGH particle density. */
    public String overlayIntensity = "LOW";

    /**
     * Overlay rendering profile: AUTOMATIC, HIGH_QUALITY, BALANCED, PERFORMANCE.
     * AUTOMATIC adapts ambient effects based on measured frame cost.
     */
    public String overlayPerformanceMode = "AUTOMATIC";

    /** Allow generated birthday/work-anniversary cards in Main Showcase. */
    public boolean celebrationsEnabled = true;

    /** Local team-recognition records. */
    public final List<CelebrationConfig> celebrations = new ArrayList<>();

    /** Automatically generate Main Showcase announcements from the Operations Calendar. */
    public boolean operationsAnnouncementsEnabled = true;

    /** Site default announcement lead time. Individual events may override it. */
    public int operationsDefaultLeadDays = 14;

    /** Normal site operating schedule, used for comparison/resume messaging. */
    public LocalTime normalOperatingStart = LocalTime.of(7,30);
    public LocalTime normalOperatingEnd = LocalTime.of(16,0);
    public final EnumSet<DayOfWeek> normalOperatingDays =
            EnumSet.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
            );

    /** Full closures, limited-service periods, and temporary modified hours. */
    public final List<OperationEvent> operationEvents = new ArrayList<>();



    /**
     * Enables the large Main Showcase to cycle between the live map and
     * company-announcement media.
     */
    public boolean mainShowcaseMediaEnabled = false;

    /** Number of seconds each Main Showcase item stays visible. */
    public int mainShowcaseIntervalSeconds = 30;

    /**
     * When AUTO LIVE severe-weather monitoring is active, keep the map visible
     * and suspend Main Showcase media rotation. Can be disabled for testing.
     */
    public boolean severeWeatherMapPriority = true;

    /** Number of visible information cards beside the map. Supported range: 6–12. */
    public int visibleWidgetCount = 10;


    public Location primary = new Location("Vance", 33.1743, -87.2336);

    /** All map pins / forecast locations. Add as many as the site needs. */
    public final List<Location> monitored = new ArrayList<>(List.of(
            new Location("Tuscaloosa", 33.2098, -87.5692),
            new Location("Vance", 33.1743, -87.2336),
            new Location("Birmingham", 33.5186, -86.8104),
            new Location("Hoover", 33.4054, -86.8114),
            new Location("Trussville", 33.6198, -86.6089)
    ));

    /**
     * Sports selections become dashboard-block choices exactly like configured
     * routes. These two college-football examples can be edited or removed.
     */
    public final List<SportsConfig> sports = new ArrayList<>(List.of(
            new SportsConfig("Alabama Football", "American Football", "4479", "136168", "Alabama", true),
            new SportsConfig("Tennessee Football", "American Football", "4479", "136957", "Tennessee", true)
    ));

    /** Traffic-aware routes always originate at the primary facility. */
    public final List<RouteConfig> routes = new ArrayList<>(List.of(
            new RouteConfig("Tuscaloosa", primary, new Location("Tuscaloosa", 33.2098, -87.5692)),
            new RouteConfig("Birmingham", primary, new Location("Birmingham", 33.5186, -86.8104)),
            new RouteConfig("Hoover", primary, new Location("Hoover", 33.4054, -86.8114)),
            new RouteConfig("Trussville", primary, new Location("Trussville", 33.6198, -86.6089))
    ));

    /**
     * Widget identifiers are persisted rather than card classes. Dynamic IDs
     * use ROUTE_n and WEATHER_LOCATION_n so newly added site data can be placed
     * on the dashboard without changing source code.
     */
    public final List<String> widgetTypes = new ArrayList<>(List.of(
            "WEATHER_PRIMARY", "ROUTE_0",
            "ROUTE_1", "ROUTE_2",
            "ROUTE_3", "ALERTS",
            "WEATHER_LOCATION_0", "WEATHER_LOCATION_2",
            "FORECAST_PRIMARY", "MEDIA",
            "WIND_PRIMARY", "STATUS"
    ));
}
