package com.wtm.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Transparent decorative overlay layer.
 *
 * Holiday effects intentionally use different animation systems instead of a
 * single generic particle animation:
 *
 *  • Christmas/Winter Frost: drawn snowflakes + frosted screen edges.
 *  • Halloween: layered, horizontally rolling fog banks.
 *  • Independence Day: rocket launches and radial firework bursts.
 *  • Thanksgiving: drifting autumn leaves.
 *  • Valentine's Day: floating hearts.
 *  • St. Patrick's Day: drifting shamrocks.
 *  • Celebration slides: short confetti burst.
 *
 * The layer never intercepts mouse input and is immediately suppressed during
 * automatic severe-weather map priority.
 */
public final class OverlayEffectsPanel extends JComponent {
    private final Random random=new Random();

    private final List<Particle> particles=new ArrayList<>();
    private final List<Firework> fireworks=new ArrayList<>();
    private final List<FogBank> fogBanks=new ArrayList<>();

    private final javax.swing.Timer timer;

    private AppTheme theme=AppTheme.DARK;
    private boolean themeEffectsEnabled=true;
    private boolean severeSuppressed=false;
    private String intensity="LOW";
    private String performanceMode="AUTOMATIC";
    private long nextFireworkAt=0;

    // Adaptive rendering state. Only ambient decoration is scaled; celebration
    // confetti and active fireworks retain priority.
    private double adaptiveScale=1.0;
    private double averagePaintMs=0.0;
    private int tickCounter=0;

    // Frost is visually static for a given size/intensity, so cache it instead
    // of rebuilding four gradients every animation frame.
    private BufferedImage frostCache;
    private int frostCacheW=-1;
    private int frostCacheH=-1;
    private String frostCacheIntensity="";

    public OverlayEffectsPanel(){
        setOpaque(false);
        setFocusable(false);

        timer=new javax.swing.Timer(33,e->tick()); // ~30 fps for smooth motion
        timer.start();
    }

    @Override
    public boolean contains(int x,int y){
        return false;
    }

    public void configure(
            AppTheme theme,
            boolean enabled,
            String intensity,
            String performanceMode
    ){
        this.theme=theme==null?AppTheme.DARK:theme;
        this.themeEffectsEnabled=enabled;
        this.intensity=intensity==null?"LOW":intensity.toUpperCase();
        this.performanceMode=performanceMode==null
                ?"AUTOMATIC":performanceMode.toUpperCase();

        adaptiveScale=initialScale();
        averagePaintMs=0.0;
        tickCounter=0;
        updateTimerDelay();
        invalidateStaticCaches();

        particles.clear();
        fireworks.clear();
        fogBanks.clear();
        nextFireworkAt=0;

        if(themeEffectsEnabled && this.theme==AppTheme.HALLOWEEN)
            createFogBanks();

        repaint();
    }

    private void invalidateStaticCaches(){
        frostCache=null;
        frostCacheW=-1;
        frostCacheH=-1;
        frostCacheIntensity="";
    }

    private double initialScale(){
        return switch(performanceMode){
            case "HIGH_QUALITY" -> 1.0;
            case "BALANCED" -> .78;
            case "PERFORMANCE" -> .55;
            default -> 1.0;
        };
    }

    private int targetFrameDelay(){
        return switch(performanceMode){
            case "HIGH_QUALITY" -> 33;  // ~30 FPS
            case "BALANCED" -> 40;      // ~25 FPS
            case "PERFORMANCE" -> 50;   // ~20 FPS
            default -> adaptiveScale<.65?50:(adaptiveScale<.88?40:33);
        };
    }

    private void updateTimerDelay(){
        int delay=targetFrameDelay();
        if(timer.getDelay()!=delay){
            timer.setDelay(delay);
            timer.setInitialDelay(delay);
        }
    }

    private boolean confettiActive(){
        return countKind("CONFETTI")>0;
    }

    /**
     * Ambient holiday density is the first thing reduced when the screen is
     * busy. Priority animation (confetti/fireworks) is never discarded.
     */
    private double ambientScale(){
        double scale=adaptiveScale;
        if(confettiActive()) scale*=.58;
        return Math.max(.32,Math.min(1.0,scale));
    }

    private int scaledAmbient(int value){
        return Math.max(1,(int)Math.round(value*ambientScale()));
    }

    private void recordPaintCost(double ms){
        averagePaintMs=averagePaintMs==0.0
                ?ms
                :(averagePaintMs*.90+ms*.10);

        if(!"AUTOMATIC".equals(performanceMode))
            return;

        double old=adaptiveScale;

        if(averagePaintMs>22.0)
            adaptiveScale=.48;
        else if(averagePaintMs>16.0)
            adaptiveScale=.62;
        else if(averagePaintMs>11.5)
            adaptiveScale=.78;
        else if(averagePaintMs<8.5)
            adaptiveScale=Math.min(1.0,adaptiveScale+.025);

        if(Math.abs(old-adaptiveScale)>.001)
            updateTimerDelay();
    }

    public void setSevereSuppressed(boolean suppressed){
        severeSuppressed=suppressed;

        if(suppressed){
            particles.clear();
            fireworks.clear();
            fogBanks.clear();
        }else if(themeEffectsEnabled && theme==AppTheme.HALLOWEEN){
            createFogBanks();
        }

        repaint();
    }

    /**
     * Releases one finite confetti shower.
     *
     * Nothing here is time-limited. Each piece remains in the overlay until its
     * normal physics carry it beyond the bottom/side of the display. This lets
     * the shower finish naturally even after the slideshow advances to another
     * card.
     */
    public void celebrate(){
        if(severeSuppressed)return;

        int amount=count(70,115,165);
        for(int i=0;i<amount;i++)
            particles.add(Particle.confetti(random,getWidth(),getHeight()));

        repaint();
    }

    private void tick(){
        if(!isShowing())return;
        tickCounter++;

        if(severeSuppressed){
            if(!particles.isEmpty()||!fireworks.isEmpty()||!fogBanks.isEmpty()){
                particles.clear();
                fireworks.clear();
                fogBanks.clear();
                repaint();
            }
            return;
        }

        /*
         * Holiday animation and celebration confetti are intentionally
         * independent. Confetti continues to fall while the slideshow rotates
         * and while any seasonal overlay continues behind/around it.
         */
        if(themeEffectsEnabled){
            switch(theme){
                case CHRISTMAS, WINTER_FROST -> maintainSnow();
                case HALLOWEEN -> updateFog();
                case INDEPENDENCE -> updateFireworks();
                case THANKSGIVING -> maintainSimpleParticles("LEAF");
                case VALENTINE -> maintainValentine();
                case ST_PATRICKS -> maintainStPatricks();
                default -> clearHolidayCollections();
            }
        }else{
            clearHolidayCollections();
        }

        trimAmbientToBudget();
        updateParticles();

        if(hasAnimatedVisuals())
            repaint();
    }

    private boolean hasAnimatedVisuals(){
        if(!particles.isEmpty()||!fireworks.isEmpty()||!fogBanks.isEmpty())
            return true;

        if(!themeEffectsEnabled)
            return false;

        return switch(theme){
            case CHRISTMAS, WINTER_FROST, HALLOWEEN, INDEPENDENCE,
                 THANKSGIVING, VALENTINE, ST_PATRICKS -> true;
            default -> false;
        };
    }

    private void maintainSnow(){
        fireworks.clear();
        fogBanks.clear();

        int target=scaledAmbient(count(45,80,125));
        while(countKind("SNOWFLAKE")<target)
            particles.add(Particle.snowflake(random,getWidth(),getHeight(),intensity));
    }

    private void maintainSimpleParticles(String kind){
        fireworks.clear();
        fogBanks.clear();

        int target=scaledAmbient(count(22,38,58));
        while(countKind(kind)<target)
            particles.add(Particle.theme(random,getWidth(),getHeight(),kind));
    }

    private void maintainValentine(){
        fireworks.clear();
        fogBanks.clear();

        int hearts=scaledAmbient(count(18,30,44));
        int petals=scaledAmbient(count(10,18,28));

        while(countKind("HEART")<hearts)
            particles.add(Particle.theme(random,getWidth(),getHeight(),"HEART"));

        while(countKind("PETAL")<petals)
            particles.add(Particle.theme(random,getWidth(),getHeight(),"PETAL"));
    }

    private void maintainStPatricks(){
        fireworks.clear();
        fogBanks.clear();

        int shamrocks=scaledAmbient(count(18,30,44));
        int gold=scaledAmbient(count(12,22,34));

        while(countKind("SHAMROCK")<shamrocks)
            particles.add(Particle.theme(random,getWidth(),getHeight(),"SHAMROCK"));

        while(countKind("GOLD_SPARK")<gold)
            particles.add(Particle.theme(random,getWidth(),getHeight(),"GOLD_SPARK"));
    }

    private void clearHolidayCollections(){
        fireworks.clear();
        fogBanks.clear();
        removeHolidayParticles();
    }

    /** Removes seasonal particles without touching an in-progress confetti shower. */
    private void removeHolidayParticles(){
        particles.removeIf(p->!"CONFETTI".equals(p.kind));
    }

    private int countKind(String kind){
        int n=0;
        for(Particle p:particles)
            if(kind.equals(p.kind))n++;
        return n;
    }

    /**
     * Enforces a hard ambient-particle budget after the adaptive scale changes.
     * Celebration confetti is never removed by this budget.
     */
    private void trimAmbientToBudget(){
        int budget=(int)Math.round(180*ambientScale());
        int ambient=0;

        for(Particle p:particles)
            if(!"CONFETTI".equals(p.kind)) ambient++;

        if(ambient<=budget) return;

        int remove=ambient-budget;
        for(Iterator<Particle> it=particles.iterator();it.hasNext() && remove>0;){
            Particle p=it.next();
            if(!"CONFETTI".equals(p.kind)){
                it.remove();
                remove--;
            }
        }
    }

    private void updateParticles(){
        for(Iterator<Particle> it=particles.iterator();it.hasNext();){
            Particle p=it.next();
            p.update(getWidth(),getHeight());

            if(p.dead)
                it.remove();
        }
    }

    // -----------------------------------------------------------------
    // Halloween fog
    // -----------------------------------------------------------------

    private void createFogBanks(){
        fogBanks.clear();

        int count=scaledAmbient(count(6,8,11));
        for(int i=0;i<count;i++)
            fogBanks.add(FogBank.create(random,getWidth(),getHeight(),i,count));
    }

    private void updateFog(){
        removeHolidayParticles();
        fireworks.clear();

        if(fogBanks.isEmpty())
            createFogBanks();

        int divisor=switch(performanceMode){
            case "PERFORMANCE" -> 3;
            case "BALANCED" -> 2;
            default -> ("AUTOMATIC".equals(performanceMode)&&adaptiveScale<.70)?2:1;
        };

        if(tickCounter%divisor==0){
            for(FogBank fog:fogBanks)
                fog.update(getWidth(),getHeight());
        }
    }

    // -----------------------------------------------------------------
    // Independence Day fireworks
    // -----------------------------------------------------------------

    private void updateFireworks(){
        removeHolidayParticles();
        fogBanks.clear();

        long now=System.currentTimeMillis();

        if(nextFireworkAt==0)
            nextFireworkAt=now+700;

        int maxFireworks=switch(performanceMode){
            case "PERFORMANCE" -> Math.max(1,count(1,2,2));
            case "BALANCED" -> Math.max(1,count(2,2,3));
            default -> count(2,3,4);
        };
        if(now>=nextFireworkAt && fireworks.size()<maxFireworks){
            fireworks.add(Firework.launch(random,getWidth(),getHeight()));

            int minDelay=switch(intensity){
                case "HIGH" -> 700;
                case "MEDIUM" -> 1100;
                default -> 1500;
            };
            nextFireworkAt=now+minDelay+random.nextInt(650);
        }

        for(Iterator<Firework> it=fireworks.iterator();it.hasNext();){
            Firework f=it.next();
            f.update(random,getWidth(),getHeight());

            if(f.dead)
                it.remove();
        }
    }

    // -----------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        if(severeSuppressed)return;

        long paintStart=System.nanoTime();

        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        boolean quality="HIGH_QUALITY".equals(performanceMode)
                || ("AUTOMATIC".equals(performanceMode)&&adaptiveScale>=.78);
        g2.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                quality?RenderingHints.VALUE_RENDER_QUALITY:RenderingHints.VALUE_RENDER_SPEED
        );

        if(themeEffectsEnabled){
            if(theme==AppTheme.HALLOWEEN){
                paintHalloweenAtmosphere(g2);
                paintFog(g2);
                paintHalloweenLights(g2);
            }

            if(theme==AppTheme.CHRISTMAS){
                paintChristmasLights(g2);
            }

            if(theme==AppTheme.INDEPENDENCE)
                paintFireworks(g2);
        }

        paintParticles(g2);

        if(themeEffectsEnabled
                && (theme==AppTheme.CHRISTMAS || theme==AppTheme.WINTER_FROST)){
            paintFrost(g2);
        }

        g2.dispose();

        double paintMs=(System.nanoTime()-paintStart)/1_000_000.0;
        recordPaintCost(paintMs);
    }

    private void paintParticles(Graphics2D g2){
        for(Particle p:particles){
            if("CONFETTI".equals(p.kind)){
                g2.setColor(p.color);
                g2.rotate(p.rotation,p.x,p.y);
                g2.fillRoundRect(
                        (int)p.x,(int)p.y,
                        p.size,Math.max(3,p.size/2),
                        2,2
                );
                g2.rotate(-p.rotation,p.x,p.y);
                continue;
            }

            switch(p.kind){
                case "SNOWFLAKE" -> drawSnowflake(g2,p);
                case "LEAF" -> drawLeaf(g2,p);
                case "HEART" -> drawPolishedHeart(g2,p);
                case "PETAL" -> drawValentinePetal(g2,p);
                case "SHAMROCK" -> drawPolishedShamrock(g2,p);
                case "GOLD_SPARK" -> drawGoldSpark(g2,p);
            }
        }
    }

    private void drawSnowflake(Graphics2D g2,Particle p){
        Graphics2D f=(Graphics2D)g2.create();

        f.translate(p.x,p.y);
        f.rotate(p.rotation);

        float alpha=Math.max(.25f,Math.min(.95f,p.alpha));
        f.setComposite(AlphaComposite.SrcOver.derive(alpha));
        f.setColor(new Color(245,251,255));

        float stroke=Math.max(1f,p.size/9f);
        f.setStroke(new BasicStroke(
                stroke,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        double radius=p.size/2.0;

        boolean detailed=!"PERFORMANCE".equals(performanceMode)
                && !("AUTOMATIC".equals(performanceMode)&&adaptiveScale<.65);

        // Six-point snowflake. Branch details are omitted under heavy load.
        for(int arm=0;arm<6;arm++){
            double angle=Math.PI*2*arm/6.0;
            double ex=Math.cos(angle)*radius;
            double ey=Math.sin(angle)*radius;

            f.draw(new Line2D.Double(0,0,ex,ey));

            if(!detailed) continue;

            double branchStart=radius*.55;
            double bx=Math.cos(angle)*branchStart;
            double by=Math.sin(angle)*branchStart;
            double branchLen=radius*.25;

            double a1=angle+Math.PI*.72;
            double a2=angle-Math.PI*.72;

            f.draw(new Line2D.Double(
                    bx,by,
                    bx+Math.cos(a1)*branchLen,
                    by+Math.sin(a1)*branchLen
            ));
            f.draw(new Line2D.Double(
                    bx,by,
                    bx+Math.cos(a2)*branchLen,
                    by+Math.sin(a2)*branchLen
            ));
        }

        f.dispose();
    }

    private void paintFrost(Graphics2D g2){
        int w=getWidth();
        int h=getHeight();
        if(w<=0||h<=0)return;

        if(frostCache==null
                || frostCacheW!=w
                || frostCacheH!=h
                || !intensity.equals(frostCacheIntensity)){
            frostCache=buildFrostCache(w,h);
            frostCacheW=w;
            frostCacheH=h;
            frostCacheIntensity=intensity;
        }

        g2.drawImage(frostCache,0,0,null);
    }

    private BufferedImage buildFrostCache(int w,int h){
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D f=image.createGraphics();

        int depth=switch(intensity){
            case "HIGH" -> 54;
            case "MEDIUM" -> 42;
            default -> 30;
        };

        f.setPaint(new GradientPaint(
                0,0,new Color(225,244,255,125),
                0,depth,new Color(225,244,255,0)));
        f.fillRect(0,0,w,depth);

        f.setPaint(new GradientPaint(
                0,h,new Color(225,244,255,120),
                0,h-depth,new Color(225,244,255,0)));
        f.fillRect(0,h-depth,w,depth);

        f.setPaint(new GradientPaint(
                0,0,new Color(225,244,255,90),
                depth,0,new Color(225,244,255,0)));
        f.fillRect(0,0,depth,h);

        f.setPaint(new GradientPaint(
                w,0,new Color(225,244,255,90),
                w-depth,0,new Color(225,244,255,0)));
        f.fillRect(w-depth,0,depth,h);

        f.dispose();
        return image;
    }

    private void paintFog(Graphics2D g2){
        for(FogBank fog:fogBanks)
            fog.paint(g2);
    }

    /**
     * Orange/purple perimeter string lights for the Halloween theme.
     * A small sinusoidal brightness shift gives them a slow, tasteful twinkle.
     */
    /**
     * Full-screen low-contrast haze used behind the moving fog banks.
     *
     * This fills the gaps between individual banks and adds long, slowly
     * undulating streams so the screen reads as one continuous fog field.
     */
    private void paintHalloweenAtmosphere(Graphics2D g2){
        int w=getWidth();
        int h=getHeight();
        if(w<=0||h<=0)return;

        Graphics2D a=(Graphics2D)g2.create();

        long now=System.currentTimeMillis();
        double t=now/5200.0;

        // Vertical ground-haze gradient: nearly invisible in the upper third,
        // gradually denser toward the floor.
        LinearGradientPaint haze=new LinearGradientPaint(
                new Point2D.Double(0,h*.18),
                new Point2D.Double(0,h),
                new float[]{0f,.36f,.70f,1f},
                new Color[]{
                        new Color(185,190,205,0),
                        new Color(185,190,205,8),
                        new Color(178,185,198,18),
                        new Color(170,177,190,30)
                }
        );
        a.setPaint(haze);
        a.fillRect(0,0,w,h);

        // Long continuous wisps span almost the full display width.
        int streams=switch(intensity){
            case "HIGH" -> 7;
            case "MEDIUM" -> 5;
            default -> 4;
        };

        for(int row=0;row<streams;row++){
            double baseY=h*(.33+row*(.55/Math.max(1,streams-1)));
            double phase=t*(.18+row*.025)+row*1.37;

            Path2D upper=new Path2D.Double();
            Path2D lower=new Path2D.Double();

            int segments=18;
            for(int i=0;i<=segments;i++){
                double x=w*i/(double)segments;
                double y=baseY
                        +Math.sin(phase+i*.43)*16
                        +Math.sin(phase*.63+i*.77)*8;
                double thickness=34+row*6
                        +Math.sin(phase+i*.31)*6;

                if(i==0){
                    upper.moveTo(x,y-thickness/2);
                    lower.moveTo(x,y+thickness/2);
                }else{
                    upper.lineTo(x,y-thickness/2);
                    lower.lineTo(x,y+thickness/2);
                }
            }

            Path2D ribbon=new Path2D.Double();
            ribbon.append(upper,false);

            // Reverse lower path manually.
            for(int i=segments;i>=0;i--){
                double x=w*i/(double)segments;
                double y=baseY
                        +Math.sin(phase+i*.43)*16
                        +Math.sin(phase*.63+i*.77)*8;
                double thickness=34+row*6
                        +Math.sin(phase+i*.31)*6;
                ribbon.lineTo(x,y+thickness/2);
            }
            ribbon.closePath();

            int alpha=10+row*2;
            a.setColor(new Color(205,210,220,alpha));
            a.fill(ribbon);
        }

        // Very subtle vignette keeps fog feeling integrated at the edges.
        RadialGradientPaint vignette=new RadialGradientPaint(
                new Point2D.Double(w/2.0,h/2.0),
                (float)Math.max(w,h)*.72f,
                new float[]{0f,.72f,1f},
                new Color[]{
                        new Color(0,0,0,0),
                        new Color(5,7,10,0),
                        new Color(5,7,10,36)
                }
        );
        a.setPaint(vignette);
        a.fillRect(0,0,w,h);

        a.dispose();
    }

    /**
     * Red/green/warm-white perimeter lights for the Christmas theme.
     * The bulbs use independent glow and a slow asynchronous twinkle.
     */
    private void paintChristmasLights(Graphics2D g2){
        int w=getWidth();
        int h=getHeight();
        if(w<=0||h<=0)return;

        Graphics2D l=(Graphics2D)g2.create();
        l.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        int spacing=switch(intensity){
            case "HIGH" -> 36;
            case "MEDIUM" -> 44;
            default -> 54;
        };

        long now=System.currentTimeMillis();

        Color[] palette={
                new Color(230,48,58),
                new Color(45,182,88),
                new Color(255,220,120)
        };

        // Dark green wire just inside the frosted perimeter.
        l.setColor(new Color(35,75,50,175));
        l.setStroke(new BasicStroke(2.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        l.drawRoundRect(15,15,Math.max(1,w-31),Math.max(1,h-31),18,18);

        int index=0;
        for(int x=30;x<w-30;x+=spacing){
            drawChristmasBulb(l,x,18,index++,now,palette,false);
            drawChristmasBulb(l,x,h-19,index++,now,palette,true);
        }

        for(int y=32;y<h-32;y+=spacing){
            drawChristmasBulb(l,18,y,index++,now,palette,true);
            drawChristmasBulb(l,w-19,y,index++,now,palette,false);
        }

        l.dispose();
    }

    private static void drawChristmasBulb(
            Graphics2D g,
            int x,
            int y,
            int index,
            long now,
            Color[] palette,
            boolean phaseShift
    ){
        Color base=palette[(index+(phaseShift?1:0))%palette.length];
        double pulse=.76+.24*Math.sin(now/760.0+index*.61);
        int alpha=(int)(135+105*pulse);

        int halo=20;
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Double(x,y),
                halo,
                new float[]{0f,.30f,.70f,1f},
                new Color[]{
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),Math.min(190,alpha)),
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),78),
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),22),
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),0)
                }
        );

        g.setPaint(glow);
        g.fill(new Ellipse2D.Double(x-halo,y-halo,halo*2,halo*2));

        // Green socket/cap.
        g.setColor(new Color(28,78,46,230));
        g.fillRoundRect(x-4,y-8,8,7,3,3);

        // Tear-drop style bulb.
        Path2D bulb=new Path2D.Double();
        bulb.moveTo(x,y+10);
        bulb.curveTo(x-7,y+4,x-6,y-2,x,y-4);
        bulb.curveTo(x+6,y-2,x+7,y+4,x,y+10);
        bulb.closePath();

        GradientPaint fill=new GradientPaint(
                x,y-4,
                new Color(
                        Math.min(255,base.getRed()+28),
                        Math.min(255,base.getGreen()+28),
                        Math.min(255,base.getBlue()+28),
                        Math.min(255,alpha+55)
                ),
                x,y+10,
                new Color(base.getRed(),base.getGreen(),base.getBlue(),alpha)
        );
        g.setPaint(fill);
        g.fill(bulb);

        g.setColor(new Color(255,255,245,165));
        g.fillOval(x-2,y-1,3,5);
    }

    private void paintHalloweenLights(Graphics2D g2){
        int w=getWidth();
        int h=getHeight();
        if(w<=0||h<=0)return;

        Graphics2D l=(Graphics2D)g2.create();
        l.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        int spacing=switch(intensity){
            case "HIGH" -> 38;
            case "MEDIUM" -> 46;
            default -> 56;
        };

        long now=System.currentTimeMillis();
        Color orange=new Color(255,126,22);
        Color purple=new Color(170,76,235);

        // Wire around the screen perimeter.
        l.setColor(new Color(55,45,60,165));
        l.setStroke(new BasicStroke(2f));
        l.drawRoundRect(12,12,Math.max(1,w-25),Math.max(1,h-25),16,16);

        int index=0;

        // Top and bottom.
        for(int x=26;x<w-26;x+=spacing){
            drawHalloweenBulb(l,x,15,index++,now,orange,purple,false);
            drawHalloweenBulb(l,x,h-16,index++,now,orange,purple,true);
        }

        // Left and right.
        for(int y=28;y<h-28;y+=spacing){
            drawHalloweenBulb(l,15,y,index++,now,orange,purple,false);
            drawHalloweenBulb(l,w-16,y,index++,now,orange,purple,true);
        }

        l.dispose();
    }

    private static void drawHalloweenBulb(
            Graphics2D g,
            int x,
            int y,
            int index,
            long now,
            Color orange,
            Color purple,
            boolean alternatePhase
    ){
        Color base=((index+(alternatePhase?1:0))%2==0)?orange:purple;

        double pulse=.78+.22*Math.sin(now/650.0+index*.73);
        int alpha=(int)(125+105*pulse);

        // Glow halo.
        int halo=18;
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Double(x,y),
                halo,
                new float[]{0f,.35f,1f},
                new Color[]{
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),Math.min(180,alpha)),
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),65),
                        new Color(base.getRed(),base.getGreen(),base.getBlue(),0)
                }
        );

        g.setPaint(glow);
        g.fill(new Ellipse2D.Double(x-halo,y-halo,halo*2,halo*2));

        // Socket.
        g.setColor(new Color(45,42,47,220));
        g.fillRoundRect(x-4,y-7,8,6,3,3);

        // Bulb.
        g.setColor(new Color(
                base.getRed(),
                base.getGreen(),
                base.getBlue(),
                Math.min(255,alpha+55)
        ));
        g.fillOval(x-5,y-2,10,13);

        // Highlight.
        g.setColor(new Color(255,255,255,135));
        g.fillOval(x-2,y,3,4);
    }

    private void paintFireworks(Graphics2D g2){
        for(Firework f:fireworks)
            f.paint(g2);
    }

    private static void drawLeaf(Graphics2D g,Particle p){
        Graphics2D l=(Graphics2D)g.create();
        l.translate(p.x,p.y);
        l.rotate(p.rotation);

        l.setColor(p.color);
        l.setStroke(new BasicStroke(1.1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

        int variant=p.variant%3;

        if(variant==0){
            // Maple-inspired silhouette.
            Path2D maple=new Path2D.Double();
            double s=p.size;
            maple.moveTo(0,-s*.55);
            maple.lineTo(s*.13,-s*.24);
            maple.lineTo(s*.34,-s*.34);
            maple.lineTo(s*.25,-s*.08);
            maple.lineTo(s*.48,0);
            maple.lineTo(s*.18,s*.12);
            maple.lineTo(s*.20,s*.42);
            maple.lineTo(0,s*.24);
            maple.lineTo(-s*.20,s*.42);
            maple.lineTo(-s*.18,s*.12);
            maple.lineTo(-s*.48,0);
            maple.lineTo(-s*.25,-s*.08);
            maple.lineTo(-s*.34,-s*.34);
            maple.lineTo(-s*.13,-s*.24);
            maple.closePath();
            l.fill(maple);

        }else if(variant==1){
            // Oak-inspired lobed leaf.
            double s=p.size;
            Path2D oak=new Path2D.Double();
            oak.moveTo(0,-s*.55);
            oak.curveTo(s*.18,-s*.48,s*.28,-s*.32,s*.17,-s*.20);
            oak.curveTo(s*.38,-s*.18,s*.40,s*.02,s*.18,s*.04);
            oak.curveTo(s*.35,s*.14,s*.27,s*.31,s*.10,s*.28);
            oak.curveTo(s*.16,s*.43,s*.07,s*.52,0,s*.55);
            oak.curveTo(-s*.07,s*.52,-s*.16,s*.43,-s*.10,s*.28);
            oak.curveTo(-s*.27,s*.31,-s*.35,s*.14,-s*.18,s*.04);
            oak.curveTo(-s*.40,s*.02,-s*.38,-s*.18,-s*.17,-s*.20);
            oak.curveTo(-s*.28,-s*.32,-s*.18,-s*.48,0,-s*.55);
            oak.closePath();
            l.fill(oak);

        }else{
            // Simple pointed autumn leaf.
            double s=p.size;
            Path2D pointed=new Path2D.Double();
            pointed.moveTo(0,-s*.58);
            pointed.curveTo(s*.48,-s*.25,s*.42,s*.25,0,s*.55);
            pointed.curveTo(-s*.42,s*.25,-s*.48,-s*.25,0,-s*.58);
            pointed.closePath();
            l.fill(pointed);
        }

        // Central vein and short stem.
        l.setColor(new Color(105,65,32,145));
        l.draw(new Line2D.Double(0,-p.size*.42,0,p.size*.48));
        l.draw(new Line2D.Double(0,p.size*.42,0,p.size*.68));

        l.dispose();
    }

    private static void drawPolishedHeart(Graphics2D g,Particle p){
        Graphics2D h=(Graphics2D)g.create();
        h.translate(p.x,p.y);
        h.rotate(p.rotation);

        double size=p.size;

        // Outer glow.
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Double(0,0),
                (float)(size*.90),
                new float[]{0f,.55f,1f},
                new Color[]{
                        new Color(255,125,165,75),
                        new Color(235,80,130,30),
                        new Color(235,80,130,0)
                }
        );
        h.setPaint(glow);
        h.fill(new Ellipse2D.Double(-size,-size,size*2,size*2));

        Path2D heart=new Path2D.Double();
        heart.moveTo(0,size*.62);
        heart.curveTo(
                -size*.68,size*.10,
                -size*.72,-size*.48,
                -size*.30,-size*.55
        );
        heart.curveTo(
                -size*.08,-size*.60,
                0,-size*.38,
                0,-size*.24
        );
        heart.curveTo(
                0,-size*.38,
                size*.08,-size*.60,
                size*.30,-size*.55
        );
        heart.curveTo(
                size*.72,-size*.48,
                size*.68,size*.10,
                0,size*.62
        );
        heart.closePath();

        GradientPaint fill=new GradientPaint(
                0,(float)(-size*.55),
                new Color(255,152,184,215),
                0,(float)(size*.62),
                new Color(202,50,105,185)
        );
        h.setPaint(fill);
        h.fill(heart);

        h.setColor(new Color(255,225,235,145));
        h.setStroke(new BasicStroke(1.2f));
        h.draw(heart);

        // Small glossy highlight.
        h.setComposite(AlphaComposite.SrcOver.derive(.42f));
        h.setColor(Color.WHITE);
        h.fill(new Ellipse2D.Double(
                -size*.28,-size*.34,
                size*.18,size*.10
        ));

        h.dispose();
    }

    private static void drawValentinePetal(Graphics2D g,Particle p){
        Graphics2D v=(Graphics2D)g.create();
        v.translate(p.x,p.y);
        v.rotate(p.rotation);

        double s=p.size;
        Path2D petal=new Path2D.Double();
        petal.moveTo(0,-s*.48);
        petal.curveTo(s*.46,-s*.12,s*.34,s*.37,0,s*.52);
        petal.curveTo(-s*.34,s*.37,-s*.46,-s*.12,0,-s*.48);
        petal.closePath();

        v.setColor(p.color);
        v.fill(petal);

        v.setColor(new Color(255,235,242,110));
        v.setStroke(new BasicStroke(.9f));
        v.draw(new Line2D.Double(0,-s*.32,0,s*.30));

        v.dispose();
    }

    private static void drawPolishedShamrock(Graphics2D g,Particle p){
        Graphics2D s=(Graphics2D)g.create();
        s.translate(p.x,p.y);
        s.rotate(p.rotation);
        s.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        double size=p.size;

        /*
         * Soft emerald halo. Keeping the glow broad and low-opacity gives the
         * clover some separation from the dashboard without looking neon.
         */
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Double(0,-size*.05),
                (float)(size*1.10),
                new float[]{0f,.52f,1f},
                new Color[]{
                        new Color(85,225,125,62),
                        new Color(32,170,82,24),
                        new Color(25,145,70,0)
                }
        );
        s.setPaint(glow);
        s.fill(new Ellipse2D.Double(
                -size*1.10,-size*1.10,
                size*2.20,size*2.20
        ));

        /*
         * Heart-shaped leaflets create a much more recognizable shamrock than
         * simple circles. Three leaflets are rotated around one center point.
         */
        Shape leaflet=createShamrockLeaf(size*.58);

        Color top=new Color(94,225,126,230);
        Color bottom=new Color(18,126,61,218);

        for(int i=0;i<3;i++){
            Graphics2D leaf=(Graphics2D)s.create();

            double angle=Math.toRadians(-120+i*120);
            leaf.rotate(angle);
            leaf.translate(0,-size*.34);

            GradientPaint fill=new GradientPaint(
                    0,(float)(-size*.54),top,
                    0,(float)(size*.28),bottom
            );
            leaf.setPaint(fill);
            leaf.fill(leaflet);

            leaf.setColor(new Color(205,255,218,115));
            leaf.setStroke(new BasicStroke(
                    Math.max(.8f,(float)(size/19.0)),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            leaf.draw(leaflet);

            // Central vein.
            leaf.setColor(new Color(225,255,232,88));
            leaf.draw(new Line2D.Double(
                    0,size*.02,
                    0,-size*.34
            ));

            leaf.dispose();
        }

        // Slightly darker center ties all three leaflets together.
        s.setPaint(new RadialGradientPaint(
                new Point2D.Double(0,0),
                (float)Math.max(4,size*.24),
                new float[]{0f,1f},
                new Color[]{
                        new Color(63,190,91,230),
                        new Color(22,116,55,205)
                }
        ));
        s.fill(new Ellipse2D.Double(
                -size*.16,-size*.16,
                size*.32,size*.32
        ));

        /*
         * Curved tapered stem rather than a rectangle. It leans naturally
         * away from the clover and remains visible at small particle sizes.
         */
        Path2D stem=new Path2D.Double();
        stem.moveTo(-size*.07,size*.10);
        stem.curveTo(
                -size*.03,size*.34,
                size*.06,size*.56,
                size*.23,size*.82
        );
        stem.curveTo(
                size*.31,size*.80,
                size*.34,size*.73,
                size*.28,size*.67
        );
        stem.curveTo(
                size*.14,size*.46,
                size*.10,size*.25,
                size*.08,size*.08
        );
        stem.closePath();

        s.setPaint(new GradientPaint(
                0,(float)(size*.10),new Color(50,170,78,220),
                0,(float)(size*.82),new Color(18,104,50,205)
        ));
        s.fill(stem);

        // Small specular glint on one leaflet.
        s.setComposite(AlphaComposite.SrcOver.derive(.42f));
        s.setColor(Color.WHITE);
        s.fill(new Ellipse2D.Double(
                -size*.18,-size*.64,
                size*.13,size*.07
        ));

        s.dispose();
    }

    /**
     * Creates a symmetric heart-like clover leaflet with the pointed end at
     * the bottom (toward the shamrock center).
     */
    private static Shape createShamrockLeaf(double size){
        Path2D leaf=new Path2D.Double();

        leaf.moveTo(0,size*.48);
        leaf.curveTo(
                -size*.16,size*.24,
                -size*.56,size*.08,
                -size*.55,-size*.24
        );
        leaf.curveTo(
                -size*.54,-size*.54,
                -size*.18,-size*.67,
                0,-size*.40
        );
        leaf.curveTo(
                size*.18,-size*.67,
                size*.54,-size*.54,
                size*.55,-size*.24
        );
        leaf.curveTo(
                size*.56,size*.08,
                size*.16,size*.24,
                0,size*.48
        );
        leaf.closePath();

        return leaf;
    }

    private static void drawGoldSpark(Graphics2D g,Particle p){
        Graphics2D s=(Graphics2D)g.create();
        s.translate(p.x,p.y);
        s.rotate(p.rotation);

        int size=Math.max(4,p.size);
        RadialGradientPaint glow=new RadialGradientPaint(
                new Point2D.Double(0,0),
                size,
                new float[]{0f,.35f,1f},
                new Color[]{
                        new Color(255,239,135,220),
                        new Color(244,190,52,90),
                        new Color(244,190,52,0)
                }
        );
        s.setPaint(glow);
        s.fill(new Ellipse2D.Double(-size,-size,size*2,size*2));

        s.setColor(new Color(255,239,150,225));
        s.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        s.draw(new Line2D.Double(-size*.55,0,size*.55,0));
        s.draw(new Line2D.Double(0,-size*.55,0,size*.55));

        s.dispose();
    }

    private int count(int low,int medium,int high){
        return switch(intensity){
            case "HIGH" -> high;
            case "MEDIUM" -> medium;
            default -> low;
        };
    }

    // -----------------------------------------------------------------
    // Shared drifting particle
    // -----------------------------------------------------------------

    private static final class Particle {
        double x,y,dx,dy,rotation,dr,swayPhase,swaySpeed;
        int size;
        int variant;
        Color color;
        String kind;
        boolean dead;
        float alpha=1f;

        static Particle confetti(Random r,int w,int h){
            Particle p=new Particle();
            p.kind="CONFETTI";
            p.x=r.nextInt(Math.max(1,w));

            // Stagger pieces above the top edge. The deepest pieces enter later,
            // producing one complete cascading shower without replenishment.
            int spawnBand=Math.max(90,(int)(h*.42));
            p.y=-12-r.nextInt(spawnBand);

            p.dx=-1.25+r.nextDouble()*2.5;
            p.dy=2.15+r.nextDouble()*2.75;
            p.dr=-.15+r.nextDouble()*.30;
            p.rotation=r.nextDouble()*Math.PI;
            p.size=7+r.nextInt(8);

            Color[] colors={
                    new Color(255,70,70),
                    new Color(65,160,255),
                    new Color(255,205,55),
                    new Color(75,205,110),
                    new Color(210,90,230)
            };

            p.color=colors[r.nextInt(colors.length)];
            return p;
        }

        static Particle snowflake(Random r,int w,int h,String intensity){
            Particle p=new Particle();
            p.kind="SNOWFLAKE";
            p.x=r.nextInt(Math.max(1,w));
            p.y=-r.nextInt(Math.max(40,h+1));

            p.size=switch(intensity){
                case "HIGH" -> 8+r.nextInt(14);
                case "MEDIUM" -> 7+r.nextInt(12);
                default -> 6+r.nextInt(10);
            };

            // Larger flakes appear slightly closer and fall more quickly.
            double depth=p.size/20.0;
            p.dy=.55+depth*1.75+r.nextDouble()*.55;
            p.dx=-.15+r.nextDouble()*.30;

            p.swayPhase=r.nextDouble()*Math.PI*2;
            p.swaySpeed=.018+r.nextDouble()*.025;

            p.rotation=r.nextDouble()*Math.PI;
            p.dr=-.012+r.nextDouble()*.024;

            p.alpha=(float)(.45+r.nextDouble()*.5);
            p.color=Color.WHITE;

            return p;
        }

        static Particle theme(Random r,int w,int h,String kind){
            Particle p=new Particle();
            p.kind=kind;
            p.x=r.nextInt(Math.max(1,w));
            p.y=-r.nextInt(Math.max(20,h+1));
            p.dx=-.45+r.nextDouble()*.9;
            p.dy=.65+r.nextDouble()*1.5;
            p.size=7+r.nextInt(11);

            if("HEART".equals(kind)){
                p.dx=-.55+r.nextDouble()*1.1;
                p.dy=.35+r.nextDouble()*.78;
                p.size=9+r.nextInt(14);
                p.swayPhase=r.nextDouble()*Math.PI*2;
                p.swaySpeed=.016+r.nextDouble()*.026;
            }
            p.rotation=r.nextDouble()*Math.PI;
            p.dr=-.035+r.nextDouble()*.07;

            if("LEAF".equals(kind)){
                p.variant=r.nextInt(3);

                Color[] autumn={
                        new Color(201,82,36,190),
                        new Color(228,128,36,190),
                        new Color(239,173,54,190),
                        new Color(165,77,38,190),
                        new Color(183,123,48,190),
                        new Color(132,84,42,185)
                };
                p.color=autumn[r.nextInt(autumn.length)];

                // Leaves float more laterally and tumble more than other icons.
                p.dx=-.85+r.nextDouble()*1.7;
                p.dy=.45+r.nextDouble()*1.15;
                p.dr=-.065+r.nextDouble()*.13;
                p.size=10+r.nextInt(14);
                p.swayPhase=r.nextDouble()*Math.PI*2;
                p.swaySpeed=.018+r.nextDouble()*.030;
            }else{
                p.variant=r.nextInt(3);

                p.color=switch(kind){
                    case "HEART" -> {
                        Color[] hearts={
                                new Color(235,70,125,185),
                                new Color(245,112,150,175),
                                new Color(205,60,112,180)
                        };
                        yield hearts[r.nextInt(hearts.length)];
                    }
                    case "PETAL" -> {
                        p.size=5+r.nextInt(8);
                        p.dx=-.55+r.nextDouble()*1.1;
                        p.dy=.35+r.nextDouble()*.75;
                        p.dr=-.045+r.nextDouble()*.09;
                        yield r.nextBoolean()
                                ?new Color(255,180,202,145)
                                :new Color(244,125,164,135);
                    }
                    case "SHAMROCK" -> {
                        p.size=9+r.nextInt(12);
                        p.dx=-.55+r.nextDouble()*1.1;
                        p.dy=.48+r.nextDouble()*.95;
                        yield new Color(55,190,105,165);
                    }
                    case "GOLD_SPARK" -> {
                        p.size=4+r.nextInt(5);
                        p.dx=-.35+r.nextDouble()*.7;
                        p.dy=.30+r.nextDouble()*.60;
                        p.dr=-.08+r.nextDouble()*.16;
                        yield new Color(246,196,60,180);
                    }
                    default -> Color.WHITE;
                };
            }

            return p;
        }

        void update(int w,int h){
            if("SNOWFLAKE".equals(kind)){
                swayPhase+=swaySpeed;
                x+=dx+Math.sin(swayPhase)*.32;
            }else if("LEAF".equals(kind)){
                swayPhase+=swaySpeed;
                x+=dx+Math.sin(swayPhase)*.55;
            }else if("HEART".equals(kind)||"PETAL".equals(kind)||"SHAMROCK".equals(kind)){
                swayPhase+=swaySpeed==0?.021:swaySpeed;
                x+=dx+Math.sin(swayPhase)*.28;
            }else{
                x+=dx;
            }

            y+=dy;
            rotation+=dr;

            if(y>h+55||x<-70||x>w+70)
                dead=true;
        }
    }

    // -----------------------------------------------------------------
    // Layered rolling Halloween fog
    // -----------------------------------------------------------------

    private static final class FogBank {
        double x;
        double y;
        double speed;
        double width;
        double height;
        double phase;
        double phase2;
        float alpha;
        float depth;

        static FogBank create(Random r,int w,int h,int index,int total){
            FogBank f=new FogBank();

            // Wide banks overlap heavily, which removes the appearance of
            // individual "cloud blobs" and creates a continuous rolling layer.
            f.width=Math.max(760,w*(.72+r.nextDouble()*.34));
            f.height=155+r.nextDouble()*175;

            double pct=index/(double)Math.max(1,total-1);
            f.y=h*(.28+pct*.62)-r.nextDouble()*55;

            f.x=-f.width+r.nextDouble()*Math.max(1,w+f.width);
            f.depth=(float)(.55+.45*pct);

            // Near fog moves a little faster than distant layers.
            f.speed=.15+r.nextDouble()*.22+f.depth*.10;
            f.phase=r.nextDouble()*Math.PI*2;
            f.phase2=r.nextDouble()*Math.PI*2;
            f.alpha=(float)(.030+r.nextDouble()*.030+f.depth*.022);

            return f;
        }

        void update(int w,int h){
            x+=speed;
            phase+=.0045+depth*.0025;
            phase2+=.0028+depth*.0017;

            // Gentle vertical turbulence rather than obvious up/down bouncing.
            y+=Math.sin(phase2)*.025;

            if(x>w+width*.18)
                x=-width*1.08;
        }

        void paint(Graphics2D g){
            Graphics2D f=(Graphics2D)g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            // Large translucent body gradient.
            float bodyAlpha=Math.min(.14f,alpha*1.28f);
            LinearGradientPaint body=new LinearGradientPaint(
                    new Point2D.Double(0,y-height*.65),
                    new Point2D.Double(0,y+height*.70),
                    new float[]{0f,.18f,.52f,.82f,1f},
                    new Color[]{
                            new Color(205,210,220,0),
                            new Color(202,207,216,(int)(255*bodyAlpha*.35)),
                            new Color(190,196,207,(int)(255*bodyAlpha)),
                            new Color(180,187,200,(int)(255*bodyAlpha*.65)),
                            new Color(175,182,195,0)
                    }
            );

            f.setPaint(body);
            f.fill(new RoundRectangle2D.Double(
                    x,
                    y-height*.62,
                    width,
                    height*1.25,
                    height,
                    height
            ));

            /*
             * Soft overlapping density patches. These use broad elliptical
             * gradients with very low opacity; because they overlap the main
             * body, the viewer perceives rolling fog rather than separate blobs.
             */
            int patches=14;
            for(int i=0;i<patches;i++){
                double pct=i/(double)(patches-1);
                double cx=x+pct*width;
                double wave1=Math.sin(phase+i*.62)*height*.10;
                double wave2=Math.sin(phase2+i*.37)*height*.05;
                double cy=y+wave1+wave2;

                double radius=width*(.095+.025*Math.sin(i*.84+phase));
                radius=Math.max(85,radius);

                int innerAlpha=(int)Math.max(4,Math.min(23,255*alpha*.78));

                RadialGradientPaint mist=new RadialGradientPaint(
                        new Point2D.Double(cx,cy),
                        (float)radius,
                        new float[]{0f,.38f,.72f,1f},
                        new Color[]{
                                new Color(215,219,226,innerAlpha),
                                new Color(202,207,216,(int)(innerAlpha*.72)),
                                new Color(190,196,207,(int)(innerAlpha*.32)),
                                new Color(180,185,197,0)
                        }
                );

                f.setPaint(mist);
                f.fill(new Ellipse2D.Double(
                        cx-radius,
                        cy-radius*.43,
                        radius*2,
                        radius*.86
                ));
            }

            // Thin wisps drift slightly ahead/behind the main bank.
            f.setStroke(new BasicStroke(
                    2.2f+depth*1.2f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            f.setColor(new Color(220,224,232,(int)(255*alpha*.42)));

            Path2D wisp=new Path2D.Double();
            double startX=x+width*.08;
            double baseY=y-height*.10;
            wisp.moveTo(startX,baseY);

            int segments=10;
            for(int i=1;i<=segments;i++){
                double px=x+width*(.08+.84*i/segments);
                double py=baseY
                        +Math.sin(phase+i*.72)*height*.075
                        +Math.sin(phase2+i*.44)*height*.035;
                wisp.lineTo(px,py);
            }

            f.draw(wisp);
            f.dispose();
        }
    }

    // -----------------------------------------------------------------
    // Independence Day firework system
    // -----------------------------------------------------------------

    private static final class Firework {
        private double rocketX;
        private double rocketY;
        private double rocketDY;

        private double targetY;
        private boolean exploded=false;
        private boolean dead=false;

        private final List<Spark> sparks=new ArrayList<>();
        private Color color;
        private Color secondary;

        static Firework launch(Random r,int w,int h){
            Firework f=new Firework();

            f.rocketX=w*.12+r.nextDouble()*Math.max(1,w*.76);
            f.rocketY=h+20;
            f.targetY=h*.12+r.nextDouble()*Math.max(60,h*.42);
            f.rocketDY=-(5.5+r.nextDouble()*2.4);

            Color[] palette={
                    new Color(238,55,65),
                    new Color(75,130,255),
                    new Color(250,250,245),
                    new Color(255,198,60)
            };

            f.color=palette[r.nextInt(palette.length)];
            f.secondary=palette[r.nextInt(palette.length)];

            return f;
        }

        void update(Random r,int w,int h){
            if(!exploded){
                rocketY+=rocketDY;
                rocketDY+=.045;

                if(rocketY<=targetY || rocketDY>=-1.0)
                    explode(r);
            }else{
                boolean any=false;

                for(Spark s:sparks){
                    s.update();

                    if(s.alpha>0.01f && s.y<h+80)
                        any=true;
                }

                if(!any)
                    dead=true;
            }
        }

        private void explode(Random r){
            exploded=true;

            int rays=34+r.nextInt(22);

            for(int i=0;i<rays;i++){
                double angle=Math.PI*2*i/rays+r.nextDouble()*.08;
                double speed=2.2+r.nextDouble()*3.2;

                Spark s=new Spark();
                s.x=rocketX;
                s.y=rocketY;
                s.dx=Math.cos(angle)*speed;
                s.dy=Math.sin(angle)*speed;
                s.alpha=.95f;
                s.size=2+r.nextInt(3);
                s.color=(i%4==0)?secondary:color;

                sparks.add(s);

                // Small trailing spark behind some primary rays.
                if(i%3==0){
                    Spark trail=new Spark();
                    trail.x=rocketX;
                    trail.y=rocketY;
                    trail.dx=Math.cos(angle)*speed*.68;
                    trail.dy=Math.sin(angle)*speed*.68;
                    trail.alpha=.72f;
                    trail.size=2;
                    trail.color=new Color(
                            s.color.getRed(),
                            s.color.getGreen(),
                            s.color.getBlue()
                    );
                    sparks.add(trail);
                }
            }
        }

        void paint(Graphics2D g){
            if(!exploded){
                // Rising launch trail.
                GradientPaint trail=new GradientPaint(
                        (float)rocketX,(float)rocketY,
                        new Color(color.getRed(),color.getGreen(),color.getBlue(),230),
                        (float)rocketX,(float)(rocketY+38),
                        new Color(color.getRed(),color.getGreen(),color.getBlue(),0)
                );

                g.setPaint(trail);
                g.setStroke(new BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(
                        rocketX,rocketY,
                        rocketX,rocketY+38
                ));

                g.setColor(new Color(255,245,220,235));
                g.fill(new Ellipse2D.Double(rocketX-2.5,rocketY-2.5,5,5));

                return;
            }

            for(Spark s:sparks){
                if(s.alpha<=0) continue;

                Graphics2D sg=(Graphics2D)g.create();
                sg.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f,Math.min(1f,s.alpha))));

                Color sparkColor=new Color(
                        s.color.getRed(),
                        s.color.getGreen(),
                        s.color.getBlue()
                );

                sg.setColor(sparkColor);
                sg.setStroke(new BasicStroke(
                        Math.max(1f,s.size*.70f),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                sg.draw(new Line2D.Double(
                        s.x,
                        s.y,
                        s.x-s.dx*2.1,
                        s.y-s.dy*2.1
                ));

                sg.fill(new Ellipse2D.Double(
                        s.x-s.size/2.0,
                        s.y-s.size/2.0,
                        s.size,
                        s.size
                ));

                sg.dispose();
            }
        }

        private static final class Spark {
            double x,y,dx,dy;
            float alpha;
            int size;
            Color color;

            void update(){
                x+=dx;
                y+=dy;

                dx*=.985;
                dy=dy*.985+.055; // subtle gravity

                alpha-=.0135f;
            }
        }
    }
}
