import com.wtm.ui.AppTheme;
import com.wtm.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public final class ThemeSmokeTest {
    private ThemeSmokeTest() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            for (AppTheme theme : AppTheme.values()) {
                Theme.setActive(theme.id());
                verifyFlatLaf(theme);
                verifyControls(theme);
            }
        });
        System.out.println("FLATLAF_THEME_SMOKE_OK " + AppTheme.values().length + " themes");
    }

    private static void verifyFlatLaf(AppTheme theme) {
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf == null || !laf.getClass().getName().startsWith("com.formdev.flatlaf"))
            throw new IllegalStateException(theme.id() + ": non-FlatLaf look and feel " + laf);
        if (Theme.bg() == null || Theme.panel() == null || Theme.panel2() == null
                || Theme.border() == null || Theme.text() == null || Theme.muted() == null
                || Theme.accent() == null)
            throw new IllegalStateException(theme.id() + ": unresolved semantic color");

        for (String key : new String[]{
                "Component.accentColor",
                "Panel.background",
                "Label.foreground",
                "Table.selectionBackground",
                "List.selectionBackground"
        }) {
            Object effective = UIManager.get(key);
            Object lafDefault = UIManager.getLookAndFeelDefaults().get(key);
            if (!Objects.equals(effective, lafDefault))
                throw new IllegalStateException(
                        theme.id() + ": stale developer override for " + key);
        }
    }

    private static void verifyControls(AppTheme theme) {
        JComboBox<String> combo = new JComboBox<>(new String[]{"North Star", "Operations"});
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", new JPanel());
        JTextField text = new JTextField("Theme smoke");
        JPasswordField password = new JPasswordField("masked");
        JTable table = new JTable(new Object[][]{{"OK", theme.display()}}, new Object[]{"State", "Theme"});
        JList<String> list = new JList<>(new String[]{"One", "Two"});
        JScrollPane scroll = new JScrollPane(table);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(combo);
        panel.add(tabs);
        panel.add(text);
        panel.add(password);
        panel.add(list);
        panel.add(scroll);
        SwingUtilities.updateComponentTreeUI(panel);

        assertFlatDelegate(theme, "combo", combo.getUI());
        assertFlatDelegate(theme, "tabs", tabs.getUI());
        assertFlatDelegate(theme, "text", text.getUI());
        assertFlatDelegate(theme, "password", password.getUI());
        assertFlatDelegate(theme, "table", table.getUI());
        assertFlatDelegate(theme, "list", list.getUI());
        if (password.getEchoChar() == 0)
            throw new IllegalStateException(theme.id() + ": password field is unmasked");

        panel.setSize(720, 480);
        panel.doLayout();
        BufferedImage image = new BufferedImage(720, 480, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
    }

    private static void assertFlatDelegate(AppTheme theme, String control, Object ui) {
        if (ui == null || !ui.getClass().getName().startsWith("com.formdev.flatlaf"))
            throw new IllegalStateException(theme.id() + ": " + control + " delegate is " + ui);
    }
}
