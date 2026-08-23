package com.wtm.employee;

import java.time.*;
import java.util.UUID;

/** Attendance event including manual and telephone call-ins. */
public record AttendanceRecord(
        String id,
        String employeeId,
        LocalDate date,
        LocalTime time,
        String type,
        String source,
        String status,
        String notes,
        String callerPhone,
        String externalReference
) {
    public AttendanceRecord {
        id=clean(id).isBlank()?UUID.randomUUID().toString():clean(id);
        employeeId=clean(employeeId);
        type=clean(type).toUpperCase();
        source=clean(source).toUpperCase();
        status=clean(status).isBlank()?"RECORDED":clean(status).toUpperCase();
        notes=clean(notes);
        callerPhone=clean(callerPhone);
        externalReference=clean(externalReference);
    }

    public boolean unavailableForWholeDay(){
        return "CALL_OUT".equals(type)||"ABSENT".equals(type);
    }

    private static String clean(String value){
        return value==null?"":value.trim();
    }
}
