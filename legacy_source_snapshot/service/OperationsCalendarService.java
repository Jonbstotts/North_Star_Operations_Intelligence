package com.wtm.service;

import com.wtm.config.AppConfig;
import com.wtm.model.*;

import java.time.*;
import java.util.*;

/**
 * Converts site operating-calendar records into temporary Main Showcase
 * announcements.
 *
 * Connected/adjacent entries are grouped before announcement-window filtering,
 * so Thanksgiving closure + Friday limited service becomes one slide.
 */
public final class OperationsCalendarService {
    private OperationsCalendarService(){}

    public static List<OperationAnnouncement> announcements(
            AppConfig config,
            LocalDate today
    ){
        if(config==null
                || !config.operationsAnnouncementsEnabled
                || today==null)
            return List.of();

        List<OperationEvent> enabled=config.operationEvents.stream()
                .filter(OperationEvent::enabled)
                .filter(e->e.startDate()!=null&&e.endDate()!=null)
                .sorted(Comparator.comparing(OperationEvent::startDate)
                        .thenComparing(OperationEvent::endDate))
                .toList();

        if(enabled.isEmpty())return List.of();

        List<List<OperationEvent>> groups=new ArrayList<>();
        List<OperationEvent> current=new ArrayList<>();
        LocalDate currentEnd=null;

        for(OperationEvent event:enabled){
            if(current.isEmpty()){
                current.add(event);
                currentEnd=event.endDate();
                continue;
            }

            if(!event.startDate().isAfter(currentEnd.plusDays(1))){
                current.add(event);
                if(event.endDate().isAfter(currentEnd))
                    currentEnd=event.endDate();
            }else{
                groups.add(List.copyOf(current));
                current.clear();
                current.add(event);
                currentEnd=event.endDate();
            }
        }
        if(!current.isEmpty())groups.add(List.copyOf(current));

        List<OperationAnnouncement> out=new ArrayList<>();

        for(List<OperationEvent> group:groups){
            LocalDate start=group.stream()
                    .map(OperationEvent::startDate)
                    .min(LocalDate::compareTo)
                    .orElseThrow();

            LocalDate end=group.stream()
                    .map(OperationEvent::endDate)
                    .max(LocalDate::compareTo)
                    .orElseThrow();

            LocalDate announcementStart=group.stream()
                    .map(e->e.startDate().minusDays(
                            e.leadDays()>0
                                    ?e.leadDays()
                                    :config.operationsDefaultLeadDays))
                    .min(LocalDate::compareTo)
                    .orElse(start);

            if(today.isBefore(announcementStart)||today.isAfter(end))
                continue;

            LocalDate resume=findNormalResume(config,end,enabled);

            out.add(new OperationAnnouncement(
                    group,start,end,announcementStart,resume));
        }

        return List.copyOf(out);
    }

    private static LocalDate findNormalResume(
            AppConfig config,
            LocalDate after,
            List<OperationEvent> events
    ){
        LocalDate candidate=after.plusDays(1);

        // Protect against malformed calendars without creating an endless loop.
        for(int i=0;i<370;i++,candidate=candidate.plusDays(1)){
            if(!config.normalOperatingDays.contains(candidate.getDayOfWeek()))
                continue;

            boolean covered=false;
            for(OperationEvent event:events){
                if(event.covers(candidate)){
                    covered=true;
                    break;
                }
            }

            if(!covered)return candidate;
        }

        return null;
    }
}
