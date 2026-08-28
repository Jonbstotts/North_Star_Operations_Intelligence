package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Compact, event-driven NorthStar Intelligence dashboard surface. */
public final class NorthStarIntelligenceCompactPanel extends RoundedPanel {
    private final NorthStarIntelligenceService service=NorthStarIntelligenceService.get();
    private final JTextField question=new JTextField();
    private final JLabel answer=new JLabel();
    private final JLabel status=new JLabel("LOCAL AI");
    private final Runnable openFull;

    public NorthStarIntelligenceCompactPanel(Runnable openFull){
        super(14);
        this.openFull=openFull;
        setName("northstar.ai.compact");
        setLayout(new BorderLayout(0,10));
        setBackground(Theme.panel());
        putClientProperty("outlineColor",Theme.border());
        setBorder(new EmptyBorder(10,12,10,12));
        add(header(),BorderLayout.NORTH);
        add(body(),BorderLayout.CENTER);
        add(composer(),BorderLayout.SOUTH);
        ThemeStyler.apply(this,Theme.active());
        refreshStatus();
    }

    private JComponent header(){
        JPanel row=new JPanel(new BorderLayout());row.setOpaque(false);
        JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("NORTHSTAR INTELLIGENCE");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
        JLabel sub=new JLabel("Evidence-grounded operations assistant");sub.setForeground(Theme.muted());sub.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));
        words.add(title);words.add(sub);row.add(words,BorderLayout.WEST);
        status.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));row.add(status,BorderLayout.EAST);return row;
    }

    private JComponent body(){
        JPanel panel=new JPanel(new BorderLayout());panel.setOpaque(false);
        answer.setText("<html>Ask about imported operations data or the local knowledge library.</html>");
        answer.setForeground(Theme.muted());answer.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));answer.setVerticalAlignment(SwingConstants.TOP);
        panel.add(answer);return panel;
    }

    private JComponent composer(){
        JPanel row=new JPanel(new BorderLayout(7,0));row.setOpaque(false);
        question.setPreferredSize(new Dimension(210,38));question.putClientProperty("JTextField.placeholderText","Ask NorthStar…");row.add(question,BorderLayout.CENTER);
        JButton ask=new JButton("Ask");ask.putClientProperty("primaryAction",Boolean.TRUE);ask.setPreferredSize(new Dimension(62,38));
        JButton open=new JButton("Open");open.setPreferredSize(new Dimension(62,38));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));actions.setOpaque(false);actions.add(ask);actions.add(open);row.add(actions,BorderLayout.EAST);
        ask.addActionListener(e->ask(ask));question.addActionListener(e->ask(ask));open.addActionListener(e->{if(openFull!=null)openFull.run();});return row;
    }

    private void ask(JButton button){
        if(!AuthorizationService.allowed(Permission.AI_ASSISTANT)){answer.setText("NorthStar Intelligence is not enabled for this account.");return;}
        String q=question.getText().trim();if(q.isBlank())return;question.setText("");button.setEnabled(false);answer.setText("<html><b>Analyzing…</b></html>");
        new SwingWorker<NorthStarIntelligenceService.Answer,Void>(){
            @Override protected NorthStarIntelligenceService.Answer doInBackground() throws Exception{return service.ask(q);}
            @Override protected void done(){button.setEnabled(true);try{var a=get();String t=a.text().replaceAll("\\s+"," ").trim();answer.setText("<html><b>Analysis complete.</b><br>"+html(t.length()>180?t.substring(0,180)+"…":t)+"</html>");}catch(Exception ex){answer.setText("<html><b>Request failed.</b><br>"+html(rootMessage(ex))+"</html>");refreshStatus();}}
        }.execute();
    }

    private void refreshStatus(){
        new SwingWorker<NorthStarIntelligenceService.Status,Void>(){
            @Override protected NorthStarIntelligenceService.Status doInBackground(){return service.testConnection();}
            @Override protected void done(){try{var s=get();status.setText(s.online()?"● READY":"○ LOCAL");status.setToolTipText(s.detail());status.setForeground(s.online()?Theme.accent():Theme.muted());}catch(Exception ex){status.setText("○ LOCAL");status.setForeground(Theme.muted());}}
        }.execute();
    }

    private static String rootMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();String m=x.getMessage();return m==null||m.isBlank()?x.getClass().getSimpleName():m;}
    private static String html(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
