package com.wtm.model;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Local team-recognition record.
 *
 * Employee photos are referenced by managed Media Library asset filename, not
 * by an arbitrary/absolute filesystem path. This keeps records portable across
 * macOS, Windows, Raspberry Pi, and a future web-hosted deployment.
 */
public record CelebrationConfig(
        String name,
        int birthdayMonth,
        int birthdayDay,
        LocalDate hireDate,
        String photoAsset,
        boolean showBirthday,
        boolean showAnniversary,
        int employeeOfMonthYear,
        int employeeOfMonthMonth,
        boolean celebrationEffect,
        boolean enabled
) {
    public boolean birthdayToday(LocalDate today){
        return enabled && showBirthday
                && birthdayMonth==today.getMonthValue()
                && birthdayDay==today.getDayOfMonth();
    }

    public boolean anniversaryToday(LocalDate today){
        return enabled && showAnniversary && hireDate!=null
                && hireDate.getMonthValue()==today.getMonthValue()
                && hireDate.getDayOfMonth()==today.getDayOfMonth()
                && !hireDate.isAfter(today);
    }

    public int anniversaryYears(LocalDate today){
        return hireDate==null?0:Math.max(0,today.getYear()-hireDate.getYear());
    }

    public boolean employeeOfMonth(YearMonth month){
        return enabled
                && month!=null
                && employeeOfMonthYear==month.getYear()
                && employeeOfMonthMonth==month.getMonthValue();
    }

    public boolean employeeOfMonthToday(LocalDate today){
        return today!=null && employeeOfMonth(YearMonth.from(today));
    }
}
