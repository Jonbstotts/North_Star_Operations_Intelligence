package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared NorthStar route/theme lifecycle core.
 * v2.1.9 resolves legacy injected routes synchronously before first paint and
 * leaves mounted module geometry stable afterward.
 */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> NORMALIZING=ThreadLocal.withInitial(()->Boolean.FALSE);
    private static final String LEGACY_DASHBOARD_TEXT="← Dashboard";
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";
    private static final String TRUCK_CLASS="com.wtm.ui.TruckTrackingPanel";
    private ThemeCoreV216(){}
    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        long mask=AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK;
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(Boolean.TRUE.equals(NORMALIZING.get()))return;
            if(event instanceof ContainerEvent ce&&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                normalizeBeforePaint(ce.getChild());
                JFrame frame=findWorkspaceFrame(ce.getContainer());
                if(frame!=null)adaptActiveRoute(frame);
            }else if(event instanceof WindowEvent we&&we.getID()==WindowEvent.WINDOW_OPENED){
                normalizeBeforePaint(we.getWindow());
                if(we.getWindow() instanceof JDialog d)polishDialog(d);
                if(we.getWindow() instanceof JFrame f&&isWorkspaceFrame(f))adaptActiveRoute(f);
            }
        },mask);
    }
    public static void applyImmediate(Component component){normalizeBeforePaint(component);}
    static void normalizeBeforePaint(Component component){
        if(component==null||Boolean.TRUE.equals(NORMALIZING.get()))return;
        NORMALIZING.set(Boolean.TRUE);
        try{
            removeLegacyRouteHeaders(component);stabilizeEmployee(component,false);
            standardizeEmployeeImmediately(component);modernizeTruckImmediately(component);
            themeCombos(component,Theme.active());UiFinalPolish.prepareBeforePaint(component);
        }finally{NORMALIZING.set(Boolean.FALSE);}
    }
    private static void adaptActiveRoute(JFrame frame){
        if(Boolean.TRUE.equals(NORMALIZING.get()))return;
        NORMALIZING.set(Boolean.TRUE);
        try{
            String route=activeRoute(frame).toLowerCase();
            if(route.contains("location"))invokePrivateStatic("com.wtm.ui.LocationsNetworkInjector","adapt",new Class<?>[]{JFrame.class},frame);
            else if(route.contains("showcase"))invokePrivateStatic("com.wtm.modular.ui.ShowcaseModuleInjector","adapt",new Class<?>[]{JFrame.class},frame);
            else if(route.contains("appearance"))invokePrivateStatic("com.wtm.modular.ui.AppearanceFoundationInjector","adapt",new Class<?>[]{JFrame.class},frame);
            removeLegacyRouteHeaders(frame);themeCombos(frame,Theme.active());
        }finally{NORMALIZING.set(Boolean.FALSE);}
    }
    private static void standardizeEmployeeImmediately(Component root){
        Component employee=findByClass(root,EMPLOYEE_CLASS);if(employee==null)return;
        invokePrivateStatic("com.wtm.modular.ui.EmployeeUiStandardizer","walk",new Class<?>[]{Component.class},employee);
    }
    private static void modernizeTruckImmediately(Component root){
        Component truck=findByClass(root,TRUCK_CLASS);if(!(truck instanceof JPanel panel))return;
        invokePrivateStatic("com.wtm.ui.TruckTrackingModernizer","modernize",new Class<?>[]{JPanel.class},panel);
        JTabbedPane tabs=findTabs(panel);if(tabs==null)return;
        for(int i=tabs.getTabCount()-1;i>=0;i--){
            String t=tabs.getTitleAt(i)==null?"":tabs.getTitleAt(i).trim().toLowerCase();
            if(t.contains("trak-4")||t.contains("trak4")||t.equals("logistics network"))tabs.removeTabAt(i);
        }
    }
    private static void stabilizeEmployee(Component c,boolean inEmployees){
        boolean employee=inEmployees||EMPLOYEE_CLASS.equals(c.getClass().getName());
        if(employee&&c instanceof JSplitPane split){
            split.setBorder(null);split.setContinuousLayout(true);split.setOpaque(false);
            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                split.setResizeWeight(0.0);split.setDividerSize(0);split.setDividerLocation(500);split.setLastDividerLocation(500);
                Component left=split.getLeftComponent();if(left instanceof JComponent jc){Dimension p=jc.getPreferredSize();jc.setMinimumSize(new Dimension(420,0));jc.setPreferredSize(new Dimension(500,p==null?1:Math.max(1,p.height)));}
            }else split.setDividerSize(0);
        }
        if(c instanceof Container ct)for(Component child:ct.getComponents())stabilizeEmployee(child,employee);
    }
    private static void removeLegacyRouteHeaders(Component component){
        if(!(component instanceof Container container))return;
        for(Component child:container.getComponents()){
            if(child instanceof JPanel&&containsLegacyDashboardButton(child)){container.remove(child);continue;}
            removeLegacyRouteHeaders(child);
        }
    }
    private static boolean containsLegacyDashboardButton(Component c){
        if(c instanceof JButton b&&LEGACY_DASHBOARD_TEXT.equals(b.getText()))return true;
        if(c instanceof Container ct)for(Component child:ct.getComponents())if(containsLegacyDashboardButton(child))return true;return false;
    }
    private static void polishDialog(JDialog dialog){
        if(!dialog.isDisplayable())return;AppTheme theme=Theme.active();JRootPane root=dialog.getRootPane();String title=dialog.getTitle()==null?"":dialog.getTitle();
        root.putClientProperty("apple.awt.windowAppearance",theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");root.setOpaque(true);root.setBackground(theme.bg());root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){root.getLayeredPane().setOpaque(true);root.getLayeredPane().setBackground(theme.bg());}
        if(dialog.getContentPane() instanceof JComponent content){content.setOpaque(true);content.setBackground(theme.bg());content.setBorder(BorderFactory.createEmptyBorder());}
        dialog.setBackground(theme.bg());themeDialogTree(dialog,theme);themeCombos(dialog,theme);
        if(isTargetForm(title)){boolean tracker=title.equalsIgnoreCase("Add NorthStar Tracker");fit(dialog,tracker?620:900,tracker?560:650);}dialog.validate();dialog.repaint();
    }
    private static boolean isTargetForm(String title){return title.equalsIgnoreCase("Add NorthStar Tracker")||title.equalsIgnoreCase("Add Network Location")||title.equalsIgnoreCase("Add Logistics Network Location");}
    private static void fit(JDialog dialog,int wantedW,int wantedH){
        GraphicsConfiguration gc=dialog.getGraphicsConfiguration();Rectangle b=gc==null?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds():gc.getBounds();Insets i=gc==null?new Insets(0,0,0,0):Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW=Math.max(440,b.width-i.left-i.right-40),maxH=Math.max(420,b.height-i.top-i.bottom-40);dialog.setMinimumSize(new Dimension(0,0));dialog.setSize(Math.min(wantedW,maxW),Math.min(wantedH,maxH));dialog.setLocationRelativeTo(dialog.getOwner());
    }
    private static void themeDialogTree(Component c,AppTheme theme){
        if(c instanceof JOptionPane p){p.setOpaque(true);p.setBackground(theme.bg());p.setForeground(theme.text());p.setBorder(new EmptyBorder(14,16,12,16));}
        else if(c instanceof JPanel p){p.setBackground(theme.bg());if(p.getClass()==JPanel.class)p.setOpaque(true);}else if(c instanceof JViewport v){v.setOpaque(true);v.setBackground(theme.bg());}
        if(c instanceof Container ct)for(Component child:ct.getComponents())themeDialogTree(child,theme);
    }
    private static void themeCombos(Component c,AppTheme theme){if(c instanceof JComboBox<?> box){if(!(box.getUI() instanceof ThemedComboBoxUI))box.setUI(new ThemedComboBoxUI(theme));box.setBackground(theme.panel2());box.setForeground(theme.text());}if(c instanceof Container ct)for(Component child:ct.getComponents())themeCombos(child,theme);}
    private static Component findByClass(Component c,String name){if(name.equals(c.getClass().getName()))return c;if(c instanceof Container ct)for(Component child:ct.getComponents()){Component f=findByClass(child,name);if(f!=null)return f;}return null;}
    private static JTabbedPane findTabs(Component c){if(c instanceof JTabbedPane t)return t;if(c instanceof Container ct)for(Component child:ct.getComponents()){JTabbedPane f=findTabs(child);if(f!=null)return f;}return null;}
    private static JFrame findWorkspaceFrame(Component c){Component p=c;while(p!=null){if(p instanceof JFrame f&&isWorkspaceFrame(f))return f;p=p.getParent();}Window w=SwingUtilities.getWindowAncestor(c);return w instanceof JFrame f&&isWorkspaceFrame(f)?f:null;}
    private static boolean isWorkspaceFrame(JFrame f){return "com.wtm.ui.OperationsWorkspaceFrame".equals(f.getClass().getName());}
    private static String activeRoute(JFrame f){try{Field x=f.getClass().getDeclaredField("activeWorkspaceRoute");x.setAccessible(true);Object v=x.get(f);return v==null?"":v.toString();}catch(Exception ex){return "";}}
    private static Object invokePrivateStatic(String className,String method,Class<?>[] sig,Object... args){try{Class<?> c=Class.forName(className);Method m=c.getDeclaredMethod(method,sig);m.setAccessible(true);return m.invoke(null,args);}catch(ReflectiveOperationException ex){return null;}}
}
