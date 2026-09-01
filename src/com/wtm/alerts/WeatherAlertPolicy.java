package com.wtm.alerts;

import com.wtm.model.WeatherAlert;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Domain policy for interpreting active weather alerts.
 *
 * Provider adapters supply WeatherAlert records; this class is the single owner
 * of operational priority, presentation level, severe-mode qualification, and
 * concise alert wording. Swing presentation maps Level to theme colors without
 * reimplementing weather semantics.
 */
public final class WeatherAlertPolicy {
    public enum Level {
        INFO,
        ADVISORY,
        WATCH,
        WARNING,
        CRITICAL
    }

    private WeatherAlertPolicy() {}

    public static Optional<WeatherAlert> primary(List<WeatherAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) return Optional.empty();
        return alerts.stream()
                .filter(java.util.Objects::nonNull)
                .max(Comparator
                        .comparingInt(WeatherAlertPolicy::priority)
                        .thenComparing(
                                WeatherAlert::expires,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ));
    }

    public static int priority(WeatherAlert alert) {
        if (alert == null) return 0;

        String text = normalizedText(alert);
        int rank;
        if (text.contains("tornado emergency")) rank = 1000;
        else if (text.contains("tornado warning")) rank = 950;
        else if (text.contains("extreme wind warning")) rank = 925;
        else if (text.contains("severe thunderstorm warning")) rank = 900;
        else if (text.contains("flash flood warning")) rank = 875;
        else if (text.contains("tornado watch")) rank = 825;
        else if (text.contains("severe thunderstorm watch")) rank = 800;
        else if (text.contains("excessive heat warning")
                || text.contains("heat warning")) rank = 775;
        else if (text.contains("heat advisory")) rank = 725;
        else if (text.contains("flood warning")) rank = 700;
        else if (text.contains("winter storm warning")) rank = 675;
        else if (text.contains("warning")) rank = 650;
        else if (text.contains("wind advisory")) rank = 625;
        else if (text.contains("dense fog advisory")) rank = 575;
        else if (text.contains("advisory")) rank = 525;
        else if (text.contains("watch")) rank = 500;
        else rank = 400;

        String severity = lower(alert.severity());
        if ("extreme".equals(severity)) rank += 90;
        else if ("severe".equals(severity)) rank += 60;
        else if ("moderate".equals(severity)) rank += 30;

        String urgency = lower(alert.urgency());
        if ("immediate".equals(urgency)) rank += 30;
        else if ("expected".equals(urgency)) rank += 15;

        return rank;
    }

    public static Level level(WeatherAlert alert) {
        if (alert == null) return Level.INFO;
        String text = normalizedText(alert);
        String severity = lower(alert.severity());

        if (text.contains("tornado emergency")
                || "extreme".equals(severity)) return Level.CRITICAL;
        if (text.contains("warning")
                || "severe".equals(severity)) return Level.WARNING;
        if (text.contains("watch")) return Level.WATCH;
        if (text.contains("advisory")
                || "moderate".equals(severity)) return Level.ADVISORY;
        return Level.INFO;
    }

    /**
     * Qualifying alerts use the same definition everywhere that can activate
     * rapid severe-weather operation. A watch may therefore activate rapid
     * monitoring while retaining WATCH presentation rather than being colored
     * as a warning.
     */
    public static boolean qualifiesForAutomaticSevereMode(WeatherAlert alert) {
        if (alert == null) return false;
        String text = normalizedText(alert);
        String severity = lower(alert.severity());
        return "extreme".equals(severity)
                || "severe".equals(severity)
                || text.contains("tornado emergency")
                || text.contains("tornado warning")
                || text.contains("tornado watch")
                || text.contains("severe thunderstorm warning")
                || text.contains("severe thunderstorm watch")
                || text.contains("flash flood warning")
                || text.contains("extreme wind warning");
    }

    public static boolean hasAutomaticSevereAlert(List<WeatherAlert> alerts) {
        return alerts != null && alerts.stream()
                .anyMatch(WeatherAlertPolicy::qualifiesForAutomaticSevereMode);
    }

    public static String shortEventName(WeatherAlert alert) {
        return shortEventName(alert == null ? "" : alert.event());
    }

    public static String shortEventName(String event) {
        String value = safe(event).trim();
        if (value.isBlank()) return "Weather Alert";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("severe thunderstorm warning")) return "Severe T-Storm Warning";
        if (lower.contains("severe thunderstorm watch")) return "Severe T-Storm Watch";
        if (lower.contains("excessive heat warning")) return "Excessive Heat Warning";
        if (lower.contains("tornado emergency")) return "Tornado Emergency";
        return abbreviate(value, 28);
    }

    public static String briefText(WeatherAlert alert, int maximumCharacters) {
        if (alert == null) return "Weather alert active";
        int max = Math.max(24, maximumCharacters);
        String event = safe(alert.event()).trim();
        String headline = safe(alert.headline()).trim();

        String value;
        if (headline.isBlank()) {
            value = event.isBlank() ? "Weather alert active" : event;
        } else if (!event.isBlank()
                && headline.toLowerCase(Locale.ROOT)
                        .contains(event.toLowerCase(Locale.ROOT))) {
            value = headline;
        } else {
            value = event.isBlank() ? headline : event + " • " + headline;
        }
        return abbreviate(value, max);
    }

    private static String normalizedText(WeatherAlert alert) {
        return (safe(alert.event()) + " " + safe(alert.headline()))
                .toLowerCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String abbreviate(String value, int maximumCharacters) {
        String text = safe(value).trim();
        if (text.length() <= maximumCharacters) return text;
        if (maximumCharacters <= 1) return "…";
        return text.substring(0, maximumCharacters - 1).trim() + "…";
    }
}
