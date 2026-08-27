package com.wtm.modular.ui;

import com.wtm.ingest.GmailDataPathService;
import com.wtm.ingest.TrustedSenderPolicy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * UI companion for the fail-closed Gmail trusted-sender policy.
 * Cosmetic and validation only; it performs no workspace/sidebar mutation.
 */
public final class TrustedEmailUiGuard {
    private static boolean installed;

    private TrustedEmailUiGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof ContainerEvent ce && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component child = ce.getChild();
                if (child != null) SwingUtilities.invokeLater(() -> normalize(child));
            }
            if (event instanceof ActionEvent ae && ae.getSource() instanceof JTextField field) {
                if (isTrustedSenderField(field)) validateAndSave(field);
            }
        }, AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);

        for (Window window : Window.getWindows()) {
            if (window instanceof Container c) normalize(c);
        }
    }

    private static void normalize(Component component) {
        if (component instanceof JLabel label) {
            String text = label.getText();
            if ("Approved senders".equalsIgnoreCase(text)) {
                label.setText("Trusted senders");
                label.setToolTipText("Only exact email addresses in this list may submit CSV documents.");
            } else if (text != null && text.startsWith("Blank approved-sender list accepts")) {
                label.setText(
                        "Trusted senders are required. Separate exact email addresses with commas or semicolons. " +
                        "Mail from all other senders is ignored."
                );
            }
        }

        if (component instanceof JTextField field && isTrustedSenderField(field)) {
            field.setToolTipText("Required. Example: manager@company.com, reports@company.com");
        }

        if (component instanceof Container c) {
            for (Component child : c.getComponents()) normalize(child);
        }
    }

    private static boolean isTrustedSenderField(JTextField field) {
        Container parent = field.getParent();
        if (parent == null) return false;

        for (Component sibling : parent.getComponents()) {
            if (sibling instanceof JLabel label) {
                String text = label.getText();
                if ("Approved senders".equalsIgnoreCase(text)
                        || "Trusted senders".equalsIgnoreCase(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateAndSave(JTextField field) {
        try {
            String raw = field.getText();
            TrustedSenderPolicy.parse(raw);
            GmailDataPathService.get().setApprovedSenders(raw);
            field.setToolTipText(raw == null || raw.isBlank()
                    ? "No trusted senders configured: email document import is blocked."
                    : "Trusted sender list saved.");
        } catch (RuntimeException ex) {
            field.setToolTipText(ex.getMessage());
            Toolkit.getDefaultToolkit().beep();
        }
    }
}
