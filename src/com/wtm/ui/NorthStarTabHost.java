package com.wtm.ui;
import javax.swing.*;import javax.swing.border.*;import java.awt.*;import java.util.*;
public final class NorthStarTabHost extends JPanel{
 private final JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)),cards=new JPanel(new CardLayout());private final java.util.List<JButton> tabs=new ArrayList<>();private final java.util.List<String> keys=new ArrayList<>();private int selected;
 public NorthStarTabHost(){super(new BorderLayout(0,10));setOpaque(false);buttons.setOpaque(false);buttons.setBorder(new EmptyBorder(0,0,2,0));cards.setOpaque(false);add(buttons,BorderLayout.NORTH);add(cards);}
 public NorthStarTabHost addTab(String title,JComponent content){String key="tab"+keys.size();keys.add(key);cards.add(content,key);JButton b=new JButton(title);b.setFocusPainted(false);b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));b.setPreferredSize(new Dimension(Math.max(125,Math.min(230,b.getFontMetrics(b.getFont()).stringWidth(title)+44)),34));int idx=tabs.size();b.addActionListener(e->select(idx));tabs.add(b);buttons.add(b);refresh();return this;}
 public void select(int i){if(i<0||i>=keys.size())return;selected=i;((CardLayout)cards.getLayout()).show(cards,keys.get(i));refresh();}
 private void refresh(){for(int i=0;i<tabs.size();i++){JButton b=tabs.get(i);boolean s=i==selected;b.setForeground(s?Color.WHITE:Theme.text());b.setBackground(s?Theme.accent():Theme.panel2());b.setBorder(new CompoundBorder(new LineBorder(s?Theme.accent():Theme.border(),1,true),new EmptyBorder(5,12,5,12)));b.setOpaque(true);}revalidate();repaint();}
}
