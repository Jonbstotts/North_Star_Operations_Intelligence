package com.wtm.ui;

import com.wtm.security.AuditService;
import com.wtm.security.UserAccount;
import com.wtm.security.UserService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Canonical North Star username/password form shared by startup sign-in and
 * protected step-up authentication. Authentication behavior therefore has one
 * owner even though the two dialogs use different presentation shells.
 */
final class LoginFormPanel extends JPanel {
    private final NorthStarLoginField username=
            new NorthStarLoginField(NorthStarLoginField.Kind.USERNAME);
    private final NorthStarLoginField password=
            new NorthStarLoginField(NorthStarLoginField.Kind.PASSWORD);
    private final JLabel status=new JLabel(" ",SwingConstants.LEFT);
    private final NorthStarPrimaryButton login=new NorthStarPrimaryButton("Sign In");
    private final Component dialogParent;
    private final Consumer<UserAccount> authenticated;
    private Timer lockoutTimer;

    LoginFormPanel(
            Component dialogParent,
            String message,
            String suggestedUsername,
            Insets contentInsets,
            Consumer<UserAccount> authenticated
    ){
        this.dialogParent=dialogParent;
        this.authenticated=Objects.requireNonNull(authenticated);
        setBackground(Theme.panel());
        Insets insets=contentInsets==null?new Insets(28,30,24,30):contentInsets;
        setBorder(new EmptyBorder(insets.top,insets.left,insets.bottom,insets.right));
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        build(message,suggestedUsername);
    }

    private void build(String message,String suggestedUsername){
        JLabel welcome=new JLabel("Welcome Back");
        welcome.setForeground(Theme.text());
        welcome.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel helper=new JLabel(
                message==null||message.isBlank()?"Sign in to continue":message);
        helper.setForeground(Theme.muted());
        helper.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        helper.setAlignmentX(Component.LEFT_ALIGNMENT);

        username.setText(suggestedUsername==null?"":suggestedUsername);
        username.setAlignmentX(Component.LEFT_ALIGNMENT);
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        username.textField().putClientProperty("northstar.placeholder","Username");
        password.textField().putClientProperty("northstar.placeholder","Password");

        status.setForeground(Theme.danger());
        status.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        login.setAlignmentX(Component.LEFT_ALIGNMENT);
        login.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        login.addActionListener(e->attemptLogin());
        password.textField().addActionListener(e->attemptLogin());

        JButton help=new JButton("Forgot Password?");
        help.setOpaque(false);
        help.setContentAreaFilled(false);
        help.setBorderPainted(false);
        help.setFocusPainted(false);
        help.setForeground(Theme.accent());
        help.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        help.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        help.setAlignmentX(Component.CENTER_ALIGNMENT);
        help.addActionListener(e->ThemedDialogs.message(
                dialogParent,
                "Passwords are reset by a user with Users & Access permission. "
                        +"Contact an application administrator for assistance.",
                "Password Assistance",
                ThemedDialogs.Kind.INFO
        ));

        JLabel footer=new JLabel(
                "© 2026 "+BrandIdentity.product()+" "+BrandIdentity.tagline());
        Color footerColor=Theme.muted();
        footer.setForeground(new Color(
                footerColor.getRed(),footerColor.getGreen(),footerColor.getBlue(),150));
        footer.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(welcome);
        add(Box.createVerticalStrut(4));
        add(helper);
        add(Box.createVerticalStrut(18));
        add(username);
        add(Box.createVerticalStrut(9));
        add(password);
        add(Box.createVerticalStrut(3));
        add(status);
        add(Box.createVerticalStrut(5));
        add(login);
        add(Box.createVerticalStrut(6));
        add(help);
        add(Box.createVerticalGlue());
        add(footer);
    }

    JButton defaultButton(){return login;}

    void focusInitial(){
        if(username.text().isBlank())username.textField().requestFocusInWindow();
        else password.textField().requestFocusInWindow();
    }

    void stop(){
        if(lockoutTimer!=null)lockoutTimer.stop();
        password.clear();
    }

    private void attemptLogin(){
        int delay=UserService.lockoutSecondsRemaining();
        if(delay>0){startDelay(delay);return;}

        char[] supplied=password.password();
        try{
            UserAccount account=UserService.authenticate(username.text(),supplied);
            if(account!=null){
                AuditService.record(account.username(),"Successful login");
                authenticated.accept(account);
                return;
            }
        }finally{
            Arrays.fill(supplied,'\0');
            password.clear();
        }

        delay=UserService.lockoutSecondsRemaining();
        if(delay>0)startDelay(delay);
        else{
            status.setText("Incorrect username or password.");
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void startDelay(int seconds){
        login.setEnabled(false);
        username.textField().setEnabled(false);
        password.textField().setEnabled(false);
        final int[] remaining={Math.max(1,seconds)};
        status.setText("Too many attempts. Try again in "+remaining[0]+" seconds.");
        if(lockoutTimer!=null)lockoutTimer.stop();
        lockoutTimer=new Timer(1000,e->{
            remaining[0]--;
            if(remaining[0]<=0){
                ((Timer)e.getSource()).stop();
                login.setEnabled(true);
                username.textField().setEnabled(true);
                password.textField().setEnabled(true);
                status.setText(" ");
                password.textField().requestFocusInWindow();
            }else{
                status.setText("Too many attempts. Try again in "+remaining[0]+" seconds.");
            }
        });
        lockoutTimer.start();
    }
}
