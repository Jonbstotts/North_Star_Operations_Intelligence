package com.wtm.ui;

import com.wtm.ingest.*;import javax.swing.*;import javax.swing.border.EmptyBorder;import javax.swing.table.DefaultTableModel;import java.awt.*;import java.util.List;

public final class DataCollectionHistoryPanel extends JPanel {
 private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Received","Source","File","Detected Type","Status","Records","Message"},0){public boolean isCellEditable(int r,int c){return false;}};
 public DataCollectionHistoryPanel(){super(new BorderLayout(8,8));setBackground(Theme.bg());setBorder(new EmptyBorder(12,4,4,4));JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);JLabel info=new JLabel("Import audit history • source, schema classification, result, record count and duplicate/review status");info.setForeground(Theme.muted());JButton refresh=new JButton("Refresh");refresh.addActionListener(e->refresh());top.add(info);top.add(refresh,BorderLayout.EAST);add(top,BorderLayout.NORTH);JTable table=new JTable(model);table.setRowHeight(28);table.setFillsViewportHeight(true);JScrollPane sp=new JScrollPane(table);sp.getVerticalScrollBar().setUnitIncrement(18);add(sp);refresh();NorthStarThemeCompliance.apply(this);}
 public void refresh(){model.setRowCount(0);List<IngestionRecord> h=DataIngestionService.get().history();for(int i=h.size()-1;i>=0&&i>=h.size()-500;i--){IngestionRecord r=h.get(i);model.addRow(new Object[]{r.receivedAt(),r.source(),r.originalName(),r.detectedType(),r.status(),r.records(),r.message()});}}
}
