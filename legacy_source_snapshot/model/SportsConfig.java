package com.wtm.model;

/**
 * One sports selection configured by a site administrator.
 *
 * TheSportsDB uses stable numeric team/league IDs. Storing those IDs keeps
 * runtime requests efficient and avoids ambiguous text searches.
 */
public record SportsConfig(
        String name,
        String sport,
        String leagueId,
        String teamId,
        String teamName,
        boolean showLogos
) {}
