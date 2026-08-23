package com.wtm.ui;

import com.wtm.config.AppConfig;
import com.wtm.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Dynamically generated operations/holiday announcement.
 *
 * The card is never stored as a media file. It exists only while its calendar
 * group is within the configured announcement window and disappears
 * automatically after the group's final date.
 */
public final class OperationsAnnouncementSlidePanel extends JPanel {
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static final DateTimeFormatter SHORT_RANGE =
            DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a");

    public OperationsAnnouncementSlidePanel(
            AppConfig config,
            OperationAnnouncement announcement,
            LocalDate today
    ){
        setLayout(new BorderLayout(0,20));
        setBorder(new EmptyBorder(28,40,28,40));
        setBackground(Theme.panel());

        JLabel title=new JLabel(
                titleFor(announcement,today),
                SwingConstants.CENTER
        );
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,36));
        title.setForeground(titleColor(announcement));

        JLabel state=new JLabel(
                stateText(announcement,today),
                SwingConstants.CENTER
        );
        state.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
        state.setForeground(Theme.muted());

        JPanel header=new JPanel(new GridLayout(2,1,0,4));
        header.setOpaque(false);
        header.add(title);
        header.add(state);

        JPanel rows=new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));

        for(OperationEvent event:announcement.events()){
            /*
             * Multi-day entries are rendered as one block per remaining
             * calendar day. Past days are intentionally omitted, so a
             * Monday-Friday temporary schedule naturally shrinks as the week
             * progresses without requiring separate calendar records.
             */
            LocalDate firstVisible=event.startDate().isBefore(today)
                    ?today
                    :event.startDate();

            for(LocalDate day=firstVisible;
                    !day.isAfter(event.endDate());
                    day=day.plusDays(1)){
                rows.add(eventRow(config,event,day));
                rows.add(Box.createVerticalStrut(10));
            }
        }

        JScrollPane scroll=new JScrollPane(rows);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel footer=new JPanel(new GridLayout(2,1,0,6));
        footer.setOpaque(false);

        if(announcement.normalOperationsResume()!=null){
            JLabel resume=new JLabel(
                    "Normal operations resume "
                    +announcement.normalOperationsResume().format(LONG_DATE)
                    +" • "+formatTime(config.normalOperatingStart)
                    +" – "+formatTime(config.normalOperatingEnd),
                    SwingConstants.CENTER
            );
            resume.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            resume.setForeground(Theme.text());
            footer.add(resume);
        }else{
            footer.add(new JLabel(""));
        }

        JLabel normal=new JLabel(
                "Normal operating hours: "
                +formatTime(config.normalOperatingStart)
                +" – "+formatTime(config.normalOperatingEnd),
                SwingConstants.CENTER
        );
        normal.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));
        normal.setForeground(Theme.muted());
        footer.add(normal);

        add(header,BorderLayout.NORTH);
        add(scroll,BorderLayout.CENTER);
        add(footer,BorderLayout.SOUTH);
    }

    private static JComponent eventRow(
            AppConfig config,
            OperationEvent event,
            LocalDate day
    ){
        RoundedPanel card=new RoundedPanel(18);
        card.setLayout(new BorderLayout(18,0));
        card.setBackground(Theme.panel2());
        card.putClientProperty("outlineColor",Theme.border());
        card.setBorder(new EmptyBorder(14,18,14,18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE,124));

        JPanel badge=new JPanel(new GridLayout(2,1,0,2));
        badge.setOpaque(true);
        badge.setBackground(typeColor(event.type()));
        badge.setBorder(new EmptyBorder(8,10,8,10));
        badge.setPreferredSize(new Dimension(165,76));

        JLabel dayLabel=new JLabel(
                day.getDayOfWeek().toString(),
                SwingConstants.CENTER
        );
        dayLabel.setForeground(bestText(typeColor(event.type())));
        dayLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));

        JLabel typeLabel=new JLabel(
                event.type().display().toUpperCase(),
                SwingConstants.CENTER
        );
        typeLabel.setForeground(bestText(typeColor(event.type())));
        typeLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));

        badge.add(dayLabel);
        badge.add(typeLabel);

        JLabel main=new JLabel(
                "<html><b style='font-size:16px'>"+escape(event.name())+"</b>"
                +"<br>"+escape(day.format(LONG_DATE))
                +detailsHtml(config,event)
                +"</html>"
        );
        main.setForeground(Theme.text());
        main.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));

        card.add(badge,BorderLayout.WEST);
        card.add(main,BorderLayout.CENTER);
        return card;
    }

    private static String detailsHtml(AppConfig config,OperationEvent event){
        if(event.type()==OperationType.FULL_CLOSURE)
            return "<br><b>CLOSED — No regular operations</b>";

        if(event.startTime()==null||event.endTime()==null)
            return "";

        String hours=formatTime(event.startTime())
                +" – "+formatTime(event.endTime());

        String change=changeText(
                config.normalOperatingStart,
                config.normalOperatingEnd,
                event.startTime(),
                event.endTime()
        );

        String label=event.type()==OperationType.LIMITED_SERVICE
                ?"Limited service hours"
                :"Modified operating hours";

        return "<br><b>"+label+": "+hours+"</b>"
                +(change.isBlank()?"":"<br>"+escape(change));
    }

    private static String changeText(
            LocalTime normalStart,
            LocalTime normalEnd,
            LocalTime eventStart,
            LocalTime eventEnd
    ){
        long startDiff=ChronoUnit.MINUTES.between(eventStart,normalStart);
        long endDiff=ChronoUnit.MINUTES.between(normalEnd,eventEnd);

        if(startDiff>0 && eventEnd.equals(normalEnd))
            return "Report "+durationText(startDiff)+" earlier than normal.";

        if(startDiff<0 && eventEnd.equals(normalEnd))
            return "Report "+durationText(-startDiff)+" later than normal.";

        if(endDiff<0 && eventStart.equals(normalStart))
            return "Operations end "+durationText(-endDiff)+" earlier than normal.";

        if(endDiff>0 && eventStart.equals(normalStart))
            return "Operations end "+durationText(endDiff)+" later than normal.";

        if(!eventStart.equals(normalStart)||!eventEnd.equals(normalEnd))
            return "Temporary schedule differs from normal operating hours.";

        return "";
    }

    private static String durationText(long minutes){
        if(minutes%60==0){
            long hours=minutes/60;
            return hours+" hour"+(hours==1?"":"s");
        }
        if(minutes>60){
            long hours=minutes/60;
            long rem=minutes%60;
            return hours+"h "+rem+"m";
        }
        return minutes+" minutes";
    }

    private static String titleFor(
            OperationAnnouncement announcement,
            LocalDate today
    ){
        boolean one=announcement.events().size()==1;
        OperationEvent first=announcement.events().get(0);

        if(one && first.type()==OperationType.MODIFIED_HOURS)
            return "MODIFIED OPERATING HOURS";

        if(one && first.type()==OperationType.LIMITED_SERVICE)
            return "LIMITED SERVICE NOTICE";

        if(one && first.type()==OperationType.FULL_CLOSURE)
            return "FACILITY CLOSURE NOTICE";

        return "HOLIDAY & OPERATIONS SCHEDULE";
    }

    private static String stateText(
            OperationAnnouncement announcement,
            LocalDate today
    ){
        if(today.isBefore(announcement.startDate())){
            long days=ChronoUnit.DAYS.between(today,announcement.startDate());
            return "UPCOMING • Begins in "+days+" day"+(days==1?"":"s")
                    +" • "+rangeText(
                            announcement.startDate(),
                            announcement.endDate());
        }

        if(today.equals(announcement.endDate())
                && !announcement.startDate().equals(announcement.endDate()))
            return "IN EFFECT • FINAL DAY";

        if(!today.isBefore(announcement.startDate())
                && !today.isAfter(announcement.endDate()))
            return "OPERATIONS SCHEDULE IN EFFECT";

        return "";
    }

    private static Color titleColor(OperationAnnouncement announcement){
        boolean closure=announcement.events().stream()
                .anyMatch(e->e.type()==OperationType.FULL_CLOSURE);
        boolean limited=announcement.events().stream()
                .anyMatch(e->e.type()==OperationType.LIMITED_SERVICE);

        if(closure)return Theme.danger();
        if(limited)return Theme.warn();
        return Theme.accent();
    }

    private static Color typeColor(OperationType type){
        return switch(type){
            case FULL_CLOSURE -> Theme.danger();
            case LIMITED_SERVICE -> Theme.warn();
            case MODIFIED_HOURS -> Theme.accent();
        };
    }

    private static Color bestText(Color bg){
        double lum=(0.299*bg.getRed()+0.587*bg.getGreen()+0.114*bg.getBlue())/255.0;
        return lum>.62?Color.BLACK:Color.WHITE;
    }

    private static String rangeText(LocalDate start,LocalDate end){
        if(start.equals(end))
            return start.format(LONG_DATE);

        if(start.getYear()==end.getYear()
                && start.getMonth()==end.getMonth())
            return start.format(DateTimeFormatter.ofPattern("MMMM d"))
                    +"–"+end.format(DateTimeFormatter.ofPattern("d, yyyy"));

        return start.format(SHORT_RANGE)
                +" – "+end.format(
                        DateTimeFormatter.ofPattern("MMM d, yyyy"));
    }

    private static String formatTime(LocalTime time){
        return time==null?"":time.format(TIME);
    }

    private static String escape(String s){
        return s==null?"":s.replace("&","&amp;")
                .replace("<","&lt;").replace(">","&gt;");
    }
}
