import com.wtm.alerts.SevereWeatherRefreshPolicy;
import com.wtm.alerts.WeatherAlertPolicy;
import com.wtm.model.WeatherAlert;

import java.time.Instant;
import java.util.List;

public final class WeatherAlertPolicySmokeTest {
    private static WeatherAlert alert(
            String event,
            String severity,
            String urgency,
            String headline
    ) {
        return new WeatherAlert(
                event,
                headline,
                severity,
                urgency,
                "Follow local safety guidance.",
                Instant.parse("2026-09-01T18:00:00Z"),
                List.of()
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        WeatherAlert tornado = alert(
                "Tornado Warning", "Extreme", "Immediate",
                "Tornado Warning issued for the operating area"
        );
        WeatherAlert watch = alert(
                "Severe Thunderstorm Watch", "Moderate", "Expected",
                "Conditions are favorable for severe thunderstorms"
        );
        WeatherAlert heat = alert(
                "Heat Advisory", "Moderate", "Expected",
                "Heat index values may reach dangerous levels"
        );

        require(WeatherAlertPolicy.level(tornado) == WeatherAlertPolicy.Level.CRITICAL,
                "tornado warning must be critical");
        require(WeatherAlertPolicy.qualifiesForAutomaticSevereMode(tornado),
                "tornado warning must activate automatic severe mode");
        require(WeatherAlertPolicy.level(watch) == WeatherAlertPolicy.Level.WATCH,
                "severe thunderstorm watch must retain watch presentation");
        require(WeatherAlertPolicy.qualifiesForAutomaticSevereMode(watch),
                "severe thunderstorm watch must activate rapid monitoring");
        require(WeatherAlertPolicy.level(heat) == WeatherAlertPolicy.Level.ADVISORY,
                "heat advisory must use advisory presentation");
        require(!WeatherAlertPolicy.qualifiesForAutomaticSevereMode(heat),
                "heat advisory must not activate severe mode");
        require(WeatherAlertPolicy.primary(List.of(heat, watch, tornado)).orElseThrow() == tornado,
                "highest-priority alert must be primary");
        require(WeatherAlertPolicy.briefText(heat, 80).length() <= 80,
                "brief alert wording must honor its maximum length");

        SevereWeatherRefreshPolicy.Cadence normal =
                SevereWeatherRefreshPolicy.cadence(false, 10, 2, 5);
        require(normal.weatherMinutes() == 10
                        && normal.alertMinutes() == 2
                        && normal.radarMinutes() == 5,
                "normal refresh cadence must preserve configured values");

        SevereWeatherRefreshPolicy.Cadence rapid =
                SevereWeatherRefreshPolicy.cadence(true, 10, 2, 5);
        require(rapid.weatherMinutes() == 1
                        && rapid.alertMinutes() == 1
                        && rapid.radarMinutes() == 1,
                "severe mode must switch weather, alerts, and radar to rapid cadence");

        System.out.println("WEATHER_ALERT_POLICY_SMOKE_OK");
    }
}
