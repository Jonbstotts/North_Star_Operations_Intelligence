package com.wtm.app;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.*;

/** v1.7 bootstrap overlay that adds NorthStar Intelligence without disturbing the recovered v1.6 runtime. */
public final class AiEnabledMain {
    private AiEnabledMain(){}
    public static void main(String[] args){Main.main(args);SwingUtilities.invokeLater(AiEnabledMain::startInjector);}
    private static void startInjector(){javax.swing.Timer timer=new javax.swing.Timer(1100,e->inject());timer.setInitialDelay(1400);timer.start();}
    private static void inject(){try{if(!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;for(Window window:Window.getWindows()){if(!(window instanceof JFrame frame)||!frame.isDisplayable())continue;if(!frame.getClass().getName().equals("com.wtm.ui.OperationsWorkspaceFrame"))continue;injectSidebar(frame);injectDashboard(frame);}}catch(Throwable ignored){}}

    private static void injectSidebar(JFrame frame)throws Exception{
        if(findByName(frame,"northstar.ai.sidebar")!=null)return;JPanel side=findSidebarPanel(frame);if(side==null)return;
        JButton button=new JButton("✦  NorthStar Intelligence");button.setName("northstar.ai.sidebar");button.setHorizontalAlignment(SwingConstants.LEFT);button.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));button.setForeground(Theme.muted());button.setBackground(Theme.panel());button.setFocusPainted(false);button.setOpaque(false);button.setContentAreaFilled(false);button.setBorder(new EmptyBorder(9,12,9,12));button.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));button.setAlignmentX(Component.LEFT_ALIGNMENT);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));button.addActionListener(e->openFull(frame));
        int index=side.getComponentCount();for(int i=0;i<side.getComponentCount();i++){Component c=side.getComponent(i);if(c instanceof JLabel l&&"ADMINISTRATION".equalsIgnoreCase(l.getText())){index=i;break;}}side.add(button,Math.max(0,index));side.revalidate();side.repaint();
    }
    private static JPanel findSidebarPanel(Container root){for(Component c:root.getComponents()){if(c instanceof JScrollPane scroll){Component view=scroll.getViewport().getView();if(view instanceof JPanel p&&containsButtonText(p,"Dashboard")&&containsButtonText(p,"Truck Tracking"))return p;}if(c instanceof Container child){JPanel p=findSidebarPanel(child);if(p!=null)return p;}}return null;}
    private static boolean containsButtonText(Container c,String text){for(Component child:c.getComponents())if(child instanceof JButton b&&b.getText()!=null&&b.getText().contains(text))return true;return false;}

    private static void openFull(JFrame frame){
        try{Field hostField=frame.getClass().getDeclaredField("workspaceContentHost");hostField.setAccessible(true);JPanel host=(JPanel)hostField.get(frame);if(host==null)return;try{Field route=frame.getClass().getDeclaredField("activeWorkspaceRoute");route.setAccessible(true);route.set(frame,"NorthStar Intelligence");}catch(Exception ignored){}host.removeAll();host.add(new NorthStarIntelligencePanel(),BorderLayout.CENTER);host.revalidate();host.repaint();highlightAiSidebar(frame);}catch(Exception ex){JOptionPane.showMessageDialog(frame,"Unable to open NorthStar Intelligence.\n"+ex.getMessage(),"NorthStar Intelligence",JOptionPane.ERROR_MESSAGE);}
    }
    private static void highlightAiSidebar(JFrame frame){Component found=findByName(frame,"northstar.ai.sidebar");if(found instanceof JButton ai){ai.setForeground(Color.WHITE);ai.setOpaque(true);ai.setContentAreaFilled(true);ai.setBackground(Theme.panel2());}}

    private static void injectDashboard(JFrame frame)throws Exception{
        Field bodyField=frame.getClass().getDeclaredField("dashboardBody");bodyField.setAccessible(true);Object bodyObj=bodyField.get(frame);if(!(bodyObj instanceof JPanel body)||!body.isShowing())return;if(findByName(body,"northstar.ai.compact")!=null)return;BorderLayout layout=body.getLayout() instanceof BorderLayout b?b:null;if(layout==null)return;Component center=layout.getLayoutComponent(BorderLayout.CENTER);if(center==null||!center.getClass().getName().contains("DashboardGridPanel"))return;Method addTile=center.getClass().getDeclaredMethod("addTile",String.class,JComponent.class,String.class,int.class,int.class);addTile.setAccessible(true);String spec=findFreeSpec(center,6,3);NorthStarIntelligenceCompactPanel compact=new NorthStarIntelligenceCompactPanel(()->openFull(frame));addTile.invoke(center,"AI_INTELLIGENCE",compact,spec,5,3);center.revalidate();center.repaint();
    }
    private static String findFreeSpec(Component grid,int w,int h){boolean[][] used=new boolean[18][24];try{Field tilesF=grid.getClass().getDeclaredField("tiles");tilesF.setAccessible(true);Object raw=tilesF.get(grid);if(raw instanceof Iterable<?> tiles){for(Object tile:tiles){Field rectF=tile.getClass().getDeclaredField("gridRect");rectF.setAccessible(true);Object rect=rectF.get(tile);int x=intField(rect,"x"),y=intField(rect,"y"),rw=intField(rect,"w"),rh=intField(rect,"h");for(int yy=Math.max(0,y);yy<Math.min(18,y+rh);yy++)for(int xx=Math.max(0,x);xx<Math.min(24,x+rw);xx++)used[yy][xx]=true;}}}catch(Exception ignored){}int[][] preferred={{0,8},{0,7},{18,8},{0,11},{6,11},{12,11},{18,11}};for(int[] p:preferred)if(fits(used,p[0],p[1],w,h))return p[0]+","+p[1]+","+w+","+h;for(int y=0;y<=18-h;y++)for(int x=0;x<=24-w;x++)if(fits(used,x,y,w,h))return x+","+y+","+w+","+h;return "0,15,6,3";}
    private static boolean fits(boolean[][] used,int x,int y,int w,int h){if(x<0||y<0||x+w>24||y+h>18)return false;for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)if(used[yy][xx])return false;return true;}
    private static int intField(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
    private static Component findByName(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){Component found=findByName(child,name);if(found!=null)return found;}}return null;}
}
