package com.wtm.ui;

import com.wtm.media.*;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Managed image library used by company announcements, employee recognition,
 * and general employee-event showcase photos.
 */
public final class MediaLibraryPanel extends JPanel {
    private final JComboBox<MediaCategory> category=
            new JComboBox<>(MediaCategory.values());
    private final DefaultListModel<Path> model=new DefaultListModel<>();
    private final JList<Path> files=new JList<>(model);
    private final JLabel preview=new JLabel(
            "Select an image to preview",
            SwingConstants.CENTER
    );
    private final JLabel count=new JLabel();

    public MediaLibraryPanel(){
        setLayout(new BorderLayout(12,12));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JPanel top=new JPanel(new BorderLayout(12,0));
        JLabel title=new JLabel("Managed Media Library");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));

        JPanel selector=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        selector.add(new JLabel("Collection"));
        selector.add(category);

        top.add(title,BorderLayout.WEST);
        top.add(selector,BorderLayout.EAST);
        add(top,BorderLayout.NORTH);

        files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        files.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focus
            ){
                String text=value instanceof Path p
                        ?p.getFileName().toString()
                        :String.valueOf(value);
                return super.getListCellRendererComponent(
                        list,text,index,selected,focus);
            }
        });

        preview.setOpaque(true);
        preview.setBackground(Theme.panel2());
        preview.setForeground(Theme.muted());
        preview.setBorder(BorderFactory.createLineBorder(Theme.border()));

        JSplitPane split=new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(files),
                preview
        );
        split.setResizeWeight(.35);
        split.setDividerLocation(360);
        add(split,BorderLayout.CENTER);

        JButton upload=new JButton("Upload Image");
        JButton delete=new JButton("Delete Selected");
        JButton cleanup=new JButton("Remove Duplicates");
        JButton refresh=new JButton("Refresh");

        upload.addActionListener(e->upload());
        delete.addActionListener(e->deleteSelected());
        cleanup.addActionListener(e->removeDuplicates());
        refresh.addActionListener(e->reload());

        JPanel bottom=new JPanel(new BorderLayout());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(upload);
        actions.add(delete);
        actions.add(cleanup);
        actions.add(refresh);

        bottom.add(actions,BorderLayout.WEST);
        bottom.add(count,BorderLayout.EAST);
        add(bottom,BorderLayout.SOUTH);

        category.addActionListener(e->reload());
        files.addListSelectionListener(e->{
            if(!e.getValueIsAdjusting())showPreview(files.getSelectedValue());
        });

        reload();
    }

    private void upload(){
        AuthorizationService.require(Permission.MEDIA_LIBRARY);

        JFileChooser chooser=ThemedFileChooser.chooseImages(this,true);
        if(chooser==null)return;

        int imported=0;
        for(var file:chooser.getSelectedFiles()){
            try{
                MediaService.importImage(
                        (MediaCategory)category.getSelectedItem(),
                        file.toPath()
                );
                imported++;
            }catch(Exception ex){
                ThemedDialogs.message(
                        this,
                        "Unable to import "+file.getName()+": "+ex.getMessage(),
                        "Media Import Failed",
                        ThemedDialogs.Kind.ERROR
                );
            }
        }

        reload();
        if(imported>0)
            ThemedDialogs.message(
                    this,
                    imported+" image"+(imported==1?"":"s")+" imported.",
                    "Media Imported",
                    ThemedDialogs.Kind.INFO
            );
    }

    private void deleteSelected(){
        AuthorizationService.require(Permission.MEDIA_LIBRARY);
        Path selected=files.getSelectedValue();
        if(selected==null)return;

        if(!ThemedDialogs.confirm(
                this,
                "Delete "+selected.getFileName()+" from the managed media library?",
                "Delete Media","Delete Media",ThemedDialogs.Kind.WARNING
        ))return;

        try{
            MediaService.delete(
                    (MediaCategory)category.getSelectedItem(),
                    selected
            );
            reload();
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,ex.getMessage(),"Delete Failed",
                    ThemedDialogs.Kind.ERROR
            );
        }
    }

    private void removeDuplicates(){
        AuthorizationService.require(Permission.MEDIA_LIBRARY);
        MediaCategory selected=(MediaCategory)category.getSelectedItem();
        if(selected==null)return;

        if(!ThemedDialogs.confirm(
                this,
                "Scan "+selected.display()+" for identical image files? "
                +"Only byte-for-byte duplicates will be removed; one copy "
                +"of every unique image will always be retained.",
                "Remove Duplicate Media",
                "Remove Duplicates",
                ThemedDialogs.Kind.WARNING
        ))return;

        try{
            int removed=MediaService.removeDuplicates(selected);
            reload();

            ThemedDialogs.message(
                    this,
                    removed==0
                            ?"No duplicate images were found."
                            :removed+" duplicate image"
                                +(removed==1?" was":"s were")
                                +" safely removed.",
                    "Duplicate Cleanup Complete",
                    ThemedDialogs.Kind.INFO
            );
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,
                    "Unable to clean duplicate media: "+ex.getMessage(),
                    "Duplicate Cleanup Failed",
                    ThemedDialogs.Kind.ERROR
            );
        }
    }

    private void reload(){
        model.clear();
        MediaCategory selected=(MediaCategory)category.getSelectedItem();
        if(selected==null)return;

        for(Path path:MediaService.list(selected))
            model.addElement(path);

        count.setText(model.size()+" image"+(model.size()==1?"":"s")+" ");
        preview.setIcon(null);
        preview.setText(
                selected==MediaCategory.ANNOUNCEMENTS
                        ?"Announcement images rotate in Main Showcase."
                        :selected==MediaCategory.EMPLOYEE_PHOTOS
                        ?"Employee photos are available to Employee Operations and celebration announcements."
                        :"Employee Showcase photos rotate in Main Showcase."
        );
    }

    private void showPreview(Path path){
        preview.setIcon(null);
        if(path==null)return;

        try{
            BufferedImage image=OrientedImageLoader.load(path);
            if(image==null)return;

            int maxW=Math.max(320,preview.getWidth()-40);
            int maxH=Math.max(220,preview.getHeight()-40);
            double scale=Math.min(
                    maxW/(double)image.getWidth(),
                    maxH/(double)image.getHeight()
            );
            scale=Math.min(1.0,scale);

            int w=Math.max(1,(int)Math.round(image.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(image.getHeight()*scale));

            preview.setText("");
            preview.setIcon(new ImageIcon(
                    image.getScaledInstance(w,h,Image.SCALE_SMOOTH)
            ));
        }catch(Exception ex){
            preview.setText("Preview unavailable.");
        }
    }
}
