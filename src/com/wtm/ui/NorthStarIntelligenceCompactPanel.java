package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Compact dashboard entry point for quick NorthStar Intelligence questions. */
public final class NorthStarIntelligenceCompactPanel extends JPanel {
    private final NorthStarIntelligenceService service=NorthStarIntelligenceService.get();
    private final JTextField question=new JTextField();
    private final JLabel answer=new JLabel("Ask about KPIs, schedules, policies or shipment trends.");
    private final Runnable openFull;

    public NorthStarIntelligenceCompactPanel(Runnable openFull){
        this.openFull=openFull;setName("northstar.ai.compact");setLayout(new BorderLayout(0,7));setBackground(Theme.panel());setBorder(new EmptyBorder(9,11,9,11));
        JLabel title=new JLabel("NORTHSTAR INTELLIGENCE");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));add(title,BorderLayout.NORTH);
        answer.setForeground(Theme.muted());answer.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));add(answer,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(6,0));bottom.setOpaque(false);question.setToolTipText("Quick question");bottom.add(question,BorderLayout.CENTER);
        JButton ask=new JButton("Ask");ask.putClientProperty("primaryAction",Boolean.TRUE);JButton open=new JButton("Open AI");JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));buttons.setOpaque(false);buttons.add(ask);buttons.add(open);bottom.add(buttons,BorderLayout.EAST);add(bottom,BorderLayout.SOUTH);
        ask.addActionListener(e->ask(ask));question.addActionListener(e->ask(ask));open.addActionListener(e->{if(openFull!=null)openFull.run();});ThemeStyler.apply(this,Theme.active());
    }

    private void ask(JButton button){
        if(!AuthorizationService.allowed(Permission.AI_ASSISTANT)){answer.setText("AI permission is not enabled for this account.");return;}
        String q=question.getText().trim();if(q.isBlank())return;question.setText("");button.setEnabled(false);answer.setText("Thinking…");
        new SwingWorker<NorthStarIntelligenceService.Answer,Void>(){
            @Override protected NorthStarIntelligenceService.Answer doInBackground()throws Exception{return service.ask(q);}
            @Override protected void done(){button.setEnabled(true);try{String text=get().text().replaceAll("\\s+"," ").trim();if(text.length()>160)text=text.substring(0,160)+"…";answer.setText("<html>"+html(text)+"</html>");}catch(Exception ex){answer.setText("Local AI unavailable. Open AI for setup details.");}}
        }.execute();
    }
    private static String html(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
