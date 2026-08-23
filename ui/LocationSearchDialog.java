package com.wtm.ui;

import com.wtm.location.OpenMeteoGeocodingService;
import com.wtm.model.LocationSearchResult;
import com.wtm.net.HttpService;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable location finder for primary, pinned, and route destinations.
 *
 * Searches run in a SwingWorker so network latency never freezes Settings.
 */
public final class LocationSearchDialog extends JDialog {
    private final OpenMeteoGeocodingService geocoder=
            new OpenMeteoGeocodingService(new HttpService());
    private final Consumer<LocationSearchResult> onUse;

    private final JTextField query=new JTextField();
    private final JButton searchButton=new JButton("Find Location");
    private final JButton useButton=new JButton("Use This Location");
    private final JLabel status=new JLabel("Enter a city or place name.");

    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Location","State/Region","Country","Latitude","Longitude","Timezone","Population"},0){
        @Override public boolean isCellEditable(int row,int col){ return false; }
        @Override public Class<?> getColumnClass(int col){
            if(col==3||col==4) return Double.class;
            if(col==6) return Long.class;
            return String.class;
        }
    };

    private final JTable table=new JTable(model);
    private List<LocationSearchResult> results=List.of();

    public LocationSearchDialog(
            Window owner,
            String initialQuery,
            Consumer<LocationSearchResult> onUse
    ){
        super(owner,"Find Location",ModalityType.APPLICATION_MODAL);
        this.onUse=onUse;

        setSize(980,570);
        setMinimumSize(new Dimension(760,460));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12,12));

        JPanel top=new JPanel(new BorderLayout(8,8));
        top.setBorder(BorderFactory.createEmptyBorder(14,14,0,14));

        JPanel row=new JPanel(new BorderLayout(8,0));
        query.setText(initialQuery==null?"":initialQuery);
        row.add(new JLabel("City / place:"),BorderLayout.WEST);
        row.add(query,BorderLayout.CENTER);
        row.add(searchButton,BorderLayout.EAST);

        JLabel help=new JLabel(
                "<html>Search by city or named place, for example <b>Hoover, Alabama</b>, "
              + "<b>Trussville</b>, or <b>Tuscaloosa, AL</b>. Choose the correct result and "
              + "the application will fill latitude/longitude automatically.</html>");

        top.add(row,BorderLayout.NORTH);
        top.add(help,BorderLayout.SOUTH);
        add(top,BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(27);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);

        JScrollPane scroll=new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(0,14,0,14));
        add(scroll,BorderLayout.CENTER);

        JPanel bottom=new JPanel(new BorderLayout(10,0));
        bottom.setBorder(BorderFactory.createEmptyBorder(0,14,14,14));
        bottom.add(status,BorderLayout.CENTER);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel=new JButton("Cancel");
        cancel.addActionListener(e->dispose());
        useButton.setEnabled(false);
        useButton.addActionListener(e->useSelected());
        buttons.add(cancel);
        buttons.add(useButton);
        bottom.add(buttons,BorderLayout.EAST);
        add(bottom,BorderLayout.SOUTH);

        searchButton.addActionListener(e->search());
        query.addActionListener(e->search());

        ThemeStyler.apply(this,ThemedDialogs.resolveTheme(owner));

        if(!query.getText().trim().isBlank())
            SwingUtilities.invokeLater(this::search);
    }

    private void search(){
        String q=query.getText().trim();
        if(q.isBlank()){
            status.setText("Enter a city or place name first.");
            return;
        }

        searchButton.setEnabled(false);
        useButton.setEnabled(false);
        model.setRowCount(0);
        status.setText("Searching for “"+q+"”…");

        new SwingWorker<List<LocationSearchResult>,Void>(){
            @Override protected List<LocationSearchResult> doInBackground() throws Exception {
                return geocoder.search(q);
            }

            @Override protected void done(){
                searchButton.setEnabled(true);
                try{
                    results=get();
                    model.setRowCount(0);
                    for(LocationSearchResult r:results){
                        model.addRow(new Object[]{
                                r.name(),r.admin1(),r.country(),
                                r.latitude(),r.longitude(),r.timezone(),r.population()
                        });
                    }

                    if(results.isEmpty()){
                        status.setText("No matching places found. Try a nearby city name or enter coordinates manually.");
                    }else{
                        status.setText(results.size()+" matching location"+(results.size()==1?"":"s")+" found.");
                        table.setRowSelectionInterval(0,0);
                    }
                }catch(Exception ex){
                    Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    status.setText("Location search failed: "+cause.getMessage());
                }
            }
        }.execute();
    }

    private void selectionChanged(ListSelectionEvent e){
        if(e.getValueIsAdjusting()) return;
        useButton.setEnabled(table.getSelectedRow()>=0);
    }

    private void useSelected(){
        int viewRow=table.getSelectedRow();
        if(viewRow<0) return;
        int row=table.convertRowIndexToModel(viewRow);
        if(row<0||row>=results.size()) return;
        onUse.accept(results.get(row));
        dispose();
    }
}
