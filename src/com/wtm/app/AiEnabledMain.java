package com.wtm.app;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.NorthStarIntelligenceCompactPanel;
import com.wtm.ui.NorthStarIntelligencePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Event-driven Intelligence integration for the canonical workspace.
 *
 * <p>The compact dashboard surface is mounted directly into the source-backed
 * OperationsWorkspaceFrame dashboard body. The former DashboardGridPanel
 * reflection path depended on a runtime-only class that does not exist in the
 * canonical source tree and therefore could never be a reliable first-paint
 * integration point.</p>
 */
public final class AiEnabledMain {
    private static final String COMPACT_NAME="northstar.ai.compact";

    private AiEnabledMain(){}

    public static void injectWorkspace(JFrame frame){
        if(frame==null||!frame.isDisplayable()||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;
        injectSidebar(frame);
        injectDashboard(frame);
    }

    @SuppressWarnings("unchecked")
    public static void injectSidebar(JFrame frame){
        if(frame==null||!AuthorizationService.allowed(Permission.AI_ASSISTANT)||findByName(frame,"northstar.ai.sidebar")!=null)return;
        try{
            JPanel side=findSidebarPanel(frame);
            if(side==null)return;

            Method create=frame.getClass().getDeclaredMethod("createSidebarButton",String.class,boolean.class);
            create.setAccessible(true);
            JButton button=(JButton)create.invoke(frame,"✦  NorthStar Intelligence",false);
            button.setName("northstar.ai.sidebar");
            button.addActionListener(e->openFull(frame));

            Field routesField=frame.getClass().getDeclaredField("sidebarRouteButtons");
            routesField.setAccessible(true);
            Object raw=routesField.get(frame);
            if(raw instanceof Map<?,?> routes)
                ((Map<String,JButton>)routes).put("NorthStar Intelligence",button);

            int insert=side.getComponentCount();
            for(int i=0;i<side.getComponentCount();i++){
                Component c=side.getComponent(i);
                if(c instanceof JLabel l&&"ADMINISTRATION".equalsIgnoreCase(l.getText())){
                    insert=i;
                    break;
                }
            }
            if(insert>0&&side.getComponent(insert-1) instanceof Box.Filler)insert--;

            side.add(button,Math.max(0,insert));
            side.revalidate();
            side.repaint();
            invoke(frame,"updateSidebarSelection");
        }catch(ReflectiveOperationException ignored){}
    }

    public static void openFull(JFrame frame){
        if(frame==null||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;
        try{
            Field hostField=frame.getClass().getDeclaredField("workspaceContentHost");
            hostField.setAccessible(true);
            Object raw=hostField.get(frame);
            if(!(raw instanceof JPanel host))return;

            setField(frame,"activeWorkspaceRoute","NorthStar Intelligence");
            invoke(frame,"closeEmbeddedSettingsSession");
            host.removeAll();
            host.add(new NorthStarIntelligencePanel(),BorderLayout.CENTER);
            host.revalidate();
            host.repaint();
            invoke(frame,"updateSidebarSelection");
        }catch(ReflectiveOperationException ex){
            JOptionPane.showMessageDialog(
                    frame,
                    "Unable to open NorthStar Intelligence.",
                    "NorthStar Intelligence",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Mounts the compact assistant before the dashboard's trailing vertical
     * glue. This works before first paint as soon as dashboardBody exists; it
     * deliberately does not require Component.isShowing().
     */
    public static void injectDashboard(JFrame frame){
        if(frame==null||!AuthorizationService.allowed(Permission.AI_ASSISTANT)||findByName(frame,COMPACT_NAME)!=null)return;
        try{
            Field bodyField=frame.getClass().getDeclaredField("dashboardBody");
            bodyField.setAccessible(true);
            Object bodyObj=bodyField.get(frame);
            if(!(bodyObj instanceof JPanel body))return;

            NorthStarIntelligenceCompactPanel compact=
                    new NorthStarIntelligenceCompactPanel(()->openFull(frame));
            compact.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension preferred=compact.getPreferredSize();
            int height=Math.max(150,preferred==null?150:preferred.height);
            compact.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));

            JPanel mount=new JPanel(new BorderLayout());
            mount.setName(COMPACT_NAME);
            mount.setOpaque(false);
            mount.setBorder(new EmptyBorder(14,0,0,0));
            mount.setAlignmentX(Component.LEFT_ALIGNMENT);
            mount.setMaximumSize(new Dimension(Integer.MAX_VALUE,height+14));
            mount.add(compact,BorderLayout.CENTER);

            int index=Math.max(0,body.getComponentCount()-1);
            body.add(mount,index);
            body.revalidate();
            body.repaint();
        }catch(ReflectiveOperationException ignored){}
    }

    private static JPanel findSidebarPanel(Container root){
        for(Component c:root.getComponents()){
            if(c instanceof JScrollPane scroll){
                Component v=scroll.getViewport().getView();
                if(v instanceof JPanel p&&containsButtonText(p,"Dashboard"))return p;
            }
            if(c instanceof Container child){
                JPanel p=findSidebarPanel(child);
                if(p!=null)return p;
            }
        }
        return null;
    }

    private static boolean containsButtonText(Container c,String text){
        for(Component child:c.getComponents())
            if(child instanceof JButton b&&b.getText()!=null&&b.getText().contains(text))return true;
        return false;
    }

    private static Component findByName(Container root,String name){
        for(Component c:root.getComponents()){
            if(name.equals(c.getName()))return c;
            if(c instanceof Container child){
                Component found=findByName(child,name);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static void invoke(Object target,String name){
        try{
            Method method=target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        }catch(Exception ignored){}
    }

    private static void setField(Object target,String name,Object value){
        try{
            Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target,value);
        }catch(Exception ignored){}
    }
}
