package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.map.TileMapPanel;
import com.wtm.model.CelebrationConfig;
import com.wtm.model.OperationAnnouncement;
import com.wtm.service.OperationsCalendarService;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Main dashboard showcase rotation for map, managed media, calendar and recognition. */
public final class MainShowcasePanel extends RoundedPanel {
    private final CardLayout cards=new CardLayout();
    private final JPanel deck=new JPanel(cards);
    private final TileMapPanel map;
    private AppConfig config;
    private boolean automaticSevereWeatherActive;
    private final List<String> cardIds=new ArrayList<>();
    private int currentIndex=0;
    private javax.swing.Timer rotationTimer;
    private Consumer<Boolean> celebrationListener=active->{};
    private final Set<String> celebrationCardIds=new HashSet<>();
    private final Set<String> celebrationEffectCardIds=new HashSet<>();
    private LocalDate builtForDate=LocalDate.now();
    private String builtSignature="";

    public MainShowcasePanel(AppConfig config,TileMapPanel map){
        super(20);
        this.config=Objects.requireNonNull(config);
        this.map=Objects.requireNonNull(map);
        putClientProperty("surfaceRole","map");
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        setMinimumSize(new Dimension(0,0));
        deck.setOpaque(false);
        add(deck,BorderLayout.CENTER);
        rebuild();
    }

    public void updateConfig(AppConfig newConfig){
        this.config=Objects.requireNonNull(newConfig);
        rebuild();
    }

    public void disposeShowcase(){
        if(rotationTimer!=null){rotationTimer.stop();rotationTimer=null;}
        celebrationListener=active->{};
    }

    public void setCelebrationListener(Consumer<Boolean> listener){
        celebrationListener=listener==null?active->{}:listener;
    }

    public void setAutomaticSevereWeatherActive(boolean active){
        automaticSevereWeatherActive=active;
        if(active&&config.severeWeatherMapPriority)showMap();
        updateRotationState();
    }

    private void rebuild(){
        if(rotationTimer!=null)rotationTimer.stop();
        deck.removeAll(); cardIds.clear(); celebrationCardIds.clear(); celebrationEffectCardIds.clear();
        currentIndex=0;

        deck.add(map,"MAP"); cardIds.add("MAP");

        if(config.mainShowcaseMediaEnabled){
            int index=0;
            for(Path file:mediaFiles()){
                JComponent media=createMediaComponent(file);
                if(media!=null){
                    String id="MEDIA_"+(index++)+"_"+safeFileName(file);
                    deck.add(media,id); cardIds.add(id);
                }
            }
        }

        LocalDate today=LocalDate.now(); builtForDate=today;
        int operationIndex=0;
        for(OperationAnnouncement announcement:OperationsCalendarService.announcements(config,today)){
            String id="OPERATIONS_"+(operationIndex++)+"_"+announcement.startDate()+"_"+announcement.endDate();
            deck.add(new OperationsAnnouncementSlidePanel(config,announcement,today),id);
            cardIds.add(id);
        }

        if(config.celebrationsEnabled){
            int index=0;
            for(CelebrationConfig c:config.celebrations){
                boolean birthday=c.birthdayToday(today);
                boolean anniversary=c.anniversaryToday(today)&&c.anniversaryYears(today)>0;
                if(birthday||anniversary){
                    String id="CELEBRATION_DATE_"+(index++)+"_"+today;
                    deck.add(new CelebrationSlidePanel(c,birthday,anniversary,today),id);
                    cardIds.add(id); celebrationCardIds.add(id);
                    if(c.celebrationEffect())celebrationEffectCardIds.add(id);
                }
                if(c.employeeOfMonthToday(today)){
                    String id="CELEBRATION_EOM_"+(index++)+"_"+today.getYear()+"_"+today.getMonthValue();
                    deck.add(new EmployeeOfMonthSlidePanel(c,java.time.YearMonth.from(today)),id);
                    cardIds.add(id); celebrationCardIds.add(id);
                    if(c.celebrationEffect())celebrationEffectCardIds.add(id);
                }
            }
        }

        builtSignature=contentSignature(today);
        cards.show(deck,"MAP");
        updateRotationState();
        revalidate(); repaint();
    }

    public void refreshDateDrivenContent(){
        LocalDate today=LocalDate.now();
        String signature=contentSignature(today);
        if(!today.equals(builtForDate)||!signature.equals(builtSignature))rebuild();
    }

    private void updateRotationState(){
        if(rotationTimer!=null)rotationTimer.stop();
        boolean severeLock=automaticSevereWeatherActive&&config.severeWeatherMapPriority;
        if(cardIds.size()<=1||severeLock){
            if(severeLock)showMap();
            return;
        }
        rotationTimer=new javax.swing.Timer(Math.max(5,config.mainShowcaseIntervalSeconds)*1000,e->advance());
        rotationTimer.setInitialDelay(Math.max(5,config.mainShowcaseIntervalSeconds)*1000);
        rotationTimer.setRepeats(true);
        rotationTimer.start();
    }

    private void advance(){
        if(automaticSevereWeatherActive&&config.severeWeatherMapPriority){showMap();return;}
        if(cardIds.size()<=1)return;
        currentIndex=(currentIndex+1)%cardIds.size();
        String id=cardIds.get(currentIndex);
        cards.show(deck,id);
        celebrationListener.accept(celebrationCardIds.contains(id)&&celebrationEffectCardIds.contains(id));
    }

    private void showMap(){
        currentIndex=0; cards.show(deck,"MAP"); celebrationListener.accept(false);
    }

    private List<Path> mediaFiles(){
        LinkedHashSet<Path> files=new LinkedHashSet<>();
        files.addAll(MediaService.list(MediaCategory.ANNOUNCEMENTS));
        files.addAll(MediaService.list(MediaCategory.EMPLOYEE_SHOWCASE));
        return files.stream()
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(p->p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER))
                .limit(500)
                .toList();
    }

    private String contentSignature(LocalDate today){
        StringBuilder b=new StringBuilder();
        b.append(today).append('|').append(config.mainShowcaseMediaEnabled)
                .append('|').append(config.operationsAnnouncementsEnabled)
                .append('|').append(config.celebrationsEnabled)
                .append('|').append(config.mainShowcaseIntervalSeconds);
        for(Path p:mediaFiles()){
            b.append('|').append(p.toAbsolutePath().normalize());
            try{b.append(':').append(Files.size(p)).append(':').append(Files.getLastModifiedTime(p).toMillis());}
            catch(IOException ignored){b.append(":?");}
        }
        for(OperationAnnouncement a:OperationsCalendarService.announcements(config,today))
            b.append("|OP:").append(a.startDate()).append(':').append(a.endDate()).append(':').append(a.normalOperationsResume());
        if(config.celebrations!=null)
            for(CelebrationConfig c:config.celebrations)b.append("|C:").append(c.toString());
        return Integer.toHexString(b.toString().hashCode())+":"+b.length();
    }

    private String safeFileName(Path p){
        return p==null||p.getFileName()==null?"media":p.getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_");
    }

    private JComponent createMediaComponent(Path file){
        try{
            BufferedImage image=OrientedImageLoader.load(file);
            if(image==null)return null;
            JPanel panel=new JPanel(new BorderLayout()); panel.setBackground(Color.BLACK);
            JLabel imageView=new JLabel(){
                @Override protected void paintComponent(Graphics g){
                    super.paintComponent(g);
                    int w=getWidth(),h=getHeight(); if(w<=0||h<=0)return;
                    double scale=Math.min(w/(double)image.getWidth(),h/(double)image.getHeight());
                    int dw=(int)Math.round(image.getWidth()*scale),dh=(int)Math.round(image.getHeight()*scale);
                    int x=(w-dw)/2,y=(h-dh)/2;
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(image,x,y,dw,dh,null); g2.dispose();
                }
            };
            panel.add(imageView,BorderLayout.CENTER); return panel;
        }catch(IOException ex){
            System.err.println("Unable to load showcase media file: "+safeFileName(file));
            return null;
        }
    }
}
