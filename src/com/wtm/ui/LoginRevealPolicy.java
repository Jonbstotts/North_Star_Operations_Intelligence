package com.wtm.ui;

/** Timing/easing policy for the startup login form reveal. */
public final class LoginRevealPolicy {
    public static final int TIMER_DELAY_MILLIS=10;
    public static final double DURATION_MILLIS=700.0;
    private static final int MAX_VERTICAL_OFFSET=8;

    private LoginRevealPolicy(){}

    public static float revealProgress(double elapsedMillis){
        double t=Math.max(0.0,Math.min(1.0,elapsedMillis/DURATION_MILLIS));
        // Quintic smootherstep: zero velocity and acceleration at both ends.
        double eased=t*t*t*(t*(t*6.0-15.0)+10.0);
        return (float)eased;
    }

    public static int verticalOffset(float progress){
        float p=Math.max(0f,Math.min(1f,progress));
        return Math.round((1f-p)*MAX_VERTICAL_OFFSET);
    }
}
