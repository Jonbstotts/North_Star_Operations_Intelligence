import com.wtm.ui.NorthStarDashboardGlyphs;

import javax.swing.Icon;

public final class GlyphSmokeTest {
    private static final String[] KEYS = {
            "labor_day", "thanksgiving", "christmas", "halloween",
            "birthday", "work_anniversary", "employee_of_month", "promotion",
            "safety_milestone", "special_event", "lets_celebrate"
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
        System.out.println("NorthStar dashboard glyph smoke test passed for " + KEYS.length + " company glyphs.");
    }
}
