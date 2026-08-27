package com.wtm.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public final class NorthStarCsvFileDialog extends JDialog {
    private final DefaultListModel<Path> model=new DefaultListModel<>();
    private final JList<Path> list=new JList<>(model);
    private final JLabel location=new JLabel();
    private final JLabel status=new JLabel("Select a CSV report to import.");
    private final JButton open=new JButton("Open");
    private Path currentDirectory;
    private Path selectedFile;

    private NorthStarCsvFileDialog(Window owner,Path initialDirectory){
        super(owner,"Import Operations KPI CSV",ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(780,520));
        setSize(900,590);
        setLayout(new BorderLayout());
        setLocationRelativeTo(owner);

        AppTheme theme=ThemedDialogs.resolveTheme(owner);

        JPanel root=new JPanel(new BorderLayout(0,14));
        root.setBorder(new EmptyBorder(18,20,18,20));

        JPanel heading=new JPanel(new BorderLayout());
        JPanel headingText=new JPanel();
        headingText.setLayout(new BoxLayout(headingText,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Import Operations KPI CSV");
        title.setFont(title.getFont().deriveFont(Font.BOLD,19f));
        JLabel subtitle=new JLabel(
                "Choose a Daily LHY / LPH, Floor Denials, or other supported CSV report.");
        subtitle.setForeground(theme.muted());
        headingText.add(title);
        headingText.add(Box.createVerticalStrut(3));
        headingText.add(subtitle);
        heading.add(headingText,BorderLayout.CENTER);
        root.add(heading,BorderLayout.NORTH);

        JPanel body=new JPanel(new BorderLayout(0,10));
        JPanel navigation=new JPanel(new BorderLayout(8,0));
        JPanel navButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));
        JButton up=new JButton("↑ Up");
        JButton documents=new JButton("Documents");
        JButton home=new JButton("Home");
        navButtons.add(up);navButtons.add(documents);navButtons.add(home);

        location.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border(),1,true),
                new EmptyBorder(8,10,8,10)));
        navigation.add(navButtons,BorderLayout.WEST);
        navigation.add(location,BorderLayout.CENTER);
        body.add(navigation,BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(54);
        list.setCellRenderer(new FileRenderer(theme));
        list.addListSelectionListener(e->{
            if(e.getValueIsAdjusting())return;
            Path path=list.getSelectedValue();
            open.setEnabled(path!=null);
            if(path==null) status.setText("Select a CSV report to import.");
            else if(Files.isDirectory(path))
                status.setText("Folder • double-click or choose Open to browse.");
            else status.setText(fileDetails(path));
        });
        list.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2){
                    Path path=list.getSelectedValue();
                    if(path!=null)activate(path);
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"activate");
        list.getActionMap().put("activate",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                Path path=list.getSelectedValue();
                if(path!=null)activate(path);
            }
        });

        JScrollPane scroll=new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(theme.border(),1,true));
        body.add(scroll,BorderLayout.CENTER);
        status.setForeground(theme.muted());
        status.setBorder(new EmptyBorder(2,2,0,2));
        body.add(status,BorderLayout.SOUTH);
        root.add(body,BorderLayout.CENTER);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        JButton cancel=new JButton("Cancel");
        open.putClientProperty("primaryAction",Boolean.TRUE);
        open.setEnabled(false);
        actions.add(cancel);actions.add(open);
        root.add(actions,BorderLayout.SOUTH);

        up.addActionListener(e->{
            Path parent=currentDirectory==null?null:currentDirectory.getParent();
            if(parent!=null)showDirectory(parent);
        });
        home.addActionListener(e->showDirectory(userHome()));
        documents.addActionListener(e->showDirectory(documentsDirectory()));
        cancel.addActionListener(e->dispose());
        open.addActionListener(e->{
            Path path=list.getSelectedValue();
            if(path!=null)activate(path);
        });

        getRootPane().setDefaultButton(open);
        getRootPane().registerKeyboardAction(
                e->dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        add(root,BorderLayout.CENTER);
        ThemeStyler.apply(this,theme);

        Path start=validDirectory(initialDirectory)?initialDirectory:documentsDirectory();
        showDirectory(start);
    }

    public static Path choose(Component parent,Path initialDirectory){
        Window owner=parent==null?null:SwingUtilities.getWindowAncestor(parent);
        NorthStarCsvFileDialog dialog=
                new NorthStarCsvFileDialog(owner,initialDirectory);
        dialog.setVisible(true);
        return dialog.selectedFile;
    }

    private void activate(Path path){
        if(Files.isDirectory(path)){showDirectory(path);return;}
        String name=path.getFileName()==null?"":path.getFileName().toString();
        if(!name.toLowerCase(Locale.ROOT).endsWith(".csv")){
            status.setText("Only CSV files can be imported.");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        selectedFile=path;
        dispose();
    }

    private void showDirectory(Path directory){
        if(!validDirectory(directory))return;
        currentDirectory=directory.toAbsolutePath().normalize();
        location.setText(currentDirectory.toString());
        model.clear();
        try(var stream=Files.list(currentDirectory)){
            List<Path> entries=stream.filter(p->{
                        try{if(Files.isHidden(p))return false;}catch(Exception ignored){}
                        if(Files.isDirectory(p))return true;
                        String n=p.getFileName()==null?"":p.getFileName().toString();
                        return n.toLowerCase(Locale.ROOT).endsWith(".csv");
                    })
                    .sorted((a,b)->{
                        boolean ad=Files.isDirectory(a),bd=Files.isDirectory(b);
                        if(ad!=bd)return ad?-1:1;
                        String an=a.getFileName()==null?"":a.getFileName().toString();
                        String bn=b.getFileName()==null?"":b.getFileName().toString();
                        return an.compareToIgnoreCase(bn);
                    }).toList();
            entries.forEach(model::addElement);
            status.setText(entries.isEmpty()
                    ?"No folders or CSV files are available in this location."
                    :"Showing "+entries.size()+" folders / CSV files.");
        }catch(Exception ex){
            status.setText("Unable to read this folder: "+ex.getMessage());
        }
    }

    private static boolean validDirectory(Path p){
        return p!=null&&Files.isDirectory(p)&&Files.isReadable(p);
    }
    private static Path userHome(){
        return Path.of(System.getProperty("user.home","."));
    }
    private static Path documentsDirectory(){
        Path docs=userHome().resolve("Documents");
        return validDirectory(docs)?docs:userHome();
    }

    private static String fileDetails(Path path){
        try{
            BasicFileAttributes a=Files.readAttributes(path,BasicFileAttributes.class);
            String modified=DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
                    .withZone(ZoneId.systemDefault())
                    .format(a.lastModifiedTime().toInstant());
            return "CSV • "+formatSize(a.size())+" • modified "+modified;
        }catch(Exception ignored){return "CSV report";}
    }

    private static String formatSize(long bytes){
        if(bytes<1024)return bytes+" B";
        double kb=bytes/1024.0;
        if(kb<1024)return String.format(Locale.US,"%.1f KB",kb);
        return String.format(Locale.US,"%.1f MB",kb/1024.0);
    }

    private static final class FileRenderer extends JPanel
            implements ListCellRenderer<Path>{
        private final JLabel icon=new JLabel();
        private final JLabel name=new JLabel();
        private final JLabel details=new JLabel();
        private final AppTheme theme;

        private FileRenderer(AppTheme theme){
            this.theme=theme;
            setLayout(new BorderLayout(12,0));
            setBorder(new EmptyBorder(7,10,7,10));
            icon.setPreferredSize(new Dimension(30,30));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel text=new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
            name.setFont(name.getFont().deriveFont(Font.BOLD,13f));
            details.setFont(details.getFont().deriveFont(Font.PLAIN,11f));
            text.add(name);text.add(Box.createVerticalStrut(2));text.add(details);
            add(icon,BorderLayout.WEST);add(text,BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(
                JList<? extends Path> list,Path value,int index,
                boolean selected,boolean focus){
            boolean directory=Files.isDirectory(value);
            icon.setIcon(UIManager.getIcon(directory
                    ?"FileView.directoryIcon":"FileView.fileIcon"));
            name.setText(value.getFileName()==null
                    ?value.toString():value.getFileName().toString());
            details.setText(directory?"Folder":fileDetails(value));
            Color bg=selected?theme.accent():theme.panel();
            Color fg=selected?readable(theme.accent()):theme.text();
            setBackground(bg);setOpaque(true);
            name.setForeground(fg);
            details.setForeground(selected?fg:theme.muted());
            return this;
        }

        private static Color readable(Color c){
            double l=.2126*c.getRed()+.7152*c.getGreen()+.0722*c.getBlue();
            return l>150?new Color(20,22,24):Color.WHITE;
        }
    }
}
