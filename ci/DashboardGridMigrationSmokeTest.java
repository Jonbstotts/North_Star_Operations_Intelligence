import com.wtm.ui.DashboardGridPanel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DashboardGridMigrationSmokeTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AtomicInteger persisted = new AtomicInteger();
        Map<String,String> current = new LinkedHashMap<>();
        current.put("_gridVersion", "2");
        current.put("WEATHER", "0,0,6,6");
        current.put("SHOWCASE", "6,0,12,6");
        new DashboardGridPanel(current, persisted::incrementAndGet);
        require(persisted.get() == 0, "current layout must not remigrate");
        require("0,0,6,6".equals(current.get("WEATHER")),
                "current layout geometry changed unexpectedly");

        persisted.set(0);
        Map<String,String> legacy = new LinkedHashMap<>();
        legacy.put("WEATHER", "0,0,3,6");
        legacy.put("SHOWCASE", "3,0,6,6");
        legacy.put("UPCOMING_EVENTS", "9,0,3,3");
        new DashboardGridPanel(legacy, persisted::incrementAndGet);
        require(persisted.get() == 1, "legacy migration must persist exactly once");
        require("2".equals(legacy.get("_gridVersion")),
                "legacy migration must write the current version marker");
        require("0,0,6,6".equals(legacy.get("WEATHER")),
                "legacy weather tile was not doubled");
        require("6,0,12,6".equals(legacy.get("SHOWCASE")),
                "legacy showcase tile was not doubled");
        require("18,0,6,3".equals(legacy.get("UPCOMING_EVENTS")),
                "legacy right-side tile was not doubled");

        persisted.set(0);
        Map<String,String> unversionedCurrent = new LinkedHashMap<>();
        unversionedCurrent.put("WEATHER", "0,0,6,6");
        unversionedCurrent.put("SHOWCASE", "6,0,12,6");
        unversionedCurrent.put("UPCOMING_EVENTS", "18,0,6,3");
        new DashboardGridPanel(unversionedCurrent, persisted::incrementAndGet);
        require(persisted.get() == 1,
                "unversioned current layout must gain a version marker once");
        require("0,0,6,6".equals(unversionedCurrent.get("WEATHER")),
                "mixed/current grid detection must not double left-side tiles");
        require("6,0,12,6".equals(unversionedCurrent.get("SHOWCASE")),
                "mixed/current grid detection corrupted showcase geometry");
        require("18,0,6,3".equals(unversionedCurrent.get("UPCOMING_EVENTS")),
                "current right-side geometry changed unexpectedly");

        System.out.println("DASHBOARD_GRID_MIGRATION_SMOKE_OK");
    }
}
