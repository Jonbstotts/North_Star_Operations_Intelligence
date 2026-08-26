package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared NorthStar theme/appearance lifecycle core.
 *
 * v2.1.8 removes the remaining legacy route-header lifecycle entirely and
 * applies dialog/combo theming before first paint. The sidebar is the sole
 * workspace navigation surface.
 */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> NORMALIZING=
            ThreadLocal.withInitial(()->Boolean.FALSE);
    private static final String LEGACY_DASHBOARD_TEXT="← Dashboard";
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";

    private ThemeCoreV216(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        long mask=AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK;
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener(){
            @Override public void eventDispatched(AWTEvent event){
                if(Boolean.TRUE.equals(NORMALIZING.get()))return;
                if(event instanceof ContainerEvent ce
                        &&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                    normalizeStructureBeforePaint(ce.getContainer());
                    return;
                }
                if(event instanceof WindowEvent we
                        &&we.getID()==WindowEvent.WINDOW_OPENED
                        &&we.getWindow() instanceof JDialog dialog){
                    normalizeBeforePaint(dialog);
                    polishDialog(dialog);
                }
            }
        },mask);
    }

    public static void applyImmediate(Component component){
        normalizeBeforePaint(component);
    }

    private static void normalizeStructureBeforePaint(Component component){
        if(component==null||Boolean.TRUE.equals(NORMALIZING.get()))return;
        NORMALIZING.set(Boolean.TRUE);
        try{
            removeLegacyRouteHeaders(component);
            stabilizeEmployee(component,false);
        }finally{
            NORMALIZING.set(Boolean.FALSE);
        }
    }

    static void normalizeBeforePaint(Component component){
        if(component==null||Boolean.TRUE.equals(NORMALIZING.get()))return;
        NORMALIZING.set(Boolean.TRUE);
        try{
            removeLegacyRouteHeaders(component);
            stabilizeEmployee(component,false);
            themeCombos(component,Theme.active());
            UiFinalPolish.prepareBeforePaint(component);
        }finally{
            NORMALIZING.set(Boolean.FALSE);
        }
    }

    private static void removeLegacyRouteHeaders(Component component){
        if(!(component instanceof Container container))return;
        Component[] children=container.getComponents();
        for(Component child:children){
            if(child instanceof Container header && containsLegacyDashboardButton(header)){
                if(header instanceof JPanel){
                    container.remove(child);
                    continue;
                }
            }
            removeLegacyRouteHeaders(child);
        }
    }

    private static boolean containsLegacyDashboardButton(Component component){
        if(component instanceof JButton b && LEGACY_DASHBOARD_TEXT.equals(b.getText()))return true;
        if(component instanceof Container c)
            for(Component child:c.getComponents())
                if(containsLegacyDashboardButton(child))return true;
        return false;
    }

    private static void stabilizeEmployee(Component c,boolean inEmployees){
        boolean employee=inEmployees||EMPLOYEE_CLASS.equals(c.getClass().getName());
        if(employee&&c instanceof JSplitPane split){
            split.setBorder(null);
            split.setContinuousLayout(true);
            split.setOpaque(false);
            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                split.setResizeWeight(0.0);
                split.setDividerSize(0);
                split.setDividerLocation(500);
                split.setLastDividerLocation(500);
                Component left=split.getLeftComponent();
                if(left instanceof JComponent jc){
                    Dimension p=jc.getPreferredSize();
                    jc.setMinimumSize(new Dimension(420,0));
                    jc.setPreferredSize(new Dimension(500,p==null?1:Math.max(1,p.height)));
                }
            }else split.setDividerSize(0);
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())stabilizeEmployee(child,employee);
    }

    private static void polishDialog(JDialog dialog){
        if(!dialog.isDisplayable())return;
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String title=dialog.getTitle()==null?"":dialog.getTitle();
        root.putClientProperty("apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        root.setOpaque(true); root.setBackground(theme.bg());
        root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){
            root.getLayeredPane().setOpaque(true);
            root.getLayeredPane().setBackground(theme.bg());
        }
        if(dialog.getContentPane() instanceof JComponent content){
            content.setOpaque(true); content.setBackground(theme.bg());
            content.setBorder(BorderFactory.createEmptyBorder());
        }
        dialog.setBackground(theme.bg());
        themeDialogTree(dialog,theme);
        themeCombos(dialog,theme);
        if(isTargetForm(title)){
            boolean tracker=title.equalsIgnoreCase("Add NorthStar Tracker");
            fit(dialog,tracker?620:900,tracker?560:650);
        }
        dialog.revalidate(); dialog.repaint();
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
            p.setOpaque(true); p.setBackground(theme.bg()); p.setForeground(theme.text());
            p.setBorder(new EmptyBorder(14,16,12,16));
        }else if(c instanceof JPanel p){
            p.setBackground(theme.bg());
            if(p.getClass()==JPanel.class)p.setOpaque(true);
        }else if(c instanceof JViewport v){
            v.setOpaque(true); v.setBackground(theme.bg());
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())themeDialogTree(child,theme);
    }

    private static void themeCombos(Component c,AppTheme theme){
        if(c instanceof JComboBox<?> box){
            if(!(box.getUI() instanceof ThemedComboBoxUI))
                box.setUI(new ThemedComboBoxUI(theme));
            box.setOpaque(true);
            box.setBackground(theme.panel2());
            box.setForeground(theme.text());
            box.setBorder(BorderFactory.createLineBorder(theme.border(),1,true));
            Dimension p=box.getPreferredSize();
            int w=p==null?180:Math.max(180,p.width);
            box.setPreferredSize(new Dimension(w,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMinimumSize(new Dimension(120,ThemedComboBoxUI.CONTROL_HEIGHT));
        }
        if(c instanceof Container container)
            for(Component child:container.getComponents())themeCombos(child,theme);
    }
}
