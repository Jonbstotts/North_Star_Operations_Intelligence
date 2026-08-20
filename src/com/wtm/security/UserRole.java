package com.wtm.security;

import java.util.EnumSet;
import java.util.Set;

/** Built-in permission templates. Individual accounts can still be customized. */
public enum UserRole {
    ADMINISTRATOR("Administrator", EnumSet.allOf(Permission.class)),
    MANAGEMENT("Management", EnumSet.of(
            Permission.GENERAL_SETTINGS,
            Permission.PINNED_LOCATIONS,
            Permission.ROUTES,
            Permission.SPORTS,
            Permission.EMPLOYEE_INFORMATION,
            Permission.EMPLOYEE_OPERATIONS,
            Permission.EMPLOYEE_TRAINING,
            Permission.EMPLOYEE_ATTENDANCE,
            Permission.EMPLOYEE_PERFORMANCE,
            Permission.EMPLOYEE_SCHEDULING,
            Permission.CALL_IN_ADMINISTRATION,
            Permission.OPERATIONS_CALENDAR,
            Permission.DASHBOARD_LAYOUT,
            Permission.MAIN_SHOWCASE,
            Permission.MEDIA_LIBRARY,
            Permission.API_USAGE,
            Permission.DATA_REFRESH,
            Permission.VIEW_AUDIT_LOG
    )),
    OPERATIONS("Operations", EnumSet.of(
            Permission.PINNED_LOCATIONS,
            Permission.ROUTES,
            Permission.SPORTS,
            Permission.OPERATIONS_CALENDAR,
            Permission.DASHBOARD_LAYOUT,
            Permission.MAIN_SHOWCASE,
            Permission.MEDIA_LIBRARY,
            Permission.DATA_REFRESH
    )),
    DISPLAY("Display User", EnumSet.noneOf(Permission.class)),
    CUSTOM("Custom", EnumSet.noneOf(Permission.class));

    private final String display;
    private final EnumSet<Permission> defaults;

    UserRole(String display,EnumSet<Permission> defaults){
        this.display=display;
        this.defaults=defaults.clone();
    }

    public String display(){ return display; }

    public Set<Permission> defaultPermissions(){
        return EnumSet.copyOf(defaults);
    }

    @Override public String toString(){ return display; }
}
