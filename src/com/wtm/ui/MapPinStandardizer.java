package com.wtm.ui;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canonical lifecycle hook for map-pin normalization.
 *
 * Current map components already render pins through their own themed paint
 * paths. This hook intentionally performs no polling or component mutation;
 * it remains as the stable bootstrap boundary for future pin adapters.
 */
public final class MapPinStandardizer {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private MapPinStandardizer(){}
    public static void start(){STARTED.compareAndSet(false,true);}
    public static boolean started(){return STARTED.get();}
}
