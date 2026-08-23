package com.wtm.ui;

import com.wtm.model.CelebrationConfig;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Dedicated Employee of the Month recognition card.
 *
 * Kept visually distinct from birthdays and anniversaries so monthly
 * recognition feels intentional rather than like a renamed birthday slide.
 */
public final class EmployeeOfMonthSlidePanel extends JPanel {
    public EmployeeOfMonthSlidePanel(CelebrationConfig config,YearMonth month){
        setLayout(new BorderLayout(18,18));
        setBorder(BorderFactory.createEmptyBorder(30,42,32,42));
        setBackground(Theme.panel());

        JPanel header=new JPanel(new BorderLayout(16,0));
        header.setOpaque(false);

        JLabel trophy=new JLabel(new TrophyIcon(58,Theme.accent()));
        trophy.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel title=new JLabel("EMPLOYEE OF THE MONTH",SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,38));
        title.setForeground(Theme.accent());

        JLabel monthLabel=new JLabel(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase(),
                SwingConstants.CENTER
        );
        monthLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        monthLabel.setForeground(Theme.muted());

        JPanel titleStack=new JPanel(new GridLayout(2,1,0,4));
        titleStack.setOpaque(false);
        titleStack.add(title);
        titleStack.add(monthLabel);

        header.add(trophy,BorderLayout.WEST);
        header.add(titleStack,BorderLayout.CENTER);
        header.add(Box.createHorizontalStrut(58),BorderLayout.EAST);

        JPanel center=new JPanel(new BorderLayout());
        center.setOpaque(false);

        JComponent portrait=portrait(config.photoAsset(),config.name());
        portrait.setPreferredSize(new Dimension(300,300));
        center.add(portrait,BorderLayout.CENTER);

        JLabel name=new JLabel(config.name(),SwingConstants.CENTER);
        name.setFont(new Font(Font.SANS_SERIF,Font.BOLD,52));
        name.setForeground(Theme.text());

        JLabel message=new JLabel(
                "Congratulations and thank you for your outstanding contribution!",
                SwingConstants.CENTER
        );
        message.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,24));
        message.setForeground(Theme.muted());

        JPanel south=new JPanel(new GridLayout(2,1,0,10));
        south.setOpaque(false);
        south.add(name);
        south.add(message);

        add(header,BorderLayout.NORTH);
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
                            if(w<=0||h<=0)return;

                            double scale=Math.min(
                                    w/(double)image.getWidth(),
                                    h/(double)image.getHeight()
                            );
                            int dw=(int)Math.round(image.getWidth()*scale);
                            int dh=(int)Math.round(image.getHeight()*scale);
                            int x=(w-dw)/2,y=(h-dh)/2;

                            Graphics2D g2=(Graphics2D)g.create();
                            g2.setRenderingHint(
                                    RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                            );
                            g2.setRenderingHint(
                                    RenderingHints.KEY_RENDERING,
                                    RenderingHints.VALUE_RENDER_QUALITY
                            );
                            g2.drawImage(image,x,y,dw,dh,null);
                            g2.dispose();
                        }
                    };
                }
            }
        }catch(Exception ignored){}

        JPanel avatar=new JPanel(new GridBagLayout());
        avatar.setBackground(Theme.panel2());
        avatar.setBorder(BorderFactory.createLineBorder(Theme.border(),2,true));

        JLabel initials=new JLabel(initials(name));
        initials.setFont(new Font(Font.SANS_SERIF,Font.BOLD,82));
        initials.setForeground(Theme.accent());
        avatar.add(initials);
        return avatar;
    }

    private static String initials(String name){
        if(name==null||name.isBlank())return "★";
        String[] parts=name.trim().split("\\s+");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<Math.min(2,parts.length);i++)
            if(!parts[i].isBlank())
                out.append(Character.toUpperCase(parts[i].charAt(0)));
        return out.isEmpty()?"★":out.toString();
    }

    private static final class TrophyIcon implements Icon{
        private final int size;
        private final Color color;

        TrophyIcon(int size,Color color){
            this.size=size;
            this.color=color;
        }

        @Override public int getIconWidth(){return size;}
        @Override public int getIconHeight(){return size;}

        @Override
        public void paintIcon(Component c,Graphics g0,int x,int y){
            Graphics2D g=(Graphics2D)g0.create();
            g.translate(x,y);
            double scale=size/64.0;
            g.scale(scale,scale);
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.setColor(color);

            // Cup bowl.
            Path2D cup=new Path2D.Double();
            cup.moveTo(18,10);
            cup.lineTo(46,10);
            cup.lineTo(43,29);
            cup.curveTo(41,39,35,43,32,43);
            cup.curveTo(29,43,23,39,21,29);
            cup.closePath();
            g.fill(cup);

            // Handles.
            g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(7,13,19,23,90,180,Arc2D.OPEN));
            g.draw(new Arc2D.Double(38,13,19,23,270,180,Arc2D.OPEN));

            // Stem/base.
            g.fillRoundRect(29,40,6,10,3,3);
            g.fillRoundRect(20,49,24,6,5,5);

            // Star accent.
            g.setColor(new Color(255,255,255,205));
            Polygon star=new Polygon();
            for(int i=0;i<10;i++){
                double a=-Math.PI/2+i*Math.PI/5;
                double r=(i%2==0)?7:3.2;
                star.addPoint(
                        (int)Math.round(32+Math.cos(a)*r),
                        (int)Math.round(24+Math.sin(a)*r)
                );
            }
            g.fill(star);
            g.dispose();
        }
    }
}
