package com.wtm.ui;

import com.wtm.callin.*;
import com.wtm.config.*;
import com.wtm.employee.*;
import com.wtm.security.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Application-level call-in workspace.
 *
 * Employee-specific call-in identity (PIN/phone) remains in Employee Operations.
 * This page owns master service state, Twilio/SendGrid configuration, activity,
 * history and local testing for the entire North Star application.
 */
public final class CallInOperationsPanel extends JPanel {
    private final AppConfig config;
    private EmployeeStore.Snapshot snapshot;

    private final DefaultTableModel activityModel=readOnlyModel(
            "Date","Time","Employee #","Employee","Type","Source","Status","Notes");
    private final JTable activityTable=new JTable(activityModel);
    private final DefaultTableModel historyModel=readOnlyModel(
            "Date","Time","Employee #","Employee","Type","Source","Status","Caller","Notes");
    private final JTable historyTable=new JTable(historyModel);

    private final JTextField historySearch=new JTextField();
    private final JComboBox<String> historyType=new JComboBox<>(new String[]{
            "ALL","CALL_OUT","RUNNING_LATE","LEAVING_EARLY","ABSENT","SCHEDULED_ABSENCE","OTHER"
    });

    private final JCheckBox serviceEnabled=new JCheckBox("Enable North Star Call-In service");
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"LOCAL_TEST","TWILIO_WEBHOOK","OFF"});
    private final JTextField webhookPort=new JTextField();
    private final JTextField publicBaseUrl=new JTextField();
    private final JTextField twilioAccountSid=new JTextField();
    private final JPasswordField twilioAuthToken=new JPasswordField();
    private final JTextField twilioFromNumber=new JTextField();
    private final JCheckBox smsNotifications=new JCheckBox("Send management SMS notifications");
    private final JTextField smsRecipients=new JTextField();
    private final JPasswordField sendGridApiKey=new JPasswordField();
    private final JCheckBox emailNotifications=new JCheckBox("Send management email notifications");
    private final JTextField emailFrom=new JTextField();
    private final JTextField emailRecipients=new JTextField();
    private final JLabel serviceStatus=new JLabel();

    private final JTextField testEmployeeNumber=new JTextField();
    private final JPasswordField testPin=new JPasswordField();
    private final JComboBox<String> testType=new JComboBox<>(new String[]{
            "CALL_OUT","RUNNING_LATE","LEAVING_EARLY","OTHER"
    });
    private final JCheckBox testNotify=new JCheckBox("Attempt management notifications");

    private final JLabel todayCount=new JLabel("0");
    private final JLabel todayCallOuts=new JLabel("0");
    private final JLabel lateCount=new JLabel("0");
    private final JLabel listenerState=new JLabel("Disabled");

    public CallInOperationsPanel(AppConfig config){
        AuthorizationService.require(Permission.CALL_IN_ADMINISTRATION);
        this.config=Objects.requireNonNull(config);
        this.snapshot=EmployeeStore.load();

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0,0,0,0));

        JTabbedPane tabs=new JTabbedPane();
        tabs.addTab("Activity",activityPanel());
        tabs.addTab("History",historyPanel());
        tabs.addTab("Settings",settingsPanel());
        tabs.addTab("Testing",testingPanel());
        add(tabs,BorderLayout.CENTER);

        historySearch.getDocument().addDocumentListener(SimpleDocumentListener.of(this::refreshHistory));
        historyType.addActionListener(e->refreshHistory());
        serviceEnabled.addActionListener(e->updateSettingsEnabledState());
        mode.addActionListener(e->updateSettingsEnabledState());
        smsNotifications.addActionListener(e->updateSettingsEnabledState());
        emailNotifications.addActionListener(e->updateSettingsEnabledState());

        loadSettings();
        refreshAll();
        ThemeStyler.apply(this,Theme.active());
    }

    private JComponent activityPanel(){
        JPanel root=new JPanel(new BorderLayout(0,14));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(18,18,18,18));

        JPanel summary=new JPanel(new GridLayout(1,4,12,0));
        summary.setOpaque(false);
        summary.add(summaryCard("TODAY'S CALL-INS",todayCount,"Recorded attendance calls"));
        summary.add(summaryCard("CALL OUTS",todayCallOuts,"Whole-day absences today"));
        summary.add(summaryCard("RUNNING LATE",lateCount,"Late-arrival reports today"));
        summary.add(summaryCard("SERVICE STATUS",listenerState,"Master call-in service"));

        RoundedPanel tableCard=new RoundedPanel(14);
        tableCard.setLayout(new BorderLayout(0,10));
        tableCard.setBorder(new EmptyBorder(14,14,14,14));
        JLabel title=new JLabel("Recent Call-In Activity");
        title.setFont(title.getFont().deriveFont(Font.BOLD,15f));
        activityTable.setRowHeight(29);
        tableCard.add(title,BorderLayout.NORTH);
        tableCard.add(new JScrollPane(activityTable),BorderLayout.CENTER);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        actions.setOpaque(false);
        JButton refresh=new JButton("Refresh");
        refresh.addActionListener(e->refreshAll());
        actions.add(refresh);
        tableCard.add(actions,BorderLayout.SOUTH);

        root.add(summary,BorderLayout.NORTH);
        root.add(tableCard,BorderLayout.CENTER);
        return root;
    }

    private JComponent historyPanel(){
        JPanel root=new JPanel(new BorderLayout(0,12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(18,18,18,18));

        RoundedPanel filters=new RoundedPanel(14);
        filters.setLayout(new GridBagLayout());
        filters.setBorder(new EmptyBorder(12,14,12,14));
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(5,6,5,6); c.fill=GridBagConstraints.HORIZONTAL; c.gridy=0;
        c.gridx=0;c.weightx=0;filters.add(new JLabel("Search"),c);
        c.gridx=1;c.weightx=1;filters.add(historySearch,c);
        c.gridx=2;c.weightx=0;filters.add(new JLabel("Type"),c);
        c.gridx=3;c.weightx=.3;filters.add(historyType,c);
        JButton clear=new JButton("Clear Filters");
        clear.addActionListener(e->{historySearch.setText("");historyType.setSelectedItem("ALL");});
        c.gridx=4;c.weightx=0;filters.add(clear,c);

        RoundedPanel tableCard=new RoundedPanel(14);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(12,12,12,12));
        historyTable.setRowHeight(29);
        tableCard.add(new JScrollPane(historyTable),BorderLayout.CENTER);

        root.add(filters,BorderLayout.NORTH);
        root.add(tableCard,BorderLayout.CENTER);
        return root;
    }

    private JComponent settingsPanel(){
        ScrollForm content=new ScrollForm();
        content.setLayout(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20,22,30,22));
        int y=0;

        addSection(content,y++,"Call-In Service");
        addFull(content,y++,serviceEnabled);
        addField(content,y++,"Mode",mode);
        addField(content,y++,"Webhook port",webhookPort);
        addField(content,y++,"Public HTTPS base URL",publicBaseUrl);
        addFull(content,y++,note("LOCAL_TEST keeps the call-in workflow local. TWILIO_WEBHOOK starts the inbound voice webhook listener when the master service is enabled."));

        addSection(content,y++,"Twilio Voice & SMS");
        addField(content,y++,"Twilio Account SID",twilioAccountSid);
        addField(content,y++,"Twilio Auth Token",twilioAuthToken);
        addField(content,y++,"Twilio SMS from-number",twilioFromNumber);
        addFull(content,y++,smsNotifications);
        addField(content,y++,"Management SMS recipients",smsRecipients);
        addFull(content,y++,note("Telephone API values should use E.164 format, for example +12055551234. Employee profile display numbers may still contain spaces, parentheses or hyphens."));

        addSection(content,y++,"Management Email");
        addField(content,y++,"SendGrid API key",sendGridApiKey);
        addFull(content,y++,emailNotifications);
        addField(content,y++,"Verified email sender",emailFrom);
        addField(content,y++,"Management email recipients",emailRecipients);

        addSection(content,y++,"Service Control");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        actions.setOpaque(false);
        JButton save=new JButton("Save & Apply");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->saveSettings());
        JButton stop=new JButton("Stop Listener");
        stop.addActionListener(e->{CallInServerManager.stop();refreshStatus();});
        JButton refresh=new JButton("Refresh Status");
        refresh.addActionListener(e->refreshStatus());
        actions.add(save);actions.add(stop);actions.add(refresh);actions.add(serviceStatus);
        addFull(content,y++,actions);

        JTextArea security=new JTextArea(
                "Production security: Twilio webhook requests are validated with X-Twilio-Signature using the configured Auth Token and exact public HTTPS URL. Do not expose a host computer directly to the Internet without an approved HTTPS reverse proxy, secure tunnel, function, or future hosted North Star service.");
        security.setEditable(false);security.setLineWrap(true);security.setWrapStyleWord(true);security.setOpaque(false);security.setRows(3);
        addFull(content,y++,security);

        return scroll(content);
    }

    private JComponent testingPanel(){
        JPanel root=new JPanel(new BorderLayout());
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(18,18,18,18));

        RoundedPanel card=new RoundedPanel(14);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(9,7,9,7);c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.WEST;
        int y=0;
        c.gridy=y++;c.gridx=0;c.gridwidth=4;c.weightx=1;
        JLabel intro=new JLabel("<html><b>Local Call-In Test</b><br>Validate employee PINs and the call-in workflow before enabling a production telephone endpoint.</html>");
        card.add(intro,c);c.gridwidth=1;
        addGridField(card,c,y++,"Employee #",testEmployeeNumber,"PIN",testPin);
        addGridField(card,c,y++,"Type",testType,"",new JLabel(""));
        c.gridy=y++;c.gridx=0;c.gridwidth=4;c.weightx=1;card.add(testNotify,c);c.gridwidth=1;
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));buttons.setOpaque(false);
        JButton simulate=new JButton("Simulate Call-In");simulate.putClientProperty("primaryAction",Boolean.TRUE);simulate.addActionListener(e->simulate());
        JButton clear=new JButton("Clear");clear.addActionListener(e->{testEmployeeNumber.setText("");testPin.setText("");testType.setSelectedItem("CALL_OUT");});
        buttons.add(simulate);buttons.add(clear);
        c.gridy=y++;c.gridx=0;c.gridwidth=4;c.weightx=1;card.add(buttons,c);
        c.gridy=y;c.weighty=1;c.fill=GridBagConstraints.BOTH;card.add(Box.createGlue(),c);

        root.add(card,BorderLayout.NORTH);
        return root;
    }

    private RoundedPanel summaryCard(String title,JLabel value,String subtitle){
        RoundedPanel card=new RoundedPanel(14);
        card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14,16,14,16));
        JLabel t=new JLabel(title);t.setForeground(Theme.muted());t.setFont(t.getFont().deriveFont(Font.BOLD,10f));
        value.setForeground(Theme.text());value.setFont(value.getFont().deriveFont(Font.BOLD,23f));
        JLabel sub=new JLabel(subtitle);sub.setForeground(Theme.muted());sub.setFont(sub.getFont().deriveFont(Font.PLAIN,10f));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);value.setAlignmentX(Component.LEFT_ALIGNMENT);sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(t);card.add(Box.createVerticalStrut(7));card.add(value);card.add(Box.createVerticalStrut(3));card.add(sub);
        return card;
    }

    private void loadSettings(){
        serviceEnabled.setSelected(config.callInEnabled);
        mode.setSelectedItem(config.callInMode);
        webhookPort.setText(Integer.toString(config.callInWebhookPort));
        publicBaseUrl.setText(config.callInPublicBaseUrl);
        twilioAccountSid.setText(config.twilioAccountSid);
        twilioAuthToken.setText(config.twilioAuthToken);
        twilioFromNumber.setText(config.callInTwilioFromNumber);
        smsNotifications.setSelected(config.callInSmsNotifications);
        smsRecipients.setText(config.callInSmsRecipients);
        sendGridApiKey.setText(config.sendGridApiKey);
        emailNotifications.setSelected(config.callInEmailNotifications);
        emailFrom.setText(config.callInEmailFrom);
        emailRecipients.setText(config.callInEmailRecipients);
        updateSettingsEnabledState();
        refreshStatus();
    }

    private void saveSettings(){
        try{
            config.callInEnabled=serviceEnabled.isSelected();
            config.callInMode=Objects.toString(mode.getSelectedItem(),"LOCAL_TEST");
            config.callInWebhookPort=Integer.parseInt(webhookPort.getText().trim());
            config.callInPublicBaseUrl=publicBaseUrl.getText().trim();
            config.twilioAccountSid=twilioAccountSid.getText().trim();
            config.twilioAuthToken=new String(twilioAuthToken.getPassword());
            config.callInTwilioFromNumber=twilioFromNumber.getText().trim();
            config.callInSmsNotifications=smsNotifications.isSelected();
            config.callInSmsRecipients=smsRecipients.getText().trim();
            config.sendGridApiKey=new String(sendGridApiKey.getPassword());
            config.callInEmailNotifications=emailNotifications.isSelected();
            config.callInEmailFrom=emailFrom.getText().trim();
            config.callInEmailRecipients=emailRecipients.getText().trim();

            ConfigService.save(config);
            ApiCredentialService.saveFrom(config);
            CallInServerManager.apply(config);
            AuditService.record("Updated master Call-In service settings");
            refreshStatus();
            ThemedDialogs.message(this,"Call-In service settings saved.","Call-In",ThemedDialogs.Kind.INFO);
        }catch(Exception ex){
            ThemedDialogs.message(this,ex.getMessage(),"Call-In Settings Error",ThemedDialogs.Kind.ERROR);
        }
    }

    private void updateSettingsEnabledState(){
        boolean master=serviceEnabled.isSelected();
        boolean twilio=master&&"TWILIO_WEBHOOK".equalsIgnoreCase(Objects.toString(mode.getSelectedItem(),""));
        webhookPort.setEnabled(twilio);publicBaseUrl.setEnabled(twilio);
        twilioAccountSid.setEnabled(master);twilioAuthToken.setEnabled(master);twilioFromNumber.setEnabled(master);
        smsNotifications.setEnabled(master);smsRecipients.setEnabled(master&&smsNotifications.isSelected());
        sendGridApiKey.setEnabled(master);emailNotifications.setEnabled(master);emailFrom.setEnabled(master);emailRecipients.setEnabled(master&&emailNotifications.isSelected());
    }

    private void refreshAll(){
        snapshot=EmployeeStore.load();
        refreshActivity();refreshHistory();refreshStatus();
    }

    private void refreshActivity(){
        activityModel.setRowCount(0);
        List<AttendanceRecord> records=snapshot.attendance.stream()
                .filter(this::isCallInLike)
                .sorted(Comparator.comparing(AttendanceRecord::date,Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AttendanceRecord::time,Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(75).toList();
        for(AttendanceRecord r:records)activityModel.addRow(row(r,false));

        LocalDate today=LocalDate.now();
        long total=recordsForDay(today).size();
        long callouts=recordsForDay(today).stream().filter(r->"CALL_OUT".equals(r.type())||"ABSENT".equals(r.type())).count();
        long late=recordsForDay(today).stream().filter(r->"RUNNING_LATE".equals(r.type())).count();
        todayCount.setText(Long.toString(total));todayCallOuts.setText(Long.toString(callouts));lateCount.setText(Long.toString(late));
    }

    private void refreshHistory(){
        historyModel.setRowCount(0);
        String q=historySearch.getText()==null?"":historySearch.getText().trim().toLowerCase(Locale.ROOT);
        String type=Objects.toString(historyType.getSelectedItem(),"ALL");
        snapshot.attendance.stream().filter(this::isCallInLike)
                .filter(r->"ALL".equals(type)||type.equalsIgnoreCase(r.type()))
                .filter(r->{
                    EmployeeProfile e=employee(r.employeeId());
                    String hay=(r.type()+" "+r.source()+" "+r.status()+" "+r.notes()+" "+(e==null?"":e.employeeNumber()+" "+e.name())).toLowerCase(Locale.ROOT);
                    return q.isBlank()||hay.contains(q);
                })
                .sorted(Comparator.comparing(AttendanceRecord::date,Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AttendanceRecord::time,Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(r->historyModel.addRow(row(r,true)));
    }

    private List<AttendanceRecord> recordsForDay(LocalDate day){
        return snapshot.attendance.stream().filter(this::isCallInLike).filter(r->day.equals(r.date())).toList();
    }

    private boolean isCallInLike(AttendanceRecord r){
        if(r==null)return false;
        String source=r.source()==null?"":r.source().toUpperCase(Locale.ROOT);
        return source.contains("CALL")||source.contains("TWILIO")||source.contains("LOCAL_TEST")
                ||Set.of("CALL_OUT","RUNNING_LATE","LEAVING_EARLY").contains(r.type());
    }

    private Object[] row(AttendanceRecord r,boolean caller){
        EmployeeProfile e=employee(r.employeeId());
        List<Object> v=new ArrayList<>();
        v.add(r.date()==null?"":r.date().toString());v.add(r.time()==null?"":r.time().withSecond(0).withNano(0).toString());
        v.add(e==null?"":e.employeeNumber());v.add(e==null?"Unknown Employee":e.name());
        v.add(displayType(r.type()));v.add(r.source());v.add(r.status());
        if(caller)v.add(r.callerPhone());
        v.add(r.notes());return v.toArray();
    }

    private EmployeeProfile employee(String id){
        if(id==null)return null;
        for(EmployeeProfile e:snapshot.employees)if(id.equals(e.id()))return e;
        return null;
    }

    private void refreshStatus(){
        String status=CallInServerManager.status();
        serviceStatus.setText("Status: "+status);
        listenerState.setText(!config.callInEnabled?"Disabled":("TWILIO_WEBHOOK".equalsIgnoreCase(config.callInMode)?status:"Ready"));
    }

    private void simulate(){
        if(!config.callInEnabled){
            ThemedDialogs.message(this,"Enable the master Call-In service in Settings before running a call-in test.","Call-In Disabled",ThemedDialogs.Kind.WARNING);return;
        }
        EmployeeStore.Snapshot fresh=EmployeeStore.load();
        EmployeeProfile employee=EmployeeService.findByEmployeeNumber(fresh,testEmployeeNumber.getText());
        if(employee==null){ThemedDialogs.message(this,"Employee number was not found.","Call-In Test",ThemedDialogs.Kind.ERROR);return;}
        char[] pin=testPin.getPassword();boolean valid;
        try{valid=EmployeeStore.verifyPin(employee,pin);}finally{Arrays.fill(pin,'\0');}
        if(!valid){ThemedDialogs.message(this,"Call-in PIN is incorrect or has not been configured.","Call-In Test",ThemedDialogs.Kind.ERROR);return;}
        AttendanceRecord record=EmployeeService.recordCallIn(fresh,employee,Objects.toString(testType.getSelectedItem(),"OTHER"),"LOCAL_TEST","Local North Star call-in simulation","","local-test-"+System.currentTimeMillis());
        if(testNotify.isSelected())CallInNotifier.notifyManagement(config,employee,record);
        testPin.setText("");refreshAll();
        ThemedDialogs.message(this,employee.name()+" "+displayType(record.type())+" call-in recorded"+(testNotify.isSelected()?" and notifications attempted.":"."),"Call-In Test Complete",ThemedDialogs.Kind.INFO);
    }

    private static String displayType(String type){
        if(type==null)return "";return switch(type){case "CALL_OUT"->"Call Out";case "RUNNING_LATE"->"Running Late";case "LEAVING_EARLY"->"Leaving Early";case "SCHEDULED_ABSENCE"->"Scheduled Absence";default->type.replace('_',' ');};
    }

    private static JLabel note(String html){JLabel l=new JLabel("<html>"+html+"</html>");l.setForeground(Theme.muted());return l;}
    private static void addSection(JPanel p,int row,String text){GridBagConstraints c=base(row);c.gridx=0;c.gridwidth=2;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(row==0?0:22,4,8,4);JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(Font.BOLD,14f));l.setBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.border()));p.add(l,c);}
    private static void addField(JPanel p,int row,String label,Component component){normalize(component);GridBagConstraints c=base(row);c.gridx=0;c.weightx=0;c.fill=GridBagConstraints.NONE;c.insets=new Insets(8,4,8,14);JLabel l=new JLabel(label);l.setPreferredSize(new Dimension(190,38));p.add(l,c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(8,0,8,4);p.add(component,c);}
    private static void addFull(JPanel p,int row,Component component){GridBagConstraints c=base(row);c.gridx=0;c.gridwidth=2;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(7,4,7,4);p.add(component,c);}
    private static GridBagConstraints base(int row){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.anchor=GridBagConstraints.WEST;return c;}
    private static void normalize(Component c){if(c instanceof JTextField||c instanceof JPasswordField||c instanceof JComboBox<?>){Dimension d=c.getPreferredSize();c.setPreferredSize(new Dimension(Math.max(180,d.width),42));c.setMinimumSize(new Dimension(140,42));c.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));}}
    private static JComponent scroll(Component c){JScrollPane s=new JScrollPane(c);s.setBorder(BorderFactory.createEmptyBorder());s.getVerticalScrollBar().setUnitIncrement(18);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return s;}
    private static void addGridField(JPanel p,GridBagConstraints c,int row,String l1,Component a,String l2,Component b){c.gridy=row;c.gridwidth=1;c.gridx=0;c.weightx=0;p.add(new JLabel(l1),c);c.gridx=1;c.weightx=.55;p.add(a,c);c.gridx=2;c.weightx=0;p.add(new JLabel(l2),c);c.gridx=3;c.weightx=.35;p.add(b,c);}
    private static DefaultTableModel readOnlyModel(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}

    private static final class ScrollForm extends JPanel implements Scrollable{
        @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(900,650);} @Override public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 18;} @Override public int getScrollableBlockIncrement(Rectangle r,int o,int d){return 100;} @Override public boolean getScrollableTracksViewportWidth(){return true;} @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }
    @FunctionalInterface private interface ChangeAction{void run();}
    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener{
        private final ChangeAction action;private SimpleDocumentListener(ChangeAction a){action=a;}static SimpleDocumentListener of(ChangeAction a){return new SimpleDocumentListener(a);}public void insertUpdate(javax.swing.event.DocumentEvent e){action.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){action.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){action.run();}
    }
}
