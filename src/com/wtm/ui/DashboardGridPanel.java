package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Source-owned snap grid for the Operations dashboard.
 *
 * Tiles move and resize only while edit mode is enabled. Layout is
 * persisted as x,y,width,height grid units, so normal dashboard use
 * has no global listeners, component scans, or delayed correction.
 */
public final class DashboardGridPanel extends JPanel {
    private static final int COLUMNS=12;
    private static final int GAP=10;
    private static final int ROW_HEIGHT=92;

    private final Map<String,String> layout;
    private final Map<String,Tile> tiles=new LinkedHashMap<>();
    private final Runnable persist;
    private boolean editMode;

    public DashboardGridPanel(Map<String,String> layout,Runnable persist){
        this.layout=Objects.requireNonNull(layout);
        this.persist=persist==null?()->{}:persist;
        setOpaque(false);
        setLayout(null);
    }

    public void addTile(String id,String label,JComponent component,String defaultSpec){
        if(id==null||id.isBlank()||component==null)return;
        String spec=layout.computeIfAbsent(id,key->defaultSpec);
        Tile tile=new Tile(id,label,component,GridSpec.parse(spec,defaultSpec));
        tiles.put(id,tile);
        add(tile);
        updatePreferredHeight();
    }

    public void setEditMode(boolean enabled){
        editMode=enabled;
        for(Tile tile:tiles.values())tile.setEditing(enabled);
        revalidate();
        repaint();
    }

    public boolean editMode(){ return editMode; }

    public void resetLayout(Map<String,String> defaults){
        if(defaults==null)return;
        for(Map.Entry<String,Tile> entry:tiles.entrySet()){
            String spec=defaults.get(entry.getKey());
            if(spec==null)continue;
            entry.getValue().spec=GridSpec.parse(spec,spec);
            layout.put(entry.getKey(),entry.getValue().spec.encode());
        }
        updatePreferredHeight();
        revalidate();
        repaint();
        persist.run();
    }

    @Override public void doLayout(){
        int width=Math.max(720,getWidth());
        int columnWidth=Math.max(42,(width-GAP*(COLUMNS-1))/COLUMNS);
        for(Tile tile:tiles.values()){
            GridSpec s=tile.spec.clamped();
            int x=s.x*(columnWidth+GAP);
            int y=s.y*(ROW_HEIGHT+GAP);
            int w=s.w*columnWidth+(s.w-1)*GAP;
            int h=s.h*ROW_HEIGHT+(s.h-1)*GAP;
            tile.setBounds(x,y,w,h);
        }
    }

    private void updatePreferredHeight(){
        int rows=1;
        for(Tile tile:tiles.values())rows=Math.max(rows,tile.spec.y+tile.spec.h);
        int height=rows*ROW_HEIGHT+Math.max(0,rows-1)*GAP;
        setPreferredSize(new Dimension(1100,height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
    }

    private int gridColumnWidth(){
        int width=Math.max(720,getWidth());
        return Math.max(42,(width-GAP*(COLUMNS-1))/COLUMNS);
    }

    private void commit(Tile tile){
        tile.spec=tile.spec.clamped();
        layout.put(tile.id,tile.spec.encode());
        updatePreferredHeight();
        revalidate();
        repaint();
        persist.run();
    }

    private final class Tile extends JPanel {
        private final String id;
        private GridSpec spec;
        private final JPanel editBar=new JPanel(new BorderLayout());
        private final JLabel resizeHandle=new JLabel("↘",SwingConstants.CENTER);
        private Point dragOrigin;
        private GridSpec dragStart;

        Tile(String id,String label,JComponent component,GridSpec spec){
            super(new BorderLayout());
            this.id=id;
            this.spec=spec;
            setOpaque(false);

            JLabel move=new JLabel("↕  "+(label==null?id:label));
            move.setForeground(Theme.text());
            move.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
            move.setBorder(new EmptyBorder(4,8,4,8));
            editBar.setBackground(Theme.panel2());
            editBar.add(move,BorderLayout.CENTER);
            editBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

            resizeHandle.setForeground(Theme.text());
            resizeHandle.setBackground(Theme.panel2());
            resizeHandle.setOpaque(true);
            resizeHandle.setPreferredSize(new Dimension(22,18));
            resizeHandle.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));

            JPanel south=new JPanel(new BorderLayout());
            south.setOpaque(false);
            south.add(resizeHandle,BorderLayout.EAST);

            add(editBar,BorderLayout.NORTH);
            add(component,BorderLayout.CENTER);
            add(south,BorderLayout.SOUTH);

            MouseAdapter drag=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){
                    if(!editMode)return;
                    dragOrigin=SwingUtilities.convertPoint(editBar,e.getPoint(),DashboardGridPanel.this);
                    dragStart=Tile.this.spec;
                }
                @Override public void mouseDragged(MouseEvent e){
                    if(!editMode||dragOrigin==null||dragStart==null)return;
                    Point now=SwingUtilities.convertPoint(editBar,e.getPoint(),DashboardGridPanel.this);
                    int cw=gridColumnWidth()+GAP;
                    int dx=Math.round((now.x-dragOrigin.x)/(float)cw);
                    int dy=Math.round((now.y-dragOrigin.y)/(float)(ROW_HEIGHT+GAP));
                    Tile.this.spec=new GridSpec(dragStart.x+dx,dragStart.y+dy,dragStart.w,dragStart.h).clamped();
                    DashboardGridPanel.this.revalidate();
                    DashboardGridPanel.this.repaint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    if(!editMode||dragOrigin==null)return;
                    dragOrigin=null;dragStart=null;commit(Tile.this);
                }
            };
            editBar.addMouseListener(drag);
            editBar.addMouseMotionListener(drag);

            MouseAdapter resize=new MouseAdapter(){
                private Point origin;
                private GridSpec start;
                @Override public void mousePressed(MouseEvent e){
                    if(!editMode)return;
                    origin=SwingUtilities.convertPoint(resizeHandle,e.getPoint(),DashboardGridPanel.this);
                    start=Tile.this.spec;
                }
                @Override public void mouseDragged(MouseEvent e){
                    if(!editMode||origin==null||start==null)return;
                    Point now=SwingUtilities.convertPoint(resizeHandle,e.getPoint(),DashboardGridPanel.this);
                    int dw=Math.round((now.x-origin.x)/(float)(gridColumnWidth()+GAP));
                    int dh=Math.round((now.y-origin.y)/(float)(ROW_HEIGHT+GAP));
                    Tile.this.spec=new GridSpec(start.x,start.y,Math.max(2,start.w+dw),Math.max(1,start.h+dh)).clamped();
                    DashboardGridPanel.this.revalidate();
                    DashboardGridPanel.this.repaint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    if(!editMode||origin==null)return;
                    origin=null;start=null;commit(Tile.this);
                }
            };
            resizeHandle.addMouseListener(resize);
            resizeHandle.addMouseMotionListener(resize);
            setEditing(false);
        }

        void setEditing(boolean editing){
            editBar.setVisible(editing);
            resizeHandle.getParent().setVisible(editing);
            setBorder(editing
                    ?BorderFactory.createLineBorder(Theme.accent(),2,true)
                    :BorderFactory.createEmptyBorder());
        }
    }

    private record GridSpec(int x,int y,int w,int h){
        static GridSpec parse(String value,String fallback){
            GridSpec parsed=parse0(value);
            if(parsed==null)parsed=parse0(fallback);
            return parsed==null?new GridSpec(0,0,3,3):parsed.clamped();
        }
        private static GridSpec parse0(String value){
            if(value==null)return null;
            try{
                String[] p=value.trim().split(",");
                if(p.length!=4)return null;
                return new GridSpec(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        Integer.parseInt(p[3].trim()));
            }catch(Exception ex){return null;}
        }
        GridSpec clamped(){
            int width=Math.max(2,Math.min(COLUMNS,w));
            int xx=Math.max(0,Math.min(COLUMNS-width,x));
            return new GridSpec(xx,Math.max(0,y),width,Math.max(1,Math.min(12,h)));
        }
        String encode(){ return x+","+y+","+w+","+h; }
    }
}
