package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.map.TileMapPanel;
import com.wtm.model.CelebrationConfig;
import com.wtm.model.OperationAnnouncement;
import com.wtm.service.OperationsCalendarService;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Configurable large-format content region.
 *
 * The live map is always the first item. Uploaded media, automatically
 * generated Operations Calendar notices, and team-recognition cards are added
 * to the same site-defined rotation when applicable.
 */
public final class MainShowcasePanel extends RoundedPanel {
    private final CardLayout cards=new CardLayout();
    private final JPanel deck=new JPanel(cards);
    private final TileMapPanel map;

    private AppConfig config;
    private boolean severeWeatherActive;
    private final List<String> cardIds=new ArrayList<>();
    private int currentIndex=0;
    private Timer rotationTimer;
    private Consumer<Boolean> celebrationListener=active->{};
    private final Set<String> celebrationCardIds=new java.util.HashSet<>();
    private final Set<String> celebrationEffectCardIds=new java.util.HashSet<>();
    private LocalDate builtForDate=LocalDate.now();

    public MainShowcasePanel(AppConfig config, TileMapPanel map){
        super(20);
        this.config=config;
        this.map=map;

        putClientProperty("surfaceRole","map");
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        setMinimumSize(new Dimension(0,0));

        deck.setOpaque(false);
        add(deck,BorderLayout.CENTER);
        rebuild();
    }

    public void updateConfig(AppConfig newConfig){
        this.config=newConfig;
        rebuild();
    }

    /**
     * Stops timers/listeners owned by this showcase before the dashboard
     * discards it during a Settings/theme/layout rebuild.
     *
     * Without this lifecycle cleanup, an old invisible showcase could keep its
     * Swing rotation Timer alive and later fire a celebration callback even
     * though its celebration card was no longer on screen.
     */
    public void disposeShowcase(){
        if(rotationTimer!=null){
            rotationTimer.stop();
            rotationTimer=null;
        }

        // Break the callback reference as an additional safeguard against an
        // obsolete showcase triggering dashboard overlay effects.
        celebrationListener=active->{};
    }

    /**
     * Called whenever a celebration card rotates into view. The host workspace uses
     * the boolean to decide whether this team member's celebration effect is enabled.
     */
    public void setCelebrationListener(Consumer<Boolean> listener){
        this.celebrationListener=listener==null?active->{}:listener;
    }

    public void setSevereWeatherActive(boolean active){
        severeWeatherActive=active;
        if(active && config.severeWeatherMapPriority) showMap();
        updateRotationState();
    }

    private void rebuild(){
        if(rotationTimer!=null) rotationTimer.stop();

        deck.removeAll();
        cardIds.clear();
        currentIndex=0;

        deck.add(map,"MAP");
        cardIds.add("MAP");

        if(config.mainShowcaseMediaEnabled){
            int index=0;
            for(Path file:mediaFiles()){
                JComponent media=createMediaComponent(file);
                if(media!=null){
                    String id="MEDIA_"+index++;
                    deck.add(media,id);
                    cardIds.add(id);
                }
            }
        }

        LocalDate today=LocalDate.now();
        builtForDate=today;

        /*
         * Operations Calendar announcements are generated dynamically. Connected
         * date ranges are already grouped by OperationsCalendarService, so one
         * holiday period becomes one showcase card.
         */
        int operationIndex=0;
        for(OperationAnnouncement announcement:
                OperationsCalendarService.announcements(config,today)){
            String id="OPERATIONS_"+(operationIndex++)+"_"
                    +announcement.startDate()+"_"+announcement.endDate();

            deck.add(
                    new OperationsAnnouncementSlidePanel(
                            config,announcement,today),
                    id
            );
            cardIds.add(id);
        }

        celebrationCardIds.clear();
        celebrationEffectCardIds.clear();
        if(config.celebrationsEnabled){
            int index=0;
            for(CelebrationConfig c:config.celebrations){
                boolean birthday=c.birthdayToday(today);
                boolean anniversary=c.anniversaryToday(today)
                        && c.anniversaryYears(today)>0;

                // Birthday/anniversary recognition remains date-specific and
                // may share one card if both occur on the same date.
                if(birthday||anniversary){
                    String id="CELEBRATION_DATE_"+(index++)+"_"+today;
                    CelebrationSlidePanel slide=
                            new CelebrationSlidePanel(c,birthday,anniversary,today);
                    deck.add(slide,id);
                    cardIds.add(id);
                    celebrationCardIds.add(id);
                    if(c.celebrationEffect())
                        celebrationEffectCardIds.add(id);
                }

                // Employee of the Month is intentionally a separate card and
                // remains active for the entire assigned month.
                if(c.employeeOfMonthToday(today)){
                    String id="CELEBRATION_EOM_"+(index++)+"_"
                            +today.getYear()+"_"+today.getMonthValue();

                    EmployeeOfMonthSlidePanel slide=
                            new EmployeeOfMonthSlidePanel(
                                    c,
                                    java.time.YearMonth.from(today)
                            );

                    deck.add(slide,id);
                    cardIds.add(id);
                    celebrationCardIds.add(id);
                    if(c.celebrationEffect())
                        celebrationEffectCardIds.add(id);
                }
            }
        }

        cards.show(deck,"MAP");
        updateRotationState();
        revalidate();
        repaint();
    }

    /**
     * Called periodically by the host workspace. This keeps Operations Calendar
     * announcements, birthdays, work anniversaries, and month-based recognition
     * correct while a display remains running across date boundaries.
     */
    public void refreshDateDrivenContent(){
        LocalDate today=LocalDate.now();
        if(!today.equals(builtForDate)) rebuild();
    }

    private void updateRotationState(){
        if(rotationTimer!=null) rotationTimer.stop();

        boolean severeLock=severeWeatherActive && config.severeWeatherMapPriority;
        boolean rotate=cardIds.size()>1 && !severeLock;

        if(!rotate){
            if(severeLock) showMap();
            return;
        }

        rotationTimer=new Timer(Math.max(5,config.mainShowcaseIntervalSeconds)*1000,e->advance());
        rotationTimer.setRepeats(true);
        rotationTimer.start();
    }

    private void advance(){
        if(severeWeatherActive && config.severeWeatherMapPriority){
            showMap();
            return;
        }
        if(cardIds.size()<=1) return;
        currentIndex=(currentIndex+1)%cardIds.size();
        String id=cardIds.get(currentIndex);
        cards.show(deck,id);

        if(celebrationCardIds.contains(id)){
            celebrationListener.accept(celebrationEffectCardIds.contains(id));
        }
    }

    private void showMap(){
        currentIndex=0;
        cards.show(deck,"MAP");
    }

    private List<Path> mediaFiles(){
        java.util.LinkedHashSet<Path> files=new java.util.LinkedHashSet<>();
        files.addAll(MediaService.list(MediaCategory.ANNOUNCEMENTS));
        files.addAll(MediaService.list(MediaCategory.EMPLOYEE_SHOWCASE));
        return files.stream().limit(200).toList();
    }

    private JComponent createMediaComponent(Path file){
        try{
            BufferedImage image=OrientedImageLoader.load(file);
            if(image==null) return null;

            JPanel panel=new JPanel(new BorderLayout());
            panel.setBackground(Color.BLACK);

            JLabel imageView=new JLabel(){
                @Override
                protected void paintComponent(Graphics g){
                    super.paintComponent(g);
                    int w=getWidth(),h=getHeight();
                    if(w<=0||h<=0) return;

                    double scale=Math.min(w/(double)image.getWidth(),h/(double)image.getHeight());
                    int dw=(int)Math.round(image.getWidth()*scale);
                    int dh=(int)Math.round(image.getHeight()*scale);
                    int x=(w-dw)/2,y=(h-dh)/2;

                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(image,x,y,dw,dh,null);
                    g2.dispose();
                }
            };

            /*
             * Announcement media intentionally uses the entire showcase region.
             * Filenames are implementation details and are not displayed on the TV.
             */
            panel.add(imageView,BorderLayout.CENTER);
            return panel;
        }catch(IOException ex){
            System.err.println(
                    "Unable to load showcase media file: "
                    +file.getFileName()
            );
            return null;
        }
    }
}
