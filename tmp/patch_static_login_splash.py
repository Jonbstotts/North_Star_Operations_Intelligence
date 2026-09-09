from pathlib import Path
import re


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# AppConfig: startup no longer has a selectable presentation/video model.
# ---------------------------------------------------------------------------
path=Path('src/com/wtm/config/AppConfig.java')
text=path.read_text()
text=replace_once(text, '''    /** NONE, STATIC_SPLASH, or INTRO_VIDEO. */
    public String startupExperience = "STATIC_SPLASH";

    /** Managed filename under MediaCategory.STARTUP_MEDIA; never an arbitrary path. */
    public String startupVideoAsset = "";


''', '', 'AppConfig startup video fields')
path.write_text(text)

# ---------------------------------------------------------------------------
# ConfigService: ignore retired startup-video properties and stop rewriting them.
# Existing config.properties files self-clean on the next Save & Apply.
# ---------------------------------------------------------------------------
path=Path('src/com/wtm/config/ConfigService.java')
text=path.read_text()
start=text.index('    /** Reads only the startup presentation choice before the full config load. */')
end=text.index('    public static AppConfig load()', start)
text=text[:start]+text[end:]
text=replace_once(text, '''            cfg.showHeader = bool(p, "showHeader", cfg.showHeader);
            cfg.showTicker = bool(p, "showTicker", cfg.showTicker);
            cfg.startupExperience=normalizeStartupExperience(
                    p.getProperty("startupExperience",cfg.startupExperience));
            cfg.startupVideoAsset=p.getProperty(
                    "startupVideoAsset",cfg.startupVideoAsset).trim();

''', '''            cfg.showHeader = bool(p, "showHeader", cfg.showHeader);
            cfg.showTicker = bool(p, "showTicker", cfg.showTicker);

''', 'ConfigService startup load')
text=replace_once(text, '''            p.setProperty("showHeader", Boolean.toString(cfg.showHeader));
            p.setProperty("showTicker", Boolean.toString(cfg.showTicker));
            p.setProperty("startupExperience",normalizeStartupExperience(cfg.startupExperience));
            p.setProperty("startupVideoAsset",cfg.startupVideoAsset==null?"":cfg.startupVideoAsset.trim());
            p.setProperty("workspaceModules",String.join(",",cfg.workspaceModules));
''', '''            p.setProperty("showHeader", Boolean.toString(cfg.showHeader));
            p.setProperty("showTicker", Boolean.toString(cfg.showTicker));
            p.setProperty("workspaceModules",String.join(",",cfg.workspaceModules));
''', 'ConfigService startup save')
path.write_text(text)

# ---------------------------------------------------------------------------
# Settings: remove the retired startup-media controls. Authentication policy
# remains under Security; General now documents the fixed branded login splash.
# ---------------------------------------------------------------------------
path=Path('src/com/wtm/ui/SettingsDialog.java')
text=path.read_text()
text=replace_once(text, '''    private final JCheckBox fullscreen=new JCheckBox("Fullscreen on startup");
    private final JComboBox<String> startupExperience=new JComboBox<>(new String[]{
            "Static Splash","Intro Video","No Startup Screen"});
    private final JLabel startupVideoStatus=new JLabel("No intro video selected");
    private String pendingStartupVideoAsset="";
    private final JComboBox<AppTheme> themeSelector=new JComboBox<>(AppTheme.values());
''', '''    private final JCheckBox fullscreen=new JCheckBox("Fullscreen on startup");
    private final JComboBox<AppTheme> themeSelector=new JComboBox<>(AppTheme.values());
''', 'Settings startup fields')
text=replace_once(text, '''        addFull(p,y++,showTicker);
        addFull(p,y++,fullscreen);
        addRow(p,y++,"Startup experience",startupExperience);
        JPanel startupMediaControls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton chooseIntro=new JButton("Choose Intro Video...");
        chooseIntro.addActionListener(e->chooseStartupVideo());
        JButton clearIntro=new JButton("Clear Selection");
        clearIntro.addActionListener(e->{pendingStartupVideoAsset="";updateStartupVideoStatus();});
        startupMediaControls.add(chooseIntro);
        startupMediaControls.add(clearIntro);
        startupMediaControls.add(startupVideoStatus);
        addFull(p,y++,startupMediaControls);
        addFull(p,y++,new JLabel(
                "<html>The intro is copied into North Star managed storage instead of the application JAR. "
              + "Its final decodable frame is cached losslessly and becomes the stationary login artwork "
              + "for normal completion, <b>Skip Intro</b>, and startup with the intro disabled. "
              + "For smooth portable playback, <b>1280×720 H.264 MP4 at 30 FPS constant frame rate</b> is recommended. "
              + "Press <b>Esc</b> or <b>Skip Intro</b> during playback. The visual track plays without audio.</html>"));

        JLabel identity=new JLabel(
''', '''        addFull(p,y++,showTicker);
        addFull(p,y++,fullscreen);
        addFull(p,y++,new JLabel(
                "<html><b>Startup sign-in:</b> North Star uses the approved primary logo and "
              + "North Star theme for the login splash. After authentication, the saved "
              + "interface theme below is applied to the Operations Workspace.</html>"));

        JLabel identity=new JLabel(
''', 'Settings general startup controls')
start=text.index('    private void chooseStartupVideo(){')
end=text.index('    private void updateAutomaticSevereControls(){', start)
text=text[:start]+text[end:]
text=replace_once(text, '''        showTicker.setSelected(cfg.showTicker);
        fullscreen.setSelected(cfg.fullscreen);
        startupExperience.setSelectedItem(startupModeToUi(cfg.startupExperience));
        pendingStartupVideoAsset=cfg.startupVideoAsset==null?"":cfg.startupVideoAsset;
        updateStartupVideoStatus();
        loginRequiredOnStartup.setSelected(cfg.loginRequiredOnStartup);
''', '''        showTicker.setSelected(cfg.showTicker);
        fullscreen.setSelected(cfg.fullscreen);
        loginRequiredOnStartup.setSelected(cfg.loginRequiredOnStartup);
''', 'Settings load startup controls')
text=replace_once(text, '''            cfg.showTicker=showTicker.isSelected();
            cfg.fullscreen=fullscreen.isSelected();
            cfg.startupExperience=startupModeFromUi(startupExperience.getSelectedItem());
            cfg.startupVideoAsset=pendingStartupVideoAsset==null?"":pendingStartupVideoAsset.trim();
            cfg.loginRequiredOnStartup=loginRequiredOnStartup.isSelected();
''', '''            cfg.showTicker=showTicker.isSelected();
            cfg.fullscreen=fullscreen.isSelected();
            cfg.loginRequiredOnStartup=loginRequiredOnStartup.isSelected();
''', 'Settings save startup controls')
path.write_text(text)

# ---------------------------------------------------------------------------
# Media library: startup-video storage is retired completely.
# ---------------------------------------------------------------------------
path=Path('src/com/wtm/media/MediaCategory.java')
text=path.read_text()
text=replace_once(text, '''    ANNOUNCEMENTS("Announcements","announcements"),
    EMPLOYEE_PHOTOS("Employee Photos","employees"),
    EMPLOYEE_SHOWCASE("Employee Showcase","showcase"),
    STARTUP_MEDIA("Startup Media","startup");
''', '''    ANNOUNCEMENTS("Announcements","announcements"),
    EMPLOYEE_PHOTOS("Employee Photos","employees"),
    EMPLOYEE_SHOWCASE("Employee Showcase","showcase");
''', 'MediaCategory startup bucket')
path.write_text(text)

path=Path('src/com/wtm/media/MediaService.java')
text=path.read_text()
start=text.index('    /** Imports a startup intro video into managed application storage. */')
end=text.index('    /** Resolves a managed asset filename without exposing arbitrary paths. */', start)
text=text[:start]+text[end:]
start=text.index('    private static String videoExtension(String filename){')
end=text.index('    private static String extension(String filename){', start)
text=text[:start]+text[end:]
path.write_text(text)

# ---------------------------------------------------------------------------
# Brand service exposes the canonical full-resolution primary artwork directly
# for read-only login rendering. The old separate splash resource is no longer
# a runtime dependency.
# ---------------------------------------------------------------------------
path=Path('src/com/wtm/ui/NorthStarBrand.java')
text=path.read_text()
text=replace_once(text, '''    private static final BufferedImage PRIMARY_SOURCE=
            load("/brand/northstar_primary_logo_exact.png");
    private static final BufferedImage SPLASH_SOURCE=
            load("/brand/northstar_splash_exact.png");
    private static final BufferedImage APP_ICON_SOURCE=
''', '''    private static final BufferedImage PRIMARY_SOURCE=
            load("/brand/northstar_primary_logo_exact.png");
    private static final BufferedImage APP_ICON_SOURCE=
''', 'NorthStarBrand splash source')
text=replace_once(text, '''    /** Full approved splash artwork used by the startup screen. */
    public static BufferedImage splashArtwork(){
        return SPLASH_SOURCE;
    }


''', '''    /** Full-resolution approved primary artwork used by the login splash. */
    public static BufferedImage primaryArtwork(){
        return PRIMARY_SOURCE;
    }


''', 'NorthStarBrand splash accessor')
path.write_text(text)

# ---------------------------------------------------------------------------
# Main: startup is now configuration load -> branded login -> saved theme.
# There is no loading screen, intro movie, poster migration, or handoff window.
# ---------------------------------------------------------------------------
Path('src/com/wtm/app/Main.java').write_text(r'''package com.wtm.app;

import com.wtm.config.*;
import com.wtm.security.*;
import com.wtm.ui.*;
import com.wtm.employee.EmployeeService;
import com.wtm.callin.CallInServerManager;

import javax.swing.*;

/** North Star Operations Intelligence desktop application entry point. */
public final class Main {
    private Main(){}

    public static void main(String[] args){
        System.setProperty("apple.awt.application.name","North Star Operations");
        System.setProperty("apple.awt.application.appearance","system");
        SwingUtilities.invokeLater(()->{
            /* Authentication has a fixed North Star identity. The user's saved
             * workspace theme is resolved from configuration, but is installed
             * only after authentication succeeds. */
            Theme.setActive(AppTheme.NORTH_STAR.id());
            ApplicationBrand.applyApplicationIcon();

            SwingWorker<AppConfig,Void> loader=new SwingWorker<>(){
                @Override protected AppConfig doInBackground(){
                    return ConfigService.load();
                }

                @Override protected void done(){
                    try{
                        AppConfig config=get();
                        prepareConfiguration(config);
                        AppTheme workspaceTheme=HolidayThemeService.effectiveTheme(
                                config,java.time.LocalDate.now());
                        config.darkMode=workspaceTheme.dark();
                        CallInServerManager.apply(config);
                        continueAfterConfiguration(config,workspaceTheme);
                    }catch(Exception ex){
                        ThemedDialogs.message(
                                null,
                                "North Star could not complete startup. Review the local configuration and try again.",
                                "Startup Error",
                                ThemedDialogs.Kind.ERROR
                        );
                    }
                }
            };
            loader.execute();
        });
    }

    private static void prepareConfiguration(AppConfig config){
        boolean migrated=EmployeeService.migrateLegacyCelebrationsIfNeeded(config);
        EmployeeService.syncCelebrations(config,EmployeeService.loadForSystem());
        if(migrated)ConfigService.save(config);
    }

    private static void continueAfterConfiguration(
            AppConfig config,
            AppTheme workspaceTheme
    ){
        if(!UserService.hasUsers()){
            UserAccount initial=AuthService.hasPassword()
                    ?LegacyAdminMigrationDialog.migrate(null,AppTheme.NORTH_STAR)
                    :FirstAdminDialog.create(null,AppTheme.NORTH_STAR);
            if(initial==null)return;
            SessionManager.login(initial);
        }

        if(config.loginRequiredOnStartup&&!SessionManager.isAuthenticated()){
            UserAccount account=StartupLoginDialog.authenticate(
                    null,
                    "Sign in to continue to the operations dashboard.",
                    ""
            );
            if(account==null)return;
            SessionManager.login(account);
        }

        Theme.setActive(workspaceTheme.id());
        OperationsWorkspaceFrame frame=new OperationsWorkspaceFrame(config);
        frame.setVisible(true);
    }
}
''')

# ---------------------------------------------------------------------------
# Login splash geometry: fixed branded surface, no movie handoff semantics.
# ---------------------------------------------------------------------------
Path('src/com/wtm/ui/LoginSplashLayout.java').write_text(r'''package com.wtm.ui;

import java.awt.*;

/** Pure geometry policy for the branded startup sign-in surface. */
public final class LoginSplashLayout {
    private static final int MAX_WIDTH=920;
    private static final int MIN_WIDTH=660;
    private static final int DESIRED_LOGIN_HEIGHT=330;
    private static final int MIN_LOGIN_HEIGHT=300;

    private LoginSplashLayout(){}

    public record Geometry(
            Rectangle windowBounds,
            Dimension artworkSize,
            int loginHeight
    ){}

    /** Fits the complete logo + authentication surface inside the usable area. */
    public static Geometry fit(Rectangle usable,int sourceWidth,int sourceHeight){
        Rectangle area=usable==null?new Rectangle(0,0,1440,900):new Rectangle(usable);
        int sw=Math.max(1,sourceWidth);
        int sh=Math.max(1,sourceHeight);
        double aspect=sw/(double)sh;

        int maxW=Math.max(1,(int)Math.floor(area.width*.82));
        int maxH=Math.max(1,(int)Math.floor(area.height*.88));
        int width=Math.min(MAX_WIDTH,maxW);
        if(width<MIN_WIDTH&&maxW>=MIN_WIDTH)width=MIN_WIDTH;
        width=Math.max(1,Math.min(width,maxW));

        int loginHeight=Math.min(
                DESIRED_LOGIN_HEIGHT,
                Math.max(MIN_LOGIN_HEIGHT,(int)Math.round(maxH*.38))
        );
        loginHeight=Math.min(loginHeight,Math.max(1,maxH/2));

        int artworkHeight=Math.max(1,(int)Math.round(width/aspect));
        if(artworkHeight+loginHeight>maxH){
            artworkHeight=Math.max(1,maxH-loginHeight);
            width=Math.min(maxW,Math.max(1,(int)Math.round(artworkHeight*aspect)));
            artworkHeight=Math.max(1,(int)Math.round(width/aspect));
        }

        int totalHeight=Math.min(maxH,artworkHeight+loginHeight);
        loginHeight=Math.max(1,totalHeight-artworkHeight);
        int x=area.x+Math.max(0,(area.width-width)/2);
        int y=area.y+Math.max(0,(area.height-totalHeight)/2);

        return new Geometry(
                new Rectangle(x,y,width,totalHeight),
                new Dimension(width,artworkHeight),
                loginHeight
        );
    }

    /** Resolves the monitor work area nearest the supplied owner. */
    public static Rectangle usableBounds(Window owner){
        GraphicsConfiguration gc=owner==null?null:owner.getGraphicsConfiguration();
        if(gc==null){
            GraphicsEnvironment ge=GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device=ge.getDefaultScreenDevice();
            gc=device==null?null:device.getDefaultConfiguration();
        }
        if(gc==null)return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        Rectangle bounds=new Rectangle(gc.getBounds());
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x+insets.left,
                bounds.y+insets.top,
                Math.max(1,bounds.width-insets.left-insets.right),
                Math.max(1,bounds.height-insets.top-insets.bottom)
        );
    }
}
''')

# Smooth, deterministic login-form reveal. This remains independent of media.
Path('src/com/wtm/ui/LoginRevealPolicy.java').write_text(r'''package com.wtm.ui;

/** Timing/easing policy for the startup login form reveal. */
public final class LoginRevealPolicy {
    public static final int TIMER_DELAY_MILLIS=10;
    public static final double DURATION_MILLIS=700.0;
    private static final int MAX_VERTICAL_OFFSET=8;

    private LoginRevealPolicy(){}

    public static float revealProgress(double elapsedMillis){
        double t=Math.max(0.0,Math.min(1.0,elapsedMillis/DURATION_MILLIS));
        // Quintic smootherstep: zero velocity and acceleration at both ends.
        double eased=t*t*t*(t*(t*6.0-15.0)+10.0);
        return (float)eased;
    }

    public static int verticalOffset(float progress){
        float p=Math.max(0f,Math.min(1f,progress));
        return Math.round((1f-p)*MAX_VERTICAL_OFFSET);
    }
}
''')

# ---------------------------------------------------------------------------
# Static branded login splash. The primary artwork is always rendered from the
# full-resolution canonical source; only the form fades in.
# ---------------------------------------------------------------------------
Path('src/com/wtm/ui/StartupLoginDialog.java').write_text(r'''package com.wtm.ui;

import com.wtm.security.UserAccount;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Startup-only authentication splash. The login always uses North Star branding;
 * the user's saved application theme is installed only after authentication.
 */
public final class StartupLoginDialog extends JDialog {
    private final LoginFormPanel form;
    private final FadingPanel reveal;
    private UserAccount authenticated;
    private Timer revealTimer;

    private StartupLoginDialog(
            Window owner,
            String message,
            String suggestedUsername
    ){
        super(owner,"North Star Sign In",ModalityType.APPLICATION_MODAL);
        Theme.setActive(AppTheme.NORTH_STAR.id());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setUndecorated(true);

        BufferedImage artwork=NorthStarBrand.primaryArtwork();
        Rectangle usable=LoginSplashLayout.usableBounds(owner);
        LoginSplashLayout.Geometry geometry=LoginSplashLayout.fit(
                usable,artwork.getWidth(),artwork.getHeight());
        Rectangle bounds=geometry.windowBounds();

        JPanel shell=new JPanel(new BorderLayout());
        shell.setBackground(Color.BLACK);
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.accent(),1,true),
                BorderFactory.createEmptyBorder(1,1,1,1)
        ));

        StartupArtworkPanel artworkPanel=new StartupArtworkPanel(artwork);
        artworkPanel.setPreferredSize(geometry.artworkSize());
        shell.add(artworkPanel,BorderLayout.NORTH);

        JSeparator separator=new JSeparator();
        separator.setForeground(Theme.accent());

        JPanel formHost=new JPanel(new GridBagLayout());
        formHost.setBackground(Theme.panel());
        form=new LoginFormPanel(
                this,message,suggestedUsername,
                new Insets(14,24,14,24),
                account->{authenticated=account;dispose();}
        );
        form.setPreferredSize(new Dimension(
                Math.min(520,Math.max(430,bounds.width-120)),
                Math.max(270,geometry.loginHeight()-18)
        ));
        formHost.add(form);

        JPanel lower=new JPanel(new BorderLayout());
        lower.setBackground(Theme.panel());
        lower.add(separator,BorderLayout.NORTH);
        lower.add(formHost,BorderLayout.CENTER);

        reveal=new FadingPanel(new BorderLayout());
        reveal.setBackground(Theme.panel());
        reveal.add(lower,BorderLayout.CENTER);
        reveal.setProgress(0f);
        shell.add(reveal,BorderLayout.CENTER);
        setContentPane(shell);

        getRootPane().setDefaultButton(form.defaultButton());
        getRootPane().registerKeyboardAction(
                e->dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        setBounds(bounds);
        ApplicationBrand.applyWindowIcon(this);

        addWindowListener(new WindowAdapter(){
            @Override public void windowOpened(WindowEvent e){
                SwingUtilities.invokeLater(StartupLoginDialog.this::startReveal);
            }
        });
    }

    public static UserAccount authenticate(
            Window owner,
            String message,
            String suggestedUsername
    ){
        StartupLoginDialog dialog=new StartupLoginDialog(
                owner,message,suggestedUsername);
        dialog.setVisible(true);
        return dialog.authenticated;
    }

    private void startReveal(){
        final long started=System.nanoTime();
        revealTimer=new Timer(LoginRevealPolicy.TIMER_DELAY_MILLIS,e->{
            double elapsed=(System.nanoTime()-started)/1_000_000.0;
            float progress=LoginRevealPolicy.revealProgress(elapsed);
            reveal.setProgress(progress);
            if(progress>=1f){
                ((Timer)e.getSource()).stop();
                reveal.setProgress(1f);
                SwingUtilities.invokeLater(form::focusInitial);
            }
        });
        revealTimer.setCoalesce(true);
        revealTimer.start();
    }

    @Override public void dispose(){
        if(revealTimer!=null)revealTimer.stop();
        form.stop();
        super.dispose();
    }

    private static final class FadingPanel extends JPanel {
        private float progress=1f;
        private FadingPanel(LayoutManager layout){
            super(layout);
            setDoubleBuffered(true);
        }
        private void setProgress(float value){
            progress=Math.max(0f,Math.min(1f,value));
            repaint();
        }
        @Override public void paint(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setComposite(AlphaComposite.SrcOver.derive(progress));
                g.translate(0,LoginRevealPolicy.verticalOffset(progress));
                super.paint(g);
            }finally{g.dispose();}
        }
    }

    private static final class StartupArtworkPanel extends JPanel {
        private final BufferedImage artwork;
        private StartupArtworkPanel(BufferedImage artwork){
            this.artwork=artwork;
            setBackground(Color.BLACK);
            setOpaque(true);
            setDoubleBuffered(true);
        }
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                drawContained(g,artwork,getWidth(),getHeight());
            }finally{g.dispose();}
        }
        private static void drawContained(Graphics2D g,BufferedImage image,int width,int height){
            double scale=Math.min(
                    width/(double)image.getWidth(),
                    height/(double)image.getHeight());
            int w=Math.max(1,(int)Math.round(image.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(image.getHeight()*scale));
            g.drawImage(
                    image,
                    (width-w)/2,(height-h)/2,
                    (width-w)/2+w,(height-h)/2+h,
                    0,0,image.getWidth(),image.getHeight(),
                    null
            );
        }
    }
}
''')

# ---------------------------------------------------------------------------
# Retire all movie/loading-screen infrastructure and its codec dependency.
# ---------------------------------------------------------------------------
for retired in [
    'src/com/wtm/ui/StartupExperienceManager.java',
    'src/com/wtm/ui/StartupPresentationLayout.java',
    'src/com/wtm/ui/StartupTransitionPolicy.java',
    'src/com/wtm/ui/NorthStarSplashScreen.java',
    'src/com/wtm/media/StartupMediaService.java',
    'src/com/wtm/media/StartupPlaybackCacheService.java',
    'ci/StartupPresentationLayoutSmokeTest.java',
    'ci/StartupMediaServiceSmokeTest.java',
    'ci/StartupLaunchResponsivenessSmokeTest.java',
    'ci/StartupPlaybackCacheSmokeTest.java',
    'ci/StartupTransitionPolicySmokeTest.java',
    'lib/jcodec-0.2.5.jar',
    'lib/jcodec-javase-0.2.5.jar',
]:
    p=Path(retired)
    if p.exists():
        p.unlink()

# ---------------------------------------------------------------------------
# Regression tests for static splash geometry and reveal easing.
# ---------------------------------------------------------------------------
Path('ci/LoginSplashLayoutSmokeTest.java').write_text(r'''import com.wtm.ui.LoginSplashLayout;
import java.awt.*;

public final class LoginSplashLayoutSmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        int[][] screens={{1280,720},{1440,900},{1920,1080},{2560,1440}};
        for(int[] screen:screens){
            Rectangle usable=new Rectangle(0,0,screen[0],screen[1]);
            LoginSplashLayout.Geometry g=LoginSplashLayout.fit(usable,1672,941);
            require(usable.contains(g.windowBounds()),"login splash escaped usable bounds");
            require(g.windowBounds().width<=920,"login splash is wider than intended");
            require(g.artworkSize().width==g.windowBounds().width,
                    "artwork width does not track login splash width");
            require(g.loginHeight()>0,"login form has no reserved height");
            double sourceAspect=1672.0/941.0;
            double renderedAspect=g.artworkSize().width/(double)g.artworkSize().height;
            require(Math.abs(sourceAspect-renderedAspect)<0.02,
                    "primary logo aspect ratio was not preserved");
        }
        System.out.println("LOGIN_SPLASH_LAYOUT_SMOKE_OK");
    }
}
''')

Path('ci/LoginRevealPolicySmokeTest.java').write_text(r'''import com.wtm.ui.LoginRevealPolicy;

public final class LoginRevealPolicySmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        require(LoginRevealPolicy.revealProgress(-1)==0f,"negative time must clamp to zero");
        require(LoginRevealPolicy.revealProgress(0)==0f,"reveal must start transparent");
        require(LoginRevealPolicy.revealProgress(LoginRevealPolicy.DURATION_MILLIS)>=1f,
                "reveal must finish at duration");
        float previous=0f;
        for(int i=0;i<=100;i++){
            float p=LoginRevealPolicy.revealProgress(
                    LoginRevealPolicy.DURATION_MILLIS*i/100.0);
            require(p>=previous,"reveal easing must be monotonic");
            previous=p;
        }
        require(LoginRevealPolicy.verticalOffset(0f)>0,
                "reveal should begin with a small vertical settle offset");
        require(LoginRevealPolicy.verticalOffset(1f)==0,
                "reveal must finish at its resting position");
        System.out.println("LOGIN_REVEAL_POLICY_SMOKE_OK");
    }
}
''')

# ---------------------------------------------------------------------------
# THIRD_PARTY_NOTICES: JCodec is no longer distributed after video removal.
# ---------------------------------------------------------------------------
path=Path('THIRD_PARTY_NOTICES.md')
text=path.read_text()
text=text.replace('''\nNorth Star Operations Intelligence also redistributes JCodec and JCodec JavaSE, version 0.2.5.\n\nJCodec is licensed under the FreeBSD License. Project: https://github.com/jcodec/jcodec\n''','\n')
path.write_text(text)

# ---------------------------------------------------------------------------
# Permanent release gates: startup is now a static branded login boundary.
# ---------------------------------------------------------------------------
path=Path('build.sh')
text=path.read_text()
pattern=re.compile(r'''if \[ ! -f lib/flatlaf-3\.7\.2\.jar \].*?if grep -Fq 'Theme\.setActive\(resolved\.id\(\)\)' src/com/wtm/ui/ThemeStyler\.java; then''',re.S)
replacement=r'''if [ ! -f lib/flatlaf-3.7.2.jar ] || [ ! -f lib/flatlaf-intellij-themes-3.7.2.jar ]; then
  echo "ERROR: required FlatLaf 3.7.2 runtime libraries are missing from lib/." >&2
  exit 1
fi

# Startup is intentionally a single static authentication surface. Movie players,
# media caches, selectable startup modes, and codec dependencies must not return.
for retired in \
  src/com/wtm/ui/StartupExperienceManager.java \
  src/com/wtm/ui/StartupPresentationLayout.java \
  src/com/wtm/ui/StartupTransitionPolicy.java \
  src/com/wtm/ui/NorthStarSplashScreen.java \
  src/com/wtm/media/StartupMediaService.java \
  src/com/wtm/media/StartupPlaybackCacheService.java \
  lib/jcodec-0.2.5.jar \
  lib/jcodec-javase-0.2.5.jar; do
  if [ -e "$retired" ]; then
    echo "ERROR: retired startup-video/loading infrastructure returned: $retired" >&2
    exit 1
  fi
done
if [ ! -f src/com/wtm/ui/StartupLoginDialog.java ] || \
   [ ! -f src/com/wtm/ui/LoginSplashLayout.java ] || \
   [ ! -f src/com/wtm/ui/LoginRevealPolicy.java ] || \
   [ ! -f src/com/wtm/ui/LoginFormPanel.java ] || \
   ! grep -Fq 'NorthStarBrand.primaryArtwork()' src/com/wtm/ui/StartupLoginDialog.java || \
   ! grep -Fq 'StartupLoginDialog.authenticate' src/com/wtm/app/Main.java; then
  echo "ERROR: canonical static NorthStar login splash ownership is missing." >&2
  exit 1
fi
if grep -R -q --include='*.java' -E 'startupVideoAsset|startupExperience|INTRO_VIDEO|StartupMediaService|StartupPlaybackCacheService|StartupExperienceManager|org\.jcodec|Choose Intro Video|Skip Intro' src; then
  echo "ERROR: startup-video or selectable loading-screen behavior returned to active source." >&2
  exit 1
fi
if ! grep -Fq 'Theme.setActive(AppTheme.NORTH_STAR.id())' src/com/wtm/app/Main.java || \
   ! grep -Fq 'Theme.setActive(workspaceTheme.id())' src/com/wtm/app/Main.java || \
   ! grep -Fq 'Theme.setActive(AppTheme.NORTH_STAR.id())' src/com/wtm/ui/StartupLoginDialog.java; then
  echo "ERROR: startup branding and saved workspace theme are no longer isolated." >&2
  exit 1
fi
if grep -Fq 'Theme.setActive(resolved.id())' src/com/wtm/ui/ThemeStyler.java; then'''
text,new_count=pattern.subn(replacement,text,count=1)
if new_count!=1:
    raise SystemExit('build startup gate block not found')
text=replace_once(text, '''for dep in lib/flatlaf-3.7.2.jar lib/flatlaf-intellij-themes-3.7.2.jar lib/jcodec-0.2.5.jar lib/jcodec-javase-0.2.5.jar; do
''', '''for dep in lib/flatlaf-3.7.2.jar lib/flatlaf-intellij-themes-3.7.2.jar; do
''', 'build dependency extraction')
old_compile='''  ci/BasemapProviderSmokeTest.java \\
  ci/StartupPresentationLayoutSmokeTest.java \\
  ci/StartupMediaServiceSmokeTest.java \\
  ci/StartupLaunchResponsivenessSmokeTest.java \\
  ci/StartupPlaybackCacheSmokeTest.java \\
  ci/StartupTransitionPolicySmokeTest.java
'''
new_compile='''  ci/BasemapProviderSmokeTest.java \\
  ci/LoginSplashLayoutSmokeTest.java \\
  ci/LoginRevealPolicySmokeTest.java
'''
text=replace_once(text,old_compile,new_compile,'build startup test compile list')
old_runs='''java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' BasemapProviderSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupPresentationLayoutSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupMediaServiceSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupLaunchResponsivenessSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupPlaybackCacheSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupTransitionPolicySmokeTest
'''
new_runs='''java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' BasemapProviderSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' LoginSplashLayoutSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' LoginRevealPolicySmokeTest
'''
text=replace_once(text,old_runs,new_runs,'build startup test run list')
text=text.replace('''  brand/northstar_primary_logo_exact.png \\
  brand/northstar_splash_exact.png \\
  brand/northstar_app_icon_exact.png; do
''','''  brand/northstar_primary_logo_exact.png \\
  brand/northstar_app_icon_exact.png; do
''')
path.write_text(text)
