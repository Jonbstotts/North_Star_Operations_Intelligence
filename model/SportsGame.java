package com.wtm.model;

import java.time.Instant;

/** Normalized sports event returned by a SportsProvider implementation. */
public record SportsGame(
        String league,
        String homeTeamId,
        String awayTeamId,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        String status,
        String progress,
        Instant startTime,
        String homeBadgeUrl,
        String awayBadgeUrl,
        boolean live,
        boolean finished,
        Instant updatedAt,
        String dataMode
) {}
