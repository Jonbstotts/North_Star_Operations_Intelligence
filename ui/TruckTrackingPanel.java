package com.wtm.ui;

import com.wtm.config.*;
import com.wtm.security.AuditService;
import com.wtm.security.SessionManager;
import com.wtm.model.*;
import com.wtm.truck.*;
import com.wtm.traffic.TomTomService;
import com.wtm.net.HttpService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Unified inbound-transportation workspace.
 *
 * Current/History use the same durable store. Provider settings control map
 * visibility independently from tracking, so hiding a carrier never stops its
 * records from updating.
 */
public final class TruckTrackingPanel extends JPanel {
    private static final DateTimeFormatter DISPLAY=
            DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");

    private final AppConfig config;
    private final TruckTrackingStore store=TruckTrackingStore.get();
    private final ShipmentEventStore eventStore=ShipmentEventStore.get();
    private final Trak4Store trak4Store=Trak4Store.get();

    private final DefaultTableModel currentModel=tableModel();
    private final DefaultTableModel historyModel=tableModel();
    private final JTable currentTable=new JTable(currentModel);
    private final JTable historyTable=new JTable(historyModel);

    private final JCheckBox showFedex=new JCheckBox("Show FedEx trucks on map");
    private final JCheckBox showStarhub=new JCheckBox(
            "Show Mercedes StarHub / Penske trucks on map");
    private final JSpinner deliveredMapDays=new JSpinner(
            new SpinnerNumberModel(2,0,30,1));
    private final JSpinner currentDays=new JSpinner(
            new SpinnerNumberModel(7,0,365,1));
    private final JSpinner archiveDays=new JSpinner(
            new SpinnerNumberModel(7,0,3650,1));
    private final JSpinner retentionDays=new JSpinner(
            new SpinnerNumberModel(730,0,36500,30));
    private final JSpinner refreshMinutes=new JSpinner(
            new SpinnerNumberModel(15,1,1440,1));

    private final JComboBox<String> fedexMode=new JComboBox<>(
            new String[]{"MANUAL","FEDEX_REST"});
    private final JTextField fedexClientId=new JTextField();
    private final JPasswordField fedexSecret=new JPasswordField();

    private final JComboBox<String> penskeMode=new JComboBox<>(
            new String[]{"MANUAL_CSV","ENTERPRISE_FEED"});
    private final JTextField penskeEndpoint=new JTextField();
    private final JPasswordField penskeToken=new JPasswordField();

    private final JComboBox<String> fedexEnvironment=new JComboBox<>(
            new String[]{"PRODUCTION","TEST"});
    private final JCheckBox showTrak4=new JCheckBox("Show Trak-4 trackers on map");
    private final JComboBox<String> trak4Mode=new JComboBox<>(
            new String[]{"MANUAL","TRAK4_REST"});
    private final JTextField trak4ReportsUrl=new JTextField();
    private final JTextField trak4AuthHeader=new JTextField();
    private final JPasswordField trak4ApiKey=new JPasswordField();
    private final JSpinner trak4RefreshMinutes=new JSpinner(
            new SpinnerNumberModel(5,1,1440,1));

    private final JLabel summary=new JLabel();

    public TruckTrackingPanel(AppConfig config){
        this.config=config;
        setLayout(new BorderLayout(0,12));
        setBackground(Theme.bg());
        setBorder(new EmptyBorder(4,4,4,4));

        JTabbedPane tabs=new JTabbedPane();
        tabs.addTab("Current",buildCurrent());
        tabs.addTab("Carrier Events",buildCarrierEvents());
        tabs.addTab("Trak-4 Trackers",buildTrak4Trackers());
        tabs.addTab("Assignments",buildTrak4Assignments());
        tabs.addTab("History",buildHistory());
        tabs.addTab("Import / Export",buildImportExport());
        tabs.addTab("Playback / Test",buildPlayback());
        tabs.addTab("Settings",buildSettings());
        add(tabs,BorderLayout.CENTER);

        loadSettings();
        store.archiveClosedOlderThan(config.truckArchiveAfterDays);
        purgeByRetention();
        refreshTables();
        ThemeStyler.apply(this,Theme.active());
    }

    private JPanel buildCurrent(){
        JPanel panel=page();
        JPanel top=new JPanel(new BorderLayout(8,0));
        top.setOpaque(false);
        summary.setForeground(Theme.muted());
        top.add(summary,BorderLayout.WEST);

        JPanel actions=actions();
        JButton add=new JButton("+ Add Shipment");
        JButton edit=new JButton("Edit Selected");
        JButton delivered=new JButton("Mark Delivered");
        JButton refresh=new JButton("Refresh");
        add.addActionListener(e->editShipment(null));
        edit.addActionListener(e->editSelected(currentTable));
        delivered.addActionListener(e->markDelivered(currentTable));
        refresh.addActionListener(e->refreshTables());
        actions.add(add);actions.add(edit);actions.add(delivered);actions.add(refresh);
        top.add(actions,BorderLayout.EAST);

        prepare(currentTable);
        panel.add(top,BorderLayout.NORTH);
        panel.add(new JScrollPane(currentTable),BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCarrierEvents(){
        JPanel panel=page();
        JComboBox<ShipmentChoice> shipment=new JComboBox<>();
        for(TruckShipment s:store.all())shipment.addItem(new ShipmentChoice(s));

        DefaultTableModel model=new DefaultTableModel(new Object[]{
                "Time","Tracking","Event","Location","Mode","Location Source"
        },0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setRowHeight(28);

        Runnable refresh=()->{
            model.setRowCount(0);
            ShipmentChoice choice=(ShipmentChoice)shipment.getSelectedItem();
            if(choice==null)return;
            for(ShipmentTrackingEvent e:eventStore.forShipment(choice.shipment.id))
                model.addRow(new Object[]{
                        format(e.eventTime),e.trackingNumber,
                        e.eventDescription.isBlank()?e.eventCode:e.eventDescription,
                        e.placeLabel(),e.movementMode,e.locationConfidence});
        };

        JButton fedex=new JButton("Refresh FedEx Events");
        fedex.putClientProperty("primaryAction",Boolean.TRUE);
        fedex.addActionListener(e->{
            ShipmentChoice choice=(ShipmentChoice)shipment.getSelectedItem();
            if(choice!=null)refreshFedExShipment(choice.shipment,fedex,refresh);
        });

        JButton showPath=new JButton("Show Checkpoints on Map");
        showPath.addActionListener(e->{
            ShipmentChoice choice=(ShipmentChoice)shipment.getSelectedItem();
            if(choice==null)return;
            List<Location> points=eventStore.forShipment(choice.shipment.id)
                    .stream().filter(ShipmentTrackingEvent::hasCoordinates)
                    .map(ev->new Location(ev.placeLabel(),ev.latitude,ev.longitude))
                    .toList();
            if(points.size()<2){
                ThemedDialogs.message(this,
                        "At least two located carrier events are required.",
                        "Carrier Events",ThemedDialogs.Kind.INFO);return;
            }
            TruckPlaybackService.get().setCarrierEvents(
                    choice.shipment,points,0,
                    "Carrier-reported scan checkpoints");
        });

        JPanel top=actions();
        top.add(new JLabel("Shipment"));top.add(shipment);
        top.add(fedex);top.add(showPath);
        shipment.addActionListener(e->refresh.run());

        panel.add(top,BorderLayout.NORTH);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);
        refresh.run();return panel;
    }

    private void refreshFedExShipment(
            TruckShipment shipment,JButton button,Runnable after){
        List<String> numbers=Arrays.stream(
                safeText(shipment.trackingNumber).split("[;|,\\s]+"))
                .map(String::trim).filter(s->!s.isBlank()).distinct().toList();
        if(numbers.isEmpty()){
            ThemedDialogs.message(this,
                    "This shipment does not contain a FedEx tracking number.",
                    "FedEx Tracking",ThemedDialogs.Kind.INFO);return;
        }

        String old=button.getText();button.setEnabled(false);
        button.setText("Refreshing…");

        SwingWorker<List<FedExTrackingService.Result>,Void> worker=
                new SwingWorker<>(){
            @Override protected List<FedExTrackingService.Result> doInBackground()
                    throws Exception{
                return new FedExTrackingService(new HttpService()).track(
                        numbers,config.fedexClientId,config.fedexClientSecret,
                        "TEST".equalsIgnoreCase(config.fedexEnvironment));
            }
            @Override protected void done(){
                button.setEnabled(true);button.setText(old);
                try{
                    List<FedExTrackingService.Result> results=get();
                    TruckShipment updated=shipment.copy();
                    List<ShipmentTrackingEvent> all=new ArrayList<>();
                    for(FedExTrackingService.Result result:results){
                        eventStore.replaceProviderEvents(
                                updated.id,result.trackingNumber(),"FEDEX",
                                result.events());
                        all.addAll(result.events());
                        if(result.estimatedDelivery()!=null)
                            updated.estimatedArrival=result.estimatedDelivery();
                        if(result.deliveredAt()!=null){
                            updated.deliveredAt=result.deliveredAt();
                            updated.status=TruckStatus.DELIVERED;
                        }else if(!safeText(result.status()).isBlank()){
                            updated.providerStatus=result.status();
                            updated.status=TruckStatus.parse(result.status());
                        }
                    }

                    all.sort(Comparator.comparing(
                            (ShipmentTrackingEvent ev)->ev.eventTime,
                            Comparator.nullsLast(Comparator.naturalOrder())));
                    ShipmentTrackingEvent latest=all.stream()
                            .filter(ShipmentTrackingEvent::hasCoordinates)
                            .reduce((a,b)->b).orElse(null);
                    if(latest!=null){
                        updated.latitude=latest.latitude;
                        updated.longitude=latest.longitude;
                        updated.currentLocation=latest.placeLabel();
                        updated.lastUpdated=latest.eventTime==null
                                ?LocalDateTime.now():latest.eventTime;
                    }
                    store.upsert(updated);refreshTables();after.run();
                    ThemedDialogs.message(TruckTrackingPanel.this,
                            results.size()+" FedEx result(s) refreshed.",
                            "FedEx Tracking",ThemedDialogs.Kind.INFO);
                }catch(Exception ex){
                    ThemedDialogs.message(TruckTrackingPanel.this,
                            "FedEx refresh failed.\n\n"+rootMessage(ex),
                            "FedEx Tracking",ThemedDialogs.Kind.ERROR);
                }
            }
        };
        worker.execute();
    }

    private JPanel buildTrak4Trackers(){
        JPanel panel=page();
        DefaultTableModel model=new DefaultTableModel(new Object[]{
                "Device ID","Label","Physical Trailer","Latitude","Longitude",
                "Last Report","Battery","Source","Active"
        },0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setRowHeight(28);
        Runnable refresh=()->{
            model.setRowCount(0);
            for(Trak4Tracker t:trak4Store.trackers())
                model.addRow(new Object[]{
                        t.deviceId,t.label,t.physicalTrailer,t.latitude,t.longitude,
                        format(t.lastReport),t.batteryPercent==null
                                ?"":t.batteryPercent+"%",t.source,
                        t.active?"Yes":"No"});
        };

        JButton add=new JButton("+ Add Tracker");
        add.addActionListener(e->{
            JTextField device=new JTextField();
            JTextField label=new JTextField();
            JTextField physical=new JTextField();
            JPanel form=new JPanel(new GridLayout(0,2,8,8));
            form.add(new JLabel("Trak-4 Device ID"));form.add(device);
            form.add(new JLabel("Label"));form.add(label);
            form.add(new JLabel("Physical Trailer"));form.add(physical);
            ThemeStyler.apply(form,Theme.active());
            int result=JOptionPane.showConfirmDialog(
                    this,form,"Add Trak-4 Tracker",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(result!=JOptionPane.OK_OPTION)return;
            Trak4Tracker t=new Trak4Tracker();
            t.deviceId=device.getText().trim();t.label=label.getText().trim();
            t.physicalTrailer=physical.getText().trim();
            trak4Store.upsertTracker(t);refresh.run();
        });

        JButton simulate=new JButton("Simulate GPS at Primary Site");
        simulate.addActionListener(e->{
            int row=table.getSelectedRow();if(row<0)return;
            String device=String.valueOf(table.getValueAt(row,0));
            Trak4Tracker t=trak4Store.trackers().stream()
                    .filter(x->x.deviceId.equals(device)).findFirst().orElse(null);
            if(t==null)return;
            t.latitude=config.primary.latitude();
            t.longitude=config.primary.longitude();
            t.lastReport=LocalDateTime.now();t.source="TEST";
            trak4Store.upsertTracker(t);
            trak4Store.applyAssignmentsToShipments();refresh.run();
        });

        JButton pull=new JButton("Refresh Trak-4 REST");
        pull.addActionListener(e->refreshTrak4(pull,refresh));
        JPanel actions=actions();actions.add(add);actions.add(simulate);actions.add(pull);
        panel.add(actions,BorderLayout.NORTH);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);
        refresh.run();return panel;
    }

    private JPanel buildTrak4Assignments(){
        JPanel panel=page();
        DefaultTableModel model=new DefaultTableModel(new Object[]{
                "Assigned","Device","Physical Trailer","Daily Trailer ID",
                "Outbound Shipment","Released","Active","Notes"
        },0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setRowHeight(28);
        Runnable refresh=()->{
            model.setRowCount(0);
            for(Trak4Assignment a:trak4Store.assignments())
                model.addRow(new Object[]{
                        format(a.assignedAt),a.deviceId,a.physicalTrailer,
                        a.dailyTrailerId,a.outboundShipmentId,
                        format(a.releasedAt),a.active?"Yes":"No",a.notes});
        };

        JButton assign=new JButton("+ Assign Tracker");
        assign.putClientProperty("primaryAction",Boolean.TRUE);
        assign.addActionListener(e->{
            List<Trak4Tracker> trackers=trak4Store.trackers();
            if(trackers.isEmpty()){
                ThemedDialogs.message(this,"Add a Trak-4 tracker first.",
                        "Trak-4 Assignment",ThemedDialogs.Kind.INFO);return;
            }
            JComboBox<Trak4Tracker> box=
                    new JComboBox<>(trackers.toArray(new Trak4Tracker[0]));
            box.setRenderer(new DefaultListCellRenderer(){
                @Override public Component getListCellRendererComponent(
                        JList<?> list,Object value,int index,
                        boolean selected,boolean focus){
                    super.getListCellRendererComponent(
                            list,value,index,selected,focus);
                    if(value instanceof Trak4Tracker t)
                        setText((t.label.isBlank()?t.deviceId:t.label)
                                +" • "+t.deviceId);
                    return this;
                }
            });
            JTextField physical=new JTextField();
            JTextField trailer=new JTextField();
            JTextField outbound=new JTextField();
            JTextField notes=new JTextField();
            JPanel form=new JPanel(new GridLayout(0,2,8,8));
            form.add(new JLabel("Tracker"));form.add(box);
            form.add(new JLabel("Physical Trailer"));form.add(physical);
            form.add(new JLabel("Today's DVIEW Trailer ID"));form.add(trailer);
            form.add(new JLabel("Outbound Shipment ID"));form.add(outbound);
            form.add(new JLabel("Notes"));form.add(notes);
            ThemeStyler.apply(form,Theme.active());
            int result=JOptionPane.showConfirmDialog(
                    this,form,"Assign Trak-4",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(result!=JOptionPane.OK_OPTION)return;
            Trak4Tracker t=(Trak4Tracker)box.getSelectedItem();
            Trak4Assignment a=new Trak4Assignment();
            a.trackerId=t.id;a.deviceId=t.deviceId;
            a.physicalTrailer=physical.getText().trim().isBlank()
                    ?t.physicalTrailer:physical.getText().trim();
            a.dailyTrailerId=trailer.getText().trim();
            a.outboundShipmentId=outbound.getText().trim();
            a.notes=notes.getText().trim();
            trak4Store.assign(a);trak4Store.applyAssignmentsToShipments();
            refresh.run();refreshTables();
        });

        JButton release=new JButton("Release Selected");
        release.addActionListener(e->{
            int row=table.getSelectedRow();if(row<0)return;
            String device=String.valueOf(table.getValueAt(row,1));
            Trak4Assignment active=trak4Store.activeAssignments().stream()
                    .filter(a->a.deviceId.equals(device)).findFirst().orElse(null);
            if(active!=null){trak4Store.release(active.id);refresh.run();}
        });
        JPanel actions=actions();actions.add(assign);actions.add(release);
        panel.add(actions,BorderLayout.NORTH);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);
        refresh.run();return panel;
    }

    private void refreshTrak4(JButton button,Runnable after){
        if(!"TRAK4_REST".equalsIgnoreCase(config.trak4Mode)){
            ThemedDialogs.message(this,
                    "Set Trak-4 mode to TRAK4_REST in Settings first.",
                    "Trak-4",ThemedDialogs.Kind.INFO);return;
        }
        String old=button.getText();button.setEnabled(false);
        button.setText("Refreshing…");
        SwingWorker<List<Trak4Tracker>,Void> worker=new SwingWorker<>(){
            @Override protected List<Trak4Tracker> doInBackground()
                    throws Exception{
                return new Trak4Service(new HttpService()).fetch(
                        config.trak4ReportsUrl,config.trak4AuthHeader,
                        config.trak4ApiKey);
            }
            @Override protected void done(){
                button.setEnabled(true);button.setText(old);
                try{
                    for(Trak4Tracker remote:get()){
                        Trak4Tracker local=trak4Store.trackers().stream()
                                .filter(t->t.deviceId.equalsIgnoreCase(
                                        remote.deviceId))
                                .findFirst().orElse(remote);
                        local.latitude=remote.latitude;local.longitude=remote.longitude;
                        local.lastReport=remote.lastReport;
                        local.batteryPercent=remote.batteryPercent;
                        local.source="TRAK4_REST";trak4Store.upsertTracker(local);
                    }
                    trak4Store.applyAssignmentsToShipments();
                    after.run();refreshTables();
                }catch(Exception ex){
                    ThemedDialogs.message(TruckTrackingPanel.this,
                            "Trak-4 refresh failed.\n\n"+rootMessage(ex),
                            "Trak-4",ThemedDialogs.Kind.ERROR);
                }
            }
        };
        worker.execute();
    }

    private static String safeText(String value){
        return value==null?"":value;
    }

    private JPanel buildHistory(){
        JPanel panel=page();
        JPanel top=actions();
        JButton export=new JButton("Export Visible CSV");
        JButton delete=new JButton("Delete Selected");
        JButton purge=new JButton("Delete Archived Older Than Retention");
        export.addActionListener(e->exportHistory());
        delete.addActionListener(e->deleteSelected(historyTable));
        purge.addActionListener(e->{
            int removed=purgeByRetention();
            ThemedDialogs.message(this,
                    removed+" archived record(s) removed.",
                    "Truck Tracking",
                    ThemedDialogs.Kind.INFO);
            refreshTables();
        });
        top.add(export);top.add(delete);top.add(purge);

        prepare(historyTable);
        panel.add(top,BorderLayout.NORTH);
        panel.add(new JScrollPane(historyTable),BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildImportExport(){
        JPanel panel=page();

        RoundedPanel card=new RoundedPanel(16);
        card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18,18,18,18));
        card.setBackground(Theme.panel());

        JLabel title=new JLabel("Shipment Data Exchange");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea help=new JTextArea(
                "Import North Star tracking CSVs or IBM/DVIEW "
              + "TrailerInfoFromTrackingNumber extracts. IBM line-level rows "
              + "are automatically consolidated into outbound shipment/load "
              + "records while preserving trailer, customer, Ship IDs, FedEx "
              + "tracking numbers and PRO numbers. Existing records update "
              + "without discarding live ETA/GPS data."
        );
        help.setEditable(false);
        help.setOpaque(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setForeground(Theme.muted());
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setMaximumSize(new Dimension(Integer.MAX_VALUE,80));

        JPanel buttons=actions();
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton importCsv=new JButton("Import Tracking CSV");
        JButton template=new JButton("Export Blank Template");
        JButton exportAll=new JButton("Export Full History");
        importCsv.addActionListener(e->importCsv());
        template.addActionListener(e->exportTemplate());
        exportAll.addActionListener(e->exportAll());
        buttons.add(importCsv);buttons.add(template);buttons.add(exportAll);

        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(help);
        card.add(Box.createVerticalStrut(14));
        card.add(buttons);

        panel.add(card,BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildPlayback(){
        JPanel panel=page();
        RoundedPanel card=new RoundedPanel(16);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(18,18,18,18));
        card.setBackground(Theme.panel());

        GridBagConstraints g=new GridBagConstraints();
        g.insets=new Insets(7,7,7,7);
        g.fill=GridBagConstraints.HORIZONTAL;
        g.weightx=1;

        JComboBox<ShipmentChoice> shipmentBox=new JComboBox<>();
        for(TruckShipment shipment:store.all())
            shipmentBox.addItem(new ShipmentChoice(shipment));

        JComboBox<RouteConfig> routeBox=new JComboBox<>();
        for(RouteConfig route:config.routes)routeBox.addItem(route);
        routeBox.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(
                    JList<?> list,Object value,int index,
                    boolean selected,boolean focus){
                super.getListCellRendererComponent(
                        list,value,index,selected,focus);
                if(value instanceof RouteConfig route)
                    setText(route.name());
                return this;
            }
        });

        JComboBox<String> basisBox=new JComboBox<>(new String[]{
                "Carrier event checkpoints (recommended when available)",
                "Reconstructed roadway",
                "Straight-line fallback"
        });

        JCheckBox show=new JCheckBox(
                "Show historical/test truck and path on map",true);
        JSlider progress=new JSlider(0,100,0);
        progress.setMajorTickSpacing(25);
        progress.setPaintTicks(true);
        progress.setPaintLabels(true);

        JLabel state=new JLabel(
                "Select an old shipment and click Build Playback Route.");
        state.setForeground(Theme.muted());

        JProgressBar routeProgress=new JProgressBar();
        routeProgress.setIndeterminate(true);
        routeProgress.setVisible(false);

        JButton build=new JButton("Build Playback Route");
        build.putClientProperty("primaryAction",Boolean.TRUE);
        JButton play=new JButton("▶ Play");
        JButton reset=new JButton("Reset");
        JButton clear=new JButton("Clear Map Playback");

        final java.util.concurrent.atomic.AtomicReference<List<Location>>
                builtPath=new java.util.concurrent.atomic.AtomicReference<>(
                        List.of());
        final java.util.concurrent.atomic.AtomicReference<RouteConfig>
                builtRoute=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<ShipmentChoice>
                builtShipment=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean roadMode=
                new java.util.concurrent.atomic.AtomicBoolean(false);

        javax.swing.Timer timer=new javax.swing.Timer(90,null);
        timer.addActionListener(e->{
            int next=progress.getValue()+1;
            if(next>100){
                timer.stop();
                play.setText("▶ Play");
                next=100;
            }
            progress.setValue(next);
        });

        Runnable applyProgress=()->{
            ShipmentChoice choice=builtShipment.get();
            RouteConfig route=builtRoute.get();
            List<Location> path=builtPath.get();

            if(!show.isSelected()
                    ||choice==null
                    ||route==null
                    ||path.size()<2){
                if(!show.isSelected())
                    TruckPlaybackService.get().clear();
                return;
            }

            if(basisBox.getSelectedIndex()==0){
                TruckPlaybackService.get().setCarrierEvents(
                        choice.shipment,path,progress.getValue(),
                        "Carrier-reported tracking checkpoints");
            }else if(roadMode.get()){
                TruckPlaybackService.get().setRoadPath(
                        choice.shipment,
                        path,
                        List.of(route.origin(),route.destination()),
                        progress.getValue(),
                        "Road route reconstructed from TomTom"
                );
            }else{
                TruckPlaybackService.get().setStraightFallback(
                        choice.shipment,
                        route.origin(),
                        route.destination(),
                        progress.getValue(),
                        "No recorded carrier breadcrumb trail available"
                );
            }

            TruckPlaybackService service=TruckPlaybackService.get();
            TruckShipment overlay=service.overlay();
            String distance=service.totalMeters()<=0
                    ?""
                    :" • "+String.format(
                            Locale.US,"%.1f mi",
                            service.totalMeters()/1609.344);

            state.setText(
                    service.mode().label()
                    +" • "+choice.shipment.primaryCarrierIdentifier()
                    +" • "+route.name()
                    +" • "+progress.getValue()+"%"
                    +distance
                    +(overlay==null||overlay.lastUpdated==null
                        ?"":" • "+format(overlay.lastUpdated))
            );
        };

        build.addActionListener(e->{
            ShipmentChoice choice=
                    (ShipmentChoice)shipmentBox.getSelectedItem();
            RouteConfig route=(RouteConfig)routeBox.getSelectedItem();
            if(choice==null||route==null){
                ThemedDialogs.message(
                        this,
                        "Select both a historical shipment and a route.",
                        "Truck Playback",
                        ThemedDialogs.Kind.INFO);
                return;
            }

            timer.stop();
            play.setText("▶ Play");
            progress.setValue(0);
            build.setEnabled(false);
            routeProgress.setVisible(true);
            state.setText("Building roadway path…");

            int selectedBasis=basisBox.getSelectedIndex();
            boolean wantsCarrierEvents=selectedBasis==0;
            boolean wantsRoad=selectedBasis==1;

            SwingWorker<List<Location>,Void> worker=
                    new SwingWorker<>(){
                @Override protected List<Location> doInBackground()
                        throws Exception{
                    if(wantsCarrierEvents){
                        List<Location> checkpoints=eventStore
                                .forShipment(choice.shipment.id).stream()
                                .filter(ShipmentTrackingEvent::hasCoordinates)
                                .map(ev->new Location(
                                        ev.placeLabel(),ev.latitude,ev.longitude))
                                .toList();
                        if(checkpoints.size()<2)
                            throw new IllegalStateException(
                                    "This shipment does not have two located carrier events yet.");
                        return checkpoints;
                    }
                    if(!wantsRoad)
                        return List.of(route.origin(),route.destination());

                    return new TomTomService(new HttpService())
                            .fetchRouteGeometry(
                                    route.origin(),
                                    route.destination(),
                                    config.tomTomApiKey);
                }

                @Override protected void done(){
                    routeProgress.setVisible(false);
                    build.setEnabled(true);
                    try{
                        List<Location> path=get();
                        builtPath.set(path);
                        builtRoute.set(route);
                        builtShipment.set(choice);
                        roadMode.set(wantsRoad);
                        applyProgress.run();
                    }catch(Exception ex){
                        builtPath.set(List.of(
                                route.origin(),route.destination()));
                        builtRoute.set(route);
                        builtShipment.set(choice);
                        roadMode.set(false);

                        TruckPlaybackService.get().setStraightFallback(
                                choice.shipment,
                                route.origin(),
                                route.destination(),
                                progress.getValue(),
                                "Roadway calculation unavailable");

                        state.setText(
                                "Roadway route unavailable — "
                              + "straight-line fallback loaded.");

                        ThemedDialogs.message(
                                TruckTrackingPanel.this,
                                "North Star could not calculate the roadway "
                              + "route. The shipment is still available for "
                              + "fallback playback, but the fallback does not "
                              + "represent road travel.\n\n"
                              +rootMessage(ex),
                                "Truck Playback Route",
                                ThemedDialogs.Kind.WARNING);
                    }
                }
            };
            worker.execute();
        });

        Runnable invalidate=()->{
            timer.stop();
            play.setText("▶ Play");
            builtPath.set(List.of());
            builtShipment.set(null);
            builtRoute.set(null);
            TruckPlaybackService.get().clear();
            state.setText(
                    "Selection changed. Click Build Playback Route.");
        };

        shipmentBox.addActionListener(e->invalidate.run());
        routeBox.addActionListener(e->invalidate.run());
        basisBox.addActionListener(e->invalidate.run());

        show.addActionListener(e->{
            if(show.isSelected())applyProgress.run();
            else TruckPlaybackService.get().clear();
        });
        progress.addChangeListener(e->applyProgress.run());

        play.addActionListener(e->{
            if(builtShipment.get()==null
                    ||builtPath.get().size()<2){
                ThemedDialogs.message(
                        this,
                        "Build the playback route first.",
                        "Truck Playback",
                        ThemedDialogs.Kind.INFO);
                return;
            }

            if(timer.isRunning()){
                timer.stop();
                play.setText("▶ Play");
            }else{
                if(progress.getValue()>=100)
                    progress.setValue(0);
                timer.start();
                play.setText("Ⅱ Pause");
            }
        });

        reset.addActionListener(e->{
            timer.stop();
            play.setText("▶ Play");
            progress.setValue(0);
        });

        clear.addActionListener(e->{
            timer.stop();
            play.setText("▶ Play");
            TruckPlaybackService.get().clear();
            builtPath.set(List.of());
            builtShipment.set(null);
            builtRoute.set(null);
            state.setText("Playback cleared from the map.");
        });

        int y=0;
        y=playbackRow(card,g,y,"Historical shipment",shipmentBox);
        y=playbackRow(card,g,y,"Known route / destination",routeBox);
        y=playbackRow(card,g,y,"Playback basis",basisBox);

        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(show,g);y++;

        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(build,g);y++;

        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(routeProgress,g);y++;

        y=playbackRow(card,g,y,"Playback position",progress);

        JPanel actions=actions();
        actions.add(play);
        actions.add(reset);
        actions.add(clear);
        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(actions,g);y++;

        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(state,g);y++;

        JTextArea note=help(
                "Reconstructed roadway playback uses TomTom road-map geometry "
              + "between the selected known endpoints. It is a probable road "
              + "route, not a claim that FedEx/Penske reported every point. "
              + "When future carrier APIs provide timestamped scan/GPS "
              + "checkpoints, those checkpoints can anchor the same playback "
              + "engine. Stored shipment status/history is never changed.");
        g.gridx=0;g.gridy=y;g.gridwidth=2;
        card.add(note,g);

        panel.add(card,BorderLayout.NORTH);
        return panel;
    }

    private static String rootMessage(Throwable throwable){
        Throwable current=throwable;
        while(current.getCause()!=null)
            current=current.getCause();
        String message=current.getMessage();
        return message==null||message.isBlank()
                ?current.getClass().getSimpleName():message;
    }

    private static int playbackRow(
            JPanel panel,GridBagConstraints g,int y,String label,JComponent field){
        g.gridwidth=1;g.gridy=y;g.gridx=0;g.weightx=0.22;
        panel.add(new JLabel(label),g);
        g.gridx=1;g.weightx=0.78;
        field.setPreferredSize(new Dimension(480,44));
        panel.add(field,g);
        return y+1;
    }

    private static final class ShipmentChoice {
        final TruckShipment shipment;
        ShipmentChoice(TruckShipment shipment){this.shipment=shipment.copy();}
        @Override public String toString(){
            String id=shipment.primaryCarrierIdentifier();
            String date=shipment.shippedDate==null?"":shipment.shippedDate.toString();
            return shipment.carrier.display()+" • "+id
                    +(date.isBlank()?"":" • "+date)
                    +" • "+shipment.status;
        }
    }

    private JPanel buildSettings(){
        normalizeSettingsControls();
        JPanel page=page();
        JPanel form=new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(10,12,10,12));
        GridBagConstraints g=new GridBagConstraints();
        g.insets=new Insets(6,6,6,6);
        g.fill=GridBagConstraints.HORIZONTAL;
        g.weightx=1;

        int y=0;
        y=section(form,g,y,"Map Display");
        y=row(form,g,y,"FedEx",showFedex);
        y=row(form,g,y,"StarHub / Penske",showStarhub);
        y=row(form,g,y,"Delivered map retention (days)",deliveredMapDays);

        y=section(form,g,y,"History / Retention");
        y=row(form,g,y,"Current view recently closed (days)",currentDays);
        y=row(form,g,y,"Archive closed shipments after (days)",archiveDays);
        y=row(form,g,y,"Delete archived history after (days; 0 = never)",
                retentionDays);
        y=row(form,g,y,"Provider refresh cadence (minutes)",refreshMinutes);

        y=section(form,g,y,"FedEx Freight / Package Visibility");
        y=row(form,g,y,"Environment",fedexEnvironment);
        y=row(form,g,y,"Mode",fedexMode);
        y=row(form,g,y,"FedEx API client ID",fedexClientId);
        y=row(form,g,y,"FedEx API client secret",fedexSecret);

        JTextArea fedexHelp=help(
                "FedEx REST mode is credential-ready. The application can "
              + "continue using PRO-number manual/CSV tracking until a FedEx "
              + "Developer project is configured. Credentials are encrypted."
        );
        y=full(form,g,y,fedexHelp);

        y=section(form,g,y,"Trak-4 In-House GPS");
        y=row(form,g,y,"Map visibility",showTrak4);
        y=row(form,g,y,"Mode",trak4Mode);
        y=row(form,g,y,"GPS Reports HTTPS URL",trak4ReportsUrl);
        y=row(form,g,y,"Authorization header",trak4AuthHeader);
        y=row(form,g,y,"API key / token",trak4ApiKey);
        y=row(form,g,y,"Refresh cadence (minutes)",trak4RefreshMinutes);
        JTextArea trak4Help=help(
                "North Star owns the tracker assignment layer. A reusable "
              + "Trak-4 device is attached to a physical trailer and then "
              + "associated with today's DVIEW Trailer ID / Outbound Shipment. "
              + "REST URL/header fields remain configurable for the account's "
              + "Trak-4 API version.");
        y=full(form,g,y,trak4Help);

        y=section(form,g,y,"Mercedes StarHub / Penske");
        y=row(form,g,y,"Mode",penskeMode);
        y=row(form,g,y,"Enterprise feed HTTPS endpoint",penskeEndpoint);
        y=row(form,g,y,"Enterprise API token",penskeToken);

        JTextArea penskeHelp=help(
                "Penske Supply Chain Insight exposes load visibility to "
              + "customers, but a universal public tracking API is not "
              + "documented. Manual/CSV is fully usable now; ENTERPRISE_FEED "
              + "is reserved for the REST/SFTP/EDI interface your Penske "
              + "account team provides."
        );
        y=full(form,g,y,penskeHelp);

        JButton save=new JButton("Save Truck Tracking Settings");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->saveSettings());
        y=full(form,g,y,save);

        g.gridy=y;g.weighty=1;
        form.add(Box.createVerticalGlue(),g);

        JScrollPane scroll=new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.bg());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll,BorderLayout.CENTER);
        return page;
    }

    private void loadSettings(){
        showFedex.setSelected(config.showFedexTrucksOnMap);
        showStarhub.setSelected(config.showStarhubTrucksOnMap);
        deliveredMapDays.setValue(config.truckDeliveredMapDays);
        currentDays.setValue(config.truckCurrentHistoryDays);
        archiveDays.setValue(config.truckArchiveAfterDays);
        retentionDays.setValue(config.truckRetentionDays);
        refreshMinutes.setValue(config.truckRefreshMinutes);
        fedexEnvironment.setSelectedItem(config.fedexEnvironment);
        fedexMode.setSelectedItem(config.fedexTrackingMode);
        fedexClientId.setText(config.fedexClientId);
        fedexSecret.setText(config.fedexClientSecret);
        penskeMode.setSelectedItem(config.penskeTrackingMode);
        penskeEndpoint.setText(config.penskeTrackingEndpoint);
        penskeToken.setText(config.penskeApiToken);
        showTrak4.setSelected(config.showTrak4OnMap);
        trak4Mode.setSelectedItem(config.trak4Mode);
        trak4ReportsUrl.setText(config.trak4ReportsUrl);
        trak4AuthHeader.setText(config.trak4AuthHeader);
        trak4ApiKey.setText(config.trak4ApiKey);
        trak4RefreshMinutes.setValue(config.trak4RefreshMinutes);
    }

    private void saveSettings(){
        config.showFedexTrucksOnMap=showFedex.isSelected();
        config.showStarhubTrucksOnMap=showStarhub.isSelected();
        config.truckDeliveredMapDays=(Integer)deliveredMapDays.getValue();
        config.truckCurrentHistoryDays=(Integer)currentDays.getValue();
        config.truckArchiveAfterDays=(Integer)archiveDays.getValue();
        config.truckRetentionDays=(Integer)retentionDays.getValue();
        config.truckRefreshMinutes=(Integer)refreshMinutes.getValue();
        config.fedexEnvironment=String.valueOf(fedexEnvironment.getSelectedItem());
        config.fedexTrackingMode=String.valueOf(fedexMode.getSelectedItem());
        config.fedexClientId=fedexClientId.getText().trim();
        config.fedexClientSecret=new String(fedexSecret.getPassword());
        config.penskeTrackingMode=String.valueOf(penskeMode.getSelectedItem());
        config.penskeTrackingEndpoint=penskeEndpoint.getText().trim();
        config.penskeApiToken=new String(penskeToken.getPassword());
        config.showTrak4OnMap=showTrak4.isSelected();
        config.trak4Mode=String.valueOf(trak4Mode.getSelectedItem());
        config.trak4ReportsUrl=trak4ReportsUrl.getText().trim();
        config.trak4AuthHeader=trak4AuthHeader.getText().trim();
        config.trak4ApiKey=new String(trak4ApiKey.getPassword());
        config.trak4RefreshMinutes=(Integer)trak4RefreshMinutes.getValue();

        ConfigService.save(config);
        store.archiveClosedOlderThan(config.truckArchiveAfterDays);
        purgeByRetention();
        AuditService.record(
                SessionManager.currentUser()==null?"unknown":
                        SessionManager.currentUser().username(),
                "Updated Truck Tracking settings"
        );
        refreshTables();
        ThemedDialogs.message(this,
                "Truck Tracking settings saved.",
                "Truck Tracking",
                ThemedDialogs.Kind.INFO);
    }

    private void editSelected(JTable table){
        String id=selectedId(table);
        if(id==null)return;
        editShipment(store.find(id));
    }

    private void editShipment(TruckShipment existing){
        TruckShipment s=existing==null?new TruckShipment():existing.copy();

        JComboBox<TruckCarrier> carrier=new JComboBox<>(TruckCarrier.values());
        carrier.setSelectedItem(s.carrier);
        JTextField tracking=new JTextField(s.trackingNumber);
        JTextField pro=new JTextField(s.proNumber);
        JTextField trailer=new JTextField(s.trailerNumber);
        JTextField outbound=new JTextField(s.outboundShipmentId);
        JTextField customer=new JTextField(s.customerBk);
        JTextField route=new JTextField(s.routeName);
        JTextField origin=new JTextField(s.origin);
        JTextField destination=new JTextField(s.destination);
        JComboBox<TruckStatus> status=new JComboBox<>(TruckStatus.values());
        status.setSelectedItem(s.status);
        JTextField location=new JTextField(s.currentLocation);
        JTextField lat=new JTextField(s.latitude==null?"":String.valueOf(s.latitude));
        JTextField lon=new JTextField(s.longitude==null?"":String.valueOf(s.longitude));
        JTextField scheduled=new JTextField(formatEdit(s.scheduledArrival));
        JTextField eta=new JTextField(formatEdit(s.estimatedArrival));
        JSpinner traffic=new JSpinner(new SpinnerNumberModel(
                s.trafficDelayMinutes,0,10000,1));
        JSpinner weather=new JSpinner(new SpinnerNumberModel(
                s.weatherDelayMinutes,0,10000,1));
        JTextField notes=new JTextField(s.notes);

        JPanel form=new JPanel(new GridLayout(0,2,8,8));
        form.setBorder(new EmptyBorder(12,12,12,12));
        Object[][] rows={
                {"Carrier",carrier},{"Tracking # / Load #",tracking},
                {"FedEx PRO #",pro},{"Trailer",trailer},
                {"Outbound shipment ID",outbound},{"Customer / dealer key",customer},
                {"Route name",route},{"Origin",origin},
                {"Destination",destination},{"Status",status},
                {"Current location",location},{"Latitude",lat},
                {"Longitude",lon},{"Scheduled arrival (YYYY-MM-DDTHH:MM)",scheduled},
                {"Estimated arrival (YYYY-MM-DDTHH:MM)",eta},
                {"Traffic delay (min)",traffic},{"Weather delay (min)",weather},
                {"Notes",notes}
        };
        for(Object[] row:rows){
            form.add(new JLabel(String.valueOf(row[0])));
            form.add((Component)row[1]);
        }
        ThemeStyler.apply(form,Theme.active());

        boolean accepted=ThemedDialogs.confirmForm(
                this,form,
                existing==null?"Add Shipment":"Edit Shipment",
                existing==null?"Add Shipment":"Save Changes"
        );
        if(!accepted)return;

        s.carrier=(TruckCarrier)carrier.getSelectedItem();
        s.trackingNumber=tracking.getText().trim();
        s.proNumber=pro.getText().trim();
        s.trailerNumber=trailer.getText().trim();
        s.outboundShipmentId=outbound.getText().trim();
        s.customerBk=customer.getText().trim();
        s.routeName=route.getText().trim();
        s.origin=origin.getText().trim();
        s.destination=destination.getText().trim();
        s.status=(TruckStatus)status.getSelectedItem();
        s.currentLocation=location.getText().trim();
        s.latitude=parseDouble(lat.getText());
        s.longitude=parseDouble(lon.getText());
        s.scheduledArrival=parseDateTime(scheduled.getText());
        s.estimatedArrival=parseDateTime(eta.getText());
        s.trafficDelayMinutes=(Integer)traffic.getValue();
        s.weatherDelayMinutes=(Integer)weather.getValue();
        s.notes=notes.getText().trim();
        if(s.status==TruckStatus.DELIVERED&&s.deliveredAt==null)
            s.deliveredAt=LocalDateTime.now();

        store.upsert(s);
        refreshTables();
    }

    private void markDelivered(JTable table){
        String id=selectedId(table);
        if(id==null)return;
        TruckShipment s=store.find(id);
        if(s==null)return;
        s.status=TruckStatus.DELIVERED;
        s.deliveredAt=LocalDateTime.now();
        store.upsert(s);
        refreshTables();
    }

    private void deleteSelected(JTable table){
        String id=selectedId(table);
        if(id==null)return;
        boolean confirmed=ThemedDialogs.confirm(
                this,
                "Permanently delete the selected shipment record?",
                "Delete Shipment",
                "Delete",
                ThemedDialogs.Kind.WARNING
        );
        if(!confirmed)return;
        store.deleteWhere(s->Objects.equals(s.id,id));
        refreshTables();
    }

    private void importCsv(){
        JFileChooser chooser=ThemedFileChooser.chooseCsv(
                this,false,"Import Tracking CSV");
        if(chooser==null)return;
        try{
            int count=store.importCsv(chooser.getSelectedFile().toPath());
            store.archiveClosedOlderThan(config.truckArchiveAfterDays);
            purgeByRetention();
            refreshTables();
            ThemedDialogs.message(this,
                    count+" shipment row(s) imported/updated.",
                    "Truck Tracking Import",
                    ThemedDialogs.Kind.INFO);
        }catch(Exception ex){
            ThemedDialogs.message(this,
                    "Import failed: "+ex.getMessage(),
                    "Truck Tracking Import",
                    ThemedDialogs.Kind.ERROR);
        }
    }

    private void exportTemplate(){
        JFileChooser chooser=ThemedFileChooser.chooseCsv(
                this,true,"Save Template");
        if(chooser==null)return;
        Path path=ensureCsv(chooser.getSelectedFile().toPath());
        TruckShipment sample=new TruckShipment();
        sample.carrier=TruckCarrier.FEDEX;
        sample.trackingNumber="TRACKING-NUMBER";
        sample.proNumber="FEDEX-PRO-NUMBER";
        sample.trailerNumber="TRAILER-ID";
        sample.outboundShipmentId="OUTBOUND-SHIPMENT-ID";
        sample.customerBk="CUSTOMER-BK";
        sample.sourceSystem="MANUAL_TEMPLATE";
        sample.routeName="Inbound Route";
        sample.origin="Origin";
        sample.destination="Destination";
        sample.status=TruckStatus.PLANNED;
        sample.scheduledArrival=LocalDateTime.now().plusDays(1)
                .withSecond(0).withNano(0);
        try{
            store.exportCsv(path,List.of(sample));
        }catch(Exception ex){
            ThemedDialogs.message(this,ex.getMessage(),
                    "Export",ThemedDialogs.Kind.ERROR);
        }
    }

    private void exportAll(){
        exportRecords(store.all());
    }

    private void exportHistory(){
        exportRecords(historyRows());
    }

    private void exportRecords(List<TruckShipment> records){
        JFileChooser chooser=ThemedFileChooser.chooseCsv(
                this,true,"Export CSV");
        if(chooser==null)return;
        try{
            store.exportCsv(
                    ensureCsv(chooser.getSelectedFile().toPath()),
                    records
            );
            boolean archive=ThemedDialogs.confirm(
                    this,
                    "Export complete. Archive closed records from this export?",
                    "Export Complete",
                    "Archive",
                    ThemedDialogs.Kind.INFO
            );
            if(archive){
                Set<String> ids=new HashSet<>();
                for(TruckShipment s:records)if(s.status.closed())ids.add(s.id);
                List<TruckShipment> all=store.all();
                for(TruckShipment s:all)
                    if(ids.contains(s.id))s.archived=true;
                store.replaceAll(all);
                refreshTables();
            }
        }catch(Exception ex){
            ThemedDialogs.message(this,
                    "Export failed: "+ex.getMessage(),
                    "Truck Tracking Export",
                    ThemedDialogs.Kind.ERROR);
        }
    }

    private int purgeByRetention(){
        if(config.truckRetentionDays<=0)return 0;
        LocalDateTime cutoff=LocalDateTime.now()
                .minusDays(config.truckRetentionDays);
        return store.deleteWhere(s->s.archived
                &&s.lastUpdated!=null
                &&s.lastUpdated.isBefore(cutoff));
    }

    private void refreshTables(){
        List<TruckShipment> current=store.current(
                config.truckCurrentHistoryDays);
        fill(currentModel,current);
        List<TruckShipment> all=store.all().stream()
                .sorted(Comparator.comparing(
                        (TruckShipment s)->s.lastUpdated,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        fill(historyModel,all);

        TruckTrackingStats stats=store.stats();
        summary.setText(
                "Active "+stats.active()
                +"   •   Delayed "+stats.delayed()
                +"   •   Weather "+stats.weatherDelayed()
                +"   •   Traffic "+stats.trafficDelayed()
                +(stats.nextArrival()==null?"":
                    "   •   Next "+stats.nextArrival().format(DISPLAY))
        );
    }

    private List<TruckShipment> historyRows(){
        List<TruckShipment> all=store.all();
        List<TruckShipment> out=new ArrayList<>();
        for(int i=0;i<historyModel.getRowCount();i++){
            String id=String.valueOf(historyModel.getValueAt(i,0));
            for(TruckShipment s:all)
                if(s.id.equals(id)){out.add(s);break;}
        }
        return out;
    }

    private static DefaultTableModel tableModel(){
        return new DefaultTableModel(new Object[]{
                "ID","Carrier","Tracking #","PRO #","Trailer",
                "Outbound Shipment","Customer","Shipped","Ship IDs",
                "Source","Route","Origin","Destination","Status",
                "Current Location","ETA","Traffic Delay","Weather Delay",
                "Updated","Archived"
        },0){
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
    }

    private static void fill(
            DefaultTableModel model,List<TruckShipment> shipments){
        model.setRowCount(0);
        for(TruckShipment s:shipments){
            model.addRow(new Object[]{
                    s.id,s.carrier.display(),s.trackingNumber,s.proNumber,
                    s.trailerNumber,s.outboundShipmentId,s.customerBk,
                    s.shippedDate,shipIdSummary(s.shipIds),s.sourceSystem,
                    s.routeName,s.origin,s.destination,s.status,s.currentLocation,
                    format(s.projectedArrival()),
                    s.trafficDelayMinutes+" min",
                    s.weatherDelayMinutes+" min",
                    format(s.lastUpdated),s.archived?"Yes":"No"
            });
        }
    }

    private static String shipIdSummary(String shipIds){
        if(shipIds==null||shipIds.isBlank())return "";
        String[] values=shipIds.split(";");
        if(values.length==1)return values[0];
        return values.length+" Ship IDs";
    }

    private static void prepare(JTable table){
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setPreferredWidth(0);
    }

    private static JPanel page(){
        JPanel p=new JPanel(new BorderLayout(8,8));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10,10,10,10));
        return p;
    }

    private static JPanel actions(){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        p.setOpaque(false);return p;
    }

    private static JTextArea help(String text){
        JTextArea area=new JTextArea(text);
        area.setEditable(false);area.setOpaque(false);
        area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setForeground(Theme.muted());
        return area;
    }

    private static int section(
            JPanel form,GridBagConstraints g,int y,String text){
        JLabel label=new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD,15f));
        g.gridx=0;g.gridy=y;g.gridwidth=2;g.weightx=1;
        g.insets=new Insets(y==0?4:18,6,6,6);
        form.add(label,g);
        g.gridwidth=1;g.insets=new Insets(6,6,6,6);
        return y+1;
    }

    private static int row(
            JPanel form,GridBagConstraints g,int y,
            String label,JComponent field){
        g.gridy=y;
        g.gridx=0;
        g.weightx=0.30;
        g.anchor=GridBagConstraints.WEST;
        g.insets=new Insets(8,8,8,18);
        JLabel labelComponent=new JLabel(label);
        labelComponent.setBorder(new EmptyBorder(0,2,0,0));
        form.add(labelComponent,g);

        g.gridx=1;
        g.weightx=0.70;
        g.anchor=GridBagConstraints.CENTER;
        g.insets=new Insets(8,8,8,8);
        form.add(field,g);
        return y+1;
    }

    private void normalizeSettingsControls(){
        for(JSpinner spinner:List.of(
                deliveredMapDays,currentDays,archiveDays,
                retentionDays,refreshMinutes)){
            Dimension size=new Dimension(420,44);
            spinner.setPreferredSize(size);
            spinner.setMinimumSize(new Dimension(260,44));
            spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));

            JComponent editor=spinner.getEditor();
            editor.setPreferredSize(size);
            editor.setMinimumSize(new Dimension(260,48));
            if(editor instanceof JSpinner.DefaultEditor de){
                JFormattedTextField text=de.getTextField();
                text.setHorizontalAlignment(SwingConstants.LEFT);
                text.setFont(text.getFont().deriveFont(15f));
                text.setBorder(null);
                text.setMargin(new Insets(2,10,2,10));
                text.setPreferredSize(new Dimension(380,40));
                text.setMinimumSize(new Dimension(240,40));
            }
        }

        for(JComboBox<?> combo:List.of(fedexMode,penskeMode)){
            combo.setPreferredSize(new Dimension(420,48));
            combo.setMinimumSize(new Dimension(260,48));
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        }

        for(JTextField field:List.of(fedexClientId,penskeEndpoint)){
            field.setPreferredSize(new Dimension(420,48));
            field.setMinimumSize(new Dimension(260,48));
            field.setMargin(new Insets(8,12,8,12));
        }

        for(JPasswordField field:List.of(fedexSecret,penskeToken)){
            field.setPreferredSize(new Dimension(420,48));
            field.setMinimumSize(new Dimension(260,48));
            field.setMargin(new Insets(8,12,8,12));
        }
    }

    private static int full(
            JPanel form,GridBagConstraints g,int y,JComponent component){
        g.gridy=y;g.gridx=0;g.gridwidth=2;g.weightx=1;
        form.add(component,g);
        g.gridwidth=1;
        return y+1;
    }

    private String selectedId(JTable table){
        int row=table.getSelectedRow();
        if(row<0){
            ThemedDialogs.message(this,
                    "Select a shipment first.",
                    "Truck Tracking",
                    ThemedDialogs.Kind.INFO);
            return null;
        }
        row=table.convertRowIndexToModel(row);
        return String.valueOf(table.getModel().getValueAt(row,0));
    }

    private static String format(LocalDateTime dt){
        return dt==null?"":dt.format(DISPLAY);
    }

    private static String formatEdit(LocalDateTime dt){
        return dt==null?"":dt.withSecond(0).withNano(0).toString();
    }

    private static LocalDateTime parseDateTime(String text){
        if(text==null||text.isBlank())return null;
        try{return LocalDateTime.parse(text.trim());}
        catch(Exception ex){return null;}
    }

    private static Double parseDouble(String text){
        if(text==null||text.isBlank())return null;
        try{return Double.parseDouble(text.trim());}
        catch(Exception ex){return null;}
    }

    private static Path ensureCsv(Path path){
        String name=path.getFileName().toString();
        if(name.toLowerCase().endsWith(".csv"))return path;
        return path.resolveSibling(name+".csv");
    }
}
