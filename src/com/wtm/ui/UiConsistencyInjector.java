package com.wtm.ui;

import javax.swing.*;import javax.swing.border.*;import java.awt.*;

/** Shared UI contract enforcement for NorthStar workspaces and dialogs. */
public final class UiConsistencyInjector{
 private UiConsistencyInjector(){}
 public static void start(){applyDefaults();javax.swing.Timer t=new javax.swing.Timer(450,e->scan());t.setInitialDelay(300);t.start();}
 private static void applyDefaults(){UIManager.put("OptionPane.background",Theme.panel());UIManager.put("Panel.background",Theme.panel());UIManager.put("TextField.background",Theme.panel2());UIManager.put("TextField.foreground",Theme.text());UIManager.put("ComboBox.background",Theme.panel2());UIManager.put("ComboBox.foreground",Theme.text());UIManager.put("List.background",Theme.panel());UIManager.put("List.foreground",Theme.text());UIManager.put("Table.background",Theme.panel());UIManager.put("Table.foreground",Theme.text());}
 private static void scan(){applyDefaults();for(Window w:Window.getWindows())if(w.isDisplayable()){if(w instanceof JDialog){NorthStarThemeCompliance.apply(w);styleTree(w);}findTabs(w);}}
 private static void findTabs(Container c){for(Component x:c.getComponents()){if(x instanceof JTabbedPane t){String owner=ancestorName(t);if(owner.contains("DataCollection")||owner.contains("TruckTracking")||owner.contains("Locations"))NorthStarTabs.refreshHeaders(t);}if(x instanceof Container ct)findTabs(ct);}}
 private static void styleTree(Component c){if(c instanceof JComponent j){if(j instanceof JPanel||j instanceof JViewport)j.setBackground(Theme.panel());if(j instanceof JTextField||j instanceof JTextArea||j instanceof JList||j instanceof JTable){j.setBackground(Theme.panel2());j.setForeground(Theme.text());}if(j instanceof JScrollPane sp){sp.getViewport().setBackground(Theme.panel());sp.setBorder(new LineBorder(Theme.border()));}}if(c instanceof Container ct)for(Component x:ct.getComponents())styleTree(x);}
 private static String ancestorName(Component c){StringBuilder b=new StringBuilder();for(Container p=c.getParent();p!=null;p=p.getParent())b.append(p.getClass().getName()).append(' ');return b.toString();}
}
