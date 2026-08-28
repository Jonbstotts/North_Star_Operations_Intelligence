package com.wtm.security;

/**
 * Granular application privileges. UI code asks AuthorizationService for a
 * permission rather than checking hard-coded role names.
 */
public enum Permission {
    GENERAL_SETTINGS("General settings"),
    PINNED_LOCATIONS("Pinned locations"),
    ROUTES("Routes"),
    SPORTS("Sports"),
    EMPLOYEE_INFORMATION("Team celebrations / employee information"),
    EMPLOYEE_OPERATIONS("Employee Operations"),
    EMPLOYEE_TRAINING("Employee training / qualifications"),
    EMPLOYEE_ATTENDANCE("Employee attendance / call-ins"),
    EMPLOYEE_PERFORMANCE("Employee performance"),
    EMPLOYEE_SCHEDULING("Employee assignment planning"),
    CALL_IN_ADMINISTRATION("Call-in / notification administration"),
    OPERATIONS_CALENDAR("Operations calendar"),
    DASHBOARD_LAYOUT("Dashboard blocks / layout"),
    MAIN_SHOWCASE("Main showcase settings"),
    MEDIA_LIBRARY("Media library"),
    API_ADMINISTRATION("API providers / credentials"),
    API_USAGE("API usage"),
    DATA_REFRESH("Data & refresh"),
    TRUCK_TRACKING("Truck tracking"),
    AI_ASSISTANT("NorthStar Intelligence"),
    AI_KNOWLEDGE_ADMIN("AI knowledge library administration"),
    AI_EMPLOYEE_METRICS("AI access to employee metrics"),
    MANAGE_USERS("Users & access"),
    VIEW_AUDIT_LOG("Audit log");

    private final String display;

    Permission(String display){
        this.display=display;
    }

    public String display(){ return display; }

    @Override public String toString(){ return display; }
}
