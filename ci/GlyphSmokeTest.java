import com.wtm.ui.NorthStarDashboardGlyphs;

import javax.swing.Icon;

public final class GlyphSmokeTest {
    private static final String[] KEYS = {
            "new_year", "mlk", "presidents_day", "memorial_day", "independence_day",
            "labor_day", "columbus_day", "veterans_day", "thanksgiving",
            "christmas", "halloween", "valentines_day", "st_patricks_day", "easter",
            "mothers_day", "fathers_day", "juneteenth", "fall_season",
            "birthday", "work_anniversary", "employee_of_month", "team_success", "promotion",
            "graduation", "welcome", "congratulations", "new_hire",
            "safety_milestone", "milestone", "project_complete", "thank_you", "holiday_season",
            "seasonal", "weather_alert", "special_event", "lets_celebrate"
    };

    private GlyphSmokeTest() {}

    public static void main(String[] args) {
        for (String key : KEYS) {
            Icon icon = NorthStarDashboardGlyphs.icon(key, 48);
            if (icon == null) {
                throw new IllegalStateException("NorthStar dashboard glyph failed to load: " + key);
            }
            if (icon.getIconWidth() != 48 || icon.getIconHeight() != 48) {
                throw new IllegalStateException(
                        "Unexpected NorthStar dashboard glyph size for " + key
                                + ": " + icon.getIconWidth() + "x" + icon.getIconHeight());
            }
        }
        System.out.println("NorthStar dashboard glyph smoke test passed for all " + KEYS.length + " company glyphs.");
    }
}
