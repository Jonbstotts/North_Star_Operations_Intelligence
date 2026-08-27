package com.wtm.model;

import java.time.LocalDate;
import java.util.List;

/** A connected group of operations-calendar events rendered as one announcement. */
public record OperationAnnouncement(
        List<OperationEvent> events,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate announcementStart,
        LocalDate normalOperationsResume
) {}
