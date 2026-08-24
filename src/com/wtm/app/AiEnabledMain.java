package com.wtm.app;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.prefs.Preferences;

/** v1.7 bootstrap overlay that adds NorthStar Intelligence to the recovered runtime. */
public final class AiEnabledMain {
    private static final Preferences PREFS=Preferences.userRoot().node("com/wtm/northstar/intelligence");
    private static final String GRID_KEY="dashboardGrid";
    private AiEnabledMain(){}
    public static void main(String[] args){Main.main(args);SwingUtilities.invokeLater(AiEnabledMain::startInjector);}
    private static void startInjector(){javax.swing.Timer timer=new javax.swing.Timer(900,e->inject());timer.setInitialDelay(1200);timer.start();}
    private static void inject(){
        try{
            for(Window window:Window.getWindows()){
                if(window==null||!window.isDisplayable())continue;
                if(window.getClass().getName().equals("com.wtm.ui.SettingsDialog"))injectAppearanceGlass(window);
                if(window instanceof JFrame frame&&frame.getClass().getName().equals("com.wtm.ui.OperationsWorkspaceFrame")){
                    applyGlassToTree(frame);
                    if(AuthorizationService.allowed(Permission.AI_ASSISTANT)){injectSidebar(frame);injectDashboard(frame);}
                }
            }
        }catch(Throwable ignored){}
    }

    private static void injectAppearanceGlass(Window settings)throws Exception{
        if(findByName(settings,"northstar.glass.appearance")!=null)return;
        Field tabsF=settings.getClass().getDeclaredField("tabs");tabsF.setAccessible(true);JTabbedPane tabs=(JTabbedPane)tabsF.get(settings);
        int idx=-1;for(int i=0;i<tabs.getTabCount();i++)if("Appearance".equalsIgnoreCase(tabs.getTitleAt(i))){idx=i;break;}if(idx<0)return;
        Component original=tabs.getComponentAt(idx);
        JPanel wrapper=new JPanel(new BorderLayout(0,10));wrapper.setName("northstar.glass.appearance");wrapper.setOpaque(false);wrapper.add(original,BorderLayout.CENTER);
        RoundedPanel card=new RoundedPanel(14);card.setLayout(new BorderLayout(12,0));card.setBackground(Theme.panel());card.putClientProperty("outlineColor",Theme.border());card.setBorder(new EmptyBorder(12,14,12,14));
        JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));JLabel title=new JLabel("GLASS SURFACES");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));JLabel note=new JLabel("Modern translucent card treatment for NorthStar dashboard and Intelligence surfaces.");note.setForeground(Theme.muted());note.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));words.add(title);words.add(Box.createVerticalStrut(3));words.add(note);card.add(words,BorderLayout.CENTER);
        JCheckBox glass=new JCheckBox("Enable Glass Surfaces");glass.setOpaque(false);glass.setSelected(IntelligenceGlassSettings.enabled());glass.addActionListener(e->{IntelligenceGlassSettings.setEnabled(glass.isSelected());for(Window w:Window.getWindows())if(w!=null&&w.isDisplayable()){applyGlassToTree(w);w.repaint();}});card.add(glass,BorderLayout.EAST);
        wrapper.add(card,BorderLayout.SOUTH);tabs.setComponentAt(idx,wrapper);tabs.revalidate();tabs.repaint();applyGlassToTree(settings);
    }

    private static void applyGlassToTree(Component c){
        if(c instanceof RoundedPanel p){Object original=p.getClientProperty("northstar.glass.originalBackground");if(original==null){p.putClientProperty("northstar.glass.originalBackground",p.getBackground());original=p.getBackground();}if(IntelligenceGlassSettings.enabled()){Color b=original instanceof Color co?co:Theme.panel();p.setBackground(new Color(b.getRed(),b.getGreen(),b.getBlue(),205));p.putClientProperty("outlineColor",new Color(Theme.border().getRed(),Theme.border().getGreen(),Theme.border().getBlue(),220));}else if(original instanceof Color co){p.setBackground(co);p.putClientProperty("outlineColor",Theme.border());}}
        if(c instanceof Container ct)for(Component child:ct.getComponents())applyGlassToTree(child);
    }

    @SuppressWarnings("unchecked") private static void injectSidebar(JFrame frame)throws Exception{
        if(findByName(frame,"northstar.ai.sidebar")!=null)return;JPanel side=findSidebarPanel(frame);if(side==null)return;Method create=frame.getClass().getDeclaredMethod("createSidebarButton",String.class,boolean.class);create.setAccessible(true);JButton button=(JButton)create.invoke(frame,"✦  NorthStar Intelligence",false);button.setName("northstar.ai.sidebar");button.addActionListener(e->openFull(frame));Field routesField=frame.getClass().getDeclaredField("sidebarRouteButtons");routesField.setAccessible(true);Object raw=routesField.get(frame);if(raw instanceof Map<?,?> routes)((Map<String,JButton>)routes).put("NorthStar Intelligence",button);int adminIndex=side.getComponentCount();for(int i=0;i<side.getComponentCount();i++){Component c=side.getComponent(i);if(c instanceof JLabel l&&"ADMINISTRATION".equalsIgnoreCase(l.getText())){adminIndex=i;break;}}int insertIndex=adminIndex;if(adminIndex>0&&side.getComponent(adminIndex-1) instanceof Box.Filler)insertIndex=adminIndex-1;side.add(button,Math.max(0,insertIndex));side.revalidate();side.repaint();invokeSidebarSelection(frame);
    }
    private static JPanel findSidebarPanel(Container root){for(Component c:root.getComponents()){if(c instanceof JScrollPane scroll){Component view=scroll.getViewport().getView();if(view instanceof JPanel p&&containsButtonText(p,"Dashboard"))return p;}if(c instanceof Container child){JPanel p=findSidebarPanel(child);if(p!=null)return p;}}return null;}
    private static boolean containsButtonText(Container c,String text){for(Component child:c.getComponents())if(child instanceof JButton b&&b.getText()!=null&&b.getText().contains(text))return true;return false;}
    private static void openFull(JFrame frame){try{Field hostField=frame.getClass().getDeclaredField("workspaceContentHost");hostField.setAccessible(true);JPanel host=(JPanel)hostField.get(frame);if(host==null)return;try{Field route=frame.getClass().getDeclaredField("activeWorkspaceRoute");route.setAccessible(true);route.set(frame,"NorthStar Intelligence");}catch(Exception ignored){}try{Method close=frame.getClass().getDeclaredMethod("closeEmbeddedSettingsSession");close.setAccessible(true);close.invoke(frame);}catch(Exception ignored){}host.removeAll();host.add(new NorthStarIntelligencePanel(),BorderLayout.CENTER);host.revalidate();host.repaint();invokeSidebarSelection(frame);applyGlassToTree(host);}catch(Exception ex){JOptionPane.showMessageDialog(frame,"Unable to open NorthStar Intelligence.\n"+ex.getMessage(),"NorthStar Intelligence",JOptionPane.ERROR_MESSAGE);}}
    private static void invokeSidebarSelection(JFrame frame){try{Method update=frame.getClass().getDeclaredMethod("updateSidebarSelection");update.setAccessible(true);update.invoke(frame);}catch(Exception ignored){}}
    private static void injectDashboard(JFrame frame)throws Exception{Field bodyField=frame.getClass().getDeclaredField("dashboardBody");bodyField.setAccessible(true);Object bodyObj=bodyField.get(frame);if(!(bodyObj instanceof JPanel body)||!body.isShowing())return;if(findByName(body,"northstar.ai.compact")!=null)return;Component grid=findDashboardGrid(body);if(grid==null)return;Method addTile=grid.getClass().getDeclaredMethod("addTile",String.class,JComponent.class,String.class,int.class,int.class);addTile.setAccessible(true);String saved=PREFS.get(GRID_KEY,"");String spec=validGridSpec(saved)?saved:findFreeSpec(grid,6,4);NorthStarIntelligenceCompactPanel compact=new NorthStarIntelligenceCompactPanel(()->openFull(frame));addTile.invoke(grid,"AI_INTELLIGENCE",compact,spec,5,4);grid.revalidate();grid.repaint();startGridPersistenceWatcher(grid,compact,spec);applyGlassToTree(compact);}
    private static void startGridPersistenceWatcher(Component grid,JComponent compact,String initial){final String[] last={initial};javax.swing.Timer watcher=new javax.swing.Timer(650,null);watcher.addActionListener(e->{if(!grid.isDisplayable()){watcher.stop();return;}try{String current=findTileGridSpec(grid,compact);if(current!=null&&!current.equals(last[0])){PREFS.put(GRID_KEY,current);last[0]=current;}}catch(Exception ignored){}});watcher.setInitialDelay(900);watcher.start();}
    private static String findTileGridSpec(Component grid,JComponent compact)throws Exception{Field tilesF=grid.getClass().getDeclaredField("tiles");tilesF.setAccessible(true);Object raw=tilesF.get(grid);if(raw instanceof Iterable<?> tiles){for(Object tile:tiles){Field contentF=tile.getClass().getDeclaredField("content");contentF.setAccessible(true);if(contentF.get(tile)!=compact)continue;Field rectF=tile.getClass().getDeclaredField("gridRect");rectF.setAccessible(true);Object rect=rectF.get(tile);int x=intField(rect,"x"),y=intField(rect,"y"),w=intField(rect,"w"),h=intField(rect,"h");return x+","+y+","+w+","+h;}}return null;}
    private static boolean validGridSpec(String spec){if(spec==null||spec.isBlank())return false;try{String[] p=spec.split(",");if(p.length!=4)return false;int x=Integer.parseInt(p[0].trim()),y=Integer.parseInt(p[1].trim()),w=Integer.parseInt(p[2].trim()),h=Integer.parseInt(p[3].trim());return x>=0&&y>=0&&w>=5&&h>=4&&x+w<=24&&y+h<=18;}catch(Exception ex){return false;}}
    private static Component findDashboardGrid(Container root){for(Component c:root.getComponents()){if(c.getClass().getName().contains("DashboardGridPanel"))return c;if(c instanceof Container child){Component found=findDashboardGrid(child);if(found!=null)return found;}}return null;}
    private static String findFreeSpec(Component grid,int w,int h){boolean[][] used=new boolean[18][24];try{Field tilesF=grid.getClass().getDeclaredField("tiles");tilesF.setAccessible(true);Object raw=tilesF.get(grid);if(raw instanceof Iterable<?> tiles){for(Object tile:tiles){Field rectF=tile.getClass().getDeclaredField("gridRect");rectF.setAccessible(true);Object rect=rectF.get(tile);int x=intField(rect,"x"),y=intField(rect,"y"),rw=intField(rect,"w"),rh=intField(rect,"h");for(int yy=Math.max(0,y);yy<Math.min(18,y+rh);yy++)for(int xx=Math.max(0,x);xx<Math.min(24,x+rw);xx++)used[yy][xx]=true;}}}catch(Exception ignored){}int[][] preferred={{0,7},{0,8},{18,7},{0,10},{6,10},{12,10},{18,10}};for(int[] p:preferred)if(fits(used,p[0],p[1],w,h))return p[0]+","+p[1]+","+w+","+h;for(int y=0;y<=18-h;y++)for(int x=0;x<=24-w;x++)if(fits(used,x,y,w,h))return x+","+y+","+w+","+h;return "0,14,6,4";}
    private static boolean fits(boolean[][] used,int x,int y,int w,int h){if(x<0||y<0||x+w>24||y+h>18)return false;for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)if(used[yy][xx])return false;return true;}
    private static int intField(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
    private static Component findByName(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){Component found=findByName(child,name);if(found!=null)return found;}}return null;}
}
