import com.wtm.config.AppConfig;
import com.wtm.config.ConfigService;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigRoundTripSmokeTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String originalHome = System.getProperty("user.home");
        Path temporaryHome = Files.createTempDirectory("northstar-config-roundtrip-");
        try {
            System.setProperty("user.home", temporaryHome.toString());

            AppConfig source = new AppConfig();
            source.themeId = "FLATLAF_LIGHT";
            source.automaticHolidayThemes = false;
            source.workspaceInfoStripEnabled = false;
            source.workspaceInfoBlockCount = 8;
            source.basemapProvider = "OPENSTREETMAP";
            source.workspaceInfoMovementMode = "TICKER";
            source.workspaceInfoScrollSeconds = 17;
            source.workspaceInfoTickerPixelsPerSecond = 41;
            source.workspaceKpiVisibleCount = 5;
            source.workspaceKpiMovementMode = "PAGED";
            source.workspaceKpiScrollSeconds = 19;
            source.workspaceKpiTickerPixelsPerSecond = 37;
            source.liveSevereWeatherMode = true;
            source.automaticSevereWeatherMode = false;
            source.autoDisableSevereWeatherMode = false;
            source.workspaceDashboardLayout.clear();
            source.workspaceDashboardLayout.put("_gridVersion", "2");
            source.workspaceDashboardLayout.put("WEATHER", "1,2,7,4");
            source.workspaceDashboardLayout.put("CUSTOM_TEST_TILE", "8,3,5,2");

            ConfigService.save(source);

            Path configFile = ConfigService.appDataDir().resolve("config.properties");
            String canonical = Files.readString(configFile);
            require(canonical.contains("workspace.infoStrip.enabled=true"),
                    "core Information row was not canonicalized active on save");

            // Simulate an older installation where the retired checkbox was off.
            Files.writeString(
                    configFile,
                    canonical.replace(
                            "workspace.infoStrip.enabled=true",
                            "workspace.infoStrip.enabled=false"));

            AppConfig loaded = ConfigService.load();

            require("FLATLAF_LIGHT".equals(loaded.themeId),
                    "selected FlatLaf theme did not round-trip");
            require(!loaded.automaticHolidayThemes,
                    "automatic holiday-theme choice did not round-trip");
            require(loaded.workspaceInfoStripEnabled,
                    "legacy disabled Information row was not migrated active");
            require(loaded.workspaceInfoBlockCount == 8,
                    "information visible-count did not round-trip");
            require("OPENSTREETMAP".equals(loaded.basemapProvider),
                    "basemap provider did not round-trip");
            require("TICKER".equals(loaded.workspaceInfoMovementMode),
                    "information movement mode did not round-trip");
            require(loaded.workspaceInfoScrollSeconds == 17,
                    "information page interval did not round-trip");
            require(loaded.workspaceInfoTickerPixelsPerSecond == 41,
                    "information ticker speed did not round-trip");
            require(loaded.workspaceKpiVisibleCount == 5,
                    "KPI visible-count did not round-trip");
            require("PAGED".equals(loaded.workspaceKpiMovementMode),
                    "KPI movement mode did not round-trip");
            require(loaded.workspaceKpiScrollSeconds == 19,
                    "KPI page interval did not round-trip");
            require(loaded.workspaceKpiTickerPixelsPerSecond == 37,
                    "KPI ticker speed did not round-trip");
            require(loaded.liveSevereWeatherMode,
                    "manual severe-weather mode did not round-trip");
            require(!loaded.automaticSevereWeatherMode,
                    "automatic severe-weather mode did not round-trip");
            require(!loaded.autoDisableSevereWeatherMode,
                    "automatic severe reset choice did not round-trip");
            require("2".equals(loaded.workspaceDashboardLayout.get("_gridVersion")),
                    "dashboard grid version metadata did not round-trip");
            require("1,2,7,4".equals(loaded.workspaceDashboardLayout.get("WEATHER")),
                    "dashboard geometry did not round-trip");
            require("8,3,5,2".equals(
                            loaded.workspaceDashboardLayout.get("CUSTOM_TEST_TILE")),
                    "dynamic dashboard tile geometry did not round-trip");
            require(loaded.workspaceDashboardLayout.size() == 3,
                    "persisted layout must replace defaults instead of mixing maps");

            System.out.println("CONFIG_ROUND_TRIP_SMOKE_OK");
        } finally {
            System.setProperty("user.home", originalHome);
            try (var paths = Files.walk(temporaryHome)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); }
                            catch (Exception ignored) {}
                        });
            }
        }
    }
}
