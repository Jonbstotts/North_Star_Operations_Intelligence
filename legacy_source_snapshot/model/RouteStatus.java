package com.wtm.model;

import java.time.Instant;

/** Current route travel time and traffic delay. */
public record RouteStatus(
        String routeName,
        int travelMinutes,
        int noTrafficMinutes,
        int delayMinutes,
        String status,
        Instant updatedAt) {}
