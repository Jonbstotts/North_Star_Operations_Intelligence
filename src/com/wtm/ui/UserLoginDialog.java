package com.wtm.ui;

import com.wtm.security.UserAccount;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Compact secure sign-in dialog used for in-application step-up authentication.
 * Startup authentication uses StartupLoginDialog so a startup presentation can
 * hand off into its dedicated logo-over-form composition without changing the
 * smaller protected-settings prompt.
 */
public final class UserLoginDialog extends JDialog {
    private final LoginFormPanel form;
    private UserAccount authenticated;

    private UserLoginDialog(
            Window owner,
            String title,
            String message,
            AppTheme requestedTheme,
            String suggestedUsername
    ){
        super(owner,title==null||title.isBlank()?"Secure Sign In":title,ModalityType.APPLICATION_MODAL);
        Theme.setActive(requestedTheme==null?AppTheme.NORTH_STAR.id():requestedTheme.id());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        getRootPane().putClientProperty("apple.awt.fullWindowContent",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar",Boolean.TRUE);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible",Boolean.FALSE);

        JPanel shell=new JPanel(new GridLayout(1,2));
        shell.setBackground(Theme.bg());
        shell.setBorder(BorderFactory.createLineBorder(Theme.border(),1,true));
        shell.add(brandPane());

        form=new LoginFormPanel(
                this,message,suggestedUsername,
                new Insets(62,30,34,30),
                account->{authenticated=account;dispose();}
        );
        shell.add(form);
        setContentPane(shell);

        getRootPane().setDefaultButton(form.defaultButton());
        getRootPane().registerKeyboardAction(
                e->dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setSize(820,500);
        setLocationRelativeTo(owner);
        ApplicationBrand.applyWindowIcon(this);
        SwingUtilities.invokeLater(form::focusInitial);
    }

    private JComponent brandPane(){
        NorthStarBackdropPanel brand=new NorthStarBackdropPanel();
        brand.setLayout(new GridBagLayout());
        brand.setBorder(new EmptyBorder(20,20,20,20));
        brand.add(new JLabel(NorthStarBrand.primaryLockup(330)));
        return brand;
    }

    public static UserAccount authenticate(
            Window owner,
            String title,
            String message,
            AppTheme theme,
            String suggestedUsername
    ){
        UserLoginDialog dialog=new UserLoginDialog(
                owner,title,message,theme,suggestedUsername);
        dialog.setVisible(true);
        return dialog.authenticated;
    }

    @Override public void dispose(){
        form.stop();
        super.dispose();
    }
}
