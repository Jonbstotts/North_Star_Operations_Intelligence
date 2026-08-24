package com.wtm.ui;

import javax.swing.*;import javax.swing.border.LineBorder;import javax.swing.table.JTableHeader;import java.awt.*;

/** Applies NorthStar palette defaults to standard Swing controls used by dynamically added workspaces. */
public final class NorthStarThemeCompliance {
 private NorthStarThemeCompliance(){}
 public static void apply(Component c){
  if(c instanceof JTable t){t.setBackground(Theme.panel());t.setForeground(Theme.text());t.setGridColor(Theme.border());t.setSelectionBackground(Theme.accent());t.setSelectionForeground(Color.WHITE);t.setShowHorizontalLines(true);t.setShowVerticalLines(false);JTableHeader h=t.getTableHeader();if(h!=null){h.setBackground(Theme.panel2());h.setForeground(Theme.text());h.setFont(h.getFont().deriveFont(Font.BOLD));}}
  else if(c instanceof JTabbedPane t){t.setBackground(Theme.bg());t.setForeground(Theme.text());}
  else if(c instanceof JTextField t){t.setBackground(Theme.panel2());t.setForeground(Theme.text());t.setCaretColor(Theme.text());t.setBorder(new LineBorder(Theme.border(),1));}
  else if(c instanceof JTextArea t){t.setForeground(Theme.text());if(t.isOpaque())t.setBackground(Theme.panel());}
  else if(c instanceof JComboBox<?> x){x.setBackground(Theme.panel2());x.setForeground(Theme.text());}
  else if(c instanceof JSpinner s){s.setBackground(Theme.panel2());s.setForeground(Theme.text());}
  else if(c instanceof AbstractButton b){b.setForeground(Theme.text());if(!(b instanceof JCheckBox)&&!(b instanceof JRadioButton)){b.setBackground(Theme.panel2());b.setBorder(new LineBorder(Theme.border(),1));}else b.setOpaque(false);}
  else if(c instanceof JScrollPane s){s.setBackground(Theme.bg());s.getViewport().setBackground(Theme.panel());s.setBorder(new LineBorder(Theme.border(),1));}
  if(c instanceof Container ct)for(Component child:ct.getComponents())apply(child);
 }
}
