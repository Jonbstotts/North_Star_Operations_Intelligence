package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Idempotent pre-paint normalization for dynamic/legacy NorthStar surfaces.
 *
 * This class is intentionally presentation-only. It may normalize component
 * layout and styling, but it must never reach into feature-private state or
 * change persisted/runtime feature configuration as part of a paint pass.
 */
public final class UiFinalPolish {
    private static final String EMPLOYEE_CLASS="com.wtm.ui.EmployeeOperationsPanel";
    private static final String TRUCK_CLASS="com.wtm.ui.TruckTrackingPanel";
    private static final String PLAYBACK_TITLE="Playback / Live Map";

    private UiFinalPolish(){}

    static void prepareBeforePaint(Component component){
        if(component==null)return;
        polishEmployeeLayout(component,false);
        polishTruckTracking(component);
        polishAppearanceScroll(component);
        polishUiFoundation(component);
        polishModulesOrganization(component);
    }

    private static void polishEmployeeLayout(Component component,boolean inEmployees){
        boolean employeeRoot=inEmployees||EMPLOYEE_CLASS.equals(component.getClass().getName());
        if(employeeRoot&&component instanceof JSplitPane split){
            split.setBorder(null);split.setContinuousLayout(true);split.setOpaque(false);
            split.setBackground(Theme.bg());
            if(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT){
                addEmployeeColumnGap(split);
                split.setResizeWeight(0.0);split.setDividerSize(0);
                split.setDividerLocation(500);split.setLastDividerLocation(500);
                Component left=split.getLeftComponent();
                if(left instanceof JComponent jc){
                    jc.setMinimumSize(new Dimension(390,0));
                    Dimension pref=jc.getPreferredSize();
                    jc.setPreferredSize(new Dimension(500,pref==null?1:Math.max(1,pref.height)));
                }
            }else split.setDividerSize(0);
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishEmployeeLayout(child,employeeRoot);
    }

    private static void addEmployeeColumnGap(JSplitPane split){
        if(Boolean.TRUE.equals(split.getClientProperty("northstar.employee.columnGap"))){split.setDividerSize(0);return;}
        Component left=split.getLeftComponent();if(left==null)return;
        JPanel wrapper=new JPanel(new BorderLayout());wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0,0,0,18));
        split.setLeftComponent(wrapper);wrapper.add(left,BorderLayout.CENTER);
        split.setDividerSize(0);split.putClientProperty("northstar.employee.columnGap",Boolean.TRUE);
    }

    private static void polishTruckTracking(Component component){
        if(TRUCK_CLASS.equals(component.getClass().getName())&&component instanceof JComponent root){
            if(Boolean.TRUE.equals(root.getClientProperty("northstar.truck.prepared.v217")))return;
            root.putClientProperty("northstar.truck.prepared.v217",Boolean.TRUE);
            JTabbedPane tabs=findTabbedPane(root);if(tabs!=null)polishPlaybackTab(tabs);
            return;
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishTruckTracking(child);
    }

    private static void polishPlaybackTab(JTabbedPane tabs){
        for(int i=0;i<tabs.getTabCount();i++){
            String title=tabs.getTitleAt(i);
            if(title==null||!title.equalsIgnoreCase(PLAYBACK_TITLE))continue;
            Component current=tabs.getComponentAt(i);
            if(current instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty("northstar.playback.scroll.fixed")))return;
            JSplitPane split=findSplit(current);if(split==null)return;
            Component controls=split.getTopComponent(),map=split.getBottomComponent();
            if(controls==null||map==null)return;
            split.setTopComponent(null);split.setBottomComponent(null);
            JPanel stack=new JPanel();stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
            stack.setBackground(Theme.bg());stack.setBorder(new EmptyBorder(0,0,24,0));
            normalizeFullWidth(controls);normalizeFullWidth(map);
            Dimension cp=controls.getPreferredSize();int ch=Math.max(360,cp==null?360:cp.height);
            controls.setPreferredSize(new Dimension(Math.max(900,cp==null?900:cp.width),ch));
            controls.setMaximumSize(new Dimension(Integer.MAX_VALUE,ch));
            Dimension mp=map.getPreferredSize();int mh=Math.max(520,mp==null?520:mp.height);
            map.setPreferredSize(new Dimension(Math.max(900,mp==null?900:mp.width),mh));
            map.setMinimumSize(new Dimension(360,380));map.setMaximumSize(new Dimension(Integer.MAX_VALUE,mh));
            stack.add(controls);stack.add(Box.createVerticalStrut(14));stack.add(map);
            JScrollPane scroll=new JScrollPane(stack,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);scroll.getViewport().setBackground(Theme.bg());
            scroll.getVerticalScrollBar().setUnitIncrement(22);scroll.getVerticalScrollBar().setBlockIncrement(140);
            scroll.putClientProperty("northstar.playback.scroll.fixed",Boolean.TRUE);
            tabs.setComponentAt(i,scroll);return;
        }
    }

    private static void normalizeFullWidth(Component component){
        if(component instanceof JComponent jc){
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);Dimension pref=jc.getPreferredSize();
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE,pref==null?1:Math.max(1,pref.height)));
        }
    }

    private static JTabbedPane findTabbedPane(Container root){
        for(Component child:root.getComponents()){
            if(child instanceof JTabbedPane tabs)return tabs;
            if(child instanceof Container nested){JTabbedPane found=findTabbedPane(nested);if(found!=null)return found;}
        }
        return null;
    }

    private static JSplitPane findSplit(Component component){
        if(component instanceof JSplitPane split)return split;
        if(component instanceof Container container)
            for(Component child:container.getComponents()){JSplitPane found=findSplit(child);if(found!=null)return found;}
        return null;
    }

    private static void polishAppearanceScroll(Component component){
        if(component instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view instanceof JComponent jc&&(containsText(view,"Appearance")||containsText(view,"Startup Experience"))
                    &&!Boolean.TRUE.equals(jc.getClientProperty("northstar.appearance.bottomSafe.v217"))){
                jc.putClientProperty("northstar.appearance.bottomSafe.v217",Boolean.TRUE);
                Dimension pref=jc.getPreferredSize();int w=pref==null?1:Math.max(1,pref.width),h=pref==null?1:Math.max(1,pref.height);
                jc.setPreferredSize(new Dimension(w,h+1050));
                Border existing=jc.getBorder();Border pad=new EmptyBorder(0,0,700,0);
                jc.setBorder(existing==null?pad:BorderFactory.createCompoundBorder(existing,pad));
                scroll.getVerticalScrollBar().setUnitIncrement(24);scroll.getVerticalScrollBar().setBlockIncrement(220);
            }
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishAppearanceScroll(child);
    }

    private static void polishUiFoundation(Component component){
        if(component instanceof Container container&&containsText(container,"NorthStar UI Foundation")){normalizeFoundationCombos(container);return;}
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishUiFoundation(child);
    }

    private static void normalizeFoundationCombos(Component component){
        if(component instanceof JComboBox<?> box){
            AppTheme theme=Theme.active();
            if(!(box.getUI() instanceof ThemedComboBoxUI))box.setUI(new ThemedComboBoxUI(theme));
            box.setBackground(theme.panel2());box.setForeground(theme.text());
            Dimension pref=box.getPreferredSize();int width=Math.max(160,pref==null?160:pref.width);
            box.setPreferredSize(new Dimension(width,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMinimumSize(new Dimension(120,ThemedComboBoxUI.CONTROL_HEIGHT));
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE,ThemedComboBoxUI.CONTROL_HEIGHT));
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())normalizeFoundationCombos(child);
    }

    private static void polishModulesOrganization(Component component){
        if(component instanceof JTabbedPane tabs&&containsTab(tabs,"Organization")){
            int index=indexOfTab(tabs,"Organization");
            if(index>=0){
                Component page=tabs.getComponentAt(index);
                if(page instanceof JComponent jc&&!Boolean.TRUE.equals(jc.getClientProperty("northstar.modules.organization.topAligned"))){
                    JPanel topWrap=new JPanel(new BorderLayout());topWrap.setOpaque(false);topWrap.setBorder(new EmptyBorder(12,0,0,0));
                    tabs.setComponentAt(index,topWrap);topWrap.add(page,BorderLayout.NORTH);
                    topWrap.putClientProperty("northstar.modules.organization.topAligned",Boolean.TRUE);
                }
            }
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishModulesOrganization(child);
    }

    private static boolean containsTab(JTabbedPane tabs,String title){return indexOfTab(tabs,title)>=0;}
    private static int indexOfTab(JTabbedPane tabs,String title){
        for(int i=0;i<tabs.getTabCount();i++)if(title.equalsIgnoreCase(tabs.getTitleAt(i)))return i;
        return -1;
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel label&&label.getText()!=null&&stripHtml(label.getText()).contains(needle))return true;
        if(component instanceof AbstractButton button&&button.getText()!=null&&button.getText().contains(needle))return true;
        if(component instanceof Container container)
            for(Component child:container.getComponents())if(containsText(child,needle))return true;
        return false;
    }
    private static String stripHtml(String text){return text.replaceAll("<[^>]+>"," ");}
}
