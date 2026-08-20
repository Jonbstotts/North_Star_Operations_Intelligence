package com.wtm.ui;

import com.wtm.model.SportsConfig;
import com.wtm.model.SportsGame;
import com.wtm.net.HttpService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Compact upcoming-games schedule for a configured team.
 *
 * The nearest event receives the largest visual treatment; later events are
 * presented as concise schedule rows so a standard dashboard block can show
 * useful weekend information during the work week.
 */
public final class SportsSchedulePanel extends JPanel {
    private static final ExecutorService LOADER=Executors.newFixedThreadPool(2,r->{
        Thread t=new Thread(r,"sports-logo-loader");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String,ImageIcon> CACHE=new ConcurrentHashMap<>();

    private final HttpService http;
    private final JPanel content=new JPanel();

    public SportsSchedulePanel(HttpService http){
        this.http=http;
        setOpaque(false);
        setLayout(new BorderLayout());

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        add(content,BorderLayout.CENTER);

        showLoading();
    }

    /**
     * Sports content is rebuilt asynchronously after the main dashboard theme
     * pass. Apply theme colors at label creation time so newly inserted text
     * never falls back to the platform default (black on many systems).
     */
    private static void stylePrimaryLabel(JLabel label){
        label.setForeground(Theme.text());
    }

    private static void styleSecondaryLabel(JLabel label){
        label.setForeground(Theme.muted());
    }

    public void updateSchedule(List<SportsGame> games,SportsConfig cfg){
        content.removeAll();

        if(games==null){
            showLoading();
            return;
        }

        if(games.isEmpty()){
            JLabel none=new JLabel(
                    "<html><b>No upcoming games returned</b><br>"
                  + escape(cfg.teamName())
                  + "<br><span style='font-size:10px'>Schedule will refresh automatically.</span></html>"
            );
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            stylePrimaryLabel(none);
            content.add(none);
            finishLayout();
            return;
        }

        SportsGame next=games.get(0);
        content.add(primaryGame(next,cfg));

        for(int i=1;i<Math.min(3,games.size());i++){
            content.add(Box.createVerticalStrut(5));
            content.add(scheduleRow(games.get(i),cfg));
        }

        finishLayout();
    }

    private JComponent primaryGame(SportsGame game,SportsConfig cfg){
        JPanel p=new JPanel(new BorderLayout(8,0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel configuredLogo=new JLabel();
        configuredLogo.setPreferredSize(new Dimension(44,44));
        configuredLogo.setHorizontalAlignment(SwingConstants.CENTER);

        boolean configuredHome=teamMatches(cfg,game.homeTeam());
        String configuredBadge=configuredHome?game.homeBadgeUrl():game.awayBadgeUrl();
        if(cfg.showLogos()) loadLogo(configuredLogo,configuredBadge);

        String opponent=configuredHome?game.awayTeam():game.homeTeam();
        String venue=configuredHome?"vs":"at";

        JLabel text=new JLabel(
                "<html><b>"+when(game.startTime())+"</b>"
              + "<br><span style='font-size:13px'>"+venue+" "+escape(shortName(opponent))+"</span>"
              + "<br><span style='font-size:10px'>"+escape(game.league())+"</span></html>"
        );
        stylePrimaryLabel(text);

        p.add(configuredLogo,BorderLayout.WEST);
        p.add(text,BorderLayout.CENTER);
        return p;
    }

    private JComponent scheduleRow(SportsGame game,SportsConfig cfg){
        boolean configuredHome=teamMatches(cfg,game.homeTeam());
        String opponent=configuredHome?game.awayTeam():game.homeTeam();
        String venue=configuredHome?"vs":"at";

        JLabel row=new JLabel(
                "<html><span style='font-size:10px'><b>"
              + compactWhen(game.startTime())
              + "</b> • "+venue+" "+escape(shortName(opponent))
              + "</span></html>"
        );
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleSecondaryLabel(row);
        return row;
    }

    private void showLoading(){
        content.removeAll();
        JLabel loading=new JLabel("Loading upcoming schedule…");
        loading.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleSecondaryLabel(loading);
        content.add(loading);
        finishLayout();
    }

    private void finishLayout(){
        content.revalidate();
        content.repaint();
        revalidate();
        repaint();
    }

    private void loadLogo(JLabel label,String url){
        if(url==null||url.isBlank()){
            label.setIcon(null);
            return;
        }

        ImageIcon cached=CACHE.get(url);
        if(cached!=null){
            label.setIcon(cached);
            return;
        }

        LOADER.submit(()->{
            try{
                byte[] bytes=http.getBytes(url);
                BufferedImage img=ImageIO.read(new ByteArrayInputStream(bytes));
                if(img==null)return;

                Image scaled=img.getScaledInstance(42,42,Image.SCALE_SMOOTH);
                ImageIcon icon=new ImageIcon(scaled);
                CACHE.put(url,icon);
                SwingUtilities.invokeLater(()->label.setIcon(icon));
            }catch(Exception ignored){}
        });
    }

    private static boolean teamMatches(SportsConfig cfg,String name){
        if(name==null)return false;
        if(name.equalsIgnoreCase(cfg.teamName()))return true;
        return !cfg.teamName().isBlank()
                && name.toLowerCase().contains(cfg.teamName().toLowerCase());
    }

    private static String when(Instant time){
        if(time==null)return "Upcoming game";
        ZonedDateTime z=time.atZone(ZoneId.systemDefault());
        String day=z.toLocalDate().equals(LocalDate.now())
                ?"TODAY"
                :z.format(DateTimeFormatter.ofPattern("EEE MMM d"));
        return day+" • "+z.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    private static String compactWhen(Instant time){
        if(time==null)return "TBD";
        return time.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE MMM d • h:mm a"));
    }

    private static String shortName(String s){
        if(s==null||s.isBlank())return "TBD";
        return s.length()>24?s.substring(0,23)+"…":s;
    }

    private static String escape(String s){
        return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
