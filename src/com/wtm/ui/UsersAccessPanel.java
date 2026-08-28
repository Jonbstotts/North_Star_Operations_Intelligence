package com.wtm.ui;

import com.wtm.security.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

/**
 * Local user/role administration for standalone deployments.
 *
 * Roles are templates; Edit Permissions allows administrators to grant/revoke
 * individual capabilities without creating duplicate role definitions.
 */
public final class UsersAccessPanel extends JPanel {
    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Username","Display Name","Role","Enabled"},0){
        @Override public Class<?> getColumnClass(int column){
            return column==3?Boolean.class:String.class;
        }
        @Override public boolean isCellEditable(int row,int column){
            return false;
        }
    };
    private final JTable table=new JTable(model);
    private final JTextArea audit=new JTextArea();

    public UsersAccessPanel(){
        AuthorizationService.require(Permission.MANAGE_USERS);

        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JLabel title=new JLabel("Users & Access");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));

        JLabel help=new JLabel(
                "<html>Roles provide permission templates. Select a user and use "
              + "<b>Edit Access</b> to grant or revoke individual privileges. "
              + "Administrators always retain all permissions.</html>"
        );

        JPanel header=new JPanel(new BorderLayout(0,6));
        header.add(title,BorderLayout.NORTH);
        header.add(help,BorderLayout.CENTER);
        add(header,BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);

        audit.setEditable(false);
        audit.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        audit.setLineWrap(false);

        JTabbedPane center=new JTabbedPane();
        center.addTab("User Accounts",new JScrollPane(table));

        if(AuthorizationService.allowed(Permission.VIEW_AUDIT_LOG))
            center.addTab("Audit Log",new JScrollPane(audit));

        add(center,BorderLayout.CENTER);

        JButton add=new JButton("+ Add User");
        JButton access=new JButton("Edit Access");
        JButton password=new JButton("Reset Password");
        JButton delete=new JButton("Delete User");
        JButton refresh=new JButton("Refresh");

        add.addActionListener(e->addUser());
        access.addActionListener(e->editAccess());
        password.addActionListener(e->resetPassword());
        delete.addActionListener(e->deleteUser());
        refresh.addActionListener(e->reload());

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(add);
        controls.add(access);
        controls.add(password);
        controls.add(delete);
        controls.add(refresh);
        add(controls,BorderLayout.SOUTH);

        reload();
    }

    private void reload(){
        model.setRowCount(0);
        for(UserAccount account:UserService.listUsers()){
            model.addRow(new Object[]{
                    account.username(),
                    account.friendlyName(),
                    account.role().display(),
                    account.enabled()
            });
        }
        audit.setText(AuditService.readRecent(300));
        audit.setCaretPosition(audit.getDocument().getLength());
    }

    private UserAccount selected(){
        int view=table.getSelectedRow();
        if(view<0){
            ThemedDialogs.message(
                    this,
                    "Select a user first.",
                    "Select User",
                    ThemedDialogs.Kind.INFO
            );
            return null;
        }
        int row=table.convertRowIndexToModel(view);
        return UserService.find(String.valueOf(model.getValueAt(row,0)));
    }

    private void addUser(){
        AppTheme theme=resolveTheme();

        if(AddUserDialog.showDialog(this,theme))
            reload();
    }

    private AppTheme resolveTheme(){
        Window window=SwingUtilities.getWindowAncestor(this);

        if(window instanceof SettingsDialog settings)
            return settings.activeThemeForProtectedContent();

        return AppTheme.FLATLAF_DARK;
    }

    private void editAccess(){
        UserAccount account=selected();
        if(account==null)return;

        JTextField display=new JTextField(account.friendlyName());
        JComboBox<UserRole> role=new JComboBox<>(UserRole.values());
        role.setSelectedItem(account.role());
        JCheckBox enabled=new JCheckBox("Account enabled",account.enabled());

        JPanel permissions=new JPanel();
        permissions.setLayout(new BoxLayout(permissions,BoxLayout.Y_AXIS));
        Map<Permission,JCheckBox> checks=new EnumMap<>(Permission.class);

        for(Permission permission:Permission.values()){
            JCheckBox box=new JCheckBox(
                    permission.display(),
                    account.has(permission)
            );
            box.setEnabled(account.role()!=UserRole.ADMINISTRATOR);
            checks.put(permission,box);
            permissions.add(box);
        }

        role.addActionListener(e->{
            UserRole selected=(UserRole)role.getSelectedItem();
            if(selected==null)return;

            Set<Permission> defaults=selected.defaultPermissions();
            for(var entry:checks.entrySet()){
                entry.getValue().setSelected(
                        selected==UserRole.ADMINISTRATOR
                                ||defaults.contains(entry.getKey())
                );
                entry.getValue().setEnabled(
                        selected!=UserRole.ADMINISTRATOR
                );
            }
        });

        JPanel top=new JPanel(new GridLayout(0,2,8,8));
        top.add(new JLabel("Display name"));top.add(display);
        top.add(new JLabel("Role template"));top.add(role);
        top.add(new JLabel("Status"));top.add(enabled);

        JPanel content=new JPanel(new BorderLayout(10,10));
        content.add(top,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(permissions);
        scroll.setPreferredSize(new Dimension(430,340));
        content.add(scroll,BorderLayout.CENTER);

        if(!ThemedDialogs.confirmForm(
                this,
                content,
                "Edit Access — "+account.username(),
                "Save Access"
        ))return;

        try{
            UserRole selected=(UserRole)role.getSelectedItem();
            EnumSet<Permission> selectedPermissions=
                    EnumSet.noneOf(Permission.class);

            for(var entry:checks.entrySet())
                if(entry.getValue().isSelected())
                    selectedPermissions.add(entry.getKey());

            UserService.updateProfile(
                    account.username(),
                    display.getText(),
                    selected==null?UserRole.CUSTOM:selected,
                    selectedPermissions,
                    enabled.isSelected()
            );
            reload();
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,ex.getMessage(),"Unable to Update Access",
                    ThemedDialogs.Kind.ERROR
            );
        }
    }

    private void resetPassword(){
        UserAccount account=selected();
        if(account==null)return;

        JPasswordField first=new JPasswordField();
        JPasswordField second=new JPasswordField();
        JPanel p=new JPanel(new GridLayout(0,2,8,8));
        p.add(new JLabel("New password"));p.add(first);
        p.add(new JLabel("Confirm password"));p.add(second);

        if(!ThemedDialogs.confirmForm(
                this,p,"Reset Password — "+account.username(),"Reset Password"
        ))return;

        char[] a=first.getPassword();
        char[] b=second.getPassword();
        try{
            if(!Arrays.equals(a,b))
                throw new IllegalArgumentException("Passwords do not match.");
            UserService.resetPassword(account.username(),a);
            ThemedDialogs.message(
                    this,"Password updated.","Password Reset",
                    ThemedDialogs.Kind.INFO
            );
            reload();
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,ex.getMessage(),"Password Reset Failed",
                    ThemedDialogs.Kind.ERROR
            );
        }finally{
            Arrays.fill(a,'\0');
            Arrays.fill(b,'\0');
        }
    }

    private void deleteUser(){
        UserAccount account=selected();
        if(account==null)return;

        if(!ThemedDialogs.confirm(
                this,"Delete user "+account.username()+"?","Delete User",
                "Delete User",ThemedDialogs.Kind.WARNING
        ))return;

        try{
            UserService.deleteUser(account.username());
            reload();
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,ex.getMessage(),"Delete Failed",
                    ThemedDialogs.Kind.ERROR
            );
        }
    }
}
