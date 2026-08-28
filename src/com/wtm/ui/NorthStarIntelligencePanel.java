package com.wtm.ui;

import com.wtm.ai.NorthStarIntelligenceService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Full canonical NorthStar Intelligence workspace. */
public final class NorthStarIntelligencePanel extends JPanel {
    private final NorthStarIntelligenceService service=NorthStarIntelligenceService.get();
    private final JTextArea conversation=new JTextArea();
    private final JTextArea question=new JTextArea(3,40);
    private final JLabel status=new JLabel();
    private final DefaultListModel<Path> libraryModel=new DefaultListModel<>();
    private final JList<Path> library=new JList<>(libraryModel);
    private final JTextField url=new JTextField();
    private final JTextField model=new JTextField();
    private final JCheckBox operational=new JCheckBox("Use eligible NorthStar operational data automatically");

    public NorthStarIntelligencePanel(){
        setLayout(new BorderLayout(0,12));setBackground(Theme.bg());setBorder(new EmptyBorder(14,16,16,16));
        add(header(),BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Ask NorthStar",askTab());tabs.addTab("Knowledge Library",knowledgeTab());tabs.addTab("Local AI Settings",settingsTab());add(tabs,BorderLayout.CENTER);
        refreshLibrary();service.addLibraryListener(this::refreshLibraryLater);refreshStatusAsync();ThemeStyler.apply(this,Theme.active());
    }

    private JComponent header(){JPanel p=new JPanel(new BorderLayout(12,0));p.setOpaque(false);JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));JLabel title=new JLabel("NorthStar Intelligence");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));JLabel subtitle=new JLabel("Local, evidence-grounded operational intelligence");subtitle.setForeground(Theme.muted());subtitle.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));words.add(title);words.add(Box.createVerticalStrut(3));words.add(subtitle);p.add(words,BorderLayout.WEST);status.setForeground(Theme.muted());status.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));p.add(status,BorderLayout.EAST);return p;}

    private JComponent askTab(){JPanel page=new JPanel(new BorderLayout(0,10));page.setOpaque(false);page.setBorder(new EmptyBorder(10,6,6,6));conversation.setEditable(false);conversation.setLineWrap(true);conversation.setWrapStyleWord(true);conversation.setText("NorthStar Intelligence is ready. Ask about imported operational data or documents in the local knowledge library.\n");page.add(new JScrollPane(conversation),BorderLayout.CENTER);JPanel bottom=new JPanel(new BorderLayout(8,8));bottom.setOpaque(false);question.setLineWrap(true);question.setWrapStyleWord(true);bottom.add(new JScrollPane(question),BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));actions.setOpaque(false);JButton clear=new JButton("Clear conversation");JButton ask=new JButton("Ask NorthStar");ask.putClientProperty("primaryAction",Boolean.TRUE);clear.addActionListener(e->conversation.setText(""));ask.addActionListener(e->ask(ask));actions.add(clear);actions.add(ask);bottom.add(actions,BorderLayout.SOUTH);page.add(bottom,BorderLayout.SOUTH);return page;}

    private void ask(JButton button){if(!AuthorizationService.allowed(Permission.AI_ASSISTANT)){message("Your account does not have NorthStar Intelligence permission.",JOptionPane.WARNING_MESSAGE);return;}String q=question.getText().trim();if(q.isBlank())return;question.setText("");conversation.append("\nYOU\n"+q+"\n\nNORTHSTAR\nThinking…\n");button.setEnabled(false);new SwingWorker<NorthStarIntelligenceService.Answer,Void>(){@Override protected NorthStarIntelligenceService.Answer doInBackground()throws Exception{return service.ask(q);}@Override protected void done(){button.setEnabled(true);try{var a=get();replaceLastThinking(a.text(),a.sources());}catch(Exception ex){replaceLastThinking("I could not complete that request.\n\n"+rootMessage(ex),List.of());}}}.execute();}
    private void replaceLastThinking(String answer,List<String> sources){String text=conversation.getText();int pos=text.lastIndexOf("Thinking…");if(pos>=0)text=text.substring(0,pos)+answer;else text+="\n"+answer;if(sources!=null&&!sources.isEmpty()){text+="\n\nSources\n";for(String source:sources)text+="• "+source+"\n";}conversation.setText(text+"\n");conversation.setCaretPosition(conversation.getDocument().getLength());}

    private JComponent knowledgeTab(){JPanel page=new JPanel(new BorderLayout(0,10));page.setOpaque(false);page.setBorder(new EmptyBorder(10,6,6,6));JLabel note=new JLabel("Local knowledge library • files remain inside NorthStar application data");note.setForeground(Theme.muted());page.add(note,BorderLayout.NORTH);library.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object value,int index,boolean selected,boolean focus){super.getListCellRendererComponent(l,value,index,selected,focus);if(value instanceof Path p)setText(p.getFileName().toString());return this;}});page.add(new JScrollPane(library),BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));actions.setOpaque(false);JButton add=new JButton("+ Add Documents");JButton remove=new JButton("Remove Selected");JButton folder=new JButton("Show Library Folder");boolean admin=AuthorizationService.allowed(Permission.AI_KNOWLEDGE_ADMIN);add.setEnabled(admin);remove.setEnabled(admin);add.addActionListener(e->chooseDocuments());remove.addActionListener(e->removeSelected());folder.addActionListener(e->showFolder());actions.add(add);actions.add(remove);actions.add(folder);page.add(actions,BorderLayout.SOUTH);return page;}
    private void chooseDocuments(){JFileChooser chooser=new JFileChooser();chooser.setMultiSelectionEnabled(true);if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;importFiles(Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList());}
    private void importFiles(List<Path> files){if(!AuthorizationService.allowed(Permission.AI_KNOWLEDGE_ADMIN))return;int ok=0;StringBuilder errors=new StringBuilder();for(Path p:files){try{service.importDocument(p);ok++;}catch(Exception ex){errors.append(p.getFileName()).append(": ").append(rootMessage(ex)).append('\n');}}refreshLibrary();message(ok+" document(s) added."+(errors.isEmpty()?"":"\n\n"+errors),errors.isEmpty()?JOptionPane.INFORMATION_MESSAGE:JOptionPane.WARNING_MESSAGE);}
    private void removeSelected(){Path p=library.getSelectedValue();if(p==null)return;try{service.removeDocument(p);refreshLibrary();}catch(Exception ex){message(rootMessage(ex),JOptionPane.ERROR_MESSAGE);}}
    private void showFolder(){try{Desktop.getDesktop().open(service.knowledgeRoot().toFile());}catch(Exception ex){message(service.knowledgeRoot().toString(),JOptionPane.INFORMATION_MESSAGE);}}

    private JComponent settingsTab(){JPanel page=new JPanel(new BorderLayout());page.setOpaque(false);page.setBorder(new EmptyBorder(16,8,8,8));JPanel form=new JPanel(new GridBagLayout());form.setOpaque(false);GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(7,7,7,7);g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;url.setText(service.ollamaUrl());model.setText(service.model());operational.setSelected(service.useOperationalData());int y=0;y=row(form,g,y,"Ollama URL",url);y=row(form,g,y,"Local model",model);g.gridx=0;g.gridy=y++;g.gridwidth=2;form.add(operational,g);g.gridwidth=1;JLabel employee=new JLabel(AuthorizationService.allowed(Permission.AI_EMPLOYEE_METRICS)?"Employee metrics access: ALLOWED":"Employee metrics access: BLOCKED");employee.setForeground(AuthorizationService.allowed(Permission.AI_EMPLOYEE_METRICS)?Theme.accent():Theme.muted());g.gridx=0;g.gridy=y++;g.gridwidth=2;form.add(employee,g);JTextArea privacy=new JTextArea("The default Ollama endpoint is loopback-only. Remote plain HTTP endpoints are rejected; use HTTPS for a remote AI host. Operational answers are grounded in NorthStar evidence before model synthesis.");privacy.setEditable(false);privacy.setOpaque(false);privacy.setLineWrap(true);privacy.setWrapStyleWord(true);g.gridy=y++;form.add(privacy,g);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));actions.setOpaque(false);JButton save=new JButton("Save Local AI Settings");JButton test=new JButton("Test Ollama");save.addActionListener(e->{try{service.saveSettings(url.getText(),model.getText(),operational.isSelected());refreshStatusAsync();}catch(Exception ex){message(rootMessage(ex),JOptionPane.ERROR_MESSAGE);}});test.addActionListener(e->refreshStatusAsync());actions.add(save);actions.add(test);g.gridy=y++;form.add(actions,g);page.add(form,BorderLayout.NORTH);return page;}

    private static int row(JPanel p,GridBagConstraints g,int y,String label,JComponent field){g.gridy=y;g.gridwidth=1;g.gridx=0;g.weightx=.25;p.add(new JLabel(label),g);g.gridx=1;g.weightx=.75;field.setPreferredSize(new Dimension(460,42));p.add(field,g);return y+1;}
    private void refreshLibrary(){libraryModel.clear();for(Path p:service.knowledgeFiles())libraryModel.addElement(p);}private void refreshLibraryLater(){SwingUtilities.invokeLater(this::refreshLibrary);}
    private void refreshStatusAsync(){status.setText("● Checking local AI…");new SwingWorker<NorthStarIntelligenceService.Status,Void>(){@Override protected NorthStarIntelligenceService.Status doInBackground(){return service.testConnection();}@Override protected void done(){try{var s=get();status.setText((s.online()?"● ":"○ ")+s.detail());status.setForeground(s.online()?Theme.accent():Theme.muted());}catch(Exception ignored){status.setText("○ Local AI offline");}}}.execute();}
    private void message(String text,int type){JOptionPane.showMessageDialog(this,text,"NorthStar Intelligence",type);}private static String rootMessage(Throwable t){Throwable c=t;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage();}
}
