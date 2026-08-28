package com.wtm.ui;

import com.wtm.callin.*;
import com.wtm.config.*;
import com.wtm.employee.*;
import com.wtm.media.*;
import com.wtm.security.*;
import com.wtm.util.PhoneNumbers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Management-only personnel workspace.
 *
 * Employee Operations is the authoritative source for employee identity,
 * recognition dates, training/qualifications, attendance/call-ins,
 * performance history, and assignment eligibility.
 */
public final class EmployeeOperationsPanel extends JPanel {
    private final AppConfig config;
    private EmployeeStore.Snapshot snapshot;

    private final DefaultTableModel employeeModel=
            readOnlyModel("Employee #","Name","Department","Shift","Active");
    private final JTable employeeTable=new JTable(employeeModel);

    private final JTextField employeeNumber=new JTextField();
    private final JTextField employeeName=new JTextField();
    private final JTextField shortName=new JTextField();
    private final JTextField department=new JTextField();
    private final JTextField shift=new JTextField();
    private final JTextField hireDate=new JTextField();
    private final JTextField birthday=new JTextField();
    private final JTextField phone=new JTextField();
    private final JTextField photoAsset=new JTextField();
    private final JCheckBox active=new JCheckBox("Active employee");
    private final JCheckBox celebrationAnnouncements=new JCheckBox(
            "Celebration announcements");
    private final JCheckBox birthdayEnabled=new JCheckBox("Birthday recognition");
    private final JCheckBox anniversaryEnabled=new JCheckBox("Anniversary recognition");
    private final JCheckBox employeeOfMonth=new JCheckBox("Employee of the Month");
    private final JPasswordField callInPin=new JPasswordField();

    private final DefaultTableModel trainingModel=
            readOnlyModel(
                    "Category","Qualification","Completed","Expires",
                    "Trainer","Status","Notes");
    private final JTable trainingTable=new JTable(trainingModel);
    private final JComboBox<String> trainingCategory=new JComboBox<>(
            new String[]{"HAZMAT","EQUIPMENT","WORK AREA","SAFETY","OTHER"});
    private final JTextField qualification=new JTextField();
    private final JTextField trainingCompleted=new JTextField();
    private final JTextField trainingExpires=new JTextField();
    private final JTextField trainer=new JTextField();
    private final JTextField trainingNotes=new JTextField();

    private final DefaultTableModel attendanceModel=
            readOnlyModel(
                    "Date","Time","Type","Source","Status","Notes");
    private final JTable attendanceTable=new JTable(attendanceModel);
    private final JComboBox<String> attendanceType=new JComboBox<>(
            new String[]{
                    "CALL_OUT","RUNNING_LATE","LEAVING_EARLY",
                    "ABSENT","SCHEDULED_ABSENCE","OTHER"
            });
    private final JTextField attendanceDate=new JTextField();
    private final JTextField attendanceTime=new JTextField();
    private final JTextField attendanceNotes=new JTextField();

    private final DefaultTableModel performanceModel=
            readOnlyModel(
                    "Date","Metric","Value","Target","Unit","Source","Notes");
    private final JTable performanceTable=new JTable(performanceModel);
    private final JTextField performanceDate=new JTextField();
    private final JTextField performanceMetric=new JTextField();
    private final JTextField performanceValue=new JTextField();
    private final JTextField performanceTarget=new JTextField();
    private final JTextField performanceUnit=new JTextField();
    private final JTextField performanceSource=new JTextField();
    private final JTextField performanceNotes=new JTextField();

    private final DefaultTableModel dutyModel=
            readOnlyModel("Duty","Required Qualification","Positions");
    private final JTable dutyTable=new JTable(dutyModel);
    private final JTextField dutyName=new JTextField();
    private final JTextField dutyQualification=new JTextField();
    private final JSpinner dutyCount=new JSpinner(
            new SpinnerNumberModel(1,1,50,1));
    private final DefaultTableModel recommendationModel=
            readOnlyModel(
                    "Duty","Required Qualification","Recommended Employee",
                    "Coverage","Reason");
    private final JTable recommendationTable=new JTable(recommendationModel);

    private final JComboBox<String> callInMode=new JComboBox<>(
            new String[]{"LOCAL_TEST","TWILIO_WEBHOOK","OFF"});
    private final JCheckBox callInEnabled=new JCheckBox(
            "Enable Twilio webhook listener");
    private final JTextField webhookPort=new JTextField();
    private final JTextField publicBaseUrl=new JTextField();
    private final JTextField twilioAccountSid=new JTextField();
    private final JPasswordField twilioAuthToken=new JPasswordField();
    private final JTextField twilioFromNumber=new JTextField();
    private final JCheckBox smsNotifications=new JCheckBox(
            "Send management SMS notifications");
    private final JTextField smsRecipients=new JTextField();
    private final JPasswordField sendGridApiKey=new JPasswordField();
    private final JCheckBox emailNotifications=new JCheckBox(
            "Send management email notifications");
    private final JTextField emailFrom=new JTextField();
    private final JTextField emailRecipients=new JTextField();
    private final JLabel callInStatus=new JLabel();

    private final JTextField testEmployeeNumber=new JTextField();
    private final JPasswordField testPin=new JPasswordField();
    private final JComboBox<String> testType=new JComboBox<>(
            new String[]{"CALL_OUT","RUNNING_LATE","LEAVING_EARLY","OTHER"});

    private String selectedEmployeeId="";

    public EmployeeOperationsPanel(AppConfig config){
        AuthorizationService.require(Permission.EMPLOYEE_OPERATIONS);
        this.config=Objects.requireNonNull(config);
        this.snapshot=EmployeeService.load();

        setLayout(new BorderLayout(14,14));
        setBorder(new EmptyBorder(14,14,14,14));

        JLabel description=new JLabel(
                "<html>Management-only employee system of record for identity, "
                +"recognition, qualifications, attendance/call-ins, performance "
                +"and daily assignment eligibility.</html>"
        );
        description.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        add(description,BorderLayout.NORTH);

        JComponent directory=buildEmployeeDirectory();
        directory.setMinimumSize(new Dimension(390,0));
        directory.setPreferredSize(new Dimension(500,1));

        JPanel directoryColumn=new JPanel(new BorderLayout());
        directoryColumn.setOpaque(false);
        directoryColumn.setBorder(new EmptyBorder(0,0,0,18));
        directoryColumn.setMinimumSize(new Dimension(390,0));
        directoryColumn.setPreferredSize(new Dimension(500,1));
        directoryColumn.add(directory,BorderLayout.CENTER);

        JSplitPane split=new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                directoryColumn,
                buildEmployeeWorkspace()
        );
        split.setResizeWeight(0.0);
        split.setDividerSize(0);
        split.setContinuousLayout(true);
        split.setOpaque(false);
        split.setDividerLocation(500);
        split.setLastDividerLocation(500);
        split.setBorder(null);
        add(split,BorderLayout.CENTER);

        loadIntegrationConfig();
        refreshEmployees();

        employeeTable.getSelectionModel().addListSelectionListener(e->{
            if(!e.getValueIsAdjusting())loadSelectedEmployee();
        });

        celebrationAnnouncements.addActionListener(e->
                updateCelebrationControlState());

        ThemeStyler.apply(this,Theme.active());
    }

    private JComponent buildEmployeeDirectory(){
        RoundedPanel card=new RoundedPanel(14);
        card.setLayout(new BorderLayout(8,8));
        card.setBorder(new EmptyBorder(10,10,10,10));

        JLabel title=new JLabel("Employees");
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));

        employeeTable.setRowHeight(28);
        employeeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hideDirectoryColumn("Employee #");
        hideDirectoryColumn("Department");
        hideDirectoryColumn("Shift");
        try{
            employeeTable.getColumn("Name").setPreferredWidth(210);
            var activeColumn=employeeTable.getColumn("Active");
            activeColumn.setMinWidth(70);
            activeColumn.setPreferredWidth(80);
            activeColumn.setMaxWidth(95);
        }catch(IllegalArgumentException ignored){}

        JPanel buttons=new JPanel(new GridLayout(1,2,8,0));
        buttons.setOpaque(false);

        JButton add=new JButton("+ Add Employee");
        add.addActionListener(e->addEmployee());

        JButton remove=new JButton("Remove");
        remove.addActionListener(e->removeEmployee());

        buttons.add(add);
        buttons.add(remove);

        card.add(title,BorderLayout.NORTH);
        card.add(new JScrollPane(employeeTable),BorderLayout.CENTER);
        card.add(buttons,BorderLayout.SOUTH);
        return card;
    }

    private void hideDirectoryColumn(String name){
        try{ employeeTable.removeColumn(employeeTable.getColumn(name)); }
        catch(IllegalArgumentException ignored){}
    }

    private JComponent buildEmployeeWorkspace(){
        JTabbedPane tabs=new JTabbedPane();

        tabs.addTab("Profile",profilePanel());

        if(AuthorizationService.allowed(Permission.EMPLOYEE_TRAINING))
            tabs.addTab("Training & Qualifications",trainingPanel());

        if(AuthorizationService.allowed(Permission.EMPLOYEE_ATTENDANCE))
            tabs.addTab("Attendance & Call-Ins",attendancePanel());

        if(AuthorizationService.allowed(Permission.EMPLOYEE_PERFORMANCE))
            tabs.addTab("Performance",performancePanel());

        if(AuthorizationService.allowed(Permission.EMPLOYEE_SCHEDULING))
            tabs.addTab("Daily Assignments",assignmentPanel());

        if(AuthorizationService.allowed(Permission.CALL_IN_ADMINISTRATION))
            tabs.addTab("Call-In Integration",callInPanel());

        return tabs;
    }

    private JComponent profilePanel(){
        JPanel content=formPanel();
        int y=0;

        addField(content,y++,"Employee number",employeeNumber);
        addField(content,y++,"Name",employeeName);
        addField(content,y++,"WMS / short name",shortName);
        addField(content,y++,"Department",department);
        addField(content,y++,"Shift",shift);
        addField(content,y++,"Hire date (YYYY-MM-DD)",hireDate);
        addField(content,y++,"Birthday (MM-DD)",birthday);
        addField(content,y++,"Phone",phone);
        JLabel phoneHelp=new JLabel(
                "<html>Enter a normal U.S. phone format such as "
                +"123-456-7890. NORTH STAR validates and converts it to E.164 "
                +"internally for Twilio/provider use.</html>");
        addFull(content,y++,phoneHelp);

        photoAsset.setEditable(false);
        JPanel photoRow=new JPanel(new BorderLayout(8,0));
        photoRow.setOpaque(false);
        photoRow.add(photoAsset,BorderLayout.CENTER);
        JButton choosePhoto=new JButton("Choose Managed Photo");
        choosePhoto.addActionListener(e->chooseManagedPhoto());
        photoRow.add(choosePhoto,BorderLayout.EAST);
        addField(content,y++,"Employee photo",photoRow);

        JPanel flags=new JPanel(new FlowLayout(FlowLayout.LEFT,12,0));
        flags.setOpaque(false);
        flags.add(active);
        flags.add(celebrationAnnouncements);
        flags.add(birthdayEnabled);
        flags.add(anniversaryEnabled);
        flags.add(employeeOfMonth);
        addFull(content,y++,flags);

        JPanel pinRow=new JPanel(new BorderLayout(8,0));
        pinRow.setOpaque(false);
        pinRow.add(callInPin,BorderLayout.CENTER);
        JButton setPin=new JButton("Set / Replace PIN");
        setPin.addActionListener(e->setSelectedEmployeePin());
        pinRow.add(setPin,BorderLayout.EAST);
        addField(content,y++,"Call-in PIN",pinRow);

        JLabel pinHelp=new JLabel(
                "<html>PINs are stored only as salted PBKDF2 hashes. "
                +"The original PIN cannot be recovered.</html>");
        addFull(content,y++,pinHelp);

        JButton save=new JButton("Save Employee Profile");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->saveSelectedProfile());
        addFull(content,y++,save);

        return scroll(content);
    }

    private JComponent trainingPanel(){
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setOpaque(false);

        trainingTable.setRowHeight(28);
        root.add(new JScrollPane(trainingTable),BorderLayout.CENTER);

        JPanel form=formPanel();
        int y=0;
        addField(form,y++,"Category",trainingCategory);
        addField(form,y++,"Qualification",qualification);
        addField(form,y++,"Completed (YYYY-MM-DD)",trainingCompleted);
        addField(form,y++,"Expires (YYYY-MM-DD, optional)",trainingExpires);
        addField(form,y++,"Trainer",trainer);
        addField(form,y++,"Notes",trainingNotes);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        buttons.setOpaque(false);
        JButton add=new JButton("+ Add Qualification");
        add.addActionListener(e->addTraining());
        JButton remove=new JButton("Remove Selected");
        remove.addActionListener(e->removeTraining());
        buttons.add(add);buttons.add(remove);
        addFull(form,y++,buttons);

        root.add(form,BorderLayout.SOUTH);
        return root;
    }

    private JComponent attendancePanel(){
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setOpaque(false);

        attendanceTable.setRowHeight(28);
        root.add(new JScrollPane(attendanceTable),BorderLayout.CENTER);

        attendanceDate.setText(LocalDate.now().toString());
        attendanceTime.setText(
                LocalTime.now().withSecond(0).withNano(0).toString());

        JPanel form=formPanel();
        int y=0;
        addField(form,y++,"Type",attendanceType);
        addField(form,y++,"Date",attendanceDate);
        addField(form,y++,"Time",attendanceTime);
        addField(form,y++,"Notes",attendanceNotes);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        buttons.setOpaque(false);
        JButton add=new JButton("+ Record Attendance Event");
        add.addActionListener(e->addAttendance());
        JButton remove=new JButton("Remove Selected");
        remove.addActionListener(e->removeAttendance());
        buttons.add(add);buttons.add(remove);
        addFull(form,y++,buttons);

        return join(root,form);
    }

    private JComponent performancePanel(){
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setOpaque(false);

        performanceTable.setRowHeight(28);
        root.add(new JScrollPane(performanceTable),BorderLayout.CENTER);

        performanceDate.setText(LocalDate.now().toString());

        JPanel form=formPanel();
        int y=0;
        addField(form,y++,"Date",performanceDate);
        addField(form,y++,"Metric",performanceMetric);
        addField(form,y++,"Value",performanceValue);
        addField(form,y++,"Target (optional)",performanceTarget);
        addField(form,y++,"Unit",performanceUnit);
        addField(form,y++,"Source",performanceSource);
        addField(form,y++,"Notes",performanceNotes);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        buttons.setOpaque(false);
        JButton add=new JButton("+ Add Performance Record");
        add.addActionListener(e->addPerformance());
        JButton remove=new JButton("Remove Selected");
        remove.addActionListener(e->removePerformance());
        buttons.add(add);buttons.add(remove);
        addFull(form,y++,buttons);

        return join(root,form);
    }

    private JComponent assignmentPanel(){
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setOpaque(false);

        JPanel top=new JPanel(new BorderLayout(8,8));
        top.setOpaque(false);
        dutyTable.setRowHeight(28);
        top.add(new JScrollPane(dutyTable),BorderLayout.CENTER);

        JPanel form=new JPanel(new GridLayout(2,4,8,6));
        form.setOpaque(false);
        form.add(new JLabel("Duty"));
        form.add(new JLabel("Required qualification"));
        form.add(new JLabel("Positions"));
        form.add(new JLabel(""));
        form.add(dutyName);
        form.add(dutyQualification);
        form.add(dutyCount);

        JButton addDuty=new JButton("+ Add Duty");
        addDuty.addActionListener(e->addDuty());
        form.add(addDuty);

        top.add(form,BorderLayout.SOUTH);

        JPanel bottom=new JPanel(new BorderLayout(8,8));
        bottom.setOpaque(false);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        actions.setOpaque(false);
        JButton generate=new JButton("Generate Today's Recommendations");
        generate.putClientProperty("primaryAction",Boolean.TRUE);
        generate.addActionListener(e->generateAssignments());
        JButton removeDuty=new JButton("Remove Selected Duty");
        removeDuty.addActionListener(e->removeDuty());
        actions.add(generate);actions.add(removeDuty);

        recommendationTable.setRowHeight(28);
        bottom.add(actions,BorderLayout.NORTH);
        bottom.add(new JScrollPane(recommendationTable),BorderLayout.CENTER);

        JSplitPane split=new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,top,bottom);
        split.setResizeWeight(.43);
        split.setBorder(null);

        refreshDuties();
        return split;
    }

    private JComponent callInPanel(){
        SettingsFormPanel content=formPanel();
        content.setBorder(new EmptyBorder(26,24,36,24));
        int y=0;

        addSectionTitle(content,y++,"Call-In Service");

        addField(content,y++,"Mode",callInMode);
        addFull(content,y++,callInEnabled);
        addField(content,y++,"Webhook port",webhookPort);
        addField(content,y++,"Public HTTPS base URL",publicBaseUrl);

        addSectionTitle(content,y++,"Twilio Voice & SMS");

        addField(content,y++,"Twilio Account SID",twilioAccountSid);
        addField(content,y++,"Twilio Auth Token",twilioAuthToken);
        addField(content,y++,"Twilio SMS from-number",twilioFromNumber);
        addFull(content,y++,smsNotifications);
        addField(content,y++,"Management SMS recipients",smsRecipients);

        JLabel smsFormat=new JLabel(
                "<html>Telephone API values should use E.164 format, for example "
                +"<b>+12055551234</b>. Employee profile display numbers may still "
                +"contain spaces, parentheses or hyphens.</html>"
        );
        smsFormat.setForeground(Theme.muted());
        addFull(content,y++,smsFormat);

        addSectionTitle(content,y++,"Management Email");

        addField(content,y++,"SendGrid API key",sendGridApiKey);
        addFull(content,y++,emailNotifications);
        addField(content,y++,"Verified email sender",emailFrom);
        addField(content,y++,"Management email recipients",emailRecipients);

        JTextArea security=new JTextArea(
                "Production security: Twilio webhook requests are validated "
                +"with X-Twilio-Signature using the configured Auth Token and "
                +"exact public HTTPS URL. Do not expose the Raspberry Pi "
                +"directly to the Internet without an approved HTTPS reverse "
                +"proxy, secure tunnel, function, or future hosted NORTH STAR/"
                +"North Star service."
        );
        security.setEditable(false);
        security.setLineWrap(true);
        security.setWrapStyleWord(true);
        security.setOpaque(false);
        security.setBorder(new EmptyBorder(6,0,6,0));
        security.setRows(3);
        addFull(content,y++,security);

        JPanel saveRow=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        saveRow.setOpaque(false);

        JButton save=new JButton("Save & Apply Call-In Integration");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->saveCallInIntegration());

        JButton stop=new JButton("Stop Webhook Listener");
        stop.addActionListener(e->{
            CallInServerManager.stop();
            refreshCallInStatus();
        });

        saveRow.add(save);
        saveRow.add(stop);
        saveRow.add(callInStatus);
        addFull(content,y++,saveRow);

        addSectionTitle(content,y++,"Local Call-In Test");

        JPanel test=new JPanel(new GridBagLayout());
        test.setOpaque(false);
        test.setBorder(new EmptyBorder(2,0,2,0));
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(8,6,8,6);
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.WEST;

        c.gridy=0;
        c.gridx=0;
        c.weightx=0;
        JLabel employeeLabel=new JLabel("Employee #");
        employeeLabel.setPreferredSize(new Dimension(120,28));
        test.add(employeeLabel,c);

        c.gridx=1;
        c.weightx=.55;
        test.add(testEmployeeNumber,c);

        c.gridx=2;
        c.weightx=0;
        JLabel pinLabel=new JLabel("PIN");
        pinLabel.setPreferredSize(new Dimension(48,28));
        test.add(pinLabel,c);

        c.gridx=3;
        c.weightx=.35;
        test.add(testPin,c);

        c.gridy=1;
        c.gridx=0;
        c.weightx=0;
        test.add(new JLabel("Type"),c);

        c.gridx=1;
        c.weightx=.55;
        test.add(testType,c);

        JButton simulate=new JButton("Simulate Call-In");
        simulate.addActionListener(e->simulateCallIn(false));
        c.gridx=2;
        c.weightx=.3;
        test.add(simulate,c);

        JButton notify=new JButton("Simulate + Notify");
        notify.addActionListener(e->simulateCallIn(true));
        c.gridx=3;
        c.weightx=.3;
        test.add(notify,c);

        addFull(content,y++,test);

        ThemeStyler.apply(content,Theme.active());
        return scroll(content);
    }

    private void refreshEmployees(){
        employeeModel.setRowCount(0);

        for(EmployeeProfile e:snapshot.employees){
            employeeModel.addRow(new Object[]{
                    e.employeeNumber(),
                    e.name(),
                    e.department(),
                    e.shift(),
                    e.active()?"Yes":"No"
            });
        }

        if(!selectedEmployeeId.isBlank()){
            for(int i=0;i<snapshot.employees.size();i++){
                if(selectedEmployeeId.equals(snapshot.employees.get(i).id())){
                    employeeTable.setRowSelectionInterval(i,i);
                    return;
                }
            }
        }

        if(!snapshot.employees.isEmpty())
            employeeTable.setRowSelectionInterval(0,0);
        else
            clearProfile();
    }

    private void loadSelectedEmployee(){
        int row=employeeTable.getSelectedRow();
        if(row<0||row>=snapshot.employees.size()){
            selectedEmployeeId="";
            clearProfile();
            return;
        }

        EmployeeProfile e=snapshot.employees.get(row);
        selectedEmployeeId=e.id();

        employeeNumber.setText(e.employeeNumber());
        employeeName.setText(e.name());
        shortName.setText(e.shortName());
        department.setText(e.department());
        shift.setText(e.shift());
        hireDate.setText(e.hireDate()==null?"":e.hireDate().toString());
        birthday.setText(e.birthday()==null
                ?""
                :String.format(
                        "%02d-%02d",
                        e.birthday().getMonthValue(),
                        e.birthday().getDayOfMonth()));
        phone.setText(PhoneNumbers.formatForDisplay(e.phone()));
        photoAsset.setText(e.photoAsset());
        active.setSelected(e.active());
        celebrationAnnouncements.setSelected(
                e.celebrationAnnouncements());
        birthdayEnabled.setSelected(e.showBirthday());
        anniversaryEnabled.setSelected(e.showAnniversary());
        employeeOfMonth.setSelected(e.employeeOfMonth());
        updateCelebrationControlState();
        callInPin.setText("");

        refreshTraining();
        refreshAttendance();
        refreshPerformance();
    }

    private void clearProfile(){
        for(JTextField field:List.of(
                employeeNumber,employeeName,shortName,department,shift,
                hireDate,birthday,phone,photoAsset))
            field.setText("");

        active.setSelected(true);
        celebrationAnnouncements.setSelected(true);
        birthdayEnabled.setSelected(true);
        anniversaryEnabled.setSelected(true);
        employeeOfMonth.setSelected(false);
        updateCelebrationControlState();
        callInPin.setText("");

        trainingModel.setRowCount(0);
        attendanceModel.setRowCount(0);
        performanceModel.setRowCount(0);
    }

    private void addEmployee(){
        EmployeeProfile employee=new EmployeeProfile(
                UUID.randomUUID().toString(),
                "",
                "New Employee",
                "",
                "",
                "",
                null,
                null,
                "",
                "",
                true,
                true,
                true,
                true,
                false,
                "",
                "",
                0
        );

        snapshot.employees.add(employee);
        selectedEmployeeId=employee.id();
        saveSnapshot();
        refreshEmployees();
    }

    private void removeEmployee(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return;

        int choice=JOptionPane.showConfirmDialog(
                this,
                "Remove "+employee.name()+" and all linked employee records?",
                "Remove Employee",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if(choice!=JOptionPane.YES_OPTION)return;

        snapshot.employees.removeIf(e->e.id().equals(employee.id()));
        snapshot.training.removeIf(r->r.employeeId().equals(employee.id()));
        snapshot.attendance.removeIf(r->r.employeeId().equals(employee.id()));
        snapshot.performance.removeIf(r->r.employeeId().equals(employee.id()));

        selectedEmployeeId="";
        saveSnapshot();
        refreshEmployees();
    }

    private void saveSelectedProfile(){
        EmployeeProfile old=selectedEmployee();
        if(old==null){
            message("Select an employee first.");
            return;
        }

        try{
            LocalDate hire=parseDate(hireDate.getText(),true);
            MonthDay dob=parseMonthDay(birthday.getText());

            String canonicalPhone=PhoneNumbers.toE164(phone.getText());

            EmployeeProfile updated=new EmployeeProfile(
                    old.id(),
                    employeeNumber.getText(),
                    employeeName.getText(),
                    shortName.getText(),
                    department.getText(),
                    shift.getText(),
                    hire,
                    dob,
                    canonicalPhone,
                    photoAsset.getText(),
                    active.isSelected(),
                    celebrationAnnouncements.isSelected(),
                    birthdayEnabled.isSelected(),
                    anniversaryEnabled.isSelected(),
                    employeeOfMonth.isSelected(),
                    old.pinSalt(),
                    old.pinHash(),
                    old.pinIterations()
            );

            replaceEmployee(updated);
            saveSnapshot();
            refreshEmployees();
            message("Employee profile saved.");
        }catch(Exception ex){
            error(ex.getMessage());
        }
    }

    private void setSelectedEmployeePin(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null){
            message("Select an employee first.");
            return;
        }

        char[] pin=callInPin.getPassword();
        try{
            EmployeeProfile updated=EmployeeStore.setPin(employee,pin);
            replaceEmployee(updated);
            saveSnapshot();
            callInPin.setText("");
            message("Call-in PIN saved securely.");
        }catch(Exception ex){
            error(ex.getMessage());
        }finally{
            Arrays.fill(pin,'\0');
        }
    }

    private void updateCelebrationControlState(){
        boolean enabled=celebrationAnnouncements.isSelected();
        birthdayEnabled.setEnabled(enabled);
        anniversaryEnabled.setEnabled(enabled);
        employeeOfMonth.setEnabled(enabled);
        if(!enabled){
            birthdayEnabled.setToolTipText(
                    "Disabled because this employee opted out of celebration announcements.");
            anniversaryEnabled.setToolTipText(
                    "Disabled because this employee opted out of celebration announcements.");
            employeeOfMonth.setToolTipText(
                    "Disabled because this employee opted out of celebration announcements.");
        }else{
            birthdayEnabled.setToolTipText(null);
            anniversaryEnabled.setToolTipText(null);
            employeeOfMonth.setToolTipText(null);
        }
    }

    private void chooseManagedPhoto(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null){
            message("Select an employee first.");
            return;
        }

        List<Path> photos=MediaService.list(MediaCategory.EMPLOYEE_PHOTOS);
        if(photos.isEmpty()){
            message(
                    "No managed employee photos are available. "
                    +"Upload one from Media Library first.");
            return;
        }

        String[] choices=photos.stream()
                .map(p->p.getFileName().toString())
                .toArray(String[]::new);

        String selected=(String)JOptionPane.showInputDialog(
                this,
                "Choose employee photo:",
                "Employee Photo",
                JOptionPane.PLAIN_MESSAGE,
                null,
                choices,
                choices[0]
        );

        if(selected!=null)
            photoAsset.setText(selected);
    }

    private void refreshTraining(){
        trainingModel.setRowCount(0);
        for(TrainingRecord r:filteredTraining()){
            trainingModel.addRow(new Object[]{
                    r.category(),
                    r.qualification(),
                    text(r.completedDate()),
                    text(r.expirationDate()),
                    r.trainer(),
                    r.status(),
                    r.notes()
            });
        }
    }

    private void addTraining(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return;

        try{
            snapshot.training.add(new TrainingRecord(
                    UUID.randomUUID().toString(),
                    employee.id(),
                    Objects.toString(trainingCategory.getSelectedItem(),""),
                    qualification.getText(),
                    parseDate(trainingCompleted.getText(),true),
                    parseDate(trainingExpires.getText(),false),
                    trainer.getText(),
                    "ACTIVE",
                    trainingNotes.getText()
            ));
            saveSnapshot();
            refreshTraining();
        }catch(Exception ex){
            error(ex.getMessage());
        }
    }

    private void removeTraining(){
        int row=trainingTable.getSelectedRow();
        List<TrainingRecord> filtered=filteredTraining();
        if(row<0||row>=filtered.size())return;

        String id=filtered.get(row).id();
        snapshot.training.removeIf(r->r.id().equals(id));
        saveSnapshot();
        refreshTraining();
    }

    private List<TrainingRecord> filteredTraining(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return List.of();
        return snapshot.training.stream()
                .filter(r->employee.id().equals(r.employeeId()))
                .sorted(Comparator.comparing(
                        TrainingRecord::qualification,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void refreshAttendance(){
        attendanceModel.setRowCount(0);
        for(AttendanceRecord r:filteredAttendance()){
            attendanceModel.addRow(new Object[]{
                    r.date(),r.time(),r.type(),r.source(),r.status(),r.notes()
            });
        }
    }

    private void addAttendance(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return;

        try{
            snapshot.attendance.add(new AttendanceRecord(
                    UUID.randomUUID().toString(),
                    employee.id(),
                    parseDate(attendanceDate.getText(),true),
                    LocalTime.parse(attendanceTime.getText().trim()),
                    Objects.toString(attendanceType.getSelectedItem(),"OTHER"),
                    "MANUAL",
                    "RECORDED",
                    attendanceNotes.getText(),
                    "",
                    ""
            ));
            saveSnapshot();
            refreshAttendance();
        }catch(Exception ex){
            error("Use YYYY-MM-DD and HH:MM. "+ex.getMessage());
        }
    }

    private void removeAttendance(){
        int row=attendanceTable.getSelectedRow();
        List<AttendanceRecord> filtered=filteredAttendance();
        if(row<0||row>=filtered.size())return;

        String id=filtered.get(row).id();
        snapshot.attendance.removeIf(r->r.id().equals(id));
        saveSnapshot();
        refreshAttendance();
    }

    private List<AttendanceRecord> filteredAttendance(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return List.of();

        return snapshot.attendance.stream()
                .filter(r->employee.id().equals(r.employeeId()))
                .sorted(Comparator
                        .comparing(
                                AttendanceRecord::date,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                AttendanceRecord::time,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void refreshPerformance(){
        performanceModel.setRowCount(0);
        for(PerformanceRecord r:filteredPerformance()){
            performanceModel.addRow(new Object[]{
                    r.date(),
                    r.metric(),
                    r.value(),
                    Double.isNaN(r.target())?"":r.target(),
                    r.unit(),
                    r.source(),
                    r.notes()
            });
        }
    }

    private void addPerformance(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return;

        try{
            double value=Double.parseDouble(
                    performanceValue.getText().trim());
            String targetText=performanceTarget.getText().trim();
            double target=targetText.isBlank()
                    ?Double.NaN
                    :Double.parseDouble(targetText);

            snapshot.performance.add(new PerformanceRecord(
                    UUID.randomUUID().toString(),
                    employee.id(),
                    parseDate(performanceDate.getText(),true),
                    performanceMetric.getText(),
                    value,
                    target,
                    performanceUnit.getText(),
                    performanceSource.getText(),
                    performanceNotes.getText()
            ));
            saveSnapshot();
            refreshPerformance();
        }catch(Exception ex){
            error(ex.getMessage());
        }
    }

    private void removePerformance(){
        int row=performanceTable.getSelectedRow();
        List<PerformanceRecord> filtered=filteredPerformance();
        if(row<0||row>=filtered.size())return;

        String id=filtered.get(row).id();
        snapshot.performance.removeIf(r->r.id().equals(id));
        saveSnapshot();
        refreshPerformance();
    }

    private List<PerformanceRecord> filteredPerformance(){
        EmployeeProfile employee=selectedEmployee();
        if(employee==null)return List.of();

        return snapshot.performance.stream()
                .filter(r->employee.id().equals(r.employeeId()))
                .sorted(Comparator
                        .comparing(
                                PerformanceRecord::date,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PerformanceRecord::metric))
                .toList();
    }

    private void refreshDuties(){
        dutyModel.setRowCount(0);
        for(DutyRequirement duty:snapshot.duties){
            dutyModel.addRow(new Object[]{
                    duty.duty(),duty.qualification(),duty.requiredCount()
            });
        }
    }

    private void addDuty(){
        if(dutyName.getText().isBlank()
                ||dutyQualification.getText().isBlank()){
            error("Duty and required qualification are required.");
            return;
        }

        snapshot.duties.add(new DutyRequirement(
                dutyName.getText(),
                dutyQualification.getText(),
                (Integer)dutyCount.getValue()
        ));
        saveSnapshot();
        refreshDuties();
    }

    private void removeDuty(){
        int row=dutyTable.getSelectedRow();
        if(row<0||row>=snapshot.duties.size())return;
        snapshot.duties.remove(row);
        saveSnapshot();
        refreshDuties();
    }

    private void generateAssignments(){
        recommendationModel.setRowCount(0);

        for(EmployeeService.AssignmentRecommendation r:
                EmployeeService.recommendAssignments(
                        snapshot,LocalDate.now())){
            recommendationModel.addRow(new Object[]{
                    r.duty(),
                    r.qualification(),
                    r.employeeName(),
                    r.covered()?"COVERED":"GAP",
                    r.reason()
            });
        }
    }

    private void loadIntegrationConfig(){
        callInMode.setSelectedItem(config.callInMode);
        callInEnabled.setSelected(config.callInEnabled);
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
        refreshCallInStatus();
    }

    private void saveCallInIntegration(){
        try{
            config.callInMode=Objects.toString(
                    callInMode.getSelectedItem(),"LOCAL_TEST");
            config.callInEnabled=callInEnabled.isSelected();
            config.callInWebhookPort=Integer.parseInt(
                    webhookPort.getText().trim());
            config.callInPublicBaseUrl=publicBaseUrl.getText().trim();
            config.twilioAccountSid=twilioAccountSid.getText().trim();
            config.twilioAuthToken=
                    new String(twilioAuthToken.getPassword());
            config.callInTwilioFromNumber=
                    twilioFromNumber.getText().trim();
            config.callInSmsNotifications=smsNotifications.isSelected();
            config.callInSmsRecipients=smsRecipients.getText().trim();
            config.sendGridApiKey=
                    new String(sendGridApiKey.getPassword());
            config.callInEmailNotifications=emailNotifications.isSelected();
            config.callInEmailFrom=emailFrom.getText().trim();
            config.callInEmailRecipients=emailRecipients.getText().trim();

            ConfigService.save(config);
            ApiCredentialService.saveFrom(config);
            CallInServerManager.apply(config);
            refreshCallInStatus();
            message("Call-in integration saved.");
        }catch(Exception ex){
            error(ex.getMessage());
        }
    }

    private void refreshCallInStatus(){
        callInStatus.setText("Status: "+CallInServerManager.status());
    }

    private void simulateCallIn(boolean notify){
        EmployeeStore.Snapshot fresh=EmployeeStore.load();
        EmployeeProfile employee=EmployeeService.findByEmployeeNumber(
                fresh,testEmployeeNumber.getText());

        if(employee==null){
            error("Employee number was not found.");
            return;
        }

        char[] pin=testPin.getPassword();
        boolean valid;
        try{
            valid=EmployeeStore.verifyPin(employee,pin);
        }finally{
            Arrays.fill(pin,'\0');
        }

        if(!valid){
            error("Call-in PIN is incorrect or has not been configured.");
            return;
        }

        AttendanceRecord record=EmployeeService.recordCallIn(
                fresh,
                employee,
                Objects.toString(testType.getSelectedItem(),"OTHER"),
                "LOCAL_TEST",
                "Local NORTH STAR call-in simulation",
                "",
                "local-test-"+System.currentTimeMillis()
        );

        if(notify)
            CallInNotifier.notifyManagement(config,employee,record);

        snapshot=EmployeeService.load();
        refreshAttendance();
        message(
                employee.name()+" "+record.type()
                        +" call-in recorded"
                        +(notify?" and notifications attempted.":".")
        );
    }

    private void saveSnapshot(){
        EmployeeService.save(snapshot,config);
        ConfigService.save(config);
    }

    private EmployeeProfile selectedEmployee(){
        if(selectedEmployeeId.isBlank())return null;
        return EmployeeService.findById(snapshot,selectedEmployeeId);
    }

    private void replaceEmployee(EmployeeProfile updated){
        for(int i=0;i<snapshot.employees.size();i++){
            if(snapshot.employees.get(i).id().equals(updated.id())){
                snapshot.employees.set(i,updated);
                selectedEmployeeId=updated.id();
                return;
            }
        }
    }

    private static SettingsFormPanel formPanel(){
        SettingsFormPanel p=new SettingsFormPanel();
        p.setLayout(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(18,20,22,20));
        return p;
    }

    private static void addField(
            JPanel panel,
            int row,
            String label,
            Component component
    ){
        GridBagConstraints c=new GridBagConstraints();
        c.gridy=row;
        c.insets=new Insets(8,6,8,6);
        c.anchor=GridBagConstraints.WEST;

        c.gridx=0;
        c.weightx=0;
        c.fill=GridBagConstraints.NONE;

        JLabel labelComponent=new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(210,38));
        panel.add(labelComponent,c);

        normalizeInputHeight(component);

        c.gridx=1;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        panel.add(component,c);
    }

    private static void addFull(
            JPanel panel,
            int row,
            Component component
    ){
        GridBagConstraints c=new GridBagConstraints();
        c.gridy=row;
        c.gridx=0;
        c.gridwidth=2;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(8,6,8,6);
        panel.add(component,c);
    }

    private static JComponent scroll(Component component){
        JScrollPane scroll=new JScrollPane(
                component,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(90);
        return scroll;
    }

    private static void addSectionTitle(
            JPanel panel,
            int row,
            String title
    ){
        /*
         * Section headings previously shared the same compact GridBag row
         * sizing as ordinary fields.  On shorter windows/macOS this let the
         * layout compress the heading until the text was partially hidden by
         * the separator or tab content border.  Give every section its own
         * protected vertical space so headings remain readable at any scroll
         * position.
         */
        JPanel section=new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section,BoxLayout.Y_AXIS));
        section.setBorder(new EmptyBorder(14,0,8,0));

        JLabel label=new JLabel(title);
        label.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(label);
        section.add(Box.createVerticalStrut(8));

        JSeparator separator=new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        section.add(separator);

        Dimension protectedSize=new Dimension(320,52);
        section.setPreferredSize(protectedSize);
        section.setMinimumSize(new Dimension(120,52));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));

        GridBagConstraints c=new GridBagConstraints();
        c.gridy=row;
        c.gridx=0;
        c.gridwidth=2;
        c.weightx=1;
        c.weighty=0;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.NORTHWEST;
        c.insets=new Insets(row==0?12:16,5,8,5);
        panel.add(section,c);
    }

    private static void normalizeInputHeight(Component component){
        if(component instanceof JTextField
                ||component instanceof JPasswordField
                ||component instanceof JComboBox<?>
                ||component instanceof JSpinner){
            Dimension preferred=component.getPreferredSize();
            int h=component instanceof JComboBox<?>
                    ?ThemedComboBoxUI.CONTROL_HEIGHT
                    :Math.max(38,Math.min(42,preferred.height));
            component.setPreferredSize(
                    new Dimension(Math.max(180,preferred.width),h));
            component.setMinimumSize(new Dimension(120,h));
            component.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE,h));
        }
    }

    /**
     * Form content tracks the viewport width so settings pages never create a
     * horizontal scrollbar merely because one row has a long label/value.
     */
    private static final class SettingsFormPanel
            extends JPanel implements Scrollable{
        @Override public Dimension getPreferredScrollableViewportSize(){
            return new Dimension(760,620);
        }

        @Override public int getScrollableUnitIncrement(
                Rectangle visibleRect,
                int orientation,
                int direction
        ){
            return 18;
        }

        @Override public int getScrollableBlockIncrement(
                Rectangle visibleRect,
                int orientation,
                int direction
        ){
            return 90;
        }

        @Override public boolean getScrollableTracksViewportWidth(){
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight(){
            return false;
        }
    }

    private static JComponent join(
            JPanel tablePanel,
            JPanel form
    ){
        JPanel root=new JPanel(new BorderLayout(8,8));
        root.setOpaque(false);
        root.add(tablePanel,BorderLayout.CENTER);
        root.add(form,BorderLayout.SOUTH);
        return root;
    }

    private static DefaultTableModel readOnlyModel(String... columns){
        return new DefaultTableModel(columns,0){
            @Override public boolean isCellEditable(int row,int column){
                return false;
            }
        };
    }

    private static LocalDate parseDate(
            String value,
            boolean required
    ){
        String text=value==null?"":value.trim();
        if(text.isBlank()){
            if(required)
                throw new IllegalArgumentException("Date is required.");
            return null;
        }
        return LocalDate.parse(text);
    }

    private static MonthDay parseMonthDay(String value){
        String text=value==null?"":value.trim();
        if(text.isBlank())return null;
        if(!text.startsWith("--"))text="--"+text;
        return MonthDay.parse(text);
    }

    private static String text(Object value){
        return value==null?"":value.toString();
    }

    private void message(String value){
        ThemedDialogs.message(
                SwingUtilities.getWindowAncestor(this),
                value,
                "Employee Operations",
                ThemedDialogs.Kind.INFO
        );
    }

    private void error(String value){
        ThemedDialogs.message(
                SwingUtilities.getWindowAncestor(this),
                value==null?"Unable to complete the request.":value,
                "Employee Operations",
                ThemedDialogs.Kind.ERROR
        );
    }
}
