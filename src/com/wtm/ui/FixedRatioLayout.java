package com.wtm.ui;

import java.awt.*;

/**
 * Layout manager for the production dashboard's two primary regions.
 *
 * Unlike GridBagLayout or JSplitPane, this class never reallocates width based
 * on a child's preferred/minimum size. The first component always receives the
 * configured fraction of the available width and the second receives the rest.
 *
 * This is intentional for a passive TV dashboard: information cards must fit
 * inside their assigned region rather than being allowed to shrink the map.
 */
public final class FixedRatioLayout implements LayoutManager2 {
    private final double firstRatio;
    private final int gap;

    public FixedRatioLayout(double firstRatio, int gap) {
        if (firstRatio <= 0.0 || firstRatio >= 1.0)
            throw new IllegalArgumentException("firstRatio must be between 0 and 1.");
        this.firstRatio = firstRatio;
        this.gap = Math.max(0, gap);
    }

    @Override public void addLayoutComponent(Component comp, Object constraints) {}
    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets i = parent.getInsets();
        return new Dimension(1400 + i.left + i.right, 800 + i.top + i.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets i = parent.getInsets();
        return new Dimension(900 + i.left + i.right, 500 + i.top + i.bottom);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Component[] children = parent.getComponents();
            if (children.length == 0) return;

            Insets in = parent.getInsets();
            int x = in.left;
            int y = in.top;
            int width = Math.max(0, parent.getWidth() - in.left - in.right);
            int height = Math.max(0, parent.getHeight() - in.top - in.bottom);

            if (children.length == 1) {
                children[0].setBounds(x, y, width, height);
                return;
            }

            int usableWidth = Math.max(0, width - gap);
            int firstWidth = (int)Math.round(usableWidth * firstRatio);
            int secondWidth = usableWidth - firstWidth;

            children[0].setBounds(x, y, firstWidth, height);
            children[1].setBounds(x + firstWidth + gap, y, secondWidth, height);

            // Any unexpected extra children are deliberately hidden rather than
            // altering the two-region production layout.
            for (int n = 2; n < children.length; n++)
                children[n].setBounds(0, 0, 0, 0);
        }
    }
}
