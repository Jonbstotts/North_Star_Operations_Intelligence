package com.wtm.media;

/** Managed media buckets kept separate for predictable application behavior. */
public enum MediaCategory {
    ANNOUNCEMENTS("Announcements","announcements"),
    EMPLOYEE_PHOTOS("Employee Photos","employees"),
    EMPLOYEE_SHOWCASE("Employee Showcase","showcase");

    private final String display;
    private final String folder;

    MediaCategory(String display,String folder){
        this.display=display;
        this.folder=folder;
    }

    public String display(){ return display; }
    public String folder(){ return folder; }

    @Override public String toString(){ return display; }
}
