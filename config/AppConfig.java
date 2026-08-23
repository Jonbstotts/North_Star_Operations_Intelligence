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

    /**
     * Allow active weather alerts to share/prioritize the top ticker.
     * Severe alerts can still override the normal ticker even when this is
     * disabled so critical operational weather remains visible.
     */
    public boolean showWeatherAlertsInTicker = true;


    /** Modular cards shown on the Operations Workspace home dashboard. */
    public final List<String> workspaceModules = new ArrayList<>(List.of(
            "WEATHER",
            "TRAFFIC_MAP",
            "UPCOMING_EVENTS",
            "TEAM_CELEBRATIONS",
            "OPERATIONS_SNAPSHOT"
    ));

    /**
     * Compact Information row placed between the primary workspace modules and
     * Operations Snapshot. Information Blocks is the single configuration
     * surface for item count, selected content, viewport size and movement.
     */
    public boolean workspaceInfoStripEnabled = true;
    public int workspaceInfoBlockCount = 4;

    /**
     * When more information choices are configured than fit in the compact
     * strip, automatically page through them without increasing dashboard
     * height. The interval is deliberately slow enough for warehouse viewing.
     */
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

    /** Operations Snapshot movement settings, independent from Information. */
    public int workspaceOpsVisibleCount = 8;
    public String workspaceOpsMovementMode = "STATIC";
    public int workspaceOpsScrollSeconds = 10;
    public int workspaceOpsTickerPixelsPerSecond = 28;

    /**
     * Dashboard layout proportions.
     *
     * These values are ratios (0.0–1.0) rather than pixel sizes so the saved
     * layout scales cleanly between laptops, desktop monitors and wall
     * displays.  Users can drag the dashboard dividers directly; the ratios
     * are saved automatically on mouse release.
     */
    public double dashboardPrimarySplitRatio = 0.70;
    public double dashboardLowerSplitRatio = 0.50;
    public double dashboardWeatherSplitRatio = 0.24;
    public double dashboardShowcaseSplitRatio = 0.74;
    public double dashboardEventsSplitRatio = 0.53;

    /**
     * Independent top-column module heights.
     *
     * Each value represents the fraction of the available primary-row height
     * occupied by that column's content.  This lets a user shorten Local
     * Weather without forcing the Main Showcase or right-side stack to shrink.
     * Freed space is intentionally left available beneath that column for
     * future dashboard modules.
     */
    public double dashboardWeatherHeightRatio = 1.00;
    public double dashboardShowcaseHeightRatio = 1.00;
    public double dashboardRightHeightRatio = 1.00;


    /**
     * Fully customizable snapping-grid dashboard layout.
     *
     * Each value is "column,row,columnSpan,rowSpan" on a 24 x 18 logical
     * grid. Grid coordinates are resolution independent so the saved layout
     * scales cleanly across window and monitor sizes.
     */
    public String dashboardWeatherGrid = "0,0,6,11";
    public String dashboardShowcaseGrid = "6,0,13,11";
    public String dashboardEventsGrid = "19,0,5,5";
    public String dashboardCelebrationsGrid = "19,5,5,6";
    public String dashboardInformationGrid = "0,11,24,3";
    public String dashboardOperationsGrid = "0,14,24,4";



    /** Configurable KPI cards for the Operations Snapshot module. */
    public final List<OperationsKpiConfig> operationsKpis = new ArrayList<>(List.of(
            new OperationsKpiConfig("lhy","LHY Performance",0,17500,"",true,true,"MANUAL"),
            new OperationsKpiConfig("lines","Lines Shipped",0,Double.NaN," lines",true,true,"MANUAL"),
            new OperationsKpiConfig("damages","Damages",0,0,"",false,true,"MANUAL"),
            new OperationsKpiConfig("floor_denials","Floor Denials",0,0,"",false,true,"MANUAL"),
            new OperationsKpiConfig("alerts","Active Alerts",0,0,"",false,true,"SYSTEM_ALERTS"),
            new OperationsKpiConfig("truck_active","Inbound Trucks",0,Double.NaN,"",true,false,"TRUCK_ACTIVE"),
            new OperationsKpiConfig("truck_delayed","Delayed Trucks",0,0,"",false,false,"TRUCK_DELAYED"),
            new OperationsKpiConfig("truck_weather","Weather Delays",0,0,"",false,false,"TRUCK_WEATHER_DELAYS"),
            new OperationsKpiConfig("truck_traffic","Traffic Delays",0,0,"",false,false,"TRUCK_TRAFFIC_DELAYS"),
            new OperationsKpiConfig("truck_eta","Next Truck ETA",0,Double.NaN,"",true,false,"TRUCK_NEXT_ARRIVAL")
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
    public String nwsUserAgent = "NorthStarOperations/1.2 (operations-display; contact=local-admin)";

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

    /** Twilio Account SID. Stored in encrypted credential store. */
    public String twilioAccountSid = "";

    /** Twilio Auth Token. Stored in encrypted credential store. */
    public String twilioAuthToken = "";

    /** SendGrid API key. Stored in encrypted credential store. */
    public String sendGridApiKey = "";


    // ---------------------------------------------------------------------
    // Truck Tracking / inbound transportation visibility.
    // ---------------------------------------------------------------------

    /** Carrier visibility on the main map; tracking continues when hidden. */
    public boolean showFedexTrucksOnMap = true;
    public boolean showStarhubTrucksOnMap = true;

    /** Delivered loads remain visible on the map for this many days. */
    public int truckDeliveredMapDays = 2;

    /** Current view includes recently closed shipments for this many days. */
    public int truckCurrentHistoryDays = 7;

    /** Closed shipments are archived automatically after this many days. */
    public int truckArchiveAfterDays = 7;

    /** 0 means never auto-delete archived history. */
    public int truckRetentionDays = 730;

    /** Provider refresh cadence. Manual/CSV records remain usable without APIs. */
    public int truckRefreshMinutes = 15;

    /** MANUAL or FEDEX_REST. */
    public String fedexTrackingMode = "MANUAL";

    /** MANUAL_CSV or ENTERPRISE_FEED. */
    public String penskeTrackingMode = "MANUAL_CSV";

    /** Optional customer-specific Penske endpoint when supplied by Penske. */
    public String penskeTrackingEndpoint = "";

    /** Protected provider credentials; encrypted by ApiCredentialService. */
    public String fedexClientId = "";
    public String fedexClientSecret = "";
    public String penskeApiToken = "";

    /** FedEx TEST uses sandbox; PRODUCTION uses production APIs. */
    public String fedexEnvironment = "PRODUCTION";

    /** In-house Trak-4 GPS remains independent of transportation carrier. */
    public boolean showTrak4OnMap = true;
    public String trak4Mode = "MANUAL";
    public String trak4ReportsUrl = "";
    public String trak4AuthHeader = "Authorization";
    public String trak4ApiKey = "";
    public int trak4RefreshMinutes = 5;

    /** Optional lightweight decorative overlays tied to themes. */
    public boolean themeOverlayEffects = true;

    /** LOW, MEDIUM, or HIGH particle density. */
    public String overlayIntensity = "LOW";


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

    /**
     * Percentage of the main dashboard width reserved for the map.
     * The remaining width is used by the information-card grid.
     */

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
