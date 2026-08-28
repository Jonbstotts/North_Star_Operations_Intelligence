package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared NorthStar theme/layout core. */
public final class ThemeCoreV216 {
    private static final AtomicBoolean STARTED=new AtomicBoolean(false);
    private static final String EMPLOYEE_PANEL_CLASS="com.wtm.ui.EmployeeOperationsPanel";

    private ThemeCoreV216(){}

    public static void start(){
        if(!STARTED.compareAndSet(false,true))return;
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent we
                    &&we.getID()==WindowEvent.WINDOW_OPENED
                    &&we.getWindow() instanceof JDialog dialog){
                polishDialog(dialog);
            }
        },AWTEvent.WINDOW_EVENT_MASK);
    }

    /** Called synchronously for the root passed to ThemeStyler. */
    public static void applyImmediate(Component component){
        if(component instanceof JDialog dialog)polishDialog(dialog);
        normalizeLocationsHeader(component);
        normalizeEmployeeOperations(component);
    }

    /**
     * The workspace shell already supplies the Locations & Routes page title.
     * Hide only the legacy in-panel title that sits beside the explanatory
     * sentence; the explanatory copy remains visible.
     */
    private static void normalizeLocationsHeader(Component root){
        Container section=findContainerContainingText(root,
                "Manage the primary facility, monitoring locations and commute routes in one place.");
        if(section==null)return;
        JLabel duplicate=findLabel(section,"Locations & Routes");
        if(duplicate!=null){
            duplicate.setVisible(false);
            duplicate.setPreferredSize(new Dimension(0,0));
            duplicate.setMinimumSize(new Dimension(0,0));
            duplicate.setMaximumSize(new Dimension(0,0));
        }
    }

    /** Employee page compact directory treatment owned by the shared UI core. */
    private static void normalizeEmployeeOperations(Component root){
        Component found=findByClass(root,EMPLOYEE_PANEL_CLASS);
        if(!(found instanceof Container employeePanel))return;
        if(employeePanel instanceof JComponent jc
                &&Boolean.TRUE.equals(jc.getClientProperty("northstar.employee.compactDirectory")))return;

        // The workspace shell already owns the large page title. Keep the
        // management-only explanatory sentence immediately below it.
        for(JLabel label:labels(employeePanel)){
            if("Employee Operations".equals(label.getText())
                    &&label.getFont()!=null
                    &&label.getFont().getSize()>=18){
                label.setVisible(false);
                label.setPreferredSize(new Dimension(0,0));
                label.setMinimumSize(new Dimension(0,0));
                label.setMaximumSize(new Dimension(0,0));
            }
        }

        JTable directory=findEmployeeDirectoryTable(employeePanel);
        if(directory!=null){
            hideTableColumn(directory,"Employee #");
            hideTableColumn(directory,"Department");
            hideTableColumn(directory,"Shift");
            // Name gets the available width; Active stays compact/readable.
            try{
                TableColumn name=directory.getColumn("Name");
                name.setPreferredWidth(210);
                TableColumn active=directory.getColumn("Active");
                active.setMinWidth(70);
                active.setPreferredWidth(80);
                active.setMaxWidth(95);
            }catch(IllegalArgumentException ignored){}
        }

        // Keep the split-pane spacing but visually remove the legacy grey
        // divider bar beside the employee directory.
        JSplitPane split=findFirst(employeePanel,JSplitPane.class);
        if(split!=null){
            split.setDividerSize(14);
            split.setContinuousLayout(true);
            split.setOpaque(false);
            split.setBackground(Theme.bg());
            if(split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui
                    &&ui.getDivider()!=null){
                ui.getDivider().setBackground(Theme.bg());
                ui.getDivider().setBorder(BorderFactory.createEmptyBorder());
            }
        }

        if(employeePanel instanceof JComponent jc)
            jc.putClientProperty("northstar.employee.compactDirectory",Boolean.TRUE);
        employeePanel.invalidate();
    }

    private static JTable findEmployeeDirectoryTable(Container root){
        for(JTable table:findAll(root,JTable.class)){
            boolean name=false,active=false,employee=false;
            for(int i=0;i<table.getModel().getColumnCount();i++){
                String n=String.valueOf(table.getModel().getColumnName(i));
                if("Name".equals(n))name=true;
                if("Active".equals(n))active=true;
                if("Employee #".equals(n))employee=true;
            }
            if(name&&active&&employee)return table;
        }
        return null;
    }

    private static void hideTableColumn(JTable table,String name){
        try{ table.removeColumn(table.getColumn(name)); }
        catch(IllegalArgumentException ignored){}
    }

    private static Container findContainerContainingText(Component root,String text){
        if(root instanceof Container c){
            if(containsText(c,text)){
                // Prefer the smallest matching container.
                for(Component child:c.getComponents()){
                    Container nested=findContainerContainingText(child,text);
                    if(nested!=null)return nested;
                }
                return c;
            }
        }
        return null;
    }

    private static JLabel findLabel(Component root,String text){
        if(root instanceof JLabel label&&text.equals(label.getText()))return label;
        if(root instanceof Container c)
            for(Component child:c.getComponents()){
                JLabel found=findLabel(child,text);
                if(found!=null)return found;
            }
        return null;
    }

    private static List<JLabel> labels(Container root){ return findAll(root,JLabel.class); }

    private static <T extends Component> T findFirst(Container root,Class<T> type){
        List<T> all=findAll(root,type);
        return all.isEmpty()?null:all.get(0);
    }

    private static <T extends Component> List<T> findAll(Component root,Class<T> type){
        List<T> result=new ArrayList<>();
        collect(root,type,result);
        return result;
    }

    private static <T extends Component> void collect(Component root,Class<T> type,List<T> out){
        if(root==null)return;
        if(type.isInstance(root))out.add(type.cast(root));
        if(root instanceof Container c)
            for(Component child:c.getComponents())collect(child,type,out);
    }

    private static Component findByClass(Component root,String className){
        if(root==null)return null;
        if(className.equals(root.getClass().getName()))return root;
        if(root instanceof Container c)
            for(Component child:c.getComponents()){
                Component found=findByClass(child,className);
                if(found!=null)return found;
            }
        return null;
    }

    private static boolean containsText(Component root,String text){
        if(root instanceof JLabel label){
            String value=label.getText();
            if(value!=null){
                String plain=value.replace("<html>","").replace("</html>","");
                if(text.equals(plain)||plain.contains(text))return true;
            }
        }
        if(root instanceof Container c)
            for(Component child:c.getComponents())if(containsText(child,text))return true;
        return false;
    }

    private static void polishDialog(JDialog dialog){
        if(dialog==null||!dialog.isDisplayable())return;
        AppTheme theme=Theme.active();
        JRootPane root=dialog.getRootPane();
        root.putClientProperty("apple.awt.windowAppearance",
                theme.dark()?"NSAppearanceNameDarkAqua":"NSAppearanceNameAqua");
        root.setOpaque(true);
        root.setBackground(theme.bg());
        root.setBorder(BorderFactory.createEmptyBorder());
        if(root.getLayeredPane()!=null){
            root.getLayeredPane().setOpaque(true);
            root.getLayeredPane().setBackground(theme.bg());
        }
        if(dialog.getContentPane() instanceof JComponent content){
            content.setOpaque(true);
            content.setBackground(theme.bg());
            content.setBorder(BorderFactory.createEmptyBorder());
        }
        dialog.setBackground(theme.bg());
        themeDialogTree(dialog,theme);
        dialog.revalidate();
        dialog.repaint();
    }

    private static void themeDialogTree(Component component,AppTheme theme){
        if(component instanceof JOptionPane pane){
            pane.setOpaque(true);
            pane.setBackground(theme.bg());
            pane.setForeground(theme.text());
            pane.setBorder(new EmptyBorder(14,16,12,16));
        }else if(component instanceof JComboBox<?> box){
            box.setOpaque(true);
            box.setBackground(theme.panel2());
            box.setForeground(theme.text());
            if(!(box.getUI() instanceof ThemedComboBoxUI))
                box.setUI(new ThemedComboBoxUI(theme));
        }else if(component instanceof JPanel panel){
            if(panel.getClass()==JPanel.class){
                panel.setOpaque(true);
                panel.setBackground(theme.bg());
            }
        }else if(component instanceof JViewport viewport){
            viewport.setOpaque(true);
            viewport.setBackground(theme.bg());
        }
        if(component instanceof Container container)
            for(Component child:container.getComponents())themeDialogTree(child,theme);
    }
}
