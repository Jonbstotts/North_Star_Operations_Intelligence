package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/**
 * Platform-independent, full-height combo box delegate.
 *
 * macOS Aqua can retain a very small native current-value rectangle even
 * after the outer JComboBox is resized.  The result is the thin clipped text
 * strip visible in several North Star settings pages.  This delegate owns the
 * full geometry: 42px control height, padded renderer, fixed arrow region and
 * a popup list using the same North Star surfaces.
 */
public final class ThemedComboBoxUI extends BasicComboBoxUI {
    public static final int CONTROL_HEIGHT=42;
    private static final int ARROW_WIDTH=42;

    private final AppTheme theme;

    public ThemedComboBoxUI(AppTheme theme){
        this.theme=theme==null?AppTheme.NORTH_STAR:theme;
    }

    @Override
    public void installUI(JComponent c){
        super.installUI(c);

        if(c instanceof JComboBox<?> combo){
            combo.setOpaque(true);
            combo.setBackground(theme.panel2());
            combo.setForeground(theme.text());
            combo.setFocusable(true);

            Dimension existing=combo.getPreferredSize();
            int width=Math.max(150,existing==null?150:existing.width);
            Dimension size=new Dimension(width,CONTROL_HEIGHT);
            combo.setPreferredSize(size);
            combo.setMinimumSize(new Dimension(120,CONTROL_HEIGHT));
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE,CONTROL_HEIGHT));
            combo.setBorder(BorderFactory.createLineBorder(theme.border(),1,true));

            installRenderer(combo);

            if(combo.isEditable() && combo.getEditor()!=null
                    &&combo.getEditor().getEditorComponent() instanceof JComponent editor){
                editor.setBorder(new EmptyBorder(8,12,8,12));
                editor.setBackground(theme.panel2());
                editor.setForeground(theme.text());
            }
        }
    }

    private <T> void installRenderer(JComboBox<T> combo){
        combo.setRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focus
            ){
                JLabel label=(JLabel)super.getListCellRendererComponent(
                        list,value,index,selected,focus);
                label.setOpaque(true);
                label.setBorder(new EmptyBorder(8,12,8,12));
                label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
                label.setBackground(selected?theme.accent():theme.panel2());
                label.setForeground(selected
                        ?readableText(theme.accent())
                        :theme.text());
                return label;
            }
        });
    }

    @Override
    protected JButton createArrowButton(){
        JButton button=new JButton(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                try{
                    g2.setRenderingHint(
                            RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(theme.panel2());
                    g2.fillRect(0,0,getWidth(),getHeight());

                    g2.setColor(theme.border());
                    g2.drawLine(0,5,0,getHeight()-6);

                    int cx=getWidth()/2;
                    int cy=getHeight()/2+1;
                    int r=6;
                    Polygon arrow=new Polygon(
                            new int[]{cx-r,cx+r,cx},
                            new int[]{cy-r/2,cy-r/2,cy+r/2},
                            3);
                    g2.setColor(theme.text());
                    g2.fillPolygon(arrow);
                }finally{
                    g2.dispose();
                }
            }
        };
        button.setName("ComboBox.arrowButton");
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(ARROW_WIDTH,CONTROL_HEIGHT));
        button.setMinimumSize(new Dimension(ARROW_WIDTH,CONTROL_HEIGHT));
        return button;
    }

    @Override
    public void paintCurrentValueBackground(
            Graphics g,
            Rectangle bounds,
            boolean hasFocus
    ){
        g.setColor(theme.panel2());
        g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);
    }

    @Override
    protected Rectangle rectangleForCurrentValue(){
        Insets insets=comboBox.getInsets();
        int x=insets.left+1;
        int y=insets.top+1;
        int width=Math.max(
                1,
                comboBox.getWidth()-insets.left-insets.right-ARROW_WIDTH-2);
        int height=Math.max(
                1,
                comboBox.getHeight()-insets.top-insets.bottom-2);
        return new Rectangle(x,y,width,height);
    }

    private static Color readableText(Color background){
        double luminance=
                (0.2126*background.getRed())
                +(0.7152*background.getGreen())
                +(0.0722*background.getBlue());
        return luminance>150?new Color(20,22,24):Color.WHITE;
    }
}
