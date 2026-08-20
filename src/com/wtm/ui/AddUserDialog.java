package com.wtm.ui;

import com.wtm.security.*;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Set;

/**
 * Theme-aware account creation dialog.
 *
 * This replaces the native JOptionPane used in v3.1.0 so account-management
 * workflows remain visually consistent across macOS, Windows, Raspberry Pi OS,
 * dark/light themes, and holiday themes.
 */
public final class AddUserDialog extends JDialog {
    private final JTextField username=new JTextField();
    private final JTextField displayName=new JTextField();
    private final JComboBox<UserRole> role=new JComboBox<>(new UserRole[]{
            UserRole.MANAGEMENT,
            UserRole.OPERATIONS,
            UserRole.DISPLAY,
            UserRole.CUSTOM,
            UserRole.ADMINISTRATOR
    });
    private final JPasswordField password=new JPasswordField();
    private final JPasswordField confirm=new JPasswordField();
    private final JLabel status=new JLabel(" ");

    private boolean created=false;

    private AddUserDialog(Window owner,AppTheme theme){
        super(owner,"NORTH STAR • Add User",ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        JPanel root=new JPanel(new BorderLayout(0,18));
        root.setBorder(BorderFactory.createEmptyBorder(22,26,20,26));

        JPanel header=new JPanel(new BorderLayout(0,5));
        JLabel title=new JLabel("Add User");
        title.setFont(title.getFont().deriveFont(Font.BOLD,21f));

        JLabel subtitle=new JLabel(
                "<html>Create a local account and choose its starting role template. "
              + "Permissions can be customized afterward from Edit Access.</html>"
        );

        header.add(title,BorderLayout.NORTH);
        header.add(subtitle,BorderLayout.CENTER);
        root.add(header,BorderLayout.NORTH);

        JPanel form=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(7,0,7,12);
        c.anchor=GridBagConstraints.WEST;

        addRow(form,c,0,"Username",username);
        addRow(form,c,1,"Display name",displayName);
        addRow(form,c,2,"Role template",role);
        addRow(form,c,3,"Password",password);
        addRow(form,c,4,"Confirm password",confirm);

        c.gridx=0;
        c.gridy=5;
        c.gridwidth=2;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(6,0,0,0);
        status.setForeground(theme.accent());
        form.add(status,c);

        root.add(form,BorderLayout.CENTER);

        JButton cancel=new JButton("Cancel");
        JButton create=new JButton("Create User");
        create.putClientProperty("primaryAction",Boolean.TRUE);

        cancel.addActionListener(e->dispose());
        create.addActionListener(e->createUser());
        confirm.addActionListener(e->createUser());
        password.addActionListener(e->{
            if(confirm.getPassword().length==0)
                confirm.requestFocusInWindow();
            else
                createUser();
        });

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        buttons.add(cancel);
        buttons.add(create);
        root.add(buttons,BorderLayout.SOUTH);

        add(root,BorderLayout.CENTER);

        ThemeStyler.apply(this,theme);

        /*
         * ThemeStyler styles all controls recursively, including the role
         * JComboBox with the same custom renderer/arrow used by Settings.
         */
        status.setForeground(theme.muted());

        getRootPane().setDefaultButton(create);

        pack();
        setMinimumSize(new Dimension(600,getHeight()));
        setLocationRelativeTo(owner);

        SwingUtilities.invokeLater(username::requestFocusInWindow);
    }

    public static boolean showDialog(Component parent,AppTheme theme){
        Window owner=parent==null
                ?null
                :SwingUtilities.getWindowAncestor(parent);

        AddUserDialog dialog=new AddUserDialog(owner,theme);
        dialog.setVisible(true);
        return dialog.created;
    }

    private static void addRow(
            JPanel form,
            GridBagConstraints c,
            int row,
            String labelText,
            JComponent field
    ){
        c.gridy=row;
        c.gridx=0;
        c.gridwidth=1;
        c.weightx=0;
        c.fill=GridBagConstraints.NONE;
        c.insets=new Insets(7,0,7,18);

        JLabel label=new JLabel(labelText);
        label.setPreferredSize(new Dimension(145,34));
        form.add(label,c);

        c.gridx=1;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(7,0,7,0);

        field.setPreferredSize(new Dimension(330,36));
        form.add(field,c);
    }

    private void createUser(){
        char[] first=password.getPassword();
        char[] second=confirm.getPassword();

        try{
            if(username.getText().trim().isBlank())
                throw new IllegalArgumentException("Username is required.");

            if(!Arrays.equals(first,second))
                throw new IllegalArgumentException("Passwords do not match.");

            UserRole selected=(UserRole)role.getSelectedItem();
            if(selected==null)selected=UserRole.CUSTOM;

            Set<Permission> permissions=selected.defaultPermissions();

            UserService.createUser(
                    username.getText(),
                    displayName.getText(),
                    selected,
                    permissions,
                    true,
                    first
            );

            created=true;
            dispose();
        }catch(Exception ex){
            status.setText(ex.getMessage());
            Toolkit.getDefaultToolkit().beep();
            password.setText("");
            confirm.setText("");
            password.requestFocusInWindow();
        }finally{
            Arrays.fill(first,'\0');
            Arrays.fill(second,'\0');
        }
    }

    @Override
    public void dispose(){
        password.setText("");
        confirm.setText("");
        super.dispose();
    }
}
