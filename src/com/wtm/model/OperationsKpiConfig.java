package com.wtm.model;

/**
 * One configurable KPI card in the Operations Snapshot.
 *
 * v4 starts with locally-configurable values so the visual workspace can be
 * deployed before site SQL/report feeds are finalized. dataSourceId is kept as
 * a stable hook for future query/API-backed metric providers.
 */
public record OperationsKpiConfig(
        String id,
        String label,
        double currentValue,
        double targetValue,
        String unit,
        boolean higherIsBetter,
        boolean enabled,
        String dataSourceId
) {
    public boolean targetConfigured(){
        return Double.isFinite(targetValue);
    }

    public boolean targetMet(){
        if(!targetConfigured()) return true;
        return higherIsBetter
                ? currentValue >= targetValue
                : currentValue <= targetValue;
    }

    public double targetPercent(){
        if(!targetConfigured() || targetValue == 0) return Double.NaN;
        return currentValue / targetValue * 100.0;
    }
}
