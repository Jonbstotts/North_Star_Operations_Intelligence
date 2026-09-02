package com.wtm.ui;

import com.wtm.ingest.DataIngestionService;
import com.wtm.ingest.GmailDataPathService;
import com.wtm.ingest.IngestionRecord;
import com.wtm.ingest.TrustedSenderPolicy;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Canonical Data Collection workspace.
 *
 * <p>Owns file/DVIEW CSV ingestion, ingestion history and the Gmail
 * DataPath connector including its fail-closed trusted-sender policy.
 * No runtime component discovery or reflective injector is required.</p>
 */
public final class DataCollectionPanel extends JPanel {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final DataIngestionService ingestion = DataIngestionService.get();
    private final GmailDataPathService gmail = GmailDataPathService.get();
    private final DefaultListModel<String> trustedSenders = new DefaultListModel<>();
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"Received", "Source", "File", "Type", "Status", "Rows", "Message"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel sourceModel = new DefaultTableModel(
            new Object[]{"Recognized Feed", "Status", "Last Updated"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JLabel gmailStatus = new JLabel();
    private final JLabel trustedState = new JLabel();
    private final JLabel lastSync = new JLabel();

    public DataCollectionPanel() {
        AuthorizationService.require(Permission.DATA_REFRESH);
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(18, 18, 18, 18));
        setBackground(Theme.bg());

        add(header(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Data Sources & Import", importPanel());
        tabs.addTab("Gmail DataPath", gmailPanel());
        tabs.addTab("Import History", historyPanel());
        add(tabs, BorderLayout.CENTER);

        reloadTrustedSenders();
        refreshSources();
        refreshHistory();
        refreshGmailStatus();
        ThemeStyler.apply(this, Theme.active());
    }

    private JComponent header() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Data Collection");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        JLabel subtitle = new JLabel(
                "Operational source hub • validated data is stored locally and made available to NorthStar Intelligence.");
        subtitle.setForeground(Theme.muted());
        panel.add(title);
        panel.add(Box.createVerticalStrut(3));
        panel.add(subtitle);
        return panel;
    }

    private JComponent importPanel() {
        JPanel page = new JPanel(new BorderLayout(10, 12));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(12, 4, 4, 4));

        JPanel controls = card();
        controls.setLayout(new BorderLayout());

        JPanel controlContent = new JPanel();
        controlContent.setOpaque(false);
        controlContent.setLayout(new BoxLayout(controlContent, BoxLayout.Y_AXIS));

        JLabel heading = sectionTitle("FILE & DVIEW EXPORT INGESTION");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlContent.add(heading);
        controlContent.add(Box.createVerticalStrut(6));

        JLabel copy = new JLabel(
                "<html>Import recognized CSV exports directly or place files in the managed incoming folder. " +
                "Unknown schemas are held for review instead of being guessed.</html>");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlContent.add(copy);
        controlContent.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton importCsv = new JButton("Import CSV");
        JButton openIncoming = new JButton("Open Incoming Folder");
        JButton scan = new JButton("Scan Folder Now");
        actions.add(importCsv);
        actions.add(openIncoming);
        actions.add(scan);
        actions.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, actions.getPreferredSize().height));
        controlContent.add(actions);
        controlContent.add(Box.createVerticalStrut(8));

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.setOpaque(false);
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox watch = new JCheckBox("Watch incoming folder", ingestion.watchEnabled());
        JCheckBox autoImport = new JCheckBox("Validated auto-import", ingestion.autoImport());
        options.add(watch);
        options.add(autoImport);
        options.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, options.getPreferredSize().height));
        controlContent.add(options);

        // BorderLayout.NORTH deliberately owns the vertical anchor. The card may
        // grow with the surrounding workspace, but its controls stay at the top.
        controls.add(controlContent, BorderLayout.NORTH);

        importCsv.addActionListener(e -> chooseAndImportCsv());
        openIncoming.addActionListener(e -> openIncomingFolder());
        scan.addActionListener(e -> {
            var records = ingestion.scanIncoming();
            refreshSources();
            refreshHistory();
            showMessage("Incoming folder scan complete • " + records.size() + " processed result(s).");
        });
        watch.addActionListener(e -> ingestion.setWatchEnabled(watch.isSelected()));
        autoImport.addActionListener(e -> ingestion.setAutoImport(autoImport.isSelected()));

        JTable sources = new JTable(sourceModel);
        sources.setRowHeight(28);
        JScrollPane sourceScroll = new JScrollPane(sources);
        sourceScroll.setBorder(BorderFactory.createTitledBorder("Recognized Operational Feeds"));

        page.add(controls, BorderLayout.NORTH);
        page.add(sourceScroll, BorderLayout.CENTER);
        return page;
    }

    private JComponent gmailPanel() {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(12, 4, 4, 4));
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));

        JPanel connector = card();
        connector.setLayout(new BorderLayout(10, 10));
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(sectionTitle("GMAIL DATAPATH • READ-ONLY CONNECTOR"), BorderLayout.WEST);
        head.add(gmailStatus, BorderLayout.EAST);
        connector.add(head, BorderLayout.NORTH);

        JPanel connectActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        connectActions.setOpaque(false);
        JButton chooseJson = new JButton("Choose OAuth JSON");
        JButton connect = new JButton("Connect Gmail");
        JButton check = new JButton("Check Now");
        connectActions.add(chooseJson);
        connectActions.add(connect);
        connectActions.add(check);
        connector.add(connectActions, BorderLayout.CENTER);
        lastSync.setForeground(Theme.muted());
        connector.add(lastSync, BorderLayout.SOUTH);

        chooseJson.addActionListener(e -> chooseOAuthJson());
        connect.addActionListener(e -> connectGmail(connect));
        check.addActionListener(e -> checkGmail(check));

        JPanel trusted = card();
        trusted.setLayout(new BorderLayout(10, 10));
        JPanel trustedHead = new JPanel();
        trustedHead.setOpaque(false);
        trustedHead.setLayout(new BoxLayout(trustedHead, BoxLayout.Y_AXIS));
        trustedHead.add(sectionTitle("TRUSTED EMAIL SENDERS"));
        JLabel note = new JLabel(
                "<html>Only exact addresses in this list may submit CSV documents. " +
                "If the list is empty, Gmail ingestion is blocked.</html>");
        trustedHead.add(note);
        trustedHead.add(Box.createVerticalStrut(4));
        trustedHead.add(trustedState);
        trusted.add(trustedHead, BorderLayout.NORTH);

        JList<String> senderList = new JList<>(trustedSenders);
        senderList.setVisibleRowCount(5);
        trusted.add(new JScrollPane(senderList), BorderLayout.CENTER);

        JPanel trustedControls = new JPanel();
        trustedControls.setOpaque(false);
        trustedControls.setLayout(new BoxLayout(trustedControls, BoxLayout.Y_AXIS));
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        JTextField address = new JTextField();
        JButton add = new JButton("Add Trusted Sender");
        addRow.add(address, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        removeRow.setOpaque(false);
        JButton remove = new JButton("Remove Selected");
        removeRow.add(remove);
        trustedControls.add(addRow);
        trustedControls.add(removeRow);
        trusted.add(trustedControls, BorderLayout.SOUTH);

        Runnable addSender = () -> {
            try {
                Set<String> parsed = TrustedSenderPolicy.parse(address.getText());
                if (parsed.size() != 1) throw new IllegalArgumentException("Enter one complete email address.");
                String sender = parsed.iterator().next();
                if (!containsTrusted(sender)) trustedSenders.addElement(sender);
                address.setText("");
                persistTrustedSenders();
            } catch (RuntimeException ex) {
                showWarning(ex.getMessage());
            }
        };
        add.addActionListener(e -> addSender.run());
        address.addActionListener(e -> addSender.run());
        remove.addActionListener(e -> {
            int selected = senderList.getSelectedIndex();
            if (selected >= 0) {
                trustedSenders.remove(selected);
                persistTrustedSenders();
            }
        });

        JPanel schedule = card();
        schedule.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JCheckBox autoCheck = new JCheckBox("Automatic Gmail checks", gmail.autoCheck());
        JSpinner interval = new JSpinner(new SpinnerNumberModel(gmail.intervalMinutes(), 5, 240, 5));
        schedule.add(autoCheck);
        schedule.add(new JLabel("Interval (minutes)"));
        schedule.add(interval);
        autoCheck.addActionListener(e -> gmail.setAutoCheck(autoCheck.isSelected()));
        interval.addChangeListener(e -> gmail.setIntervalMinutes((Integer) interval.getValue()));

        page.add(connector);
        page.add(Box.createVerticalStrut(10));
        page.add(trusted);
        page.add(Box.createVerticalStrut(10));
        page.add(schedule);
        page.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JComponent historyPanel() {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(12, 4, 4, 4));
        JTable table = new JTable(historyModel);
        table.setRowHeight(28);
        page.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh History");
        refresh.addActionListener(e -> refreshHistory());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(refresh);
        page.add(south, BorderLayout.SOUTH);
        return page;
    }

    private JPanel card() {
        JPanel panel = new JPanel();
        panel.setBackground(Theme.panel());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border(), 1, true),
                new EmptyBorder(14, 14, 14, 14)));
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        return label;
    }

    private void chooseAndImportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import NorthStar CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            IngestionRecord record = ingestion.importFile(chooser.getSelectedFile().toPath(), "Data Collection");
            refreshSources();
            refreshHistory();
            showMessage(record.status() + " • " + record.detectedType() + " • " + record.records() + " rows");
        } catch (Exception ex) {
            showWarning(rootMessage(ex));
        }
    }

    private void openIncomingFolder() {
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop folder launch is unavailable.");
            Desktop.getDesktop().open(ingestion.incoming().toFile());
        } catch (Exception ex) {
            showWarning(rootMessage(ex));
        }
    }

    private void chooseOAuthJson() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Google OAuth Desktop Client JSON");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            gmail.installClientJson(chooser.getSelectedFile().toPath());
            refreshGmailStatus();
        } catch (Exception ex) {
            showWarning(rootMessage(ex));
        }
    }

    private void connectGmail(JButton button) {
        button.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return gmail.connectInteractive(); }
            @Override protected void done() {
                button.setEnabled(true);
                try { showMessage("Connected to " + get()); }
                catch (Exception ex) { showWarning(rootMessage(ex)); }
                refreshGmailStatus();
            }
        }.execute();
    }

    private void checkGmail(JButton button) {
        persistTrustedSenders();
        button.setEnabled(false);
        new SwingWorker<GmailDataPathService.SyncResult, Void>() {
            @Override protected GmailDataPathService.SyncResult doInBackground() throws Exception {
                return gmail.syncNow();
            }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    var result = get();
                    showMessage("Gmail DataPath sync complete.\nMessages scanned: " + result.messages()
                            + "\nNew CSV attachments: " + result.csvAttachments()
                            + "\nImported: " + result.imported()
                            + "\nDuplicate / review: " + result.duplicateOrReview());
                    refreshHistory();
                    refreshSources();
                } catch (Exception ex) {
                    showWarning(rootMessage(ex));
                }
                refreshGmailStatus();
            }
        }.execute();
    }

    private void refreshSources() {
        sourceModel.setRowCount(0);
        for (DataIngestionService.Type type : DataIngestionService.Type.values()) {
            if (type == DataIngestionService.Type.UNKNOWN) continue;
            Path file = ingestion.dataFile(type);
            String status = Files.isRegularFile(file) ? "Ready" : "Not loaded";
            String updated = "—";
            if (Files.isRegularFile(file)) {
                try {
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    updated = FILE_TIME.format(modified.atZone(ZoneId.systemDefault()));
                } catch (Exception ignored) {}
            }
            sourceModel.addRow(new Object[]{type.name().replace('_', ' '), status, updated});
        }
    }

    private void refreshHistory() {
        historyModel.setRowCount(0);
        for (IngestionRecord record : ingestion.history()) {
            historyModel.addRow(new Object[]{record.receivedAt(), record.source(), record.originalName(),
                    record.detectedType(), record.status(), record.records(), record.message()});
        }
    }

    private void reloadTrustedSenders() {
        trustedSenders.clear();
        try {
            for (String sender : TrustedSenderPolicy.parse(gmail.approvedSenders())) trustedSenders.addElement(sender);
        } catch (RuntimeException ignored) {
            // Keep the list empty. GmailDataPathService remains fail-closed.
        }
        refreshTrustedState();
    }

    private void persistTrustedSenders() {
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < trustedSenders.size(); i++) {
            if (i > 0) raw.append(", ");
            raw.append(trustedSenders.get(i));
        }
        gmail.setApprovedSenders(raw.toString());
        refreshTrustedState();
    }

    private boolean containsTrusted(String value) {
        for (int i = 0; i < trustedSenders.size(); i++)
            if (value.equalsIgnoreCase(trustedSenders.get(i))) return true;
        return false;
    }

    private void refreshTrustedState() {
        int count = trustedSenders.size();
        if (count == 0) {
            trustedState.setText("BLOCKED • no trusted senders configured");
            trustedState.setForeground(new Color(224, 170, 75));
        } else {
            trustedState.setText("PROTECTED • " + count + (count == 1 ? " trusted sender" : " trusted senders"));
            trustedState.setForeground(new Color(40, 205, 150));
        }
    }

    private void refreshGmailStatus() {
        GmailDataPathService.Status status = gmail.status();
        gmailStatus.setText(status.connected()
                ? "● CONNECTED • " + status.mailbox()
                : status.configured() ? "● READY TO AUTHORIZE" : "○ NOT CONFIGURED");
        gmailStatus.setForeground(status.connected() ? new Color(40, 205, 150) : Theme.muted());
        long epoch = gmail.lastSyncEpoch();
        lastSync.setText(epoch <= 0 ? "Last sync: never"
                : "Last sync: " + FILE_TIME.format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())));
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "NorthStar Data Collection", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "NorthStar Data Collection", JOptionPane.WARNING_MESSAGE);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
