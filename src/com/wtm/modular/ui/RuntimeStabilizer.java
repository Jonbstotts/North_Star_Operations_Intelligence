package com.wtm.modular.ui;

/**
 * Compatibility shell retained for older launch paths.
 * v2.1.9 retires the periodic scanner because it mutated Truck Tracking and
 * continuously restarted Main Showcase rotation. Layout lifecycle is now
 * owned synchronously by ThemeCoreV216.
 */
public final class RuntimeStabilizer {
    private RuntimeStabilizer(){}
    public static void start(){ /* intentionally timer-free */ }
}
