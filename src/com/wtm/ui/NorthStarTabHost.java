package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.util.*;

/** Canonical NorthStar segmented tab host. */
public final class NorthStarTabHost extends JPanel {
    private static final int TAB_H = 30, GAP = 4, HPAD = 12;
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, 0));
    private final JPanel cards = new JPanel(new CardLayout());
    private final java.util.List<JButton> tabs = new ArrayList<>();
    private final java.util.List<String> keys = new ArrayList<>();
    private int selected;

    public NorthStarTabHost() {
        super(new BorderLayout(0, 8));
        setOpaque(false); strip.setOpaque(false); strip.setBorder(new EmptyBorder(0,0,1,0)); cards.setOpaque(false);
        JScrollPane scroller = new JScrollPane(strip, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setBorder(null); scroller.setOpaque(false); scroller.getViewport().setOpaque(false); scroller.getHorizontalScrollBar().setUnitIncrement(24);
        scroller.setPreferredSize(new Dimension(100, TAB_H + 4));
        add(scroller, BorderLayout.NORTH); add(cards, BorderLayout.CENTER);
    }

    public NorthStarTabHost addTab(String title, JComponent content) {
        String key = "tab" + keys.size(); keys.add(key); cards.add(content, key);
        JButton b = new JButton(title); b.setUI(new BasicButtonUI()); b.setFocusPainted(false); b.setFocusable(false); b.setOpaque(true); b.setContentAreaFilled(true);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        int textW = b.getFontMetrics(b.getFont()).stringWidth(title); int width = Math.max(74, Math.min(220, textW + HPAD * 2));
        Dimension size = new Dimension(width, TAB_H); b.setPreferredSize(size); b.setMinimumSize(size); b.setMaximumSize(size); b.setMargin(new Insets(0,HPAD,0,HPAD));
        int idx = tabs.size(); b.addActionListener(e -> select(idx)); tabs.add(b); strip.add(b); refresh(); return this;
    }

    public void select(int i) { if (i < 0 || i >= keys.size()) return; selected = i; ((CardLayout)cards.getLayout()).show(cards, keys.get(i)); refresh(); }
    public int selectedIndex() { return selected; }
    private void refresh() {
        for (int i=0;i<tabs.size();i++) { JButton b=tabs.get(i); boolean active=i==selected; b.setForeground(active?Color.WHITE:Theme.muted()); b.setBackground(active?Theme.accent():Theme.panel2()); b.setBorder(new CompoundBorder(new LineBorder(active?Theme.accent():Theme.border(),1,true),new EmptyBorder(0,HPAD-1,0,HPAD-1))); }
        revalidate(); repaint();
    }
}
