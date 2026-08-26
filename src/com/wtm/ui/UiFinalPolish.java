package com.wtm.ui;

import com.wtm.config.AppConfig;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Final idempotent UI stabilization for dynamic/injected NorthStar surfaces. */
public final class UiFinalPolish {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";
    private static final String TRUCK_CLASS="com.wtm.ui.TruckTrackingPanel";
    private static final String SHOWCASE_CLASS="com.wtm.ui.MainShowcasePanel";
    private static final String PLAYBACK_TITLE="Playback / Live Map";

    private UiFinalPolish(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        Runnable install=()->{
            Timer timer=new Timer(650,e->scan());
            timer.setInitialDelay(80);
            timer.start();
            scan();
        };
        if(SwingUtilities.isEventDispatchThread())install.run();
        else SwingUtilities.invokeLater(install);
    }

    private static void scan(){
        for(Window window:Window.getWindows()){
            if(window==null||!window.isDisplayable())continue;
            if(window instanceof JDialog dialog)polishDialog(dialog);
            if(window instanceof Container container){
                polishEmployeeLayout(container,false);
                polishTruckTracking(container);
                polishAppearanceScroll(container);
                polishUiFoundation(container);
                polishModulesOrganization(container);
                polishMainShowcase(container);
            }
        }
    }

    private static void polishDialog(JDialog dialog){
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String marker=theme.id()+":"+dialog.getTitle();
        boolean first=!marker.equals(root.getClientProperty("northstar.finalPolish.theme.v3"));
        if(first){
            root.putClientProperty("northstar.finalPolish.theme.v3",marker);
            root.putClientProperty("apple.awt.windowAppearance",
                    theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
            root.setOpaque(true);
            root.setBackground(theme.bg());
            root.setBorder(BorderFactory.createEmptyBorder());
            if(root.getLayeredPane()!=null){
                root.getLayeredPane().setOpaque(true);
                root.getLayeredPane().setBackground(theme.bg());
            }
            dialog.setBackground(theme.bg());
            if(dialog.getContentPane()!=null){
                dialog.getContentPane().setBackground(theme.bg());
                if(dialog.getContentPane() instanceof JComponent jc){
                    jc.setOpaque(true);
                    jc.setBorder(BorderFactory.createEmptyBorder());
                }
            }
            forceDialogSurface(dialog,theme);
            ThemeStyler.apply(dialog,theme);
        }

        String title=dialog.getTitle()==null?"":dialog.getTitle();
        boolean geometrySensitive=title.equalsIgnoreCase("Add NorthStar Tracker")
                ||title.equalsIgnoreCase("Add Network Location")
                ||title.equalsIgnoreCase("Add Logistics Network Location");
        if(geometrySensitive&&!Boolean.TRUE.equals(
                root.getClientProperty("northstar.dialog.geometry.compact.v3"))){
            root.putClientProperty("northstar.dialog.geometry.compact.v3",Boolean.TRUE);
            SwingUtilities.invokeLater(()->fitDialogCompact(dialog));
        }else if(first){
            dialog.revalidate(); dialog.repaint();
        }
    }

    private static void forceDialogSurface(Component component,AppTheme theme){
        if(component instanceof JPanel panel){
            panel.setBackground(theme.bg());
            if(panel.getClass()==JPanel.class)panel.setOpaque(true);
        }else if(component instanceof JViewport viewport){
            viewport.setBackground(theme.bg()); viewport.setOpaque(true);
        }
        if(component instanceof Container c){
            for(Component child:c.getComponents())forceDialogSurface(child,theme);
        }
    }

    private static void fitDialogCompact(JDialog dialog){
        if(!dialog.isDisplayable())return;
        try{
            dialog.pack();
            GraphicsConfiguration gc=dialog.getGraphicsConfiguration();
            Rectangle bounds=gc==null
                    ?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                    :gc.getBounds();
            Insets ins=gc==null?new Insets(0,0,0,0):Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxW=Math.max(520,bounds.width-ins.left-ins.right-48);
            int maxH=Math.max(500,bounds.height-ins.top-ins.bottom-48);
            String title=dialog.getTitle()==null?"":dialog.getTitle();
            boolean location=title.equalsIgnoreCase("Add Network Location")
                    ||title.equalsIgnoreCase("Add Logistics Network Location");
            int targetW=location?900:650;
            int targetH=location?650:610;
            dialog.setMinimumSize(new Dimension(Math.min(targetW,maxW),Math.min(targetH,maxH)));
            dialog.setSize(Math.min(targetW,maxW),Math.min(targetH,maxH));
            dialog.setLocationRelativeTo(dialog.getOwner());
            dialog.revalidate(); dialog.repaint();
        }catch(Exception ignored){ dialog.revalidate(); dialog.repaint(); }
    }

    private static void polishEmployeeLayout(Component component,boolean inEmployees){
        boolean employeeRoot=inEmployees||EMPLOYEE_CLASS.equals(component.getClass().getName());
        if(employeeRoot&&component instanceof JSplitPane split){
            split.setBorder(null); split.setContinuousLayout(true); split.setOpaque(false);
            split.setBackground(Theme.bg());
            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                addEmployeeColumnGap(split);
                int total=split.getWidth();
                if(total>0){
                    int target=Math.max(360,Math.min(500,(int)Math.round(total*0.34)));
                    if(Math.abs(split.getDividerLocation()-target)>8)split.setDividerLocation(target);
                }
                split.setResizeWeight(0.0);
            }else if(split.getDividerSize()!=0)split.setDividerSize(0);
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishEmployeeLayout(child,employeeRoot);
    }

    private static void addEmployeeColumnGap(JSplitPane split){
        if(Boolean.TRUE.equals(split.getClientProperty("northstar.employee.columnGap"))){
            split.setDividerSize(0); return;
        }
        Component left=split.getLeftComponent(); if(left==null)return;
        JPanel wrapper=new JPanel(new BorderLayout()); wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0,0,0,18));
        split.setLeftComponent(wrapper); wrapper.add(left,BorderLayout.CENTER);
        split.setDividerSize(0);
        split.putClientProperty("northstar.employee.columnGap",Boolean.TRUE);
        split.revalidate(); split.repaint();
    }

    private static void polishTruckTracking(Component component){
        if(TRUCK_CLASS.equals(component.getClass().getName())&&component instanceof Container root){
            JTabbedPane tabs=findTabbedPane(root); if(tabs!=null)polishPlaybackTab(tabs);
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishTruckTracking(child);
    }

    private static void polishPlaybackTab(JTabbedPane tabs){
        for(int i=0;i<tabs.getTabCount();i++){
            String title=tabs.getTitleAt(i);
            if(title==null||!title.equalsIgnoreCase(PLAYBACK_TITLE))continue;
            Component current=tabs.getComponentAt(i);
            if(current instanceof JComponent jc&&Boolean.TRUE.equals(
                    jc.getClientProperty("northstar.playback.scroll.fixed")))return;
            JSplitPane split=findSplit(current); if(split==null)return;
            Component controls=split.getTopComponent(), map=split.getBottomComponent();
            if(controls==null||map==null)return;
            split.setTopComponent(null); split.setBottomComponent(null);
            JPanel stack=new JPanel(); stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
            stack.setBackground(Theme.bg()); stack.setBorder(new EmptyBorder(0,0,24,0));
            normalizeFullWidth(controls); normalizeFullWidth(map);
            Dimension cp=controls.getPreferredSize(); int ch=Math.max(360,cp==null?360:cp.height);
            controls.setPreferredSize(new Dimension(Math.max(900,cp==null?900:cp.width),ch));
            controls.setMaximumSize(new Dimension(Integer.MAX_VALUE,ch));
            Dimension mp=map.getPreferredSize(); int mh=Math.max(520,mp==null?520:mp.height);
            map.setPreferredSize(new Dimension(Math.max(900,mp==null?900:mp.width),mh));
            map.setMinimumSize(new Dimension(360,380)); map.setMaximumSize(new Dimension(Integer.MAX_VALUE,mh));
            stack.add(controls); stack.add(Box.createVerticalStrut(14)); stack.add(map);
            JScrollPane scroll=new JScrollPane(stack,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null); scroll.getViewport().setBackground(Theme.bg());
            scroll.getVerticalScrollBar().setUnitIncrement(22); scroll.getVerticalScrollBar().setBlockIncrement(140);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);
            ThemeStyler.apply(scroll,Theme.active()); scroll.setBorder(null);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);
            tabs.setComponentAt(i,scroll); tabs.revalidate(); tabs.repaint(); return;
        }
    }

    private static void normalizeFullWidth(Component component){
        if(component instanceof JComponent jc){
            jc.setAlignmentX(Component.LEFT_ALIGNMENT); Dimension p=jc.getPreferredSize();
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE,p==null?1:Math.max(1,p.height)));
        }
    }

    private static JTabbedPane findTabbedPane(Container root){
        for(Component child:root.getComponents()){
            if(child instanceof JTabbedPane tabs)return tabs;
            if(child instanceof Container nested){JTabbedPane found=findTabbedPane(nested);if(found!=null)return found;}
        } return null;
    }

    private static JSplitPane findSplit(Component component){
        if(component instanceof JSplitPane split)return split;
        if(component instanceof Container c)
            for(Component child:c.getComponents()){JSplitPane f=findSplit(child);if(f!=null)return f;}
        return null;
    }

    private static void polishAppearanceScroll(Component component){
        if(component instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view!=null&&(containsText(view,"Appearance")||containsText(view,"Startup Experience"))
                    &&!Boolean.TRUE.equals(scroll.getClientProperty("northstar.appearance.bottomSafe.v3"))){
                scroll.putClientProperty("northstar.appearance.bottomSafe.v3",Boolean.TRUE);
                scroll.getVerticalScrollBar().setUnitIncrement(20);
                scroll.getVerticalScrollBar().setBlockIncrement(160);
                if(view instanceof JComponent jc){
                    Border existing=jc.getBorder();
                    Border pad=new EmptyBorder(0,0,320,0);
                    jc.setBorder(existing==null?pad:BorderFactory.createCompoundBorder(existing,pad));
                }
                scroll.revalidate(); scroll.repaint();
            }
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishAppearanceScroll(child);
    }

    private static void polishUiFoundation(Component component){
        if(component instanceof Container c&&containsText(c,"NorthStar UI Foundation")){
            normalizeFoundationCombos(c); return;
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishUiFoundation(child);
    }

    private static void normalizeFoundationCombos(Component component){
        if(component instanceof JComboBox<?> box){
            AppTheme theme=Theme.active();
            if(!(box.getUI() instanceof ThemedComboBoxUI))box.setUI(new ThemedComboBoxUI(theme));
            box.setBackground(theme.panel2()); box.setForeground(theme.text());
            Dimension p=box.getPreferredSize(); int w=Math.max(160,p==null?160:p.width);
            box.setPreferredSize(new Dimension(w,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMinimumSize(new Dimension(120,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE,ThemedComboBoxUI.CONTROL_HEIGHT));
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())normalizeFoundationCombos(child);
    }

    private static void polishModulesOrganization(Component component){
        if(component instanceof JTabbedPane tabs&&containsTab(tabs,"Organization")){
            int idx=indexOfTab(tabs,"Organization");
            if(idx>=0){
                Component page=tabs.getComponentAt(idx);
                if(page instanceof JComponent jc&&!Boolean.TRUE.equals(
                        jc.getClientProperty("northstar.modules.organization.topAligned"))){
                    JPanel topWrap=new JPanel(new BorderLayout());
                    topWrap.setOpaque(false); topWrap.setBorder(new EmptyBorder(12,0,0,0));
                    tabs.setComponentAt(idx,topWrap); topWrap.add(page,BorderLayout.NORTH);
                    topWrap.putClientProperty("northstar.modules.organization.topAligned",Boolean.TRUE);
                    tabs.revalidate(); tabs.repaint();
                }
            }
        }
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishModulesOrganization(child);
    }

    private static boolean containsTab(JTabbedPane tabs,String title){return indexOfTab(tabs,title)>=0;}
    private static int indexOfTab(JTabbedPane tabs,String title){
        for(int i=0;i<tabs.getTabCount();i++)if(title.equalsIgnoreCase(tabs.getTitleAt(i)))return i;
        return -1;
    }

    private static void polishMainShowcase(Component component){
        if(SHOWCASE_CLASS.equals(component.getClass().getName())&&component instanceof JComponent jc)
            ensureShowcaseRotation(jc);
        if(component instanceof Container c)
            for(Component child:c.getComponents())polishMainShowcase(child);
    }

    private static void ensureShowcaseRotation(JComponent showcase){
        long now=System.currentTimeMillis(); Object n=showcase.getClientProperty("northstar.showcase.nextCheck");
        if(n instanceof Long l&&now<l)return; showcase.putClientProperty("northstar.showcase.nextCheck",now+5000L);
        try{
            Field cf=showcase.getClass().getDeclaredField("config"); cf.setAccessible(true);
            Object raw=cf.get(showcase); if(!(raw instanceof AppConfig config))return;
            boolean changed=false;
            if(!config.mainShowcaseMediaEnabled){config.mainShowcaseMediaEnabled=true;changed=true;}
            if(!config.operationsAnnouncementsEnabled){config.operationsAnnouncementsEnabled=true;changed=true;}
            if(config.mainShowcaseIntervalSeconds<5){config.mainShowcaseIntervalSeconds=15;changed=true;}
            Field cards=showcase.getClass().getDeclaredField("cardIds"); cards.setAccessible(true);
            Object idsObj=cards.get(showcase); int count=idsObj instanceof List<?> ids?ids.size():0;
            Field tf=showcase.getClass().getDeclaredField("rotationTimer"); tf.setAccessible(true);
            Object timerObj=tf.get(showcase); boolean running=timerObj instanceof Timer t&&t.isRunning();
            if(changed||count<=1||(count>1&&!running))
                if(showcase instanceof MainShowcasePanel panel)panel.updateConfig(config);
        }catch(ReflectiveOperationException ignored){}
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel l&&l.getText()!=null&&stripHtml(l.getText()).contains(needle))return true;
        if(component instanceof AbstractButton b&&b.getText()!=null&&b.getText().contains(needle))return true;
        if(component instanceof Container c)
            for(Component child:c.getComponents())if(containsText(child,needle))return true;
        return false;
    }
    private static String stripHtml(String text){return text.replaceAll("<[^>]+>"," ");}
}
