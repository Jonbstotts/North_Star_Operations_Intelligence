package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService; import com.wtm.ai.NorthStarLhyAnalytics;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Stable expanded Intelligence analysis view. Uses opaque dialog chrome and glass-styled inner cards. */
public final class IntelligenceAnalysisDialog extends JDialog {
    public IntelligenceAnalysisDialog(Window owner,String question,NorthStarIntelligenceService.Answer answer){
        super(owner,"NorthStar Intelligence Analysis",ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1180,780); setMinimumSize(new Dimension(900,620)); setLocationRelativeTo(owner);
        setContentPane(build(question,answer));
    }
    private JComponent build(String q,NorthStarIntelligenceService.Answer a){
        JPanel root=new JPanel(new BorderLayout()); root.setBackground(Theme.bg()); root.setBorder(new EmptyBorder(18,18,18,18));
        GlassSurfacePanel shell=new GlassSurfacePanel(24); shell.setLayout(new BorderLayout(16,16)); shell.setBorder(new EmptyBorder(18,20,18,20));
        JPanel head=new JPanel(new BorderLayout()); head.setOpaque(false);
        JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("NORTHSTAR INTELLIGENCE"); title.setForeground(Theme.text()); title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        JLabel sub=new JLabel("Adaptive analysis • "+shorten(q,110)); sub.setForeground(Theme.muted()); sub.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12)); words.add(title);words.add(Box.createVerticalStrut(4));words.add(sub);head.add(words,BorderLayout.WEST);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));controls.setOpaque(false);JButton close=new JButton("Close");close.addActionListener(e->dispose());controls.add(close);head.add(controls,BorderLayout.EAST); shell.add(head,BorderLayout.NORTH);
        JPanel content=new JPanel(new GridLayout(1,2,16,0));content.setOpaque(false);content.add(summaryCard(a));content.add(visualCard(q));shell.add(content,BorderLayout.CENTER);shell.add(sourceBar(a),BorderLayout.SOUTH);root.add(shell,BorderLayout.CENTER);return root;
    }
    private JComponent summaryCard(NorthStarIntelligenceService.Answer a){GlassSurfacePanel p=new GlassSurfacePanel(18);p.setLayout(new BorderLayout(0,10));p.setBorder(new EmptyBorder(18,18,18,18));JLabel title=new JLabel("AI SUMMARY");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));p.add(title,BorderLayout.NORTH);JTextArea text=new JTextArea(a.text());text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);text.setForeground(Theme.text());text.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));JScrollPane s=new JScrollPane(text);s.setOpaque(false);s.getViewport().setOpaque(false);s.setBorder(null);p.add(s,BorderLayout.CENTER);return p;}
    private JComponent visualCard(String q){GlassSurfacePanel p=new GlassSurfacePanel(18);p.setLayout(new BorderLayout(0,10));p.setBorder(new EmptyBorder(18,18,18,18));JLabel title=new JLabel("OPERATIONAL EVIDENCE");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));p.add(title,BorderLayout.NORTH);NorthStarLhyAnalytics.Trend tr=NorthStarLhyAnalytics.trend();String lower=q.toLowerCase(Locale.ROOT);if(tr.available()&&(lower.contains("lhy")||lower.contains("labor hour")||lower.contains("trend")||lower.contains("performance"))){JPanel body=new JPanel(new BorderLayout(0,12));body.setOpaque(false);JPanel metrics=new JPanel(new GridLayout(2,2,8,8));metrics.setOpaque(false);metrics.add(metric("LATEST",tr.latest()));metrics.add(metric("5-DAY AVG",tr.recentAverage()));metrics.add(metric("WEEK AVG",tr.weekAverage()));metrics.add(metric("MONTH AVG",tr.monthAverage()));body.add(metrics,BorderLayout.NORTH);java.util.List<Double> vals=tr.points().stream().map(NorthStarLhyAnalytics.Point::value).toList();java.util.List<String> labels=tr.points().stream().map(x->x.date().format(DateTimeFormatter.ofPattern("M/d"))).toList();body.add(new IntelligenceTrendChart(vals,labels,tr.target()),BorderLayout.CENTER);p.add(body,BorderLayout.CENTER);}else{JTextArea n=new JTextArea("No chartable LHY history was found for this question.\n\nNorthStar uses its persisted kpi-history.csv for LHY analytics. Import KPI/LHY history through the normal KPI CSV workflow and the chart will populate automatically.");n.setLineWrap(true);n.setWrapStyleWord(true);n.setEditable(false);n.setOpaque(false);n.setForeground(Theme.muted());n.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));p.add(n,BorderLayout.CENTER);}return p;}
    private JComponent metric(String label,double val){GlassSurfacePanel p=new GlassSurfacePanel(14);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(new EmptyBorder(9,11,9,11));JLabel l=new JLabel(label);l.setForeground(Theme.muted());l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));JLabel v=new JLabel(val==0?"—":String.format("%,.0f",val));v.setForeground(Theme.text());v.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));p.add(l);p.add(Box.createVerticalStrut(3));p.add(v);return p;}
    private JComponent sourceBar(NorthStarIntelligenceService.Answer a){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);String s=(a.sources()==null||a.sources().isEmpty())?"NorthStar verified analytics / no document source":"Sources • "+String.join("  •  ",a.sources());JLabel l=new JLabel(shorten(s,160));l.setForeground(Theme.muted());l.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));p.add(l,BorderLayout.WEST);return p;}
    private static String shorten(String s,int n){if(s==null)return "";return s.length()>n?s.substring(0,n-1)+"…":s;}
}
