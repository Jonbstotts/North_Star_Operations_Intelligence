package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.*;

/** Canonical styling bridge for legacy JTabbedPane workspaces. */
public final class NorthStarTabs {
    private static final int TAB_H = 28, HPAD = 10;

    public static JTabbedPane create() {
        JTabbedPane t = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        configure(t); t.addChangeListener(e -> refreshHeaders(t)); return t;
    }
    public static void configure(JTabbedPane t) {
        t.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT); t.setOpaque(false); t.setBackground(Theme.bg()); t.setForeground(Theme.text()); t.setBorder(new EmptyBorder(2,0,0,0));
        t.setUI(new BasicTabbedPaneUI() {
            @Override protected Insets getTabAreaInsets(int p){ return new Insets(0,0,2,0); }
            @Override protected Insets getContentBorderInsets(int p){ return new Insets(0,0,0,0); }
            @Override protected void paintContentBorder(Graphics g,int p,int i){}
            @Override protected void paintFocusIndicator(Graphics g,int p,Rectangle[] r,int i,Rectangle ir,Rectangle tr,boolean s){}
        });
    }
    public static void add(JTabbedPane t,String title,JComponent content){ t.addTab(title,content); installHeader(t,t.getTabCount()-1,title); refreshHeaders(t); }
    private static void installHeader(JTabbedPane t,int i,String title){
        JLabel l=new JLabel(title,SwingConstants.CENTER); l.setOpaque(true); l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
        int textW=l.getFontMetrics(l.getFont()).stringWidth(title); int width=Math.max(68,Math.min(205,textW+HPAD*2)); Dimension size=new Dimension(width,TAB_H); l.setPreferredSize(size); l.setMinimumSize(size); l.setMaximumSize(size);
        final int idx=i; l.addMouseListener(new MouseAdapter(){ public void mousePressed(MouseEvent e){ if(idx<t.getTabCount())t.setSelectedIndex(idx); }}); t.setTabComponentAt(i,l);
    }
    public static void refreshHeaders(JTabbedPane t){
        configure(t); for(int i=0;i<t.getTabCount();i++){ Component c=t.getTabComponentAt(i); if(!(c instanceof JLabel)){ installHeader(t,i,t.getTitleAt(i)); c=t.getTabComponentAt(i); } if(c instanceof JLabel l){ boolean active=i==t.getSelectedIndex(); l.setForeground(active?Color.WHITE:Theme.muted()); l.setBackground(active?Theme.accent():Theme.panel2()); l.setBorder(new CompoundBorder(new LineBorder(active?Theme.accent():Theme.border(),1,true),new EmptyBorder(0,HPAD-1,0,HPAD-1))); }} t.revalidate(); t.repaint();
    }
    private NorthStarTabs(){}
}
