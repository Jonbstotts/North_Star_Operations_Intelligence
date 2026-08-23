package com.wtm.usage;

/**
 * Read-only API usage row shown in Settings.
 *
 * limit <= 0 means the provider does not publish a fixed quota that the
 * application can safely represent as a percentage.
 */
public record ApiUsageRecord(
        String provider,
        String category,
        long used,
        long limit,
        String period,
        String note
) {
    public double percent(){
        return limit<=0 ? -1.0 : used*100.0/limit;
    }
}
