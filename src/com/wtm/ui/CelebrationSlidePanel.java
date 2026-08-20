package com.wtm.ui;

import com.wtm.model.CelebrationConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.LocalDate;

/** Generated birthday/work-anniversary card for the Main Showcase. */
public final class CelebrationSlidePanel extends JPanel {
    public CelebrationSlidePanel(
            CelebrationConfig config,
            boolean birthday,
            boolean anniversary,
            LocalDate today
    ){
        setLayout(new BorderLayout(18,18));
        setBorder(BorderFactory.createEmptyBorder(36,42,36,42));
        setBackground(Theme.panel());

        String heading;
        if(birthday && anniversary) heading="HAPPY BIRTHDAY & WORK ANNIVERSARY!";
        else if(birthday) heading="HAPPY BIRTHDAY!";
        else heading="WORK ANNIVERSARY";

        JLabel title=new JLabel(heading,SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,38));
        title.setForeground(Theme.accent());

        JLabel name=new JLabel(config.name(),SwingConstants.CENTER);
        name.setFont(new Font(Font.SANS_SERIF,Font.BOLD,52));
        name.setForeground(Theme.text());

        JPanel center=new JPanel(new BorderLayout(18,18));
        center.setOpaque(false);

        JComponent portrait=portrait(config.photoAsset(),config.name());
        portrait.setPreferredSize(new Dimension(280,280));
        center.add(portrait,BorderLayout.CENTER);

        String message;
        if(anniversary){
            int years=config.anniversaryYears(today);
            message=years>0
                    ?"Celebrating "+years+" Year"+(years==1?"":"s")+" With the Team"
                    :"Thank You for Being Part of the Team";
        }else{
            message="Wishing You a Great Day!";
        }
        JLabel messageLabel=new JLabel(message,SwingConstants.CENTER);
        messageLabel.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,26));
        messageLabel.setForeground(Theme.muted());

        JPanel south=new JPanel(new GridLayout(2,1,0,10));
        south.setOpaque(false);
        south.add(name);
        south.add(messageLabel);

        add(title,BorderLayout.NORTH);
        add(center,BorderLayout.CENTER);
        add(south,BorderLayout.SOUTH);
    }

    private static JComponent portrait(String asset,String name){
        try{
            Path path=MediaService.resolve(MediaCategory.EMPLOYEE_PHOTOS,asset);
            if(path!=null){
                BufferedImage image=OrientedImageLoader.load(path);
                if(image!=null){
                    return new JPanel(){
                        {setOpaque(false);}
                        @Override protected void paintComponent(Graphics g){
                            super.paintComponent(g);
                            int w=getWidth(),h=getHeight();
                            double scale=Math.min(w/(double)image.getWidth(),h/(double)image.getHeight());
                            int dw=(int)(image.getWidth()*scale),dh=(int)(image.getHeight()*scale);
                            int x=(w-dw)/2,y=(h-dh)/2;
                            Graphics2D g2=(Graphics2D)g.create();
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                            g2.drawImage(image,x,y,dw,dh,null);
                            g2.dispose();
                        }
                    };
                }
            }
        }catch(Exception ignored){}

        String initials=initials(name);
        JPanel avatar=new JPanel(new GridBagLayout());
        avatar.setBackground(Theme.panel2());
        avatar.setBorder(BorderFactory.createLineBorder(Theme.border(),2,true));
        JLabel l=new JLabel(initials);
        l.setFont(new Font(Font.SANS_SERIF,Font.BOLD,76));
        l.setForeground(Theme.accent());
        avatar.add(l);
        return avatar;
    }

    private static String initials(String name){
        if(name==null||name.isBlank())return "★";
        String[] parts=name.trim().split("\\s+");
        StringBuilder b=new StringBuilder();
        for(int i=0;i<Math.min(2,parts.length);i++)
            if(!parts[i].isBlank())b.append(Character.toUpperCase(parts[i].charAt(0)));
        return b.isEmpty()?"★":b.toString();
    }
}
