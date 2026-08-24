package com.wtm.ui;
import com.wtm.map.TileMapPanel;import javax.swing.*;import javax.swing.plaf.LayerUI;import java.awt.*;import java.util.*;
public final class MainMapNetworkOverlayInjector{
 private MainMapNetworkOverlayInjector(){}public static void start(){javax.swing.Timer t=new javax.swing.Timer(1000,e->scan());t.setInitialDelay(3000);t.start();}
 private static void scan(){for(Window w:Window.getWindows())if(w instanceof JFrame&&w.isDisplayable())walk(w);}
 private static void walk(Container c){for(Component x:c.getComponents()){if(x instanceof TileMapPanel m&&!truckAncestor(m)&&!(m.getParent() instanceof JLayer))wrap(m);if(x instanceof Container ct)walk(ct);}}
 private static boolean truckAncestor(Component c){for(Container p=c.getParent();p!=null;p=p.getParent())if(p.getClass().getName().contains("TruckTracking"))return true;return false;}
 private static void wrap(TileMapPanel m){Container p=m.getParent();if(p==null)return;int ix=-1;for(int i=0;i<p.getComponentCount();i++)if(p.getComponent(i)==m){ix=i;break;}LayoutManager lm=p.getLayout();Object constraints=null;if(lm instanceof BorderLayout b)constraints=b.getConstraints(m);else if(lm instanceof GridBagLayout g)constraints=g.getConstraints(m);p.remove(m);JLayer<JComponent> layer=new JLayer<>(m,new LayerUI<>(){public void paint(Graphics g,JComponent c){super.paint(g,c);Graphics2D g2=(Graphics2D)g.create();NetworkOverlayPainter.paint(g2,m,c.getWidth(),c.getHeight(),false,Set.of());g2.dispose();}});if(lm instanceof BorderLayout)p.add(layer,constraints);else if(lm instanceof GridBagLayout)p.add(layer,constraints);else p.add(layer,Math.max(0,ix));p.revalidate();p.repaint();}
}
