package com.wtm.ui;

import com.wtm.model.TeamSearchResult;
import com.wtm.net.HttpService;
import com.wtm.sports.TheSportsDbService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-backed Find Team dialog used by Settings > Sports.
 *
 * Network work runs off the Swing Event Dispatch Thread so API latency never
 * freezes the settings window.
 */
public final class TeamSearchDialog extends JDialog {
    private final String apiKey;
    private final boolean premium;
    private final Consumer<TeamSearchResult> onUse;

    private final TheSportsDbService service =
            new TheSportsDbService(new HttpService());
    private final HttpService http = new HttpService();

    private final JTextField query=new JTextField();
    private final JButton searchButton=new JButton("Find Team");
    private final JButton useButton=new JButton("Use This Team");
    private final JLabel status=new JLabel("Enter a team name to search.");
    private final JLabel logo=new JLabel("No logo",SwingConstants.CENTER);

    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Team","League","Sport","Country","Team ID","League ID","Provider"},0){
        @Override public boolean isCellEditable(int row,int column){ return false; }
    };
    private final JTable table=new JTable(model);
    private List<TeamSearchResult> results=List.of();

    public TeamSearchDialog(
            Window owner,
            String initialQuery,
            String apiKey,
            boolean premium,
            Consumer<TeamSearchResult> onUse
    ){
        super(owner,"Find Sports Team",ModalityType.APPLICATION_MODAL);
        this.apiKey=apiKey==null?"":apiKey.trim();
        this.premium=premium;
        this.onUse=onUse;

        setSize(980,610);
        setMinimumSize(new Dimension(760,500));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12,12));

        JPanel top=new JPanel(new BorderLayout(8,8));
        top.setBorder(BorderFactory.createEmptyBorder(14,14,0,14));

        JPanel searchRow=new JPanel(new BorderLayout(8,0));
        query.setText(initialQuery==null?"":initialQuery);
        searchRow.add(new JLabel("Team name:"),BorderLayout.WEST);
        searchRow.add(query,BorderLayout.CENTER);
        searchRow.add(searchButton,BorderLayout.EAST);

        JLabel note=new JLabel(
                premium
                ? "<html>Premium search enabled. Search across sports and leagues, then choose the correct result.</html>"
                : "<html><b>Free-key note:</b> TheSportsDB currently restricts general v1 team-name searching. "
                  + "The dialog will still try the configured key, but most arbitrary team searches require "
                  + "a premium key. Existing manually configured sports blocks continue to work.</html>");

        top.add(searchRow,BorderLayout.NORTH);
        top.add(note,BorderLayout.SOUTH);
        add(top,BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(27);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);

        JPanel center=new JPanel(new BorderLayout(12,0));
        center.setBorder(BorderFactory.createEmptyBorder(0,14,0,14));
        center.add(new JScrollPane(table),BorderLayout.CENTER);

        RoundedPanel preview=new RoundedPanel(16);
        preview.setPreferredSize(new Dimension(210,10));
        preview.setLayout(new BorderLayout(8,8));
        preview.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JLabel previewTitle=new JLabel("TEAM LOGO",SwingConstants.CENTER);
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD,13f));
        logo.setPreferredSize(new Dimension(180,180));
        preview.add(previewTitle,BorderLayout.NORTH);
        preview.add(logo,BorderLayout.CENTER);
        center.add(preview,BorderLayout.EAST);

        add(center,BorderLayout.CENTER);

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
            status.setText("Enter a team name first.");
            return;
        }

        searchButton.setEnabled(false);
        useButton.setEnabled(false);
        status.setText("Searching TheSportsDB for “"+q+"”…");
        model.setRowCount(0);
        logo.setIcon(null);
        logo.setText("Searching…");

        new SwingWorker<List<TeamSearchResult>,Void>(){
            @Override protected List<TeamSearchResult> doInBackground() throws Exception {
                return service.searchTeams(q,apiKey,premium);
            }

            @Override protected void done(){
                searchButton.setEnabled(true);
                try{
                    results=get();
                    populate(results);

                    if(results.isEmpty()){
                        if(!premium || apiKey.isBlank() || "123".equals(apiKey)){
                            status.setText("No results. General team search is restricted on the current free provider key.");
                        }else{
                            status.setText("No matching teams were returned.");
                        }
                        logo.setText("No result");
                    }else{
                        status.setText(results.size()+" matching team"+(results.size()==1?"":"s")+" found.");
                        table.setRowSelectionInterval(0,0);
                    }
                }catch(Exception ex){
                    Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    status.setText("Search failed: "+cause.getMessage());
                    logo.setText("Search failed");
                }
            }
        }.execute();
    }

    private void populate(List<TeamSearchResult> found){
        model.setRowCount(0);
        for(TeamSearchResult r:found){
            model.addRow(new Object[]{
                    r.teamName(),r.leagueName(),r.sport(),r.country(),
                    r.teamId(),r.leagueId(),r.provider()
            });
        }
    }

    private void selectionChanged(ListSelectionEvent e){
        if(e.getValueIsAdjusting()) return;
        int viewRow=table.getSelectedRow();
        if(viewRow<0){
            useButton.setEnabled(false);
            logo.setIcon(null);
            logo.setText("No logo");
            return;
        }

        int row=table.convertRowIndexToModel(viewRow);
        if(row<0 || row>=results.size()) return;

        TeamSearchResult result=results.get(row);
        useButton.setEnabled(true);
        loadLogo(result.badgeUrl());
    }

    private void loadLogo(String url){
        logo.setIcon(null);
        if(url==null||url.isBlank()){
            logo.setText("No logo available");
            return;
        }

        logo.setText("Loading logo…");
        new SwingWorker<ImageIcon,Void>(){
            @Override protected ImageIcon doInBackground() throws Exception {
                byte[] bytes=http.getBytes(url);
                BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
                if(image==null) return null;

                int max=170;
                double scale=Math.min(max/(double)image.getWidth(),max/(double)image.getHeight());
                scale=Math.min(1.0,scale);
                int w=Math.max(1,(int)Math.round(image.getWidth()*scale));
                int h=Math.max(1,(int)Math.round(image.getHeight()*scale));
                Image scaled=image.getScaledInstance(w,h,Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            @Override protected void done(){
                try{
                    ImageIcon icon=get();
                    logo.setText(icon==null?"No logo available":"");
                    logo.setIcon(icon);
                }catch(Exception ex){
                    logo.setText("Logo unavailable");
                }
            }
        }.execute();
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
