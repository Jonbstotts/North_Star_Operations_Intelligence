package com.wtm.truck;

import java.time.LocalDateTime;

public record TruckTrackingStats(
        int active,
        int delayed,
        int weatherDelayed,
        int trafficDelayed,
        LocalDateTime nextArrival,
        String nextArrivalCarrier,
        String nextArrivalTracking
) {}
