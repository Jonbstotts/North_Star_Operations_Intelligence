package com.wtm.employee;

import java.time.LocalDate;
import java.util.UUID;

/** Historical employee performance measurement. */
public record PerformanceRecord(
        String id,
        String employeeId,
        LocalDate date,
        String metric,
        double value,
        double target,
        String unit,
        String source,
        String notes
) {
    public PerformanceRecord {
        id=clean(id).isBlank()?UUID.randomUUID().toString():clean(id);
        employeeId=clean(employeeId);
        metric=clean(metric);
        unit=clean(unit);
        source=clean(source);
        notes=clean(notes);
    }

    public boolean targetMet(boolean higherIsBetter){
        if(Double.isNaN(target))return true;
        return higherIsBetter?value>=target:value<=target;
    }

    private static String clean(String value){
        return value==null?"":value.trim();
    }
}
