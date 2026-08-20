package com.wtm.employee;

import java.time.LocalDate;
import java.util.UUID;

/** Training, certification, equipment or work-area qualification. */
public record TrainingRecord(
        String id,
        String employeeId,
        String category,
        String qualification,
        LocalDate completedDate,
        LocalDate expirationDate,
        String trainer,
        String status,
        String notes
) {
    public TrainingRecord {
        id=clean(id).isBlank()?UUID.randomUUID().toString():clean(id);
        employeeId=clean(employeeId);
        category=clean(category);
        qualification=clean(qualification);
        trainer=clean(trainer);
        status=clean(status).isBlank()?"ACTIVE":clean(status).toUpperCase();
        notes=clean(notes);
    }

    public boolean currentlyQualified(LocalDate today){
        if(!"ACTIVE".equalsIgnoreCase(status))return false;
        return expirationDate==null||!expirationDate.isBefore(today);
    }

    private static String clean(String value){
        return value==null?"":value.trim();
    }
}
