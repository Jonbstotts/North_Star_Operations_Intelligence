package com.wtm.ui;

import com.wtm.security.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/**
 * North Star secure login.
 *
 * The two-pane layout mirrors the new operations-application shell: a stable
 * product identity surface at left and a compact authentication workflow at
 * right. Authentication itself remains owned by UserService.
 */
public final class UserLoginDialog extends JDialog {
    private final NorthStarLoginField username=
            new NorthStarLoginField(NorthStarLoginField.Kind.USERNAME);
    private final NorthStarLoginField password=
            new NorthStarLoginField(NorthStarLoginField.Kind.PASSWORD);
    private final JLabel status=new JLabel(" ",SwingConstants.LEFT);
    private final NorthStarPrimaryButton login=new NorthStarPrimaryButton("Sign In");
    private UserAccount authenticated;
    private Timer lockoutTimer;

    private UserLoginDialog(
            Window owner,
            String title,
            String message,
            AppTheme requestedTheme,
            String suggestedUsername
    ){
        super(owner,"Secure Sign In",ModalityType.APPLICATION_MODAL);
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
        shell.add(formPane(message,suggestedUsername));
        setContentPane(shell);

        getRootPane().setDefaultButton(login);
        getRootPane().registerKeyboardAction(
                e->dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setSize(820,500);
        setLocationRelativeTo(owner);
        ApplicationBrand.applyWindowIcon(this);

        SwingUtilities.invokeLater(()->{
            if(username.text().isBlank())username.textField().requestFocusInWindow();
            else password.textField().requestFocusInWindow();
        });
    }

    private JComponent brandPane(){
        NorthStarBackdropPanel brand=new NorthStarBackdropPanel();
        brand.setLayout(new GridBagLayout());
        brand.setBorder(new EmptyBorder(20,20,20,20));

        // Use the approved primary North Star logo raster exactly as supplied.
        JLabel approved=new JLabel(NorthStarBrand.primaryLockup(330));
        brand.add(approved);
        return brand;
    }

    private JComponent formPane(String message,String suggestedUsername){
        JPanel form=new JPanel();
        form.setBackground(Theme.panel());
        form.setBorder(new EmptyBorder(62,30,34,30));
        form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));

        JLabel welcome=new JLabel("Welcome Back");
        welcome.setForeground(Theme.text());
        welcome.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel helper=new JLabel(message==null||message.isBlank()?"Sign in to continue":message);
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
                this,
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

        form.add(welcome);
        form.add(Box.createVerticalStrut(4));
        form.add(helper);
        form.add(Box.createVerticalStrut(24));
        form.add(username);
        form.add(Box.createVerticalStrut(12));
        form.add(password);
        form.add(Box.createVerticalStrut(5));
        form.add(status);
        form.add(Box.createVerticalStrut(7));
        form.add(login);
        form.add(Box.createVerticalStrut(10));
        form.add(help);
        form.add(Box.createVerticalGlue());
        form.add(footer);
        return form;
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

    private void attemptLogin(){
        int delay=UserService.lockoutSecondsRemaining();
        if(delay>0){startDelay(delay);return;}

        char[] supplied=password.password();
        try{
            UserAccount account=UserService.authenticate(username.text(),supplied);
            if(account!=null){
                authenticated=account;
                AuditService.record(account.username(),"Successful login");
                dispose();
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

    @Override public void dispose(){
        if(lockoutTimer!=null)lockoutTimer.stop();
        password.clear();
        super.dispose();
    }
}
