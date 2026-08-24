package com.wtm.ui;
import com.wtm.ai.NorthStarIntelligenceService; import com.wtm.security.AuthorizationService; import com.wtm.security.Permission; import javax.swing.*; import javax.swing.border.EmptyBorder; import java.awt.*;
public final class NorthStarIntelligenceCompactPanel extends RoundedPanel {
 private final NorthStarIntelligenceService service=NorthStarIntelligenceService.get(); private final JTextField question=new JTextField(); private final JLabel answer=new JLabel(); private final JLabel status=new JLabel("LOCAL AI"); private final Runnable openFull;
 public NorthStarIntelligenceCompactPanel(Runnable openFull){super(14);this.openFull=openFull;setName("northstar.ai.compact");setLayout(new BorderLayout(0,10));setBackground(Theme.panel());putClientProperty("outlineColor",Theme.border());setBorder(new EmptyBorder(10,12,10,12));add(header(),BorderLayout.NORTH);add(body(),BorderLayout.CENTER);add(composer(),BorderLayout.SOUTH);ThemeStyler.apply(this,Theme.active());refreshStatus();}
 private JComponent header(){JPanel r=new JPanel(new BorderLayout());r.setOpaque(false);JPanel w=new JPanel();w.setOpaque(false);w.setLayout(new BoxLayout(w,BoxLayout.Y_AXIS));JLabel t=new JLabel("NORTHSTAR INTELLIGENCE");t.setForeground(Theme.text());t.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));JLabel s=new JLabel("Operations assistant • adaptive analysis");s.setForeground(Theme.muted());s.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));w.add(t);w.add(s);r.add(w,BorderLayout.WEST);status.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));r.add(status,BorderLayout.EAST);return r;}
 private JComponent body(){JPanel b=new JPanel(new BorderLayout());b.setOpaque(false);answer.setText("<html>Ask about KPIs, schedules, policies, shipments, weather or traffic.<br><span style='color:#7E8A99'>Analytical questions open a full evidence view.</span></html>");answer.setForeground(Theme.muted());answer.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));answer.setVerticalAlignment(SwingConstants.TOP);b.add(answer);return b;}
 private JComponent composer(){JPanel s=new JPanel(new BorderLayout(7,0));s.setOpaque(false);question.setPreferredSize(new Dimension(210,38));question.putClientProperty("JTextField.placeholderText","Ask NorthStar…");s.add(question,BorderLayout.CENTER);NorthStarPrimaryButton ask=new NorthStarPrimaryButton("Ask");ask.setPreferredSize(new Dimension(62,38));JButton open=new JButton("Open");open.setPreferredSize(new Dimension(62,38));JPanel bs=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));bs.setOpaque(false);bs.add(ask);bs.add(open);s.add(bs,BorderLayout.EAST);ask.addActionListener(e->ask(ask));question.addActionListener(e->ask(ask));open.addActionListener(e->{if(openFull!=null)openFull.run();});return s;}
 private void ask(JButton button){
  if(!AuthorizationService.allowed(Permission.AI_ASSISTANT)){answer.setText("AI access is not enabled for this account.");return;}
  String q=question.getText().trim(); if(q.isBlank())return; question.setText(""); button.setEnabled(false); answer.setText("<html><b>Analyzing…</b></html>");
  new SwingWorker<NorthStarIntelligenceService.Answer,Void>(){
   protected NorthStarIntelligenceService.Answer doInBackground()throws Exception{return service.ask(q);}
   protected void done(){button.setEnabled(true); NorthStarIntelligenceService.Answer a;
    try{a=get();}
    catch(Exception ex){String msg=rootMessage(ex);answer.setText("<html><b>AI request failed.</b><br>"+html(shorten(msg,190))+"<br><span style='color:#7E8A99'>Ollama is reachable; this message is the generation error.</span></html>");answer.setToolTipText(msg);return;}
    String t=a.text()==null?"":a.text().replaceAll("\\s+"," ").trim();
    answer.setText("<html><b>Analysis ready.</b><br>"+html(t.length()>170?t.substring(0,170)+"…":t)+"<br><span style='color:#7E8A99'>Opening evidence view…</span></html>");
    try{Window owner=SwingUtilities.getWindowAncestor(NorthStarIntelligenceCompactPanel.this);new IntelligenceAnalysisDialog(owner,q,a).setVisible(true);}catch(Throwable uiEx){String msg=rootMessage(uiEx);answer.setText("<html><b>Analysis complete.</b><br>"+html(t.length()>190?t.substring(0,190)+"…":t)+"<br><span style='color:#7E8A99'>Expanded view error: "+html(shorten(msg,90))+"</span></html>");answer.setToolTipText(msg);}
   }
  }.execute();
 }
 private void refreshStatus(){new SwingWorker<NorthStarIntelligenceService.Status,Void>(){protected NorthStarIntelligenceService.Status doInBackground(){return service.testConnection();}protected void done(){try{var s=get();status.setText(s.online()?"● OLLAMA":"○ OFFLINE");status.setToolTipText(s.detail());status.setForeground(s.online()?Theme.accent():Theme.muted());}catch(Exception e){status.setText("○ OFFLINE");}}}.execute();}
 private static String rootMessage(Throwable t){Throwable c=t;while(c.getCause()!=null)c=c.getCause();String m=c.getMessage();return m==null||m.isBlank()?c.getClass().getSimpleName():m;}
 private static String shorten(String s,int n){if(s==null)return "";return s.length()>n?s.substring(0,n-1)+"…":s;}
 private static String html(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
