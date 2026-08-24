package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;

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
        Rectangle target = dashboardBounds(frame, layered);
        target.grow(-8, -8);
        if (target.width < 760 || target.height < 520) {
            Rectangle content = SwingUtilities.convertRectangle(frame.getContentPane(), frame.getContentPane().getBounds(), layered);
            target = new Rectangle(content.x + 12, content.y + 12, Math.max(760, content.width - 24), Math.max(520, content.height - 24));
        }

        OverlayPanel panel = new OverlayPanel(question, answer);
        panel.setName("northstar.ai.analysis.overlay");
        panel.setBounds(start);
        panel.setVisible(true);
        layered.add(panel, JLayeredPane.POPUP_LAYER);
        layered.moveToFront(panel);
        layered.revalidate();
        layered.repaint();

        Rectangle finalTarget = target;
        panel.setCloseAction(() -> animate(panel, layered, panel.getBounds(), start, 300, () -> {
            layered.remove(panel);
            layered.revalidate();
            layered.repaint();
        }));
        animate(panel, layered, start, finalTarget, 380, null);
    }

    private static Rectangle dashboardBounds(JFrame frame, JLayeredPane layered) {
        try {
            Field f = frame.getClass().getDeclaredField("dashboardBody");
            f.setAccessible(true);
            Object body = f.get(frame);
            if (body instanceof Component c && c.isShowing()) {
                return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), layered);
            }
        } catch (Exception ignored) {}

        Container content = frame.getContentPane();
        Rectangle r = SwingUtilities.convertRectangle(content, content.getBounds(), layered);
        int nav = Math.min(210, Math.max(0, r.width / 8));
        return new Rectangle(r.x + nav, r.y + 64, r.width - nav, r.height - 64);
    }

    private static void animate(JComponent c, JLayeredPane layered, Rectangle from, Rectangle to, int duration, Runnable end) {
        final long begin = System.nanoTime();
        Timer timer = new Timer(15, null);
        timer.addActionListener(e -> {
            double t = Math.min(1.0, (System.nanoTime() - begin) / (duration * 1_000_000.0));
            double eased = 1.0 - Math.pow(1.0 - t, 3.0);
            int x = lerp(from.x, to.x, eased);
            int y = lerp(from.y, to.y, eased);
            int w = lerp(from.width, to.width, eased);
            int h = lerp(from.height, to.height, eased);
            c.setBounds(x, y, w, h);
            c.revalidate();
            c.repaint();
            layered.repaint();
            if (t >= 1.0) {
                timer.stop();
                if (end != null) end.run();
            }
        });
        timer.start();
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container ct) {
                Component f = find(ct, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static final class OverlayPanel extends JPanel {
        private Runnable closeAction;

        OverlayPanel(String question, NorthStarIntelligenceService.Answer answer) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(8, 8, 8, 8));

            GlassSurfacePanel shell = new GlassSurfacePanel(24);
            shell.setLayout(new BorderLayout(16, 16));
            shell.setBorder(new EmptyBorder(20, 22, 18, 22));

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            JLabel t = new JLabel("NORTHSTAR INTELLIGENCE");
            t.setForeground(Theme.text());
            t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            JLabel s = new JLabel("Focused analysis • " + shorten(question, 125));
            s.setForeground(Theme.muted());
            s.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            titles.add(t);
            titles.add(Box.createVerticalStrut(4));
            titles.add(s);
            head.add(titles, BorderLayout.WEST);

            JButton close = new JButton("Close");
            close.setPreferredSize(new Dimension(86, 34));
            close.addActionListener(e -> { if (closeAction != null) closeAction.run(); });
            head.add(close, BorderLayout.EAST);
            shell.add(head, BorderLayout.NORTH);

            JPanel columns = new JPanel(new GridLayout(1, 2, 16, 0));
            columns.setOpaque(false);
            columns.add(summary(answer));
            columns.add(IntelligenceEvidencePanel.create(question));
            shell.add(columns, BorderLayout.CENTER);

            JLabel sources = new JLabel(answer.sources() == null || answer.sources().isEmpty()
                    ? "No source files cited"
                    : "Sources • " + String.join("  •  ", answer.sources()));
            sources.setForeground(Theme.muted());
            sources.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            shell.add(sources, BorderLayout.SOUTH);
            add(shell, BorderLayout.CENTER);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = Theme.bg();
            g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 236));
            g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 24, 24);
            g.setPaint(new GradientPaint(0, 0, new Color(255,255,255,18), 0, Math.max(1,getHeight()/2), new Color(255,255,255,0)));
            g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight()/2), 24, 24);
            g.dispose();
            super.paintComponent(g0);
        }

        void setCloseAction(Runnable r) { closeAction = r; }

        private JComponent summary(NorthStarIntelligenceService.Answer a) {
            GlassSurfacePanel p = new GlassSurfacePanel(18);
            p.setLayout(new BorderLayout(0, 10));
            p.setBorder(new EmptyBorder(16, 16, 16, 16));
            JLabel l = new JLabel("AI SUMMARY");
            l.setForeground(Theme.text());
            l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            p.add(l, BorderLayout.NORTH);
            JTextArea text = new JTextArea(a.text());
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setForeground(Theme.text());
            text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            JScrollPane sp = new JScrollPane(text);
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            p.add(sp, BorderLayout.CENTER);
            return p;
        }

        private static String shorten(String s, int n) {
            if (s == null) return "";
            return s.length() > n ? s.substring(0, n - 1) + "…" : s;
        }
    }
}
