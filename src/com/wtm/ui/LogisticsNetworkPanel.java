package com.wtm.ui;

import com.wtm.firstparty.NetworkLocation;
import com.wtm.firstparty.NetworkLocationStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Source-owned Logistics Network management surface used by Traffic & Routes.
 * Replaces the retired runtime injector with an explicit workspace component.
 */
public final class LogisticsNetworkPanel extends JPanel {
    private final NetworkLocationStore store=NetworkLocationStore.get();
    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Name","Category","External ID","Address","Latitude","Longitude","Geofence (m)","Active","ID"},0){
        @Override public Class<?> getColumnClass(int column){return column==7?Boolean.class:Object.class;}
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private final JTable table=new JTable(model);

    public LogisticsNetworkPanel(){
        super(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTextArea help=new JTextArea(
                "The Logistics Network is the reusable facility/dealer location directory used by first-party "
              + "tracking and route intelligence. Import DVIEW Address data or maintain locations manually. "
              + "Existing coordinates and geofences are preserved when matching imported entities are refreshed.");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);
        add(help,BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        hideIdColumn();
        add(new JScrollPane(table),BorderLayout.CENTER);

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("+ Add location");
        add.addActionListener(e->editLocation(null));
        JButton edit=new JButton("Edit selected");
        edit.addActionListener(e->{ NetworkLocation n=selected(); if(n!=null)editLocation(n); });
        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->removeSelected());
        JButton importCsv=new JButton("Import DVIEW / CSV");
        importCsv.addActionListener(e->importCsv());
        JButton template=new JButton("Export Import Template");
        template.addActionListener(e->exportTemplate());
        JButton refresh=new JButton("Refresh");
        refresh.addActionListener(e->reload());
        controls.add(add);controls.add(edit);controls.add(remove);
        controls.add(importCsv);controls.add(template);controls.add(refresh);
        add(controls,BorderLayout.SOUTH);

        reload();
        ThemeStyler.apply(this,Theme.active());
    }

    private void hideIdColumn(){
        try{ table.removeColumn(table.getColumn("ID")); }
        catch(IllegalArgumentException ignored){}
    }

    private void reload(){
        model.setRowCount(0);
        List<NetworkLocation> rows=store.all();
        for(NetworkLocation n:rows){
            model.addRow(new Object[]{
                    n.name(),n.category(),n.externalId(),n.address(),
                    finite(n.latitude()),finite(n.longitude()),n.geofenceMeters(),n.active(),n.id()
            });
        }
    }

    private static Object finite(double value){return Double.isFinite(value)?value:"";}

    private NetworkLocation selected(){
        int view=table.getSelectedRow();
        if(view<0){
            ThemedDialogs.message(this,"Select a Logistics Network row first.","Logistics Network",ThemedDialogs.Kind.INFO);
            return null;
        }
        int row=table.convertRowIndexToModel(view);
        String id=String.valueOf(model.getValueAt(row,8));
        return store.find(id);
    }

    private void editLocation(NetworkLocation existing){
        JTextField name=new JTextField(existing==null?"":existing.name());
        JComboBox<String> category=new JComboBox<>(new String[]{
                "Logistics Center","Dealer","Supplier","Carrier Facility","Other / Temporary Destination"
        });
        if(existing!=null)category.setSelectedItem(existing.category());
        JTextField externalId=new JTextField(existing==null?"":existing.externalId());
        JTextField address=new JTextField(existing==null?"":existing.address());
        JTextField latitude=new JTextField(existing==null||!Double.isFinite(existing.latitude())?"":String.valueOf(existing.latitude()));
        JTextField longitude=new JTextField(existing==null||!Double.isFinite(existing.longitude())?"":String.valueOf(existing.longitude()));
        JSpinner geofence=new JSpinner(new SpinnerNumberModel(existing==null?500:existing.geofenceMeters(),25,10000,25));
        JCheckBox active=new JCheckBox("Active",existing==null||existing.active());
        JTextArea notes=new JTextArea(existing==null?"":existing.notes(),4,34);
        notes.setLineWrap(true);notes.setWrapStyleWord(true);

        JPanel form=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(4,4,4,4);c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;
        addRow(form,c,0,"Name",name);addRow(form,c,1,"Category",category);
        addRow(form,c,2,"External ID",externalId);addRow(form,c,3,"Address",address);
        addRow(form,c,4,"Latitude",latitude);addRow(form,c,5,"Longitude",longitude);
        addRow(form,c,6,"Geofence meters",geofence);
        c.gridx=1;c.gridy=7;form.add(active,c);
        c.gridx=0;c.gridy=8;c.weightx=0;form.add(new JLabel("Notes"),c);
        c.gridx=1;c.weightx=1;form.add(new JScrollPane(notes),c);
        ThemeStyler.apply(form,Theme.active());

        if(!ThemedDialogs.confirmForm(this,form,existing==null?"Add Logistics Network Location":"Edit Logistics Network Location","Save"))return;
        String trimmed=name.getText().trim();
        if(trimmed.isBlank()){
            ThemedDialogs.message(this,"Name is required.","Logistics Network",ThemedDialogs.Kind.WARNING);
            return;
        }
        try{
            double lat=parseCoordinate(latitude.getText());
            double lon=parseCoordinate(longitude.getText());
            String id=existing==null?UUID.randomUUID().toString():existing.id();
            store.upsert(new NetworkLocation(
                    id,trimmed,String.valueOf(category.getSelectedItem()),address.getText().trim(),externalId.getText().trim(),
                    lat,lon,((Number)geofence.getValue()).intValue(),notes.getText().trim(),active.isSelected()));
            reload();
        }catch(IllegalArgumentException ex){
            ThemedDialogs.message(this,ex.getMessage(),"Invalid Logistics Network Location",ThemedDialogs.Kind.WARNING);
        }
    }

    private static double parseCoordinate(String text){
        if(text==null||text.isBlank())return Double.NaN;
        try{return Double.parseDouble(text.trim());}
        catch(NumberFormatException ex){throw new IllegalArgumentException("Latitude and longitude must be numeric or blank.");}
    }

    private static void addRow(JPanel panel,GridBagConstraints c,int row,String label,Component field){
        c.gridx=0;c.gridy=row;c.weightx=0;panel.add(new JLabel(label),c);
        c.gridx=1;c.weightx=1;panel.add(field,c);
    }

    private void removeSelected(){
        NetworkLocation n=selected();
        if(n==null)return;
        if(!ThemedDialogs.confirm(
                this,
                "Remove “"+n.name()+"” from the Logistics Network?",
                "Remove Location",
                "Remove",
                ThemedDialogs.Kind.WARNING
        ))return;
        store.delete(n.id());reload();
    }

    private void importCsv(){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Import Logistics Network CSV");
        ThemeStyler.apply(chooser,Theme.active());
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{
            int changed=store.importCsv(chooser.getSelectedFile().toPath());
            reload();
            ThemedDialogs.message(this,"Imported / refreshed "+changed+" Logistics Network location(s).","Logistics Network Import",ThemedDialogs.Kind.INFO);
        }catch(Exception ex){
            ThemedDialogs.message(this,"Unable to import Logistics Network CSV: "+ex.getMessage(),"Import Failed",ThemedDialogs.Kind.ERROR);
        }
    }

    private void exportTemplate(){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Export Logistics Network Import Template");
        chooser.setSelectedFile(new java.io.File("NorthStar-Logistics-Network-Import-Template.csv"));
        ThemeStyler.apply(chooser,Theme.active());
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{
            Path target=chooser.getSelectedFile().toPath();
            store.exportTemplate(target);
            ThemedDialogs.message(this,"Template exported to "+target,"Logistics Network",ThemedDialogs.Kind.INFO);
        }catch(Exception ex){
            ThemedDialogs.message(this,"Unable to export template: "+ex.getMessage(),"Export Failed",ThemedDialogs.Kind.ERROR);
        }
    }
}
