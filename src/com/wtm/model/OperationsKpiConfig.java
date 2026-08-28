package com.wtm.model;

/**
 * One configurable KPI card in the Operations Snapshot.
 *
 * NorthStar starts with locally-configurable values so the visual workspace can be
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

    /**
     * Certain operational exception metrics have fixed semantics regardless of
     * older saved configuration: fewer damages, denials and active alerts is
     * always better. This also repairs legacy profiles that accidentally saved
     * them as "Higher is better".
     */
    public boolean effectiveHigherIsBetter(){
        String key=((id==null?"":id)+" "+(label==null?"":label))
                .toLowerCase(java.util.Locale.ROOT);
        if(key.contains("damage")
                ||key.contains("floor_denial")
                ||key.contains("floor denial")
                ||key.contains("active_alert")
                ||key.contains("active alert"))
            return false;
        return higherIsBetter;
    }

    public boolean targetMet(){
        if(!targetConfigured()) return true;
        return effectiveHigherIsBetter()
                ? currentValue >= targetValue
                : currentValue <= targetValue;
    }

    public double targetPercent(){
        if(!targetConfigured() || targetValue == 0) return Double.NaN;
        return currentValue / targetValue * 100.0;
    }
}
