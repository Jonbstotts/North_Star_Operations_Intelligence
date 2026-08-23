package com.wtm.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One site operating-calendar entry.
 *
 * startDate/endDate may be equal for a one-day event. Full Closure ignores
 * start/end times; Limited Service and Modified Hours require both.
 * leadDays <= 0 means use the site's default announcement lead time.
 */
public record OperationEvent(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        OperationType type,
        LocalTime startTime,
        LocalTime endTime,
        int leadDays,
        boolean enabled
) {
    public OperationEvent{
        if(endDate!=null && startDate!=null && endDate.isBefore(startDate))
            throw new IllegalArgumentException("Operation end date cannot be before start date.");
    }

    public boolean covers(LocalDate date){
        return enabled && date!=null && startDate!=null && endDate!=null
                && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean touches(OperationEvent other){
        if(other==null||startDate==null||endDate==null
                ||other.startDate()==null||other.endDate()==null)
            return false;

        return !other.startDate().isAfter(endDate.plusDays(1))
                && !startDate.isAfter(other.endDate().plusDays(1));
    }
}
