package com.wtm.ui;
import javax.swing.*;import javax.swing.border.*;import java.awt.*;import java.awt.event.*;
public final class NorthStarTabs{
 public static JTabbedPane create(){JTabbedPane t=new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);t.setOpaque(false);t.setBackground(Theme.bg());t.setForeground(Theme.text());t.setBorder(new EmptyBorder(4,0,0,0));t.addChangeListener(e->refreshHeaders(t));return t;}
 public static void add(JTabbedPane t,String title,JComponent content){t.addTab(title,content);installHeader(t,t.getTabCount()-1,title);refreshHeaders(t);}
 private static void installHeader(JTabbedPane t,int i,String title){JLabel l=new JLabel(title,SwingConstants.CENTER);l.setOpaque(true);l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));l.setBorder(new CompoundBorder(new LineBorder(Theme.border(),1,true),new EmptyBorder(6,14,6,14)));int width=Math.max(112,Math.min(220,l.getFontMetrics(l.getFont()).stringWidth(title)+42));l.setPreferredSize(new Dimension(width,31));l.setMinimumSize(new Dimension(width,31));final int idx=i;l.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(idx<t.getTabCount())t.setSelectedIndex(idx);}});t.setTabComponentAt(i,l);}
 public static void refreshHeaders(JTabbedPane t){for(int i=0;i<t.getTabCount();i++){Component c=t.getTabComponentAt(i);if(!(c instanceof JLabel)){installHeader(t,i,t.getTitleAt(i));c=t.getTabComponentAt(i);}if(c instanceof JLabel l){boolean s=i==t.getSelectedIndex();l.setForeground(s?Color.WHITE:Theme.text());l.setBackground(s?Theme.accent():Theme.panel2());l.setBorder(new CompoundBorder(new LineBorder(s?Theme.accent():Theme.border(),1,true),new EmptyBorder(6,14,6,14)));}}t.revalidate();t.repaint();}
 private NorthStarTabs(){}
}
