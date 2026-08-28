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

    private UiFinalPolish(){}

    static void prepareBeforePaint(Component component){
        if(component==null)return;
        polishEmployeeLayout(component,false);
        polishAppearanceScroll(component);
        polishUiFoundation(component);
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

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel label&&label.getText()!=null&&stripHtml(label.getText()).contains(needle))return true;
        if(component instanceof AbstractButton button&&button.getText()!=null&&button.getText().contains(needle))return true;
        if(component instanceof Container container)
            for(Component child:container.getComponents())if(containsText(child,needle))return true;
        return false;
    }
    private static String stripHtml(String text){return text.replaceAll("<[^>]+>"," ");}
}
