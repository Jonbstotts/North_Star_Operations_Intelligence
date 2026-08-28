package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Custom North Star login field with integrated vector icon and optional password
 * visibility control. It deliberately avoids native text-field chrome so the
 * authentication surface reads like a product UI instead of a desktop form.
 */
public final class NorthStarLoginField extends JPanel {
    public enum Kind { USERNAME, PASSWORD }

    private final JTextField field;
    private final Kind kind;
    private boolean focused;
    private boolean passwordVisible=false;
    private char passwordEcho;
    private JButton visibilityButton;

    public NorthStarLoginField(Kind kind){
        this.kind=kind;
        setOpaque(false);
        setLayout(new BorderLayout(10,0));
        setBorder(new EmptyBorder(0,16,0,14));
        setPreferredSize(new Dimension(380,54));
        setMaximumSize(new Dimension(Integer.MAX_VALUE,54));

        JLabel icon=new JLabel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(focused?Theme.accent():Theme.muted());
                if(kind==Kind.USERNAME) paintUser(g2,getWidth(),getHeight());
                else paintLock(g2,getWidth(),getHeight());
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(28,28));
        add(icon,BorderLayout.WEST);

        if(kind==Kind.PASSWORD){
            JPasswordField password=new JPasswordField();

            /*
             * Do not rely on the platform LAF for the initial echo character.
             * Some macOS combinations can report an unset/zero echo character
             * before the password UI delegate is installed.
             */
            passwordEcho='\u2022';
            password.setEchoChar(passwordEcho);
            field=password;
        }else{
            field=new JTextField();
        }

        field.setOpaque(false);
        // Keep a small symmetric vertical inset so typed text and placeholders
        // share the exact same visual center as the leading icon.
        field.setBorder(new EmptyBorder(2,4,2,2));
        field.setForeground(Theme.text());
        field.setCaretColor(Theme.accent());
        field.setSelectionColor(Theme.accent());
        field.setSelectedTextColor(Color.WHITE);
        field.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        field.setPreferredSize(new Dimension(260,50));
        add(field,BorderLayout.CENTER);

        if(kind==Kind.PASSWORD){
            visibilityButton=new JButton(){
                @Override protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(passwordVisible
                            ?Theme.accent()
                            :Theme.muted());
                    paintEye(g2,getWidth(),getHeight(),passwordVisible);
                    g2.dispose();
                }
            };
            visibilityButton.setOpaque(false);
            visibilityButton.setContentAreaFilled(false);
            visibilityButton.setBorderPainted(false);
            visibilityButton.setFocusPainted(false);
            visibilityButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            visibilityButton.setPreferredSize(new Dimension(34,34));
            visibilityButton.setToolTipText("Show password");
            visibilityButton.addActionListener(e->
                    setPasswordVisible(!passwordVisible));
            add(visibilityButton,BorderLayout.EAST);
        }

        FocusAdapter focus=new FocusAdapter(){
            @Override public void focusGained(FocusEvent e){
                focused=true; repaint(); icon.repaint();
            }
            @Override public void focusLost(FocusEvent e){
                focused=false; repaint(); icon.repaint();
            }
        };
        field.addFocusListener(focus);

        // FlatLaf owns the text/password field delegates. Preserve the explicit
        // password echo character without replacing the installed Look & Feel UI.
        if(kind==Kind.PASSWORD)
            ((JPasswordField)field).setEchoChar(passwordEcho);
    }

    public JTextField textField(){ return field; }
    public String text(){ return field.getText(); }
    public void setText(String text){ field.setText(text==null?"":text); }
    public void clear(){ field.setText(""); }
    public char[] password(){
        return field instanceof JPasswordField p?p.getPassword():new char[0];
    }

    public boolean isPasswordVisible(){
        return kind==Kind.PASSWORD&&passwordVisible;
    }

    /**
     * Explicit visibility control used by the integrated eye button and useful
     * for deterministic UI testing. Passwords always start masked.
     */
    public void setPasswordVisible(boolean visible){
        if(kind!=Kind.PASSWORD||!(field instanceof JPasswordField password))
            return;

        passwordVisible=visible;
        password.setEchoChar(visible?(char)0:passwordEcho);

        if(visibilityButton!=null){
            visibilityButton.setToolTipText(
                    visible?"Hide password":"Show password");
            visibilityButton.repaint();
        }

        field.revalidate();
        field.repaint();
        repaint();
        field.requestFocusInWindow();
    }

    @Override protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w=getWidth()-1;
        int h=getHeight()-1;
        Color panel=Theme.panel();
        g2.setColor(new Color(
                panel.getRed(),panel.getGreen(),panel.getBlue(),235));
        g2.fillRoundRect(0,0,w,h,16,16);

        g2.setStroke(new BasicStroke(focused?1.8f:1f));
        g2.setColor(focused
                ?Theme.accent()
                :Theme.border());
        g2.drawRoundRect(0,0,w,h,16,16);

        if(focused){
            Color accent=Theme.accent();
            g2.setColor(new Color(
                    accent.getRed(),accent.getGreen(),accent.getBlue(),22));
            g2.fillRoundRect(2,2,w-3,h-3,14,14);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private static void paintUser(Graphics2D g,int w,int h){
        double cx=w/2.0, cy=h/2.0;
        g.fill(new Ellipse2D.Double(cx-4,cy-8,8,8));
        g.fill(new RoundRectangle2D.Double(cx-7,cy+1,14,8,6,6));
    }

    private static void paintLock(Graphics2D g,int w,int h){
        double cx=w/2.0, cy=h/2.0;
        g.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(cx-5,cy-9,10,12,0,180,Arc2D.OPEN));
        g.fill(new RoundRectangle2D.Double(cx-7,cy-2,14,11,3,3));
    }

    private static void paintEye(Graphics2D g,int w,int h,boolean open){
        int cx=w/2, cy=h/2;
        Path2D path=new Path2D.Double();
        path.moveTo(cx-10,cy);
        path.curveTo(cx-5,cy-7,cx+5,cy-7,cx+10,cy);
        path.curveTo(cx+5,cy+7,cx-5,cy+7,cx-10,cy);
        g.setStroke(new BasicStroke(1.8f));
        g.draw(path);
        if(open) g.fillOval(cx-3,cy-3,6,6);
        else g.drawOval(cx-3,cy-3,6,6);
    }
}
