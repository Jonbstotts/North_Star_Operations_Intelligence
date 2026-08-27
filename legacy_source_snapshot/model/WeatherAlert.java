package com.wtm.model;

import java.time.Instant;
import java.util.List;

/** Active NWS alert plus optional polygon geometry for map rendering. */
public record WeatherAlert(
        String event,
        String headline,
        String severity,
        String urgency,
        String instruction,
        Instant expires,
        List<List<double[]>> polygons) {}
