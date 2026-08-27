package com.wtm.model;

/**
 * Provider-neutral team-search result.
 *
 * Keeping the search result independent from TheSportsDB allows another sports
 * provider to implement the same Find Team workflow later.
 */
public record TeamSearchResult(
        String teamId,
        String teamName,
        String leagueId,
        String leagueName,
        String sport,
        String country,
        String badgeUrl,
        String provider
) {
    @Override
    public String toString(){
        String league=(leagueName==null||leagueName.isBlank())?"Unknown league":leagueName;
        String type=(sport==null||sport.isBlank())?"Sport":sport;
        return teamName+" — "+league+" — "+type;
    }
}
