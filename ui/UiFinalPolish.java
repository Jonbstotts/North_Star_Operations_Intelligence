package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-structural final UI polish.
 *
 * v2.1.8 deliberately stops rewriting tab panes, split panes, or module trees.
 * Structural post-load mutation was the source of controls/tabs appearing and
 * disappearing. Geometry is now owned by the module and ThemeCore before paint.
 */
public final class UiFinalPolish {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private UiFinalPolish(){}

    public static void start(){
        if(STARTED.compareAndSet(false,true))ThemeCoreV216.start();
    }

    static void prepareBeforePaint(Component component){
        if(component==null)return;
        polishAppearanceScroll(component);
        // Intentionally no employee/truck/tab structural mutation here.
    }

    private static void polishAppearanceScroll(Component component){
        if(component instanceof JScrollPane scroll){
            Component view=scroll.getViewport()==null?null:scroll.getViewport().getView();
            if(view instanceof JComponent jc
                    &&(containsText(view,"Appearance")||containsText(view,"Startup Experience"))
                    &&!Boolean.TRUE.equals(jc.getClientProperty("northstar.appearance.bottomSafe.v218"))){
                jc.putClientProperty("northstar.appearance.bottomSafe.v218",Boolean.TRUE);
                Dimension pref=jc.getPreferredSize();
                int w=pref==null?1:Math.max(1,pref.width);
                int h=pref==null?1:Math.max(1,pref.height);
                jc.setPreferredSize(new Dimension(w,h+1050));
                Border existing=jc.getBorder();
                Border pad=new EmptyBorder(0,0,700,0);
                jc.setBorder(existing==null?pad:BorderFactory.createCompoundBorder(existing,pad));
                scroll.getVerticalScrollBar().setUnitIncrement(24);
                scroll.getVerticalScrollBar().setBlockIncrement(220);
            }
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())polishAppearanceScroll(child);
    }

    private static boolean containsText(Component component,String needle){
        if(component instanceof JLabel l&&l.getText()!=null
                &&l.getText().replaceAll("<[^>]+>"," ").contains(needle))return true;
        if(component instanceof AbstractButton b&&b.getText()!=null&&b.getText().contains(needle))return true;
        if(component instanceof Container c)
            for(Component child:c.getComponents())if(containsText(child,needle))return true;
        return false;
    }
}
