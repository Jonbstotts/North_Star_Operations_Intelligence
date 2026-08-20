package com.wtm.ui;

import com.wtm.security.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/** Branded one-time migration from the legacy shared administrator password. */
public final class LegacyAdminMigrationDialog extends JDialog {
    private final JTextField username=new JTextField("admin");
    private final JTextField displayName=new JTextField("System Administrator");
    private final JPasswordField password=new JPasswordField();
    private final JLabel status=new JLabel(" ",SwingConstants.CENTER);

    private UserAccount account;

    private LegacyAdminMigrationDialog(Window owner,AppTheme ignoredTheme){
        super(owner,"NORTH STAR • Account Upgrade",ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Best-effort macOS title-bar integration: preserve native window
        // controls while letting the NORTH STAR surface visually extend beneath it.
        getRootPane().putClientProperty("apple.awt.fullWindowContent",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible",Boolean.FALSE);
        setResizable(false);

        NorthStarBackdropPanel root=new NorthStarBackdropPanel();
        root.setLayout(new GridBagLayout());
        root.setBorder(new EmptyBorder(28,34,28,34));

        RoundedPanel card=new RoundedPanel(28);
        card.setBackground(Theme.panel());
        card.setLayout(new BorderLayout(0,16));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border(),1,true),
                new EmptyBorder(22,32,26,32)
        ));
        card.setPreferredSize(new Dimension(520,640));

        NorthStarBrandLockup logo=new NorthStarBrandLockup(
                NorthStarBrandLockup.Layout.VERTICAL,
                118,
                28,
                true
        );
        card.add(logo,BorderLayout.NORTH);

        JPanel form=new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(7,0,7,12);
        c.fill=GridBagConstraints.HORIZONTAL;

        c.gridx=0;c.gridy=0;c.gridwidth=2;c.weightx=1;
        JLabel title=new JLabel("Upgrade to named user access",SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD,18f));
        form.add(title,c);

        c.gridy++;
        JLabel help=new JLabel(
                "<html><div style='text-align:center'>Verify the previous "
                +"administrator password once. NORTH STAR will convert it into a "
                +"named Administrator account and remove the legacy credential."
                +"</div></html>",
                SwingConstants.CENTER
        );
        help.setForeground(Theme.muted());
        form.add(help,c);

        c.gridwidth=1;
        addRow(form,c,2,"Username",username);
        addRow(form,c,3,"Display name",displayName);
        addRow(form,c,4,"Existing password",password);

        c.gridx=0;c.gridy=5;c.gridwidth=2;
        status.setForeground(Theme.danger());
        form.add(status,c);

        card.add(form,BorderLayout.CENTER);

        JButton upgrade=new JButton("Upgrade Account");
        upgrade.putClientProperty("primaryAction",Boolean.TRUE);
        JButton cancel=new JButton("Cancel");

        upgrade.addActionListener(e->upgrade());
        password.addActionListener(e->upgrade());
        cancel.addActionListener(e->dispose());

        JPanel buttons=new JPanel(new GridLayout(2,1,0,8));
        buttons.setOpaque(false);
        buttons.add(upgrade);
        buttons.add(cancel);
        card.add(buttons,BorderLayout.SOUTH);

        root.add(card);
        setContentPane(root);
        ThemeStyler.apply(this,AppTheme.NORTH_STAR);

        root.setBackground(Theme.bg());
        card.setBackground(Theme.panel());
        help.setForeground(Theme.muted());
        status.setForeground(Theme.danger());

        getRootPane().setDefaultButton(upgrade);
        setSize(610,750);
        setLocationRelativeTo(owner);
        ApplicationBrand.applyWindowIcon(this);
    }

    public static UserAccount migrate(Window owner,AppTheme theme){
        LegacyAdminMigrationDialog dialog=
                new LegacyAdminMigrationDialog(owner,theme);
        dialog.setVisible(true);
        return dialog.account;
    }

    private static void addRow(
            JPanel panel,
            GridBagConstraints c,
            int row,
            String label,
            JComponent field
    ){
        c.gridy=row;c.gridx=0;c.gridwidth=1;c.weightx=0;
        c.insets=new Insets(7,0,7,16);
        panel.add(new JLabel(label),c);

        c.gridx=1;c.weightx=1;c.insets=new Insets(7,0,7,0);
        field.setPreferredSize(new Dimension(300,40));
        panel.add(field,c);
    }

    private void upgrade(){
        char[] supplied=password.getPassword();
        try{
            if(!AuthService.verify(supplied)){
                status.setText("Existing administrator password is incorrect.");
                return;
            }

            account=UserService.createFirstAdministrator(
                    username.getText(),
                    displayName.getText(),
                    supplied
            );
            AuditService.record(
                    account.username(),
                    "Migrated legacy administrator authentication to named user account"
            );
            AuthService.removeLegacyRecord();
            dispose();
        }catch(Exception ex){
            status.setText(ex.getMessage());
        }finally{
            Arrays.fill(supplied,'\0');
            password.setText("");
        }
    }
}
