package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Theme-aware file selection used by managed media imports.
 *
 * JFileChooser.showOpenDialog() lets the platform construct the enclosing
 * dialog, which can reintroduce native light colors on macOS. This wrapper
 * creates that dialog explicitly and themes the entire chooser/window before
 * it becomes visible.
 */
public final class ThemedFileChooser {
    private ThemedFileChooser(){}

    public static JFileChooser chooseImages(Component parent,boolean multiSelect){
        StyledChooser chooser=new StyledChooser();
        chooser.setMultiSelectionEnabled(multiSelect);
        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        chooser.setApproveButtonText(multiSelect?"Import Images":"Choose Image");

        AppTheme theme=ThemedDialogs.resolveTheme(parent);
        JDialog dialog=chooser.buildDialog(parent);
        dialog.setTitle(multiSelect?"Import Images":"Choose Image");
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        AtomicBoolean approved=new AtomicBoolean(false);
        chooser.addActionListener(e->{
            if(JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())){
                approved.set(true);
                dialog.dispose();
            }else if(JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())){
                dialog.dispose();
            }
        });

        ThemeStyler.apply(dialog,theme);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(760,520));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return approved.get()?chooser:null;
    }

    private static final class StyledChooser extends JFileChooser{
        JDialog buildDialog(Component parent){
            return createDialog(parent);
        }
    }
}
