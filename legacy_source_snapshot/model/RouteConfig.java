package com.wtm.model;

/** Configuration for a monitored commute route. */
public record RouteConfig(String name, Location origin, Location destination) {}
