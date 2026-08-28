package com.wtm.modular.ui;

import com.wtm.ingest.GmailDataPathService;
import com.wtm.ingest.TrustedSenderPolicy;
import com.wtm.ui.Theme;
import com.wtm.ui.ThemeStyler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Set;

/**
 * Passive trusted-sender management for the Gmail DataPath connector.
 *
 * <p>Security remains fail-closed: no configured trusted senders means Gmail
 * document ingestion is blocked. This class owns no global AWT listener or
 * delayed lifecycle; WorkspaceLifecycleV3 invokes {@link #apply(Component)} at
 * explicit workspace/navigation boundaries.</p>
 */
public final class TrustedEmailUiGuard {
    private static final String INSTALLED = "northstar.gmail.trustedSenderUi";

    private TrustedEmailUiGuard() {}

    public static void apply(Component component) {
        scan(component);
    }

    private static void scan(Component component) {
        if (component == null) return;

        if (component instanceof JComponent jc
                && Boolean.TRUE.equals(jc.getClientProperty(INSTALLED))) {
            return;
        }

        if (component instanceof Container container) {
            if (isGmailDataPathCard(container)) {
                installTrustedSenderUi(container);
                return;
            }
            for (Component child : container.getComponents()) scan(child);
        }
    }

    private static boolean isGmailDataPathCard(Container container) {
        if (!"com.wtm.ui.GlassSurfacePanel".equals(container.getClass().getName())) return false;
        return containsLabel(container, "GMAIL DATAPATH");
    }

    private static boolean containsLabel(Container container, String needle) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                String text = label.getText();
                if (text != null && text.toUpperCase().contains(needle)) return true;
            }
            if (child instanceof Container nested && containsLabel(nested, needle)) return true;
        }
        return false;
    }

    private static void installTrustedSenderUi(Container card) {
        if (!(card instanceof JComponent jc)) return;
        if (Boolean.TRUE.equals(jc.getClientProperty(INSTALLED))) return;
        if (!(card.getLayout() instanceof BorderLayout)) return;

        jc.putClientProperty(INSTALLED, Boolean.TRUE);
        JComponent security = buildTrustedSenderPanel();
        card.add(security, BorderLayout.SOUTH);
        ThemeStyler.apply(security, Theme.active());
        card.revalidate();
        card.repaint();
    }

    private static JComponent buildTrustedSenderPanel() {
        GmailDataPathService gmail = GmailDataPathService.get();

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(14, 0, 0, 0));

        JPanel heading = new JPanel(new BorderLayout(8, 4));
        heading.setOpaque(false);
        JLabel title = new JLabel("TRUSTED EMAIL SENDERS");
        title.setForeground(Theme.text());
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        JLabel help = new JLabel(
                "<html>Only exact addresses in this list may submit Gmail DataPath CSV documents. " +
                "If the list is empty, email document import is blocked.</html>"
        );
        help.setForeground(Theme.muted());
        heading.add(title, BorderLayout.NORTH);
        heading.add(help, BorderLayout.CENTER);
        root.add(heading, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        try {
            for (String sender : TrustedSenderPolicy.parse(gmail.approvedSenders())) {
                model.addElement(sender);
            }
        } catch (RuntimeException ignored) {
            // Keep the visible list empty if old preferences contain invalid data.
            // The fail-closed ingestion policy still prevents unsafe imports.
        }

        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(3);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(320, 82));
        root.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        JLabel inputLabel = new JLabel("Email address");
        inputLabel.setForeground(Theme.text());
        JTextField input = new JTextField();
        input.setToolTipText("Enter one complete email address, for example manager@company.com");
        JButton add = new JButton("Add Trusted Sender");
        inputRow.add(inputLabel, BorderLayout.WEST);
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(add, BorderLayout.EAST);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        actionRow.setOpaque(false);
        JButton remove = new JButton("Remove Selected");
        JButton save = new JButton("Save Trusted Senders");
        JLabel state = new JLabel();
        actionRow.add(remove);
        actionRow.add(save);
        actionRow.add(state);

        controls.add(inputRow);
        controls.add(actionRow);
        root.add(controls, BorderLayout.SOUTH);

        Runnable refreshState = () -> {
            int count = model.getSize();
            if (count == 0) {
                state.setText("BLOCKED • no trusted senders configured");
                state.setForeground(new Color(224, 170, 75));
            } else {
                state.setText("PROTECTED • " + count
                        + (count == 1 ? " trusted sender" : " trusted senders"));
                state.setForeground(new Color(40, 205, 150));
            }
        };

        Runnable persist = () -> {
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < model.size(); i++) {
                if (i > 0) raw.append(", ");
                raw.append(model.get(i));
            }
            gmail.setApprovedSenders(raw.toString());
            refreshState.run();
        };

        ActionListener addSender = e -> {
            try {
                Set<String> parsed = TrustedSenderPolicy.parse(input.getText());
                if (parsed.size() != 1) {
                    throw new SecurityException("Enter one complete email address.");
                }
                String sender = parsed.iterator().next();
                if (!contains(model, sender)) model.addElement(sender);
                input.setText("");
                persist.run();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(
                        root,
                        ex.getMessage(),
                        "Trusted Email Sender",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        };

        add.addActionListener(addSender);
        input.addActionListener(addSender);
        remove.addActionListener(e -> {
            int selected = list.getSelectedIndex();
            if (selected >= 0) {
                model.remove(selected);
                persist.run();
            }
        });
        save.addActionListener(e -> {
            persist.run();
            JOptionPane.showMessageDialog(
                    root,
                    model.isEmpty()
                            ? "Trusted sender list saved. Gmail document ingestion remains blocked until at least one sender is added."
                            : "Trusted sender list saved. Only listed addresses may submit Gmail DataPath documents.",
                    "Trusted Email Senders",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });

        refreshState.run();
        return root;
    }

    private static boolean contains(DefaultListModel<String> model, String value) {
        for (int i = 0; i < model.size(); i++) {
            if (value.equalsIgnoreCase(model.get(i))) return true;
        }
        return false;
    }
}
