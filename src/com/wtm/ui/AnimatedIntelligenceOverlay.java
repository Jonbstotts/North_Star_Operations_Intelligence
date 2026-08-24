package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Animated in-dashboard NorthStar Intelligence analysis surface. */
public final class AnimatedIntelligenceOverlay {
    private AnimatedIntelligenceOverlay() {}

    public static void show(JFrame frame, JComponent source, String question, NorthStarIntelligenceService.Answer answer) {
        if (frame == null || source == null || !source.isShowing()) {
            IntelligenceAnalysisDialog d = new IntelligenceAnalysisDialog(frame, question, answer);
            d.setVisible(true);
            return;
        }
        JLayeredPane layered = frame.getLayeredPane();
        Component existing = find(layered, "northstar.ai.analysis.overlay");
        if (existing != null) layered.remove(existing);

        Rectangle start = SwingUtilities.convertRectangle(source.getParent(), source.getBounds(), layered);
        Rectangle content = SwingUtilities.convertRectangle(frame.getContentPane(), frame.getContentPane().getBounds(), layered);
        int leftInset = Math.max(start.x - content.x, 18);
        int top = Math.max(content.y + 70, start.y - 10);
        int right = content.x + content.width - 24;
        int bottom = content.y + Math.max(420, (int)(content.height * 0.72));
        Rectangle target = new Rectangle(content.x + leftInset, top,
                Math.max(720, right - (content.x + leftInset)),
                Math.max(460, bottom - top));
        if (target.x + target.width > content.x + content.width - 16)
            target.width = content.x + content.width - 16 - target.x;
        if (target.y + target.height > content.y + content.height - 16)
            target.height = content.y + content.height - 16 - target.y;

        OverlayPanel panel = new OverlayPanel(question, answer);
        panel.setName("northstar.ai.analysis.overlay");
        panel.setBounds(start);
        panel.setVisible(true);
        layered.add(panel, JLayeredPane.POPUP_LAYER);
        layered.moveToFront(panel);
        layered.revalidate(); layered.repaint();

        panel.setCloseAction(() -> animate(panel, layered, panel.getBounds(), start, 260, () -> {
            layered.remove(panel); layered.revalidate(); layered.repaint();
        }));
        animate(panel, layered, start, target, 340, null);
    }

    private static void animate(JComponent c, JLayeredPane layered, Rectangle from, Rectangle to, int duration, Runnable end) {
        final long begin = System.nanoTime();
        Timer timer = new Timer(15, null);
        timer.addActionListener(e -> {
            double t = Math.min(1.0, (System.nanoTime() - begin) / (duration * 1_000_000.0));
            double eased = 1.0 - Math.pow(1.0 - t, 3.0);
            int x = lerp(from.x,to.x,eased), y = lerp(from.y,to.y,eased),
                    w = lerp(from.width,to.width,eased), h = lerp(from.height,to.height,eased);
            c.setBounds(x,y,w,h); c.revalidate(); c.repaint(); layered.repaint();
            if (t >= 1.0) { timer.stop(); if (end != null) end.run(); }
        });
        timer.start();
    }
    private static int lerp(int a,int b,double t){return (int)Math.round(a+(b-a)*t);}
    private static Component find(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container ct){Component f=find(ct,name);if(f!=null)return f;}}return null;}

    private static final class OverlayPanel extends JPanel {
        private Runnable closeAction;
        OverlayPanel(String question, NorthStarIntelligenceService.Answer answer) {
            setOpaque(false); setLayout(new BorderLayout()); setBorder(new EmptyBorder(10,10,10,10));
            GlassSurfacePanel shell = new GlassSurfacePanel(22);
            shell.setLayout(new BorderLayout(14,14)); shell.setBorder(new EmptyBorder(18,20,18,20));
            JPanel head=new JPanel(new BorderLayout());head.setOpaque(false);
            JPanel titles=new JPanel();titles.setOpaque(false);titles.setLayout(new BoxLayout(titles,BoxLayout.Y_AXIS));
            JLabel t=new JLabel("NORTHSTAR INTELLIGENCE");t.setForeground(Theme.text());t.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
            JLabel s=new JLabel("Adaptive analysis • "+shorten(question,105));s.setForeground(Theme.muted());s.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
            titles.add(t);titles.add(Box.createVerticalStrut(3));titles.add(s);head.add(titles,BorderLayout.WEST);
            JButton close=new JButton("Close");close.addActionListener(e->{if(closeAction!=null)closeAction.run();});head.add(close,BorderLayout.EAST);
            shell.add(head,BorderLayout.NORTH);

            JPanel columns=new JPanel(new GridLayout(1,2,14,0));columns.setOpaque(false);
            columns.add(summary(answer));
            columns.add(IntelligenceEvidencePanel.create(question));
            shell.add(columns,BorderLayout.CENTER);
            JLabel sources=new JLabel(answer.sources()==null||answer.sources().isEmpty()?"No source files cited":"Sources • "+String.join("  •  ",answer.sources()));
            sources.setForeground(Theme.muted());sources.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));shell.add(sources,BorderLayout.SOUTH);
            add(shell,BorderLayout.CENTER);
        }
        void setCloseAction(Runnable r){closeAction=r;}
        private JComponent summary(NorthStarIntelligenceService.Answer a){
            GlassSurfacePanel p=new GlassSurfacePanel(16);p.setLayout(new BorderLayout(0,8));p.setBorder(new EmptyBorder(14,14,14,14));
            JLabel l=new JLabel("AI SUMMARY");l.setForeground(Theme.text());l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));p.add(l,BorderLayout.NORTH);
            JTextArea text=new JTextArea(a.text());text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);text.setForeground(Theme.text());text.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));
            JScrollPane sp=new JScrollPane(text);sp.setBorder(null);sp.setOpaque(false);sp.getViewport().setOpaque(false);p.add(sp,BorderLayout.CENTER);return p;
        }
        private static String shorten(String s,int n){if(s==null)return "";return s.length()>n?s.substring(0,n-1)+"…":s;}
    }
}
