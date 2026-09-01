package com.wtm.alerts;

/**
 * Owns provider refresh cadence while severe-weather monitoring is active.
 * Traffic and sports cadence are intentionally outside this policy because the
 * manual/automatic severe mode applies only to weather, NWS alerts, and radar.
 */
public final class SevereWeatherRefreshPolicy {
    public record Cadence(int weatherMinutes, int alertMinutes, int radarMinutes) {}

    private static final int RAPID_MINUTES = 1;

    private SevereWeatherRefreshPolicy() {}

    public static Cadence cadence(
            boolean rapid,
            int normalWeatherMinutes,
            int normalAlertMinutes,
            int normalRadarMinutes
    ) {
        if (rapid) {
            return new Cadence(RAPID_MINUTES, RAPID_MINUTES, RAPID_MINUTES);
        }
        return new Cadence(
                Math.max(1, normalWeatherMinutes),
                Math.max(1, normalAlertMinutes),
                Math.max(1, normalRadarMinutes)
        );
    }
}
