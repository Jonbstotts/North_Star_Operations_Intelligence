package com.wtm.employee;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.UUID;

/** Canonical employee identity used throughout North Star. */
public record EmployeeProfile(
        String id,
        String employeeNumber,
        String name,
        String shortName,
        String department,
        String shift,
        LocalDate hireDate,
        MonthDay birthday,
        String phone,
        String photoAsset,
        boolean active,
        boolean celebrationAnnouncements,
        boolean showBirthday,
        boolean showAnniversary,
        boolean employeeOfMonth,
        String pinSalt,
        String pinHash,
        int pinIterations
) {
    public EmployeeProfile {
        id=clean(id).isBlank()?UUID.randomUUID().toString():clean(id);
        employeeNumber=clean(employeeNumber);
        name=clean(name);
        shortName=clean(shortName);
        department=clean(department);
        shift=clean(shift);
        phone=clean(phone);
        photoAsset=clean(photoAsset);
        pinSalt=clean(pinSalt);
        pinHash=clean(pinHash);
        pinIterations=Math.max(0,pinIterations);
    }

    public EmployeeProfile withPin(
            String salt,
            String hash,
            int iterations
    ){
        return new EmployeeProfile(
                id,employeeNumber,name,shortName,department,shift,
                hireDate,birthday,phone,photoAsset,active,
                celebrationAnnouncements,showBirthday,showAnniversary,employeeOfMonth,
                salt,hash,iterations
        );
    }

    public EmployeeProfile withoutPin(){
        return withPin("","",0);
    }

    private static String clean(String value){
        return value==null?"":value.trim();
    }
}
