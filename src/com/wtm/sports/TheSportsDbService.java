package com.wtm.sports;

import com.wtm.model.SportsConfig;
import com.wtm.model.SportsGame;
import com.wtm.model.TeamSearchResult;
import com.wtm.net.HttpService;
import com.wtm.util.MiniJson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TheSportsDB adapter used by configurable Sports Schedule dashboard blocks.
 *
 * The warehouse display is primarily viewed during the work week, so sports
 * blocks intentionally focus on upcoming scheduled games instead of live
 * scores. Team search/artwork can still use the configured provider tier.
 */
public final class TheSportsDbService {
    private final HttpService http;
    private final Map<String,String> badgeCache=new ConcurrentHashMap<>();

    public TheSportsDbService(HttpService http){ this.http=http; }

    /**
     * Searches the configured sports provider for teams.
     *
     * TheSportsDB v2 provides general text team search for premium subscribers.
     * The current free v1 search endpoint is provider-restricted; it is still
     * attempted so the UI can accurately report whether the current key can
     * perform the requested search.
     */
    public List<TeamSearchResult> searchTeams(
            String query,
            String configuredKey,
            boolean premium
    ) throws Exception {
        String q=query==null?"":query.trim();
        if(q.isBlank()) return List.of();

        String key=(configuredKey==null||configuredKey.isBlank())?"123":configuredKey.trim();

        if(premium && !key.equals("123")){
            String url="https://www.thesportsdb.com/api/v2/json/search/team/"+encPath(q);
            String json=http.getTextWithHeader(url,"X-API-KEY",key);
            List<TeamSearchResult> results=parseTeamSearchResults(json,"TheSportsDB v2");
            if(!results.isEmpty()) return results;
        }

        String json=v1(key,"searchteams.php?t="+enc(q));
        return parseTeamSearchResults(json,"TheSportsDB v1");
    }

    private List<TeamSearchResult> parseTeamSearchResults(String json,String provider){
        Object parsed=MiniJson.parse(json);
        if(!(parsed instanceof Map<?,?>)) return List.of();

        Map<String,Object> root=MiniJson.obj(parsed);
        Object raw=null;

        // v1 commonly uses "teams"; v2 response naming can vary by method/version.
        for(String k:List.of("teams","team","data","results")){
            Object candidate=root.get(k);
            if(candidate instanceof List<?>){
                raw=candidate;
                break;
            }
            if(candidate instanceof Map<?,?>){
                raw=List.of(candidate);
                break;
            }
        }

        if(raw==null && root.containsKey("idTeam")){
            raw=List.of(root);
        }
        if(!(raw instanceof List<?> list)) return List.of();

        List<TeamSearchResult> out=new ArrayList<>();
        Set<String> seen=new HashSet<>();

        for(Object item:list){
            if(!(item instanceof Map<?,?>)) continue;
            Map<String,Object> team=MiniJson.obj(item);

            String id=first(team,"idTeam","id");
            String name=first(team,"strTeam","name","teamName");
            if(id.isBlank() || name.isBlank()) continue;
            if(!seen.add(id)) continue;

            out.add(new TeamSearchResult(
                    id,
                    name,
                    first(team,"idLeague","leagueId"),
                    first(team,"strLeague","leagueName","league"),
                    first(team,"strSport","sport"),
                    first(team,"strCountry","country"),
                    first(team,"strBadge","strTeamBadge","strLogo","badge"),
                    provider
            ));
        }

        return out;
    }

    private static String encPath(String value){
        return enc(value).replace("+","%20");
    }

    /**
     * Returns the next scheduled events for a configured team.
     *
     * The provider's eventsnext endpoint normally returns several upcoming
     * games. Results are sorted chronologically and limited for dashboard use.
     */
    public List<SportsGame> fetchUpcoming(
            SportsConfig cfg,
            String configuredKey,
            int limit
    ) throws Exception {
        String key=(configuredKey==null||configuredKey.isBlank())
                ?"123":configuredKey.trim();

        List<SportsGame> events=parseEventList(
                v1(key,"eventsnext.php?id="+enc(cfg.teamId())),
                "Upcoming schedule"
        );

        Instant now=Instant.now().minus(Duration.ofHours(6));

        events=events.stream()
                .filter(g->!g.finished())
                .filter(g->g.startTime()==null || !g.startTime().isBefore(now))
                .sorted(Comparator.comparing(
                        SportsGame::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .limit(Math.max(1,limit))
                .map(g->withBadges(g,key))
                .toList();

        return events;
    }

    /**
     * Retained for source compatibility with older integrations. The first
     * upcoming event is returned; live/recent results are no longer preferred.
     */
    public SportsGame fetch(
            SportsConfig cfg,
            String configuredKey,
            boolean premium
    ) throws Exception {
        List<SportsGame> upcoming=fetchUpcoming(cfg,configuredKey,1);
        return upcoming.isEmpty()?unavailable(cfg,false):upcoming.get(0);
    }

    private String v1(String key,String endpoint) throws Exception {
        return http.getText("https://www.thesportsdb.com/api/v1/json/"+enc(key)+"/"+endpoint);
    }

    private List<SportsGame> parseEventList(String json,String mode){
        Object parsed=MiniJson.parse(json);
        if(!(parsed instanceof Map<?,?>)) return List.of();
        Map<String,Object> root=MiniJson.obj(parsed);
        Object raw=null;
        for(String key:List.of("events","event","livescore","livescores")){
            if(root.get(key) instanceof List<?>){ raw=root.get(key); break; }
        }
        if(raw==null) return List.of();

        List<SportsGame> out=new ArrayList<>();
        for(Object item:MiniJson.arr(raw)){
            if(!(item instanceof Map<?,?>)) continue;
            Map<String,Object> e=MiniJson.obj(item);
            out.add(parseEvent(e,mode));
        }
        return out;
    }

    private SportsGame parseEvent(Map<String,Object> e,String mode){
        String status=first(e,"strStatus","strProgress");
        String progress=first(e,"strProgress","strStatus");
        boolean live=isLiveStatus(status,progress);
        boolean finished=isFinishedStatus(status,progress);
        return new SportsGame(
                val(e,"strLeague"), val(e,"idHomeTeam"), val(e,"idAwayTeam"),
                val(e,"strHomeTeam"), val(e,"strAwayTeam"),
                score(e.get("intHomeScore")), score(e.get("intAwayScore")),
                status, progress, parseTime(e),
                first(e,"strHomeTeamBadge","strHomeTeamLogo","strHomeTeamBadgeUrl"),
                first(e,"strAwayTeamBadge","strAwayTeamLogo","strAwayTeamBadgeUrl"),
                live, finished, Instant.now(), mode);
    }

    private SportsGame withBadges(SportsGame game,String key){
        String home=game.homeBadgeUrl();
        String away=game.awayBadgeUrl();
        try{
            if(home==null||home.isBlank()) home=badgeForTeamId(game.homeTeamId(),key);
            if(away==null||away.isBlank()) away=badgeForTeamId(game.awayTeamId(),key);
        }catch(Exception ignored){}
        return new SportsGame(game.league(),game.homeTeamId(),game.awayTeamId(),
                game.homeTeam(),game.awayTeam(),game.homeScore(),game.awayScore(),
                game.status(),game.progress(),game.startTime(),home,away,game.live(),
                game.finished(),game.updatedAt(),game.dataMode());
    }

    /** Numeric team lookup is available on the v1 API and avoids ambiguous name searches. */
    private String badgeForTeamId(String teamId,String key) throws Exception {
        if(teamId==null||teamId.isBlank()) return "";
        String cacheKey="id:"+teamId;
        String cached=badgeCache.get(cacheKey);
        if(cached!=null) return cached;
        String json=v1(key,"lookupteam.php?id="+enc(teamId));
        Object parsed=MiniJson.parse(json);
        if(!(parsed instanceof Map<?,?>)) return "";
        Object teams=MiniJson.obj(parsed).get("teams");
        if(!(teams instanceof List<?> list)||list.isEmpty()) return "";
        Map<String,Object> team=MiniJson.obj(list.get(0));
        String badge=first(team,"strBadge","strTeamBadge","strLogo");
        badgeCache.put(cacheKey,badge);
        String name=val(team,"strTeam");
        if(!name.isBlank()) badgeCache.put(name.toLowerCase(),badge);
        return badge;
    }

    public String configuredTeamBadge(SportsConfig cfg,String configuredKey) throws Exception {
        String key=(configuredKey==null||configuredKey.isBlank())?"123":configuredKey.trim();
        return badgeForTeamId(cfg.teamId(),key);
    }

    private SportsGame unavailable(SportsConfig cfg,boolean premium){
        return new SportsGame("",cfg.teamId(),"",cfg.teamName(),"",-1,-1,
                "UNAVAILABLE","",null,"","",false,false,Instant.now(),
                premium?"Premium / no event":"Free schedule");
    }

    private static String val(Map<String,Object> m,String k){ return MiniJson.str(m.get(k)); }
    private static String first(Map<String,Object> m,String... keys){
        for(String k:keys){ String v=val(m,k); if(!v.isBlank()&&!"null".equalsIgnoreCase(v)) return v; }
        return "";
    }
    private static int score(Object o){
        if(o==null) return -1;
        String s=String.valueOf(o).trim();
        if(s.isBlank()||"null".equalsIgnoreCase(s)) return -1;
        try{return (int)Math.round(Double.parseDouble(s));}catch(Exception e){return -1;}
    }
    private static Instant parseTime(Map<String,Object> e){
        String ts=first(e,"strTimestamp","strEventTimestamp");
        try{
            if(!ts.isBlank()){
                if(ts.endsWith("Z")||ts.matches(".*[+-]\\d\\d:\\d\\d$")) return OffsetDateTime.parse(ts).toInstant();
                return LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC);
            }
        }catch(Exception ignored){}
        String date=val(e,"dateEvent"), time=first(e,"strTime","strEventTime");
        try{
            if(!date.isBlank()){
                if(time.isBlank()) time="00:00:00";
                if(time.length()==5) time += ":00";
                return LocalDateTime.parse(date+"T"+time).toInstant(ZoneOffset.UTC);
            }
        }catch(Exception ignored){}
        return null;
    }
    private static boolean isLiveStatus(String status,String progress){
        String s=(status+" "+progress).toUpperCase();
        if(s.contains("FINAL")||s.contains("FINISHED")||s.contains(" FT")||s.startsWith("FT")) return false;
        return s.matches(".*\\b(Q[1-4]|OT|HT|1H|2H|P[1-3]|IN[1-9]|ET|BT)\\b.*")
                || s.matches(".*\\d{1,3}:\\d{2}.*");
    }
    private static boolean isFinishedStatus(String status,String progress){
        String s=(status+" "+progress).toUpperCase();
        return s.contains("FINAL")||s.contains("FINISHED")||s.matches(".*\\b(FT|AOT|AET|PEN|AP)\\b.*");
    }
    private static String enc(String v){ return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8); }
}
