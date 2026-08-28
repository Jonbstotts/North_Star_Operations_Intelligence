package com.wtm.ui;

import com.wtm.config.AppConfig;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Resolves optional date-driven branded overlays over the selected FlatLaf base. */
public final class HolidayThemeService {
    private HolidayThemeService(){}

    public static AppTheme effectiveTheme(AppConfig config, LocalDate date){
        if(config==null)return AppTheme.FLATLAF_DARK;

        AppTheme fallback=AppTheme.fromId(config.themeId);
        if(!config.automaticHolidayThemes || date==null)return fallback;

        int year=date.getYear();
        if(!date.isBefore(LocalDate.of(year,1,1))&&!date.isAfter(LocalDate.of(year,1,31)))return AppTheme.WINTER_FROST;
        if(inRange(date,LocalDate.of(year,2,10),LocalDate.of(year,2,14)))return AppTheme.VALENTINE;
        if(inRange(date,LocalDate.of(year,3,10),LocalDate.of(year,3,17)))return AppTheme.ST_PATRICKS;
        if(inRange(date,LocalDate.of(year,7,1),LocalDate.of(year,7,4)))return AppTheme.INDEPENDENCE;
        if(inRange(date,LocalDate.of(year,10,15),LocalDate.of(year,10,31)))return AppTheme.HALLOWEEN;

        LocalDate thanksgiving=LocalDate.of(year,11,1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4,DayOfWeek.THURSDAY));
        if(inRange(date,thanksgiving.minusDays(7),thanksgiving.plusDays(3)))return AppTheme.THANKSGIVING;
        if(inRange(date,LocalDate.of(year,12,1),LocalDate.of(year,12,31)))return AppTheme.CHRISTMAS;
        return fallback;
    }

    public static String automaticThemeDescription(LocalDate date){
        if(date==null)return "";
        AppConfig temp=new AppConfig();
        temp.automaticHolidayThemes=true;
        temp.themeId="FLATLAF_DARK";
        AppTheme resolved=effectiveTheme(temp,date);
        return resolved==AppTheme.FLATLAF_DARK
                ?"No automatic holiday override today"
                :"Automatic today: "+resolved.display();
    }

    private static boolean inRange(LocalDate date,LocalDate start,LocalDate end){
        return !date.isBefore(start)&&!date.isAfter(end);
    }
}
