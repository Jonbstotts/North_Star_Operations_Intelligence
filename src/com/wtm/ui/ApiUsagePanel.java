package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.usage.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

/**
 * Displays locally tracked API usage against known provider allowances.
 *
 * The entire table is explicitly themed because JTable otherwise keeps parts
 * of the platform/default Swing palette, which can make text unreadable when
 * the dashboard uses a dark-family theme.
 */
public final class ApiUsagePanel extends JPanel {
    private final AppConfig config;
    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Provider","Category","Used","Limit","Period","Usage","Status","Notes"},0){
        @Override public boolean isCellEditable(int r,int c){return false;}
        @Override public Class<?> getColumnClass(int c){
            if(c==2||c==3)return Long.class;
            return String.class;
        }
    };

    private final JTable table=new JTable(model);
    private final JScrollPane scrollPane=new JScrollPane(table);
    private final JLabel summary=new JLabel();

    public ApiUsagePanel(AppConfig config){
        super(new BorderLayout(10,10));
        this.config=config;

        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTextArea help=new JTextArea(
                "These figures count requests made by this installation. They do not include "
              + "requests from another computer/application using the same API key. Provider "
              + "dashboards remain the authoritative account-wide source.");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);

        JPanel top=new JPanel(new BorderLayout(8,8));
        top.setOpaque(false);
        top.add(help,BorderLayout.CENTER);
        top.add(summary,BorderLayout.SOUTH);
        add(top,BorderLayout.NORTH);

        table.setRowHeight(27);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setDefaultRenderer(Object.class,new UsageRenderer());
        table.setDefaultRenderer(Long.class,new UsageRenderer());

        add(scrollPane,BorderLayout.CENTER);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);

        JButton refresh=new JButton("Refresh Usage");
        refresh.addActionListener(e->refresh());

        JButton reset=new JButton("Reset Local Counters");
        reset.addActionListener(e->reset());

        buttons.add(refresh);
        buttons.add(reset);
        add(buttons,BorderLayout.SOUTH);

        applyTheme();
        refresh();
    }

    /**
     * Applies the active application theme to every JTable surface.
     *
     * JTable header, body, viewport, grid and selection colors are separate
     * Swing properties, so each is set intentionally.
     */
    private void applyTheme(){
        AppTheme theme=ThemedDialogs.resolveTheme(this);
        Color bg=theme.bg();
        Color panel=theme.panel();
        Color panel2=theme.panel2();
        Color border=theme.border();
        Color text=theme.text();
        Color muted=theme.muted();
        Color accent=theme.accent();

        setBackground(bg);

        table.setBackground(panel);
        table.setForeground(text);
        table.setGridColor(border);
        table.setSelectionBackground(accent);
        table.setSelectionForeground(bestSelectionText(accent));
        table.setFont(table.getFont().deriveFont(13f));

        JTableHeader header=table.getTableHeader();
        header.setBackground(panel2);
        header.setForeground(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD,13f));
        header.setBorder(BorderFactory.createMatteBorder(0,0,1,0,border));

        scrollPane.getViewport().setBackground(panel);
        scrollPane.setBackground(panel);
        scrollPane.setBorder(BorderFactory.createLineBorder(border));

        summary.setForeground(muted);

        for(Component c:getComponents()){
            if(c instanceof JPanel p){
                p.setBackground(bg);
                for(Component child:p.getComponents()){
                    if(child instanceof JLabel l) l.setForeground(text);
                    if(child instanceof JTextArea a) a.setForeground(text);
                    if(child instanceof JButton b){
                        b.setForeground(text);
                        b.setBackground(panel2);
                        b.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(border,1,true),
                                BorderFactory.createEmptyBorder(5,10,5,10)
                        ));
                    }
                }
            }
        }

        ThemeStyler.apply(this,theme);
        repaint();
    }

    private static Color bestSelectionText(Color bg){
        double luminance=(0.299*bg.getRed()+0.587*bg.getGreen()+0.114*bg.getBlue())/255.0;
        return luminance>0.62 ? Color.BLACK : Color.WHITE;
    }

    public void refresh(){
        // Re-apply in case the application theme changed while Settings was open/rebuilt.
        applyTheme();

        boolean customer="OPEN_METEO_CUSTOMER".equalsIgnoreCase(config.weatherProvider);
        List<ApiUsageRecord> rows=ApiUsageTracker.get().snapshot(
                customer,
                config.sportsPremiumLiveScores
        );

        model.setRowCount(0);

        int warning=0;
        for(ApiUsageRecord r:rows){
            String limit=r.limit()<=0?"—":Long.toString(r.limit());
            String usage=r.percent()<0?"Tracked only":String.format("%.1f%%",r.percent());
            String status=status(r);

            if(r.percent()>=80) warning++;

            model.addRow(new Object[]{
                    r.provider(),
                    r.category(),
                    r.used(),
                    limit,
                    r.period(),
                    usage,
                    status,
                    r.note()
            });
        }

        summary.setText(
                warning==0
                ?"No locally tracked API category is currently at or above 80% of its known allowance."
                :warning+" API categor"+(warning==1?"y is":"ies are")
                        +" at or above 80% of a known allowance."
        );
    }

    private static String status(ApiUsageRecord r){
        if(r.percent()<0)return "INFO";
        if(r.percent()>=95)return "CRITICAL";
        if(r.percent()>=80)return "WARNING";
        if(r.percent()>=60)return "WATCH";
        return "OK";
    }

    private void reset(){
        boolean confirmed=ThemedDialogs.confirm(
                this,
                "Reset this installation's local API counters?\nProvider-side usage is not affected.",
                "Reset Local API Usage",
                "Reset Counters",
                ThemedDialogs.Kind.WARNING
        );

        if(confirmed){
            ApiUsageTracker.get().resetLocalHistory();
            refresh();
        }
    }

    /**
     * Theme-aware renderer with usage-status emphasis.
     */
    private final class UsageRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean selected,
                boolean focus,
                int row,
                int col
        ){
            Component c=super.getTableCellRendererComponent(
                    table,value,selected,focus,row,col
            );

            if(selected){
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
                return c;
            }

            AppTheme theme=ThemedDialogs.resolveTheme(ApiUsagePanel.this);
            c.setBackground(theme.panel());

            int modelRow=table.convertRowIndexToModel(row);
            String status=String.valueOf(
                    table.getModel().getValueAt(modelRow,6)
            );

            if("CRITICAL".equals(status)){
                c.setForeground(Theme.danger());
            }else if("WARNING".equals(status)||"WATCH".equals(status)){
                c.setForeground(Theme.warn());
            }else if("INFO".equals(status)){
                c.setForeground(theme.muted());
            }else{
                c.setForeground(theme.text());
            }

            if(col==2||col==3){
                setHorizontalAlignment(SwingConstants.RIGHT);
            }else{
                setHorizontalAlignment(SwingConstants.LEFT);
            }

            return c;
        }
    }
}
