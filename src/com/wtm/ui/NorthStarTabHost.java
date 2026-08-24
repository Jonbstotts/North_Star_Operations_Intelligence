package com.wtm.ui;
import javax.swing.*;import javax.swing.border.*;import javax.swing.plaf.basic.BasicButtonUI;import java.awt.*;import java.awt.event.*;import java.util.*;
/** Canonical NorthStar in-workspace tabs. Matches the compact Intelligence reference tabs. */
public final class NorthStarTabHost extends JPanel{
 private static final int TAB_H=27,GAP=8,HPAD=11;
 private final JPanel strip=new JPanel(new FlowLayout(FlowLayout.LEFT,GAP,0));
 private final JPanel cards=new JPanel(new CardLayout());
 private final java.util.List<JButton> tabs=new ArrayList<>();private final java.util.List<String> keys=new ArrayList<>();private int selected;
 public NorthStarTabHost(){super(new BorderLayout(0,9));setOpaque(false);strip.setOpaque(false);strip.setBorder(new EmptyBorder(0,0,1,0));cards.setOpaque(false);add(strip,BorderLayout.NORTH);add(cards,BorderLayout.CENTER);}
 public NorthStarTabHost addTab(String title,JComponent content){String key="tab"+keys.size();keys.add(key);cards.add(content,key);JButton b=new JButton(title);b.setUI(new BasicButtonUI());b.setFocusPainted(false);b.setFocusable(false);b.setOpaque(true);b.setContentAreaFilled(true);b.setBorderPainted(true);b.setRolloverEnabled(true);b.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));int tw=b.getFontMetrics(b.getFont()).stringWidth(title),w=Math.max(62,Math.min(220,tw+HPAD*2));Dimension d=new Dimension(w,TAB_H);b.setPreferredSize(d);b.setMinimumSize(d);b.setMaximumSize(d);b.setMargin(new Insets(0,HPAD,0,HPAD));int idx=tabs.size();b.addActionListener(e->select(idx));b.addMouseListener(new MouseAdapter(){public void mouseEntered(MouseEvent e){style(b,idx==selected,true);}public void mouseExited(MouseEvent e){style(b,idx==selected,false);}});tabs.add(b);strip.add(b);refreshStyle();return this;}
 public void select(int i){if(i<0||i>=keys.size())return;selected=i;((CardLayout)cards.getLayout()).show(cards,keys.get(i));refreshStyle();}
 public int selectedIndex(){return selected;}
 public void refreshStyle(){for(int i=0;i<tabs.size();i++)style(tabs.get(i),i==selected,false);revalidate();repaint();}
 private static void style(JButton b,boolean active,boolean hover){Color border=active?Theme.accent():(hover?Theme.text():Theme.border());Color bg=hover&&!active?Theme.panel():Theme.panel2();b.setForeground(Theme.text());b.setBackground(bg);b.setBorder(new CompoundBorder(new LineBorder(border,1,true),new EmptyBorder(0,HPAD-1,0,HPAD-1)));}
}
