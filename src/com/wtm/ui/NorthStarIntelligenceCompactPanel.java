package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Compact dashboard entry point for quick NorthStar Intelligence questions. */
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

        add(buildHeader(),BorderLayout.NORTH);
        add(buildBody(),BorderLayout.CENTER);
        add(buildComposer(),BorderLayout.SOUTH);

        ThemeStyler.apply(this,Theme.active());
        refreshStatus();
    }

    private JComponent buildHeader(){
        JPanel row=new JPanel(new BorderLayout(8,0));
        row.setOpaque(false);

        JPanel titleBlock=new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock,BoxLayout.Y_AXIS));

        JLabel title=new JLabel("NORTHSTAR INTELLIGENCE");
        title.setForeground(Theme.text());
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle=new JLabel("Operations assistant");
        subtitle.setForeground(Theme.muted());
        subtitle.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(subtitle);
        row.add(titleBlock,BorderLayout.WEST);

        status.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));
        status.setForeground(Theme.muted());
        status.setBorder(new EmptyBorder(3,7,3,7));
        row.add(status,BorderLayout.EAST);
        return row;
    }

    private JComponent buildBody(){
        JPanel body=new JPanel(new BorderLayout());
        body.setOpaque(false);
        answer.setText("<html><span style='color:#AEB7C4'>Ask about KPIs, schedules, policies, shipments, weather or traffic.</span></html>");
        answer.setForeground(Theme.muted());
        answer.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
        answer.setVerticalAlignment(SwingConstants.TOP);
        body.add(answer,BorderLayout.CENTER);
        return body;
    }

    private JComponent buildComposer(){
        JPanel shell=new JPanel(new BorderLayout(7,0));
        shell.setOpaque(false);

        question.setToolTipText("Ask NorthStar Intelligence");
        question.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        question.setPreferredSize(new Dimension(210,38));
        question.putClientProperty("JTextField.placeholderText","Ask NorthStar…");
        shell.add(question,BorderLayout.CENTER);

        NorthStarPrimaryButton ask=new NorthStarPrimaryButton("Ask");
        ask.setPreferredSize(new Dimension(62,38));

        JButton open=new JButton("Open");
        open.setToolTipText("Open full NorthStar Intelligence workspace");
        open.setPreferredSize(new Dimension(62,38));

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));
        buttons.setOpaque(false);
        buttons.add(ask);
        buttons.add(open);
        shell.add(buttons,BorderLayout.EAST);

        ask.addActionListener(e->ask(ask));
        question.addActionListener(e->ask(ask));
        open.addActionListener(e->{if(openFull!=null)openFull.run();});
        return shell;
    }

    private void ask(JButton button){
        if(!AuthorizationService.allowed(Permission.AI_ASSISTANT)){
            answer.setText("<html>AI access is not enabled for this account.</html>");
            return;
        }
        String q=question.getText().trim();
        if(q.isBlank())return;
        question.setText("");
        button.setEnabled(false);
        answer.setText("<html><b>Thinking…</b></html>");

        new SwingWorker<NorthStarIntelligenceService.Answer,Void>(){
            @Override protected NorthStarIntelligenceService.Answer doInBackground()throws Exception{
                return service.ask(q);
            }
            @Override protected void done(){
                button.setEnabled(true);
                try{
                    NorthStarIntelligenceService.Answer result=get();
                    String text=result.text().replaceAll("\\s+"," ").trim();
                    if(text.length()>220)text=text.substring(0,220)+"…";
                    String source=result.sources()==null||result.sources().isEmpty()
                            ?""
                            :"<br><span style='color:#7E8A99;font-size:9px'>Source-backed answer</span>";
                    answer.setText("<html>"+html(text)+source+"</html>");
                }catch(Exception ex){
                    answer.setText("<html>Local AI is offline. Open the full workspace to configure Ollama.</html>");
                }
            }
        }.execute();
    }

    private void refreshStatus(){
        new SwingWorker<NorthStarIntelligenceService.Status,Void>(){
            @Override protected NorthStarIntelligenceService.Status doInBackground(){return service.testConnection();}
            @Override protected void done(){
                try{
                    NorthStarIntelligenceService.Status s=get();
                    status.setText(s.online()?"● ONLINE":"○ OFFLINE");
                    status.setForeground(s.online()?Theme.accent():Theme.muted());
                }catch(Exception ex){
                    status.setText("○ OFFLINE");
                    status.setForeground(Theme.muted());
                }
            }
        }.execute();
    }

    private static String html(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
