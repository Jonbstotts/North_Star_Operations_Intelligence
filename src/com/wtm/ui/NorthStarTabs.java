package com.wtm.ui;

import javax.swing.*;import javax.swing.border.EmptyBorder;import java.awt.*;

/** Standard tab construction for NorthStar. New workspaces should use this helper for consistent alignment/theme behavior. */
public final class NorthStarTabs {
 private NorthStarTabs(){}
 public static JTabbedPane create(){JTabbedPane t=new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);t.setBackground(Theme.bg());t.setForeground(Theme.text());t.setBorder(new EmptyBorder(4,0,0,0));t.addChangeListener(e->refreshHeaders(t));return t;}
 public static void add(JTabbedPane t,String title,JComponent content){t.addTab(title,content);int i=t.getTabCount()-1;JLabel l=new JLabel(title,SwingConstants.CENTER);l.setOpaque(true);l.setBorder(new EmptyBorder(8,18,8,18));l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));l.setPreferredSize(new Dimension(Math.max(150,l.getPreferredSize().width),36));t.setTabComponentAt(i,l);refreshHeaders(t);}
 public static void refreshHeaders(JTabbedPane t){for(int i=0;i<t.getTabCount();i++){Component c=t.getTabComponentAt(i);if(c instanceof JLabel l){boolean selected=i==t.getSelectedIndex();l.setForeground(selected?Color.WHITE:Theme.text());l.setBackground(selected?Theme.accent():Theme.panel2());}}}
}
