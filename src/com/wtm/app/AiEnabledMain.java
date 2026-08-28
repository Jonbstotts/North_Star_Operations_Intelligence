package com.wtm.app;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.NorthStarIntelligenceCompactPanel;
import com.wtm.ui.NorthStarIntelligencePanel;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.prefs.Preferences;

/** Event-driven Intelligence integration. No polling timers or global Swing scans. */
public final class AiEnabledMain {
    private static final Preferences PREFS=Preferences.userRoot().node("com/wtm/northstar/intelligence");
    private static final String GRID_KEY="dashboardGrid";
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
            JPanel side=findSidebarPanel(frame);if(side==null)return;
            Method create=frame.getClass().getDeclaredMethod("createSidebarButton",String.class,boolean.class);create.setAccessible(true);
            JButton button=(JButton)create.invoke(frame,"✦  NorthStar Intelligence",false);button.setName("northstar.ai.sidebar");button.addActionListener(e->openFull(frame));
            Field routesField=frame.getClass().getDeclaredField("sidebarRouteButtons");routesField.setAccessible(true);Object raw=routesField.get(frame);if(raw instanceof Map<?,?> routes)((Map<String,JButton>)routes).put("NorthStar Intelligence",button);
            int insert=side.getComponentCount();for(int i=0;i<side.getComponentCount();i++){Component c=side.getComponent(i);if(c instanceof JLabel l&&"ADMINISTRATION".equalsIgnoreCase(l.getText())){insert=i;break;}}
            if(insert>0&&side.getComponent(insert-1) instanceof Box.Filler)insert--;
            side.add(button,Math.max(0,insert));side.revalidate();side.repaint();invoke(frame,"updateSidebarSelection");
        }catch(ReflectiveOperationException ignored){}
    }

    public static void openFull(JFrame frame){
        if(frame==null||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;
        try{
            Field hostField=frame.getClass().getDeclaredField("workspaceContentHost");hostField.setAccessible(true);Object raw=hostField.get(frame);if(!(raw instanceof JPanel host))return;
            setField(frame,"activeWorkspaceRoute","NorthStar Intelligence");invoke(frame,"closeEmbeddedSettingsSession");host.removeAll();host.add(new NorthStarIntelligencePanel(),BorderLayout.CENTER);host.revalidate();host.repaint();invoke(frame,"updateSidebarSelection");
        }catch(ReflectiveOperationException ex){JOptionPane.showMessageDialog(frame,"Unable to open NorthStar Intelligence.","NorthStar Intelligence",JOptionPane.ERROR_MESSAGE);}
    }

    public static void injectDashboard(JFrame frame){
        if(frame==null||!AuthorizationService.allowed(Permission.AI_ASSISTANT)||findByName(frame,"northstar.ai.compact")!=null)return;
        try{
            Field bodyField=frame.getClass().getDeclaredField("dashboardBody");bodyField.setAccessible(true);Object bodyObj=bodyField.get(frame);if(!(bodyObj instanceof JPanel body)||!body.isShowing())return;
            Component grid=findDashboardGrid(body);if(grid==null)return;
            Method addTile=grid.getClass().getDeclaredMethod("addTile",String.class,JComponent.class,String.class,int.class,int.class);addTile.setAccessible(true);
            String saved=PREFS.get(GRID_KEY,"");String spec=validGridSpec(saved)?saved:findFreeSpec(grid,6,4);
            NorthStarIntelligenceCompactPanel compact=new NorthStarIntelligenceCompactPanel(()->openFull(frame));addTile.invoke(grid,"AI_INTELLIGENCE",compact,spec,5,4);grid.revalidate();grid.repaint();
        }catch(ReflectiveOperationException ignored){}
    }

    private static Component findDashboardGrid(Container root){for(Component c:root.getComponents()){if(c.getClass().getName().contains("DashboardGridPanel"))return c;if(c instanceof Container child){Component found=findDashboardGrid(child);if(found!=null)return found;}}return null;}
    private static JPanel findSidebarPanel(Container root){for(Component c:root.getComponents()){if(c instanceof JScrollPane scroll){Component v=scroll.getViewport().getView();if(v instanceof JPanel p&&containsButtonText(p,"Dashboard"))return p;}if(c instanceof Container child){JPanel p=findSidebarPanel(child);if(p!=null)return p;}}return null;}
    private static boolean containsButtonText(Container c,String text){for(Component child:c.getComponents())if(child instanceof JButton b&&b.getText()!=null&&b.getText().contains(text))return true;return false;}
    private static Component findByName(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){Component f=findByName(child,name);if(f!=null)return f;}}return null;}
    private static boolean validGridSpec(String spec){if(spec==null||spec.isBlank())return false;try{String[] p=spec.split(",");if(p.length!=4)return false;int x=Integer.parseInt(p[0].trim()),y=Integer.parseInt(p[1].trim()),w=Integer.parseInt(p[2].trim()),h=Integer.parseInt(p[3].trim());return x>=0&&y>=0&&w>=5&&h>=4&&x+w<=24&&y+h<=18;}catch(Exception ex){return false;}}
    private static String findFreeSpec(Component grid,int w,int h){boolean[][] used=new boolean[18][24];try{Field tilesF=grid.getClass().getDeclaredField("tiles");tilesF.setAccessible(true);Object raw=tilesF.get(grid);if(raw instanceof Iterable<?> tiles)for(Object tile:tiles){Field rectF=tile.getClass().getDeclaredField("gridRect");rectF.setAccessible(true);Object rect=rectF.get(tile);int x=intField(rect,"x"),y=intField(rect,"y"),rw=intField(rect,"w"),rh=intField(rect,"h");for(int yy=Math.max(0,y);yy<Math.min(18,y+rh);yy++)for(int xx=Math.max(0,x);xx<Math.min(24,x+rw);xx++)used[yy][xx]=true;}}catch(Exception ignored){}int[][] preferred={{0,7},{18,7},{0,10},{6,10},{12,10},{18,10}};for(int[] p:preferred)if(fits(used,p[0],p[1],w,h))return p[0]+","+p[1]+","+w+","+h;for(int y=0;y<=18-h;y++)for(int x=0;x<=24-w;x++)if(fits(used,x,y,w,h))return x+","+y+","+w+","+h;return "0,14,6,4";}
    private static boolean fits(boolean[][] used,int x,int y,int w,int h){if(x<0||y<0||x+w>24||y+h>18)return false;for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)if(used[yy][xx])return false;return true;}
    private static int intField(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
    private static void invoke(Object target,String name){try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception ignored){}}
    private static void setField(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception ignored){}}
}
