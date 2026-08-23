package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class KpiImportProgressDialog extends JDialog {
    private final JLabel message=new JLabel("Preparing import…");
    private final JProgressBar progress=new JProgressBar(0,100);

    public KpiImportProgressDialog(Component parent){
        super(parent==null?null:SwingUtilities.getWindowAncestor(parent),
                "Importing KPI Data",ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        AppTheme theme=ThemedDialogs.resolveTheme(parent);
        JPanel root=new JPanel(new BorderLayout(0,14));
        root.setBorder(new EmptyBorder(22,24,22,24));

        JLabel title=new JLabel("Importing Operations KPI Data");
        title.setFont(title.getFont().deriveFont(Font.BOLD,17f));
        JPanel top=new JPanel();
        top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));
        top.add(title);top.add(Box.createVerticalStrut(5));top.add(message);

        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(470,24));
        root.add(top,BorderLayout.NORTH);
        root.add(progress,BorderLayout.CENTER);
        add(root);

        ThemeStyler.apply(this,theme);
        progress.setBackground(theme.panel2());
        progress.setForeground(theme.accent());
        progress.setBorder(BorderFactory.createLineBorder(theme.border(),1,true));

        pack();
        setMinimumSize(new Dimension(540,getHeight()));
        setLocationRelativeTo(parent);
    }

    public void updateProgress(int value,String text){
        int safe=Math.max(0,Math.min(100,value));
        progress.setValue(safe);
        progress.setString(safe+"%");
        if(text!=null&&!text.isBlank())message.setText(text);
    }
}
