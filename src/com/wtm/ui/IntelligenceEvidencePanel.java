package com.wtm.ui;

import com.wtm.ai.NorthStarLhyAnalytics;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Shared chart/evidence view for dashboard Intelligence analysis. */
public final class IntelligenceEvidencePanel {
 private IntelligenceEvidencePanel(){}
 public static JComponent create(String q){
  GlassSurfacePanel p=new GlassSurfacePanel(16);p.setLayout(new BorderLayout(0,10));p.setBorder(new EmptyBorder(14,14,14,14));
  JLabel title=new JLabel("OPERATIONAL EVIDENCE");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));p.add(title,BorderLayout.NORTH);
  if(NorthStarLhyAnalytics.matches(q)){
   var s=NorthStarLhyAnalytics.trend();
   if(s!=null&&s.points()!=null&&s.points().size()>=2){
    JPanel body=new JPanel(new BorderLayout(0,10));body.setOpaque(false);JPanel m=new JPanel(new GridLayout(2,2,8,8));m.setOpaque(false);
    m.add(metric("LATEST",s.latest()));m.add(metric("5-DAY AVG",s.recentAverage()));m.add(metric("WEEK AVG",s.weekAverage()));m.add(metric("MONTH AVG",s.monthAverage()));body.add(m,BorderLayout.NORTH);
    body.add(new IntelligenceTrendChart(s.points().stream().map(com.wtm.ai.NorthStarLhyAnalytics.Point::value).toList(),s.points().stream().map(pt->pt.date().toString()).toList(),s.target()),BorderLayout.CENTER);p.add(body,BorderLayout.CENTER);return p;
   }
  }
  JTextArea n=new JTextArea("NorthStar will place charts, KPI cards, tables, route history, document evidence, or other relevant visual analysis here when the prompt and available data support it.");
  n.setEditable(false);n.setLineWrap(true);n.setWrapStyleWord(true);n.setOpaque(false);n.setForeground(Theme.muted());n.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));p.add(n,BorderLayout.CENTER);return p;
 }
 private static JComponent metric(String label,double value){GlassSurfacePanel p=new GlassSurfacePanel(12);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(new EmptyBorder(8,10,8,10));JLabel l=new JLabel(label);l.setForeground(Theme.muted());l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));JLabel v=new JLabel(String.format("%,.0f",value));v.setForeground(Theme.text());v.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));p.add(l);p.add(Box.createVerticalStrut(2));p.add(v);return p;}
}
