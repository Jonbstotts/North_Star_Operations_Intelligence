package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.util.Locale;

/**
 * Recursively applies the active application theme to Swing component trees.
 * v2.1.16 makes sidebar-route detection intrinsic instead of timing dependent:
 * native RoundedSidebarButton instances and late injected Data Collection /
 * NorthStar Intelligence buttons are recognized before generic JButton
 * styling can ever add an inactive outline during startup or config refresh.
 */
public final class ThemeStyler {
    private ThemeStyler(){}

    public static void apply(Component component,AppTheme theme){
        if(component==null)return;
        AppTheme resolved=theme==null?Theme.active():theme;
        applyRecursive(component,resolved);
    }

    private static void applyRecursive(Component component,AppTheme theme){
        Color bg=theme.bg();
        Color panel=theme.panel();
        Color panel2=theme.panel2();
        Color text=theme.text();
        Color border=theme.border();

        if(component instanceof JDialog dialog){
            dialog.setBackground(bg);
            if(dialog.getRootPane()!=null){
                dialog.getRootPane().putClientProperty(
                        "apple.awt.windowAppearance",
                        theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
                dialog.getRootPane().setOpaque(true);
                dialog.getRootPane().setBackground(bg);
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder());
            }
            if(dialog.getLayeredPane()!=null){
                dialog.getLayeredPane().setOpaque(true);
                dialog.getLayeredPane().setBackground(bg);
            }
            if(dialog.getContentPane()!=null)dialog.getContentPane().setBackground(bg);
        }

        if(component instanceof JOptionPane pane){
            pane.setOpaque(true); pane.setBackground(bg); pane.setForeground(text);
            pane.setBorder(new EmptyBorder(14,16,12,16));
        }else if(component instanceof JRootPane root){
            root.setOpaque(true); root.setBackground(bg); root.setBorder(BorderFactory.createEmptyBorder());
        }else if(component instanceof JLayeredPane layered){
            layered.setOpaque(true); layered.setBackground(bg);
        }else if(component instanceof JLabel label){
            label.setForeground(text); label.setOpaque(false);
        }else if(component instanceof JTextArea area){
            area.setBackground(panel2); area.setForeground(text); area.setCaretColor(text);
            area.setSelectionColor(theme.accent()); area.setSelectedTextColor(readableText(theme.accent()));
            area.setBorder(fieldBorder(border));
        }else if(component instanceof JPasswordField field){
            styleTextField(field,theme);
        }else if(component instanceof JTextField field){
            styleTextField(field,theme);
        }else if(component instanceof JComboBox<?> box){
            box.setBackground(panel2); box.setForeground(text); box.setOpaque(true);
            box.setUI(new ThemedComboBoxUI(theme));
        }else if(component instanceof JCheckBox check){
            check.setForeground(text); check.setBackground(bg); check.setOpaque(false);
        }else if(component instanceof JRadioButton radio){
            radio.setForeground(text); radio.setBackground(bg); radio.setOpaque(false);
        }else if(component instanceof JButton button){
            if(isSidebarRouteButton(button)){
                button.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
                button.setFocusPainted(false);
                button.setFocusable(false);
                button.setRolloverEnabled(false);
                button.getModel().setRollover(false);
                button.getModel().setArmed(false);
                button.getModel().setPressed(false);
                button.setOpaque(false);
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setBorder(new EmptyBorder(9,12,9,12));
            }else{
                boolean primary=Boolean.TRUE.equals(button.getClientProperty("primaryAction"));
                button.setForeground(primary?readableText(theme.accent()):text);
                button.setBackground(primary?theme.accent():panel2);
                button.setFocusPainted(false); button.setOpaque(true); button.setContentAreaFilled(true);
                button.setBorderPainted(true);
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(primary?theme.accent():border,1,true),
                        new EmptyBorder(7,12,7,12)));
            }
        }else if(component instanceof JTable table){
            table.setBackground(panel); table.setForeground(text);
            table.setSelectionBackground(theme.accent()); table.setSelectionForeground(readableText(theme.accent()));
            table.setGridColor(border); table.setShowGrid(false); table.setShowHorizontalLines(true);
            table.setIntercellSpacing(new Dimension(0,1)); table.setRowHeight(Math.max(28,table.getRowHeight()));
            table.setFillsViewportHeight(true);
            if(table.getTableHeader()!=null){
                table.getTableHeader().setBackground(panel2); table.getTableHeader().setForeground(text);
                table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0,0,1,0,border));
            }
        }else if(component instanceof JList<?> list){
            list.setBackground(panel); list.setForeground(text);
            list.setSelectionBackground(theme.accent()); list.setSelectionForeground(readableText(theme.accent()));
        }else if(component instanceof JTabbedPane tabs){
            tabs.setBackground(bg); tabs.setForeground(text); tabs.setOpaque(false);
            tabs.setBorder(BorderFactory.createEmptyBorder());
            final AppTheme t=theme;
            tabs.setUI(new BasicTabbedPaneUI(){
                @Override protected void installDefaults(){
                    super.installDefaults(); tabAreaInsets=new Insets(6,6,0,6);
                    selectedTabPadInsets=new Insets(0,0,0,0); contentBorderInsets=new Insets(1,0,0,0);
                }
                @Override protected void paintTabBackground(Graphics g,int tp,int ti,int x,int y,int w,int h,boolean selected){
                    g.setColor(selected?t.panel2():t.panel()); g.fillRoundRect(x+2,y+2,w-4,h-3,8,8);
                }
                @Override protected void paintTabBorder(Graphics g,int tp,int ti,int x,int y,int w,int h,boolean selected){
                    g.setColor(selected?t.accent():t.border()); g.drawRoundRect(x+2,y+2,w-5,h-4,8,8);
                }
                @Override protected void paintContentBorder(Graphics g,int tabPlacement,int selectedIndex){}
                @Override protected void paintFocusIndicator(Graphics g,int tp,Rectangle[] r,int ti,Rectangle ir,Rectangle tr,boolean s){}
            });
        }else if(component instanceof JSplitPane split){
            split.setBackground(bg); split.setBorder(BorderFactory.createEmptyBorder());
            split.setContinuousLayout(true);
        }else if(component instanceof JScrollPane scroll){
            scroll.setBackground(bg); scroll.setBorder(new RoundedOutlineBorder(border,12));
            scroll.getViewport().setBackground(panel); scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.getHorizontalScrollBar().setUnitIncrement(18);
        }else if(component instanceof JSpinner spinner){
            spinner.setBackground(panel2); spinner.setForeground(text); spinner.setBorder(fieldBorder(border));
            normalizeHeight(spinner,38);
            if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)applyRecursive(editor.getTextField(),theme);
        }else if(component instanceof JSeparator separator){
            separator.setForeground(border); separator.setBackground(border);
        }else if(component instanceof RoundedPanel rounded){
            rounded.setBackground(panel); rounded.putClientProperty("outlineColor",border);
        }else if(component instanceof JPanel p){
            if(p.isOpaque())p.setBackground(bg);
        }else if(component instanceof JViewport viewport){
            viewport.setBackground(panel);
        }

        if(component instanceof Container container){
            for(Component child:container.getComponents())applyRecursive(child,theme);
        }
    }

    private static boolean isSidebarRouteButton(JButton button){
        if(Boolean.TRUE.equals(button.getClientProperty("northstar.sidebar.route")))return true;
        String className=button.getClass().getName();
        if(className.contains("OperationsWorkspaceFrame$RoundedSidebarButton"))return true;
        String text=button.getText();
        if(text==null)return false;
        String normalized=text.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
        return normalized.contains("data collection")
                ||normalized.contains("northstar intelligence");
    }

    private static void styleTextField(JTextField field,AppTheme theme){
        field.setBackground(theme.panel2()); field.setForeground(theme.text()); field.setCaretColor(theme.text());
        field.setSelectionColor(theme.accent()); field.setSelectedTextColor(readableText(theme.accent()));
        field.setBorder(fieldBorder(theme.border())); field.setOpaque(true); normalizeHeight(field,38);
    }
    private static Color readableText(Color background){
        if(background==null)return Color.WHITE;
        double luminance=(0.2126*background.getRed())+(0.7152*background.getGreen())+(0.0722*background.getBlue());
        return luminance>150?new Color(20,22,24):Color.WHITE;
    }
    private static void normalizeHeight(JComponent component,int height){
        Dimension preferred=component.getPreferredSize(); int width=Math.max(120,preferred==null?120:preferred.width);
        component.setPreferredSize(new Dimension(width,height)); component.setMinimumSize(new Dimension(100,height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
    }
    private static Border fieldBorder(Color border){
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border,1,true),new EmptyBorder(7,9,7,9));
    }
    private static final class RoundedOutlineBorder extends AbstractBorder {
        private final Color color; private final int radius;
        private RoundedOutlineBorder(Color color,int radius){this.color=color;this.radius=radius;}
        @Override public Insets getBorderInsets(Component c){return new Insets(1,1,1,1);}
        @Override public Insets getBorderInsets(Component c,Insets i){i.set(1,1,1,1);return i;}
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){
            Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.drawRoundRect(x,y,w-1,h-1,radius,radius); g2.dispose();
        }
    }
}
