package com.wtm.model;

/** A named geographic point monitored by the dashboard. */
public record Location(String name, double latitude, double longitude) {
    @Override public String toString() { return name; }
}
