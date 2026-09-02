package com.wtm.ui;

/**
 * Calculates seamless geometry for dashboard continuous tickers.
 *
 * A cycle is exactly {@code itemCount * slotWidth}. Repeating that identical
 * cycle enough times to cover {@code cycleWidth + viewportWidth} guarantees
 * that wrapping the viewport by one cycle is visually indistinguishable from
 * ordinary movement, even when fewer items exist than visible slots.
 */
public final class ContinuousTickerGeometry {
    private ContinuousTickerGeometry(){}

    public static Layout calculate(
            int viewportWidth,
            int visibleSlots,
            int itemCount,
            int minimumSlotWidth
    ){
        int width=Math.max(1,viewportWidth);
        int slots=Math.max(1,visibleSlots);
        int items=Math.max(1,itemCount);
        int minimum=Math.max(1,minimumSlotWidth);

        int slotWidth=Math.max(
                minimum,
                (int)Math.ceil(width/(double)slots)
        );
        int cycleWidth=Math.multiplyExact(items,slotWidth);
        int copies=Math.max(
                2,
                1+(int)Math.ceil(width/(double)cycleWidth)
        );
        int trackWidth=Math.multiplyExact(cycleWidth,copies);
        return new Layout(slotWidth,cycleWidth,copies,trackWidth);
    }

    public record Layout(
            int slotWidth,
            int cycleWidth,
            int copies,
            int trackWidth
    ){}
}
