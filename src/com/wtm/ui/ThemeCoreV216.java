package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NorthStar theme/appearance core hardening for late-created Swing surfaces.
 * This is intentionally generic: modules keep using standard Swing controls,
 * while the theme engine owns window chrome surfaces, dialog spacing, and
 * first-paint layout invariants.
 */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";

    private ThemeCoreV216(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener(){
            @Override public void eventDispatched(AWTEvent event){
                if(event instanceof WindowEvent we
                        &&we.getID()==WindowEvent.WINDOW_OPENED
                        &&we.getWindow() instanceof JDialog dialog){
                    SwingUtilities.invokeLater(()->polishDialog(dialog));
                }
            }
        },AWTEvent.WINDOW_EVENT_MASK);
        SwingUtilities.invokeLater(()->{
            Timer watcher=new Timer(300,e->{
                for(Window window:Window.getWindows())
                    if(window!=null&&window.isDisplayable())applyImmediate(window);
            });
            watcher.setInitialDelay(0);
            watcher.start();
        });
    }

    public static void applyImmediate(Component component){
        if(component==null)return;
        applyEmployeeFirstPaint(component,false);
        expandAppearanceScroll(component);
    }

    private static void polishDialog(JDialog dialog){
        if(!dialog.isDisplayable())return;
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String title=dialog.getTitle()==null?"":dialog.getTitle();

        root.putClientProperty("apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        root.setOpaque(true);root.setBackground(theme.bg());
        root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){
            root.getLayeredPane().setOpaque(true);
            root.getLayeredPane().setBackground(theme.bg());
        }
        if(dialog.getContentPane() instanceof JComponent content){
            content.setOpaque(true);content.setBackground(theme.bg());
            content.setBorder(BorderFactory.createEmptyBorder());
        }
        dialog.setBackground(theme.bg());
        themeDialogTree(dialog,theme);

        if(isTargetForm(title)){
            root.putClientProperty("northstar.dialog.geometry.compact.v3",Boolean.TRUE);
            boolean tracker=title.equalsIgnoreCase("Add NorthStar Tracker");
            fit(dialog,tracker?560:760,tracker?500:555);
        }
        dialog.revalidate();dialog.repaint();
    }

    private static boolean isTargetForm(String title){
        return title.equalsIgnoreCase("Add NorthStar Tracker")
                ||title.equalsIgnoreCase("Add Network Location")
                ||title.equalsIgnoreCase("Add Logistics Network Location");
    }

    private static void fit(JDialog dialog,int wantedW,int wantedH){
        GraphicsConfiguration gc=dialog.getGraphicsConfiguration();
        Rectangle b=gc==null
                ?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                :gc.getBounds();
        Insets i=gc==null?new Insets(0,0,0,0):Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW=Math.max(440,b.width-i.left-i.right-40);
        int maxH=Math.max(420,b.height-i.top-i.bottom-40);
        dialog.setMinimumSize(new Dimension(0,0));
        dialog.setSize(Math.min(wantedW,maxW),Math.min(wantedH,maxH));
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

    private static void themeDialogTree(Component c,AppTheme theme){
        if(c instanceof JOptionPane p){
            p.setOpaque(true);p.setBackground(theme.bg());p.setForeground(theme.text());
            p.setBorder(new EmptyBorder(14,16,12,16));
        }else if(c instanceof JPanel p){
            p.setBackground(theme.bg());
            if(p.getClass()==JPanel.class)p.setOpaque(true);
        }else if(c instanceof JViewport v){
            v.setOpaque(true);v.setBackground(theme.bg());
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())themeDialogTree(child,theme);
    }

    private static void applyEmployeeFirstPaint(Component c,boolean inEmployees){
        boolean employee=inEmployees||EMPLOYEE_CLASS.equals(c.getClass().getName());
        if(employee&&c instanceof JSplitPane split){
            split.setBorder(null);split.setContinuousLayout(true);split.setOpaque(false);
            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                split.setResizeWeight(0.0);split.setDividerSize(0);
                split.setDividerLocation(500);split.setLastDividerLocation(500);
                Component left=split.getLeftComponent();
                if(left instanceof JComponent jc){
                    jc.setMinimumSize(new Dimension(390,0));
                    Dimension p=jc.getPreferredSize();
                    jc.setPreferredSize(new Dimension(500,p==null?1:Math.max(1,p.height)));
                }
            }else split.setDividerSize(0);
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())applyEmployeeFirstPaint(child,employee);
    }

    private static void expandAppearanceScroll(Component c){
        if(c instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view instanceof JComponent jc&&(contains(view,"Startup Experience")||contains(view,"Appearance"))
                    &&!Boolean.TRUE.equals(jc.getClientProperty("northstar.appearance.scroll.core216"))){
                jc.putClientProperty("northstar.appearance.scroll.core216",Boolean.TRUE);
                Dimension p=jc.getPreferredSize();
                int w=p==null?1:Math.max(1,p.width),h=p==null?1:Math.max(1,p.height);
                jc.setPreferredSize(new Dimension(w,h+850));
                Border old=jc.getBorder();
                jc.setBorder(BorderFactory.createCompoundBorder(
                        old==null?BorderFactory.createEmptyBorder():old,
                        new EmptyBorder(0,0,560,0)));
                scroll.getVerticalScrollBar().setUnitIncrement(24);
                scroll.getVerticalScrollBar().setBlockIncrement(200);
                scroll.revalidate();
            }
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())expandAppearanceScroll(child);
    }

    private static boolean contains(Component c,String text){
        if(c instanceof JLabel l&&l.getText()!=null&&l.getText().replaceAll("<[^>]+>"," ").contains(text))return true;
        if(c instanceof AbstractButton b&&b.getText()!=null&&b.getText().contains(text))return true;
        if(c instanceof Container container)
            for(Component child:container.getComponents())if(contains(child,text))return true;
        return false;
    }
}
