package com.wtm.modular.ui;

import com.wtm.ui.NorthStarDashboardGlyphs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive high-resolution glyph normalization for dashboard event and
 * recognition rows.
 *
 * <p>This class owns no global AWT listener, timer, delayed pass, or workspace
 * lifecycle. WorkspaceLifecycleV3 calls {@link #apply(Window)} only at bounded
 * structural boundaries after the canonical Dashboard tree exists.</p>
 */
public final class DashboardGlyphArtGuard {
    private DashboardGlyphArtGuard() {}

    public static void apply(Window window) {
        if (!(window instanceof Container root)) return;
        Container events = findCard(root, "UPCOMING EVENTS");
        if (events != null) scanRows(events, false);
        Container celebrations = findCard(root, "TEAM CELEBRATIONS");
        if (celebrations != null) scanRows(celebrations, true);
    }

    private static Container findCard(Container root, String heading) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && heading.equalsIgnoreCase(text(label))) return root;
        }
        for (Component child : root.getComponents()) {
            if (child instanceof Container c) {
                Container found = findCard(c, heading);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void scanRows(Container container, boolean celebration) {
        Container tile = directIconTile(container);
        if (tile != null) {
            String combined = combinedText(container);
            String key = celebration ? celebrationKey(combined) : eventKey(combined);
            JLabel iconLabel = firstIconLabel(tile);
            int size = celebration ? 48 : 40;
            Icon icon = key == null ? null : NorthStarDashboardGlyphs.icon(key, size);
            if (icon != null && iconLabel != null) {
                iconLabel.setIcon(icon);
                iconLabel.setText(null);
                iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container c) scanRows(c, celebration);
        }
    }

    private static Container directIconTile(Container row) {
        for (Component child : row.getComponents()) {
            if (!(child instanceof Container c)) continue;
            Dimension d = c.getPreferredSize();
            if (d == null || d.width < 44 || d.height < 44 || d.width > 72 || d.height > 72) continue;
            if (firstIconLabel(c) != null) return c;
        }
        return null;
    }

    private static JLabel firstIconLabel(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getIcon() != null) return label;
            if (child instanceof Container c) {
                JLabel nested = firstIconLabel(c);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static String combinedText(Container root) {
        List<String> text = new ArrayList<>();
        collectText(root, text);
        return String.join(" ", text).toLowerCase(Locale.ROOT);
    }

    private static void collectText(Container root, List<String> out) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) {
                String value = text(label);
                if (!value.isBlank()) out.add(value);
            }
            if (child instanceof Container c) collectText(c, out);
        }
    }

    private static String text(JLabel label) {
        return label == null || label.getText() == null ? "" : label.getText().trim();
    }

    private static String celebrationKey(String text) {
        if (text.contains("employee of the month")) return "employee_of_month";
        if (text.contains("anniversary")) return "work_anniversary";
        if (text.contains("birthday")) return "birthday";
        if (text.contains("promotion")) return "promotion";
        if (text.contains("graduation")) return "graduation";
        if (text.contains("new hire")) return "new_hire";
        if (text.contains("welcome")) return "welcome";
        if (text.contains("congrat")) return "congratulations";
        if (text.contains("team success")) return "team_success";
        if (text.contains("thank you")) return "thank_you";
        if (text.contains("safety")) return "safety_milestone";
        if (text.contains("milestone")) return "milestone";
        if (text.contains("project complete") || text.contains("project completion")) return "project_complete";
        return "lets_celebrate";
    }

    private static String eventKey(String text) {
        if (text.contains("new year")) return "new_year";
        if (text.contains("martin luther king") || text.contains("mlk")) return "mlk";
        if (text.contains("president")) return "presidents_day";
        if (text.contains("memorial")) return "memorial_day";
        if (text.contains("independence") || text.contains("fourth of july") || text.contains("4th of july")) return "independence_day";
        if (text.contains("labor day")) return "labor_day";
        if (text.contains("columbus") || text.contains("indigenous peoples")) return "columbus_day";
        if (text.contains("veteran")) return "veterans_day";
        if (text.contains("thanksgiving")) return "thanksgiving";
        if (text.contains("christmas")) return "christmas";
        if (text.contains("halloween")) return "halloween";
        if (text.contains("valentine")) return "valentines_day";
        if (text.contains("st. patrick") || text.contains("st patrick")) return "st_patricks_day";
        if (text.contains("easter")) return "easter";
        if (text.contains("mother")) return "mothers_day";
        if (text.contains("father")) return "fathers_day";
        if (text.contains("juneteenth")) return "juneteenth";
        if (text.contains("safety")) return "safety_milestone";
        if (text.contains("project complete") || text.contains("project completion")) return "project_complete";
        if (text.contains("milestone")) return "milestone";
        if (text.contains("weather alert") || text.contains("severe weather") || text.contains("storm alert")) return "weather_alert";
        if (text.contains("holiday season")) return "holiday_season";
        if (text.contains("seasonal")) return "seasonal";
        if (text.contains("promotion")) return "promotion";
        if (text.contains("graduation")) return "graduation";
        if (text.contains("new hire")) return "new_hire";
        if (text.contains("welcome")) return "welcome";
        if (text.contains("congrat")) return "congratulations";
        if (text.contains("team success")) return "team_success";
        if (text.contains("thank you")) return "thank_you";
        if (text.contains("celebrat")) return "lets_celebrate";
        return "special_event";
    }
}
