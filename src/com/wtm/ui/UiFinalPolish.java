package com.wtm.ui;

import com.wtm.config.AppConfig;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Idempotent final UI stabilization for dynamic/injected surfaces that may be
 * created after the ordinary ThemeStyler traversal.
 */
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

            if(window instanceof JDialog dialog)
                polishDialog(dialog);

            if(window instanceof Container container){
                polishEmployeeLayout(container,false);
                polishTruckTracking(container);
                polishAppearanceScroll(container);
                polishUiFoundation(container);
                polishMainShowcase(container);
            }
        }
    }

    private static void polishDialog(JDialog dialog){
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        String marker=theme.id()+":"+dialog.getTitle();
        boolean first=!marker.equals(root.getClientProperty("northstar.finalPolish.theme"));

        if(first){
            root.putClientProperty("northstar.finalPolish.theme",marker);
            root.putClientProperty(
                    "apple.awt.windowAppearance",
                    theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
            dialog.setBackground(theme.bg());
            if(dialog.getContentPane()!=null)
                dialog.getContentPane().setBackground(theme.bg());
            ThemeStyler.apply(dialog,theme);
        }

        String title=dialog.getTitle()==null?"":dialog.getTitle();
        boolean geometrySensitive=
                title.equalsIgnoreCase("Add NorthStar Tracker")
                ||title.equalsIgnoreCase("Add Network Location")
                ||title.equalsIgnoreCase("Add Logistics Network Location");

        if(geometrySensitive&&!Boolean.TRUE.equals(
                root.getClientProperty("northstar.dialog.geometry.fixed"))){
            root.putClientProperty("northstar.dialog.geometry.fixed",Boolean.TRUE);
            SwingUtilities.invokeLater(()->fitDialogToContent(dialog));
        }else if(first){
            dialog.revalidate();
            dialog.repaint();
        }
    }

    private static void fitDialogToContent(JDialog dialog){
        if(!dialog.isDisplayable())return;
        try{
            dialog.pack();

            GraphicsConfiguration gc=dialog.getGraphicsConfiguration();
            Rectangle usable=gc==null
                    ?GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getMaximumWindowBounds()
                    :gc.getBounds();
            Insets screenInsets=gc==null
                    ?new Insets(0,0,0,0)
                    :Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxW=Math.max(520,usable.width-screenInsets.left-screenInsets.right-48);
            int maxH=Math.max(560,usable.height-screenInsets.top-screenInsets.bottom-48);

            String title=dialog.getTitle()==null?"":dialog.getTitle();
            int minW=title.equalsIgnoreCase("Add Network Location")?900:560;
            int minH=title.equalsIgnoreCase("Add Network Location")?700:690;

            Dimension packed=dialog.getSize();
            int w=Math.min(maxW,Math.max(minW,packed.width));
            int h=Math.min(maxH,Math.max(minH,packed.height));
            dialog.setSize(w,h);
            dialog.setMinimumSize(new Dimension(Math.min(minW,maxW),Math.min(minH,maxH)));
            dialog.setLocationRelativeTo(dialog.getOwner());
            dialog.revalidate();
            dialog.repaint();
        }catch(Exception ignored){
            dialog.revalidate();
            dialog.repaint();
        }
    }

    private static void polishEmployeeLayout(Component component,boolean inEmployees){
        boolean employeeRoot=inEmployees||EMPLOYEE_CLASS.equals(component.getClass().getName());

        if(employeeRoot&&component instanceof JSplitPane split){
            split.setBorder(null);
            split.setContinuousLayout(true);
            split.setOpaque(false);
            split.setBackground(Theme.bg());

            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                addEmployeeColumnGap(split);
            }else{
                if(split.getDividerSize()!=0)split.setDividerSize(0);
            }
        }

        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishEmployeeLayout(child,employeeRoot);
        }
    }

    private static void addEmployeeColumnGap(JSplitPane split){
        if(Boolean.TRUE.equals(split.getClientProperty("northstar.employee.columnGap"))){
            if(split.getDividerSize()!=0)split.setDividerSize(0);
            return;
        }
        Component left=split.getLeftComponent();
        if(left==null)return;

        JPanel wrapper=new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0,0,0,16));
        split.setLeftComponent(wrapper);
        wrapper.add(left,BorderLayout.CENTER);
        split.setDividerSize(0);
        split.putClientProperty("northstar.employee.columnGap",Boolean.TRUE);
        split.revalidate();
        split.repaint();
    }

    private static void polishTruckTracking(Component component){
        if(TRUCK_CLASS.equals(component.getClass().getName())&&component instanceof Container root){
            JTabbedPane tabs=findTabbedPane(root);
            if(tabs!=null)polishPlaybackTab(tabs);
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishTruckTracking(child);
        }
    }

    private static void polishPlaybackTab(JTabbedPane tabs){
        for(int i=0;i<tabs.getTabCount();i++){
            String title=tabs.getTitleAt(i);
            if(title==null||!title.equalsIgnoreCase(PLAYBACK_TITLE))continue;

            Component current=tabs.getComponentAt(i);
            if(current instanceof JComponent jc&&Boolean.TRUE.equals(
                    jc.getClientProperty("northstar.playback.scroll.fixed")))return;

            JSplitPane split=findSplit(current);
            if(split==null)return;

            Component controls=split.getTopComponent();
            Component map=split.getBottomComponent();
            if(controls==null||map==null)return;

            split.setTopComponent(null);
            split.setBottomComponent(null);

            JPanel stack=new JPanel();
            stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
            stack.setBackground(Theme.bg());
            stack.setBorder(new EmptyBorder(0,0,24,0));

            normalizeFullWidth(controls);
            normalizeFullWidth(map);

            Dimension controlPref=controls.getPreferredSize();
            int controlsHeight=Math.max(360,controlPref==null?360:controlPref.height);
            controls.setPreferredSize(new Dimension(
                    Math.max(900,controlPref==null?900:controlPref.width),controlsHeight));
            controls.setMaximumSize(new Dimension(Integer.MAX_VALUE,controlsHeight));

            Dimension mapPref=map.getPreferredSize();
            int mapHeight=Math.max(520,mapPref==null?520:mapPref.height);
            map.setPreferredSize(new Dimension(
                    Math.max(900,mapPref==null?900:mapPref.width),mapHeight));
            map.setMinimumSize(new Dimension(360,380));
            map.setMaximumSize(new Dimension(Integer.MAX_VALUE,mapHeight));

            stack.add(controls);
            stack.add(Box.createVerticalStrut(14));
            stack.add(map);

            JScrollPane scroll=new JScrollPane(
                    stack,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(Theme.bg());
            scroll.getVerticalScrollBar().setUnitIncrement(22);
            scroll.getVerticalScrollBar().setBlockIncrement(140);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);

            ThemeStyler.apply(scroll,Theme.active());
            scroll.setBorder(null);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);
            tabs.setComponentAt(i,scroll);
            tabs.revalidate();
            tabs.repaint();
            return;
        }
    }

    private static void normalizeFullWidth(Component component){
        if(component instanceof JComponent jc){
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension pref=jc.getPreferredSize();
            int h=pref==null?1:Math.max(1,pref.height);
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE,h));
        }
    }

    private static JTabbedPane findTabbedPane(Container root){
        for(Component child:root.getComponents()){
            if(child instanceof JTabbedPane tabs)return tabs;
            if(child instanceof Container nested){
                JTabbedPane found=findTabbedPane(nested);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static JSplitPane findSplit(Component component){
        if(component instanceof JSplitPane split)return split;
        if(component instanceof Container container){
            for(Component child:container.getComponents()){
                JSplitPane found=findSplit(child);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static void polishAppearanceScroll(Component component){
        if(component instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view!=null&&(containsText(view,"Appearance & Display")
                    ||containsText(view,"Startup Experience"))){
                if(!Boolean.TRUE.equals(scroll.getClientProperty("northstar.appearance.bottomSafe.v2"))){
                    scroll.putClientProperty("northstar.appearance.bottomSafe.v2",Boolean.TRUE);
                    scroll.getVerticalScrollBar().setUnitIncrement(20);
                    scroll.getVerticalScrollBar().setBlockIncrement(140);
                    if(view instanceof JComponent jc){
                        Border existing=jc.getBorder();
                        Border pad=new EmptyBorder(0,0,160,0);
                        jc.setBorder(existing==null?pad:
                                BorderFactory.createCompoundBorder(existing,pad));
                    }
                    scroll.revalidate();
                    scroll.repaint();
                }
            }
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishAppearanceScroll(child);
        }
    }

    private static void polishUiFoundation(Component component){
        if(component instanceof Container container&&
                containsText(container,"NorthStar UI Foundation")){
            normalizeFoundationCombos(container);
            return;
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishUiFoundation(child);
        }
    }

    private static void normalizeFoundationCombos(Component component){
        if(component instanceof JComboBox<?> box){
            AppTheme theme=Theme.active();
            if(!(box.getUI() instanceof ThemedComboBoxUI))
                box.setUI(new ThemedComboBoxUI(theme));
            box.setBackground(theme.panel2());
            box.setForeground(theme.text());
            Dimension pref=box.getPreferredSize();
            int width=Math.max(160,pref==null?160:pref.width);
            box.setPreferredSize(new Dimension(width,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMinimumSize(new Dimension(120,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE,ThemedComboBoxUI.CONTROL_HEIGHT));
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                normalizeFoundationCombos(child);
        }
    }

    private static void polishMainShowcase(Component component){
        if(SHOWCASE_CLASS.equals(component.getClass().getName())
                &&component instanceof JComponent showcase){
            ensureShowcaseRotation(showcase);
        }
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                polishMainShowcase(child);
        }
    }

    private static void ensureShowcaseRotation(JComponent showcase){
        long now=System.currentTimeMillis();
        Object nextObj=showcase.getClientProperty("northstar.showcase.nextCheck");
        long next=nextObj instanceof Long l?l:0L;
        if(now<next)return;
        showcase.putClientProperty("northstar.showcase.nextCheck",now+5000L);

        try{
            Field configField=showcase.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            Object raw=configField.get(showcase);
            if(!(raw instanceof AppConfig config))return;

            boolean changed=false;
            if(!config.mainShowcaseMediaEnabled){
                config.mainShowcaseMediaEnabled=true;
                changed=true;
            }
            if(!config.operationsAnnouncementsEnabled){
                config.operationsAnnouncementsEnabled=true;
                changed=true;
            }
            if(config.mainShowcaseIntervalSeconds<5){
                config.mainShowcaseIntervalSeconds=15;
                changed=true;
            }

            Field cardsField=showcase.getClass().getDeclaredField("cardIds");
            cardsField.setAccessible(true);
            Object idsObj=cardsField.get(showcase);
            int cardCount=idsObj instanceof List<?> ids?ids.size():0;

            Field timerField=showcase.getClass().getDeclaredField("rotationTimer");
            timerField.setAccessible(true);
            Object timerObj=timerField.get(showcase);
            boolean timerRunning=timerObj instanceof Timer timer&&timer.isRunning();

            if(changed||cardCount<=1||(cardCount>1&&!timerRunning)){
                if(showcase instanceof MainShowcasePanel panel)
                    panel.updateConfig(config);
            }
        }catch(ReflectiveOperationException ignored){
            // Keep the stabilizer safe across older/newer project builds.
        }
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel label&&label.getText()!=null
                &&stripHtml(label.getText()).contains(needle))return true;
        if(component instanceof AbstractButton button&&button.getText()!=null
                &&button.getText().contains(needle))return true;
        if(component instanceof Container container){
            for(Component child:container.getComponents())
                if(containsText(child,needle))return true;
        }
        return false;
    }

    private static String stripHtml(String text){
        return text.replaceAll("<[^>]+>"," ");
    }
}
