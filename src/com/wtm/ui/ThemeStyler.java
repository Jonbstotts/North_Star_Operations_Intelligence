package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * Recursively applies the active application theme to Swing component trees.
 *
 * This deliberately styles nested settings/dialog content too. New settings
 * pages should be able to use ordinary Swing components without falling back
 * to the host operating system's light/default appearance.
 */
public final class ThemeStyler {
    private ThemeStyler(){}

    public static void apply(Component component,AppTheme theme){
        if(component==null)return;

        Color bg=theme.bg();
        Color panel=theme.panel();
        Color panel2=theme.panel2();
        Color text=theme.text();
        Color muted=theme.muted();
        Color border=theme.border();

        if(component instanceof JLabel label){
            label.setForeground(text);
            label.setOpaque(false);
        }else if(component instanceof JTextArea area){
            area.setBackground(panel2);
            area.setForeground(text);
            area.setCaretColor(text);
            area.setSelectionColor(theme.accent());
            area.setSelectedTextColor(readableText(theme.accent()));
            area.setBorder(fieldBorder(border));
        }else if(component instanceof JTextField field){
            field.setBackground(panel2);
            field.setForeground(text);
            field.setCaretColor(text);
            field.setSelectionColor(theme.accent());
            field.setSelectedTextColor(readableText(theme.accent()));
            field.setBorder(fieldBorder(border));
            field.setOpaque(true);
            normalizeHeight(field,38);
            normalizeHeight(field,38);
        }else if(component instanceof JPasswordField field){
            field.setBackground(panel2);
            field.setForeground(text);
            field.setCaretColor(text);
            field.setSelectionColor(theme.accent());
            field.setSelectedTextColor(readableText(theme.accent()));
            field.setBorder(fieldBorder(border));
            field.setOpaque(true);
        }else if(component instanceof JComboBox<?> box){
            box.setBackground(panel2);
            box.setForeground(text);
            box.setOpaque(true);
            box.setUI(new ThemedComboBoxUI(theme));
        }else if(component instanceof JCheckBox check){
            check.setForeground(text);
            check.setBackground(bg);
            check.setOpaque(false);
        }else if(component instanceof JRadioButton radio){
            radio.setForeground(text);
            radio.setBackground(bg);
            radio.setOpaque(false);
        }else if(component instanceof JButton button){
            boolean primary=Boolean.TRUE.equals(
                    button.getClientProperty("primaryAction"));
            button.setForeground(primary
                    ?readableText(theme.accent())
                    :text);
            button.setBackground(primary?theme.accent():panel2);
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(
                            primary?theme.accent():border,1,true),
                    new EmptyBorder(7,12,7,12)
            ));
        }else if(component instanceof JTable table){
            table.setBackground(panel);
            table.setForeground(text);
            table.setSelectionBackground(theme.accent());
            table.setSelectionForeground(readableText(theme.accent()));
            table.setGridColor(border);
            table.setShowGrid(false);
            table.setShowHorizontalLines(true);
            table.setIntercellSpacing(new Dimension(0,1));
            table.setRowHeight(Math.max(28,table.getRowHeight()));
            table.setFillsViewportHeight(true);
            if(table.getTableHeader()!=null){
                table.getTableHeader().setBackground(panel2);
                table.getTableHeader().setForeground(text);
                table.getTableHeader().setBorder(
                        BorderFactory.createMatteBorder(
                                0,0,1,0,border));
            }
        }else if(component instanceof JList<?> list){
            list.setBackground(panel);
            list.setForeground(text);
            list.setSelectionBackground(theme.accent());
            list.setSelectionForeground(readableText(theme.accent()));
        }else if(component instanceof JTabbedPane tabs){
            tabs.setBackground(bg);
            tabs.setForeground(text);
            tabs.setOpaque(true);
            tabs.setBorder(BorderFactory.createLineBorder(border,1,true));
            tabs.setUI(new BasicTabbedPaneUI(){
                @Override protected void installDefaults(){
                    super.installDefaults();
                    tabAreaInsets=new Insets(6,6,0,6);
                    selectedTabPadInsets=new Insets(0,0,0,0);
                    contentBorderInsets=new Insets(1,0,0,0);
                }

                @Override protected void paintTabBackground(
                        Graphics g,int tabPlacement,int tabIndex,
                        int x,int y,int w,int h,boolean selected){
                    g.setColor(selected?theme.panel2():theme.panel());
                    g.fillRoundRect(x+2,y+2,w-4,h-3,8,8);
                }

                @Override protected void paintTabBorder(
                        Graphics g,int tabPlacement,int tabIndex,
                        int x,int y,int w,int h,boolean selected){
                    g.setColor(selected?theme.accent():border);
                    g.drawRoundRect(x+2,y+2,w-5,h-4,8,8);
                }

                @Override protected void paintContentBorder(
                        Graphics g,int tabPlacement,int selectedIndex){
                    g.setColor(border);
                    g.drawRect(
                            0,
                            calculateTabAreaHeight(
                                    tabPlacement,runCount,maxTabHeight),
                            tabs.getWidth()-1,
                            tabs.getHeight()-calculateTabAreaHeight(
                                    tabPlacement,runCount,maxTabHeight)-1
                    );
                }

                @Override protected void paintFocusIndicator(
                        Graphics g,int tabPlacement,Rectangle[] rects,
                        int tabIndex,Rectangle iconRect,
                        Rectangle textRect,boolean isSelected){
                    // No native dotted focus ring; selected outline is enough.
                }
            });
        }else if(component instanceof JSplitPane split){
            split.setBackground(bg);
            split.setBorder(BorderFactory.createEmptyBorder());
            split.setDividerSize(8);
            split.setContinuousLayout(true);
        }else if(component instanceof JScrollPane scroll){
            scroll.setBackground(bg);
            scroll.setBorder(BorderFactory.createLineBorder(border,1,true));
            scroll.getViewport().setBackground(panel);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.getHorizontalScrollBar().setUnitIncrement(18);
        }else if(component instanceof JSpinner spinner){
            spinner.setBackground(panel2);
            spinner.setForeground(text);
            spinner.setBorder(fieldBorder(border));
            normalizeHeight(spinner,38);
            if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor){
                apply(editor.getTextField(),theme);
            }
        }else if(component instanceof JSeparator separator){
            separator.setForeground(border);
            separator.setBackground(border);
        }else if(component instanceof RoundedPanel rounded){
            rounded.setBackground(panel);
            rounded.putClientProperty("outlineColor",border);
        }else if(component instanceof JPanel panelComponent){
            panelComponent.setBackground(bg);
        }else if(component instanceof JViewport viewport){
            viewport.setBackground(panel);
        }

        if(component instanceof Container container){
            for(Component child:container.getComponents())
                apply(child,theme);
        }
    }

    private static Color readableText(Color background){
        if(background==null)return Color.WHITE;
        double luminance=
                (0.2126*background.getRed())
                +(0.7152*background.getGreen())
                +(0.0722*background.getBlue());
        return luminance>150?new Color(20,22,24):Color.WHITE;
    }

    private static void normalizeHeight(JComponent component,int height){
        Dimension preferred=component.getPreferredSize();
        int width=Math.max(120,preferred==null?120:preferred.width);
        component.setPreferredSize(new Dimension(width,height));
        component.setMinimumSize(new Dimension(100,height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
    }

    private static Border fieldBorder(Color border){
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border,1,true),
                new EmptyBorder(7,9,7,9)
        );
    }
}
