package com.wtm.model;

import java.time.Instant;
import java.util.List;

/** Immutable weather data returned by the weather provider. */
public record WeatherSnapshot(
        String locationName,
        double temperatureF,
        double apparentTemperatureF,
        double highF,
        double lowF,
        double precipitationProbability,
        double humidityPercent,
        double windMph,
        double gustMph,
        int weatherCode,
        String condition,
        Instant updatedAt,
        List<HourlyPoint> hourly,
        List<DailyPoint> daily) {

    public record HourlyPoint(
            String time,
            double temperatureF,
            double precipitationProbability,
            int weatherCode
    ) {}

    public record DailyPoint(
            String date,
            double highF,
            double lowF,
            double precipitationProbability,
            int weatherCode
    ) {}
}
