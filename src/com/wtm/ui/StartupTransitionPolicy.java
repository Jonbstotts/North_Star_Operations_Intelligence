package com.wtm.ui;

/**
 * Pure timing policy for the startup identity -> login handoff.
 * Keeping the easing function outside Swing makes the transition deterministic
 * and regression-testable instead of embedding animation math in a dialog.
 */
public final class StartupTransitionPolicy {
    public static final int LOGIN_REVEAL_MILLIS=820;
    public static final int TIMER_DELAY_MILLIS=8;
    public static final int FORM_TRAVEL_PIXELS=10;

    private StartupTransitionPolicy(){}

    /** Quintic smoother-step: zero velocity and acceleration at both ends. */
    public static float revealProgress(double elapsedMillis){
        double t=Math.max(0,Math.min(1,elapsedMillis/LOGIN_REVEAL_MILLIS));
        double eased=t*t*t*(t*(t*6-15)+10);
        return (float)eased;
    }

    public static int verticalOffset(float progress){
        float p=Math.max(0f,Math.min(1f,progress));
        return Math.round((1f-p)*FORM_TRAVEL_PIXELS);
    }
}
