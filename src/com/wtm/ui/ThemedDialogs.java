package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single themed modal-dialog path for all administrative UI.
 *
 * Using one implementation prevents macOS/Windows native JOptionPane painting
 * from breaking the active application theme. Custom forms, messages,
 * confirmations, and option prompts all pass through ThemeStyler.
 */
public final class ThemedDialogs {
    public enum Kind { INFO, WARNING, ERROR }

    private ThemedDialogs(){}

    public static AppTheme resolveTheme(Component parent){
        Window window=parent==null?null:SwingUtilities.getWindowAncestor(parent);
        if(window instanceof SettingsDialog settings)
            return settings.activeThemeForProtectedContent();
        return Theme.active();
    }

    public static void message(
            Component parent,
            String message,
            String title,
            Kind kind
    ){
        JPanel content=new JPanel(new BorderLayout(14,0));
        content.add(icon(kind,resolveTheme(parent)),BorderLayout.WEST);
        content.add(wrapped(message),BorderLayout.CENTER);
        show(parent,title,content,new String[]{"OK"},0,resolveTheme(parent));
    }

    public static boolean confirm(
            Component parent,
            String message,
            String title,
            String confirmLabel,
            Kind kind
    ){
        JPanel content=new JPanel(new BorderLayout(14,0));
        content.add(icon(kind,resolveTheme(parent)),BorderLayout.WEST);
        content.add(wrapped(message),BorderLayout.CENTER);
        return show(
                parent,title,content,
                new String[]{"Cancel",confirmLabel},
                1,resolveTheme(parent)
        )==1;
    }

    public static boolean confirmForm(
            Component parent,
            JComponent form,
            String title,
            String confirmLabel
    ){
        return show(
                parent,title,form,
                new String[]{"Cancel",confirmLabel},
                1,resolveTheme(parent)
        )==1;
    }

    public static int options(
            Component parent,
            String message,
            String title,
            String[] options,
            int defaultIndex
    ){
        return show(
                parent,title,wrapped(message),options,defaultIndex,
                resolveTheme(parent)
        );
    }

    private static int show(
            Component parent,
            String title,
            JComponent body,
            String[] options,
            int defaultIndex,
            AppTheme theme
    ){
        Window owner=parent==null?null:SwingUtilities.getWindowAncestor(parent);
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setResizable(false);

        JPanel root=new JPanel(new BorderLayout(0,18));
        root.setBorder(BorderFactory.createEmptyBorder(22,24,18,24));
        root.add(body,BorderLayout.CENTER);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        AtomicInteger selected=new AtomicInteger(-1);
        JButton defaultButton=null;

        for(int i=0;i<options.length;i++){
            final int index=i;
            JButton button=new JButton(options[i]);
            button.addActionListener(e->{
                selected.set(index);
                dialog.dispose();
            });
            buttons.add(button);
            if(i==defaultIndex)defaultButton=button;
        }
        root.add(buttons,BorderLayout.SOUTH);
        dialog.add(root,BorderLayout.CENTER);

        ThemeStyler.apply(dialog,theme);

        if(defaultButton!=null){
            defaultButton.setBackground(theme.accent());
            defaultButton.setForeground(bestText(theme.accent()));
            defaultButton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.border(),1,true),
                    BorderFactory.createEmptyBorder(6,12,6,12)
            ));
            dialog.getRootPane().setDefaultButton(defaultButton);
        }

        dialog.getRootPane().registerKeyboardAction(
                e->dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        dialog.pack();
        dialog.setMinimumSize(new Dimension(Math.max(430,dialog.getWidth()),dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return selected.get();
    }

    private static JComponent wrapped(String message){
        String escaped=message==null?"":message
                .replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\n","<br>");
        JLabel label=new JLabel("<html><div style='width:420px'>"+escaped+"</div></html>");
        return label;
    }

    private static JComponent icon(Kind kind,AppTheme theme){
        JLabel label=new JLabel(switch(kind){
            case INFO -> "i";
            case WARNING -> "!";
            case ERROR -> "×";
        },SwingConstants.CENTER);
        label.setOpaque(true);
        label.setPreferredSize(new Dimension(42,42));
        label.setFont(label.getFont().deriveFont(Font.BOLD,22f));
        Color bg=switch(kind){
            case INFO -> theme.accent();
            case WARNING -> Theme.warn();
            case ERROR -> Theme.danger();
        };
        label.setBackground(bg);
        label.setForeground(bestText(bg));
        label.setBorder(BorderFactory.createLineBorder(theme.border(),1,true));
        return label;
    }

    private static Color bestText(Color bg){
        double lum=(0.299*bg.getRed()+0.587*bg.getGreen()+0.114*bg.getBlue())/255.0;
        return lum>.62?Color.BLACK:Color.WHITE;
    }
}
