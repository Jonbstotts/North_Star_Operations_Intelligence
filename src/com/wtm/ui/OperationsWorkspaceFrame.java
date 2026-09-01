package com.wtm.ui;

import com.wtm.alerts.NwsAlertService;
import com.wtm.alerts.SevereWeatherRefreshPolicy;
import com.wtm.alerts.WeatherAlertPolicy;
import com.wtm.config.AppConfig;
import com.wtm.config.ConfigService;
import com.wtm.map.TileMapPanel;
import com.wtm.media.MediaCategory;
import com.wtm.media.MediaService;
import com.wtm.modular.ui.WorkspaceLifecycleV3;
import com.wtm.model.*;
import com.wtm.net.HttpService;
import com.wtm.radar.RainViewerService;
import com.wtm.security.*;
import com.wtm.traffic.TomTomService;
import com.wtm.weather.OpenMeteoService;
import com.wtm.sports.TheSportsDbService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

/**
 * North Star Operations Intelligence modular workspace.
 *
 * This is the sole dashboard/runtime experience in the standalone product.
 */
public final class OperationsWorkspaceFrame extends JFrame {
    private static final DateTimeFormatter DAY_FORMAT=
            DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter TIME_FORMAT=
            DateTimeFormatter.ofPattern("h:mm a");

    private AppConfig config;
    private final HttpService http=new HttpService();
    private final OpenMeteoService weatherService=new OpenMeteoService(http);
    private final NwsAlertService alertService=new NwsAlertService(http);
    private final RainViewerService radarService=new RainViewerService(http);
    private final TomTomService trafficService=new TomTomService(http);
    private final TheSportsDbService sportsService=new TheSportsDbService(http);

    private ScheduledExecutorService refreshExecutor;
    private ScheduledFuture<?> weatherRefreshFuture;
    private ScheduledFuture<?> alertRefreshFuture;
    private ScheduledFuture<?> radarRefreshFuture;
    private ScheduledFuture<?> trafficRefreshFuture;
    private ScheduledFuture<?> sportsRefreshFuture;
    private volatile boolean rapidRefreshScheduled=false;
    private volatile boolean automaticSevereWeatherLatched=false;
    private TileMapPanel map;
    private MainShowcasePanel mainShowcase;
    private OverlayEffectsPanel overlayEffects;

    private javax.swing.Timer informationRotationTimer;
    private javax.swing.Timer informationTickerTimer;
    private JViewport informationTickerViewport;
    private JPanel informationTickerTrack;
    private int informationTickerCycleWidth=0;
    private int informationPageStart=0;

    private javax.swing.Timer operationsRotationTimer;
    private javax.swing.Timer operationsTickerTimer;
    private JViewport operationsTickerViewport;
    private JPanel operationsTickerTrack;
    private int operationsTickerCycleWidth=0;
    private int operationsPageStart=0;

    private volatile WeatherSnapshot weather;
    private volatile List<WeatherAlert> alerts=List.of();
    private final Map<Integer,RouteStatus> routeStatuses=
            new ConcurrentHashMap<>();
    private final Map<Integer,List<SportsGame>> sportsSchedules=
            new ConcurrentHashMap<>();
    private final Map<Integer,ImageIcon> sportsLogos=
            new ConcurrentHashMap<>();

    private JPanel root;
    private JPanel dashboardBody;
    private JPanel workspaceContentHost;
    private SettingsDialog embeddedSettingsSession;
    private String activeWorkspaceRoute="Dashboard";
    private final Map<String,JButton> sidebarRouteButtons=
            new LinkedHashMap<>();
    private final Map<String,JComponent> dashboardExtensions=
            new LinkedHashMap<>();
    private final Map<String,JComponent> summaryExtensions=
            new LinkedHashMap<>();
    private DashboardGridPanel dashboardGrid;
    private JPanel summaryExtensionsHost;
    private JLabel dateTimeLabel;
    private JLabel topWeatherLabel;
    private JLabel topTrafficLabel;
    private JButton alertBadge;
    private JPopupMenu weatherAlertPopup;
    private boolean weatherAlertPopupVisibleAtPress=false;
    private HeaderTicker headerTicker;
    private JButton dashboardLayoutGear;
    private boolean dashboardLayoutEditing=false;

    private JPanel weatherModule;
    private JPanel eventsModule;
    private JPanel celebrationsModule;
    private JPanel infoStripModule;
    private JPanel operationsModule;

    private final javax.swing.Timer clockTimer;

    public OperationsWorkspaceFrame(AppConfig config){
        super("NORTH STAR • Operations Intelligence");
        this.config=Objects.requireNonNull(config);

        Theme.setActive(config.themeId);
        config.darkMode=Theme.active().dark();
        setTitle(BrandIdentity.product()+" • Operations Workspace");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180,720));
        setSize(1540,920);
        setLocationRelativeTo(null);
        ApplicationBrand.applyWindowIcon(this);

        overlayEffects=new OverlayEffectsPanel();
        setGlassPane(overlayEffects);
        overlayEffects.setVisible(true);

        buildUi();
        ThemeStyler.apply(this,Theme.active());
        WorkspaceLifecycleV3.initializeWorkspace(this);
        startRefreshers();

        clockTimer=new javax.swing.Timer(1000,e->updateClock());
        clockTimer.start();
        updateClock();

        if(config.fullscreen)
            setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void buildUi(){
        if(map!=null){
            map.shutdown();
            map=null;
        }

        stopInformationMovement();
        stopOperationsMovement();

        /*
         * Workspace branding and decorative holiday animation are independent.
         * North Star remains the application identity, while the overlay engine
         * receives the effective seasonal theme so snow/fog/fireworks/etc.
         * continue to function in the modern workspace.
         */
        AppTheme configuredTheme=AppTheme.fromId(config.themeId);
        Theme.setActive(configuredTheme.id());
        config.darkMode=configuredTheme.dark();

        AppTheme overlayTheme=HolidayThemeService.effectiveTheme(
                config,
                LocalDate.now()
        );

        if(overlayEffects!=null){
            overlayEffects.configure(
                    overlayTheme,
                    config.themeOverlayEffects,
                    config.overlayIntensity,
                    config.overlayPerformanceMode
            );
            overlayEffects.setSevereSuppressed(
                    severeWeatherPriorityActive()
            );
        }

        getContentPane().removeAll();

        root=new JPanel(new BorderLayout());
        root.setBackground(Theme.bg());
        root.add(buildTopBar(),BorderLayout.NORTH);
        root.add(buildWorkspace(),BorderLayout.CENTER);
        setContentPane(root);

        refreshVisibleModules();
        revalidate();
        repaint();
    }

    private boolean severeWeatherPriorityActive(){
        return config.severeWeatherMapPriority&&severeWeatherActive();
    }

    private JComponent buildTopBar(){
        JPanel bar=new JPanel(new BorderLayout(18,0));
        bar.setBackground(Theme.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,0,1,0,Theme.border()),
                new EmptyBorder(10,16,10,16)
        ));
        bar.setPreferredSize(new Dimension(100,62));

        ApplicationBrand.applyWindowIcon(this);
        ApplicationBrand.applyApplicationIcon();

        NorthStarBrandLockup lockup=new NorthStarBrandLockup(
                NorthStarBrandLockup.Layout.HORIZONTAL,42,16,true);
        lockup.setToolTipText(
                BrandIdentity.product()+" • "+BrandIdentity.tagline());
        bar.add(lockup,BorderLayout.WEST);

        JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));
        right.setOpaque(false);

        alertBadge=new JButton("●  0 alerts");
        alertBadge.setForeground(Theme.muted());
        alertBadge.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
        alertBadge.setFocusPainted(false);
        alertBadge.setContentAreaFilled(false);
        alertBadge.setOpaque(false);
        alertBadge.setBorder(new EmptyBorder(5,8,5,8));
        alertBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        alertBadge.setToolTipText("Show active weather alerts");
        alertBadge.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mousePressed(java.awt.event.MouseEvent e){
                weatherAlertPopupVisibleAtPress=weatherAlertPopup!=null
                        &&weatherAlertPopup.isVisible();
            }
        });
        alertBadge.addActionListener(e->toggleWeatherAlertMenu(alertBadge));
        right.add(alertBadge);

        JSeparator divider=new JSeparator(SwingConstants.VERTICAL);
        divider.setPreferredSize(new Dimension(1,30));
        divider.setForeground(Theme.border());
        right.add(divider);

        UserAccount user=SessionManager.currentUser();
        JPanel identity=new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity,BoxLayout.Y_AXIS));
        JLabel name=new JLabel(
                user==null?BrandIdentity.product()+" Display":user.friendlyName());
        name.setForeground(Theme.text());
        name.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
        JLabel role=new JLabel(user==null?"Display Session":user.role().display());
        role.setForeground(Theme.muted());
        role.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
        identity.add(name);identity.add(role);
        right.add(identity);

        dashboardLayoutGear=navUtilityButton("⚙");
        dashboardLayoutGear.setToolTipText("Customize dashboard layout");
        dashboardLayoutGear.setVisible(
                AuthorizationService.allowed(Permission.DASHBOARD_LAYOUT));
        dashboardLayoutGear.addActionListener(e->toggleDashboardLayoutFromGear());
        right.add(dashboardLayoutGear);

        bar.add(right,BorderLayout.EAST);
        if(!config.showTicker)return bar;

        JPanel chrome=new JPanel(new BorderLayout());
        chrome.setOpaque(false);
        chrome.add(bar,BorderLayout.NORTH);
        chrome.add(buildHeaderTickerStrip(),BorderLayout.SOUTH);
        return chrome;
    }

    private JComponent buildHeaderTickerStrip(){
        JPanel strip=new JPanel(new BorderLayout(14,0));
        strip.setOpaque(true);
        strip.setBackground(Theme.panel2());
        strip.setBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.border()));
        strip.setPreferredSize(new Dimension(100,24));

        if(config.showHeader&&config.headerText!=null&&!config.headerText.isBlank()){
            JLabel headerTitle=new JLabel(config.headerText.trim());
            headerTitle.setForeground(Theme.text());
            headerTitle.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
            headerTitle.setBorder(new EmptyBorder(0,12,0,2));
            strip.add(headerTitle,BorderLayout.WEST);
        }

        headerTicker=new HeaderTicker();
        headerTicker.setBorder(new EmptyBorder(0,10,0,12));
        headerTicker.setEntries(headerTickerEntries());
        strip.add(headerTicker,BorderLayout.CENTER);
        return strip;
    }

    private void toggleWeatherAlertMenu(Component invoker){
        boolean closeRequested=weatherAlertPopupVisibleAtPress
                ||(weatherAlertPopup!=null&&weatherAlertPopup.isVisible());
        weatherAlertPopupVisibleAtPress=false;

        if(closeRequested){
            if(weatherAlertPopup!=null)
                weatherAlertPopup.setVisible(false);
            weatherAlertPopup=null;
            return;
        }

        showWeatherAlertMenu(invoker);
    }

    private void showWeatherAlertMenu(Component invoker){
        if(weatherAlertPopup!=null){
            weatherAlertPopup.setVisible(false);
            weatherAlertPopup=null;
        }

        JPanel content=new JPanel();
        content.setBackground(Theme.panel());
        content.setBorder(new EmptyBorder(4,4,4,4));
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));

        if(config.liveSevereWeatherMode){
            content.add(weatherAlertPopupRow(
                    "SEVERE WEATHER TEST MODE",
                    "Manual Live Severe Weather Mode is active. Rapid monitoring and severe-weather presentation are being tested.",
                    Theme.danger()
            ));
        }

        List<WeatherAlert> ordered=alerts==null
                ?List.of()
                :alerts.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(WeatherAlertPolicy::priority).reversed())
                        .toList();

        if(ordered.isEmpty()&&!config.liveSevereWeatherMode){
            JLabel none=new JLabel("No active weather alerts");
            none.setForeground(Theme.muted());
            none.setBorder(new EmptyBorder(14,14,14,14));
            content.add(none);
        }else{
            for(WeatherAlert alert:ordered){
                if(content.getComponentCount()>0)
                    content.add(new JSeparator());
                content.add(weatherAlertPopupRow(
                        WeatherAlertPolicy.shortEventName(alert),
                        weatherAlertPopupDetails(alert),
                        weatherAlertColor(alert)
                ));
            }
        }

        JScrollPane scroll=new JScrollPane(
                content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.panel());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        int height=Math.min(430,Math.max(70,content.getPreferredSize().height+6));
        scroll.setPreferredSize(new Dimension(520,height));

        JPopupMenu popup=new JPopupMenu();
        weatherAlertPopup=popup;
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e){}

            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e){
                if(weatherAlertPopup==popup)
                    weatherAlertPopup=null;
            }

            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e){
                if(weatherAlertPopup==popup)
                    weatherAlertPopup=null;
            }
        });
        popup.setBorder(BorderFactory.createLineBorder(Theme.border(),1));
        popup.setLayout(new BorderLayout());
        popup.add(scroll,BorderLayout.CENTER);
        popup.show(invoker,0,invoker.getHeight()+4);
    }

    private JPanel weatherAlertPopupRow(String title,String details,Color color){
        JPanel row=new JPanel();
        row.setBackground(Theme.panel());
        row.setBorder(new EmptyBorder(10,12,10,12));
        row.setLayout(new BoxLayout(row,BoxLayout.Y_AXIS));

        JLabel heading=new JLabel(title);
        heading.setForeground(color);
        heading.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea description=new JTextArea(details);
        description.setEditable(false);
        description.setFocusable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setForeground(color);
        description.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
        description.setRows(Math.min(7,Math.max(2,details.length()/72+1)));
        description.setColumns(54);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(heading);
        row.add(Box.createVerticalStrut(4));
        row.add(description);
        return row;
    }

    private String weatherAlertPopupDetails(WeatherAlert alert){
        List<String> lines=new ArrayList<>();
        if(alert.headline()!=null&&!alert.headline().isBlank())
            lines.add(alert.headline().trim());

        String severity=safe(alert.severity()).trim();
        String urgency=safe(alert.urgency()).trim();
        if(!severity.isBlank()||!urgency.isBlank())
            lines.add(String.join(" • ",
                    java.util.stream.Stream.of(severity,urgency)
                            .filter(value->!value.isBlank())
                            .toList()));

        if(alert.expires()!=null)
            lines.add("Expires "+DateTimeFormatter.ofPattern("MMM d, h:mm a")
                    .withZone(ZoneId.systemDefault())
                    .format(alert.expires()));

        if(alert.instruction()!=null&&!alert.instruction().isBlank())
            lines.add(alert.instruction().trim());

        if(lines.isEmpty())
            return WeatherAlertPolicy.briefText(alert,180);
        return String.join("\n",lines);
    }

    private List<TickerEntry> headerTickerEntries(){
        List<TickerEntry> entries=new ArrayList<>();
        if(config.liveSevereWeatherMode)
            entries.add(new TickerEntry(
                    "⚠ SEVERE WEATHER TEST MODE • Rapid weather, alert, and radar monitoring active",
                    Theme.danger()
            ));

        if(alerts!=null){
            alerts.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(WeatherAlertPolicy::priority).reversed())
                    .forEach(alert->entries.add(new TickerEntry(
                            weatherAlertTickerText(alert),
                            weatherAlertColor(alert)
                    )));
        }

        if(config.tickerText!=null&&!config.tickerText.isBlank())
            entries.add(new TickerEntry(config.tickerText.trim(),Theme.text()));
        if(entries.isEmpty())
            entries.add(new TickerEntry(
                    "North Star operations monitoring active",
                    Theme.text()
            ));
        return entries;
    }

    private String weatherAlertTickerText(WeatherAlert alert){
        WeatherAlertPolicy.Level level=WeatherAlertPolicy.level(alert);
        String prefix=(level==WeatherAlertPolicy.Level.WARNING
                ||level==WeatherAlertPolicy.Level.CRITICAL)
                ?"⚠ SEVERE WEATHER • "
                :"⚠ WEATHER ALERT • ";
        return prefix+WeatherAlertPolicy.briefText(alert,150);
    }

    private Color weatherAlertColor(WeatherAlert alert){
        return weatherAlertColor(WeatherAlertPolicy.level(alert));
    }

    private Color weatherAlertColor(WeatherAlertPolicy.Level level){
        if(level==WeatherAlertPolicy.Level.CRITICAL
                ||level==WeatherAlertPolicy.Level.WARNING)
            return Theme.danger();
        if(level==WeatherAlertPolicy.Level.WATCH
                ||level==WeatherAlertPolicy.Level.ADVISORY)
            return Theme.warn();
        return Theme.muted();
    }

    private void refreshHeaderTicker(){
        if(headerTicker!=null)
            headerTicker.setEntries(headerTickerEntries());
    }

    private void toggleDashboardLayoutFromGear(){
        if(!AuthorizationService.allowed(Permission.DASHBOARD_LAYOUT))return;

        if(!"Dashboard".equalsIgnoreCase(activeWorkspaceRoute))
            showDashboardRoute();

        if(dashboardGrid==null)return;
        dashboardLayoutEditing=!dashboardLayoutEditing;
        dashboardGrid.setEditMode(dashboardLayoutEditing);
        if(!dashboardLayoutEditing)ConfigService.save(config);
        updateDashboardLayoutGearState();
    }

    private void finishDashboardLayoutEditing(){
        if(!dashboardLayoutEditing)return;
        if(dashboardGrid!=null)dashboardGrid.setEditMode(false);
        dashboardLayoutEditing=false;
        ConfigService.save(config);
        updateDashboardLayoutGearState();
    }

    private void updateDashboardLayoutGearState(){
        if(dashboardLayoutGear==null)return;
        dashboardLayoutGear.setToolTipText(
                dashboardLayoutEditing
                        ?"Save dashboard layout"
                        :"Customize dashboard layout");
        dashboardLayoutGear.setBorder(BorderFactory.createLineBorder(
                dashboardLayoutEditing?Theme.accent():Theme.border(),
                dashboardLayoutEditing?2:1,
                true
        ));
        dashboardLayoutGear.repaint();
    }

    private JComponent buildWorkspace(){
        JPanel workspace=new JPanel(new BorderLayout());
        workspace.setBackground(Theme.bg());
        workspace.add(buildSidebar(),BorderLayout.WEST);

        workspaceContentHost=new JPanel(new BorderLayout());
        workspaceContentHost.setBackground(Theme.bg());
        workspace.add(workspaceContentHost,BorderLayout.CENTER);

        showDashboardRoute();
        return workspace;
    }

    private JComponent createDashboardView(){
        dashboardExtensions.clear();
        summaryExtensions.clear();
        dashboardBody=new JPanel();
        dashboardBody.setLayout(new BoxLayout(dashboardBody,BoxLayout.Y_AXIS));
        dashboardBody.setBackground(Theme.bg());
        dashboardBody.setBorder(new EmptyBorder(8,10,8,10));

        JComponent summary=buildSummaryStrip();
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE,64));
        summary.setPreferredSize(new Dimension(100,60));

        JComponent modules=buildModuleGrid();
        modules.setAlignmentX(Component.LEFT_ALIGNMENT);

        dashboardBody.add(summary);
        dashboardBody.add(Box.createVerticalStrut(6));
        dashboardBody.add(modules);
        dashboardBody.add(Box.createVerticalGlue());

        JScrollPane scroll=new JScrollPane(dashboardBody);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.bg());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    private void showDashboardRoute(){
        activeWorkspaceRoute="Dashboard";
        updateSidebarSelection();
        closeEmbeddedSettingsSession();
        releaseDashboardModules();

        if(workspaceContentHost==null)return;
        workspaceContentHost.removeAll();
        workspaceContentHost.add(createDashboardView(),BorderLayout.CENTER);
        workspaceContentHost.revalidate();
        workspaceContentHost.repaint();

        refreshVisibleModules();
        WorkspaceLifecycleV3.dashboardMounted(this);
    }

    private JComponent buildSidebar(){
        sidebarRouteButtons.clear();
        JPanel side=new JPanel();
        side.setBackground(Theme.panel());
        side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(14,10,16,10));

        side.add(sideDashboardButton());
        side.add(Box.createVerticalStrut(12));
        side.add(sideSectionLabel("OPERATIONS"));

        addPermittedSidePage(
                side,"☀  Weather","Weather","Data & Refresh",
                Permission.DATA_REFRESH);
        addPermittedSidePage(
                side,"▰  Traffic & Routes","Traffic & Routes","Routes",
                Permission.ROUTES);
        addPermittedSidePage(
                side,"▣  Operations Calendar","Operations Calendar",
                "Operations Calendar",Permission.OPERATIONS_CALENDAR);
        addPermittedSidePage(
                side,"▦  Employees","Employee Operations",
                "Employees",Permission.EMPLOYEE_OPERATIONS);
        addPermittedSidePage(
                side,"●  Pinned Locations","Pinned Locations",
                "Pinned Locations",Permission.PINNED_LOCATIONS);
        addPermittedSidePage(
                side,"◉  Sports","Sports","Sports",Permission.SPORTS);
        addPermittedSidePage(
                side,"▤  Main Showcase","Main Showcase",
                "Main Showcase",Permission.MAIN_SHOWCASE);

        side.add(Box.createVerticalStrut(12));
        side.add(sideSectionLabel("ADMINISTRATION"));

        if(AuthorizationService.allowed(Permission.DATA_REFRESH))
            side.add(sideDataCollectionButton());

        /*
         * Information Blocks and Operations Snapshot configuration share the
         * Workspace Setup page, so the sidebar routes to that source-owned tab.
         */
        addPermittedSidePage(
                side,"⚙  Workspace Setup","Workspace Setup",
                "Workspace Setup",Permission.DASHBOARD_LAYOUT);
        addPermittedSidePage(
                side,"▧  Media Library","Media Library",
                "Media Library",Permission.MEDIA_LIBRARY);
        addPermittedSidePage(
                side,"♜  Users & Access","Users & Access",
                "Users & Access",Permission.MANAGE_USERS);
        addPermittedSidePage(
                side,"⌁  General","General","General",
                Permission.GENERAL_SETTINGS);
        addPermittedSidePage(
                side,"↻  Data & Refresh","Data & Refresh",
                "Data & Refresh",Permission.DATA_REFRESH);
        addPermittedSidePage(
                side,"◈  API Providers","API Providers",
                "API Providers",Permission.API_ADMINISTRATION);
        addPermittedSidePage(
                side,"▥  API Usage","API Usage",
                "API Usage",Permission.API_USAGE);

        // Every authenticated user can reach their own account page.
        side.add(sideSettingsButton(
                "♙  My Account","My Account","My Account",null));

        JScrollPane scroll=new JScrollPane(
                side,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(BorderFactory.createMatteBorder(
                0,0,0,1,Theme.border()));
        scroll.getViewport().setBackground(Theme.panel());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(212,100));
        return scroll;
    }

    private JLabel sideSectionLabel(String text){
        JLabel label=new JLabel(text);
        label.setForeground(Theme.muted());
        label.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));
        label.setBorder(new EmptyBorder(0,10,6,0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton sideDashboardButton(){
        JButton button=createSidebarButton("▦  Dashboard",true);
        sidebarRouteButtons.put("Dashboard",button);
        button.addActionListener(e->showDashboardRoute());
        updateSidebarSelection();
        return button;
    }

    private JButton sideDataCollectionButton(){
        JButton button=createSidebarButton("▣  Data Collection",false);
        sidebarRouteButtons.put("Data Collection",button);
        button.addActionListener(e->showWorkspaceExtensionRoute(
                "Data Collection",new DataCollectionPanel()));
        updateSidebarSelection();
        return button;
    }

    private void addPermittedSidePage(
            JPanel parent,
            String label,
            String routeTitle,
            String settingsTab,
            Permission permission
    ){
        if(permission==null||AuthorizationService.allowed(permission))
            parent.add(sideSettingsButton(
                    label,routeTitle,settingsTab,permission));
    }

    private JButton sideSettingsButton(
            String text,
            String routeTitle,
            String settingsTab,
            Permission permission
    ){
        JButton button=createSidebarButton(text,false);
        sidebarRouteButtons.put(routeTitle,button);
        button.addActionListener(e->openWorkspaceSettingsPage(
                routeTitle,settingsTab,permission));
        updateSidebarSelection();
        return button;
    }

    private JButton createSidebarButton(String text,boolean bold){
        RoundedSidebarButton button=new RoundedSidebarButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(new Font(
                Font.SANS_SERIF,
                bold?Font.BOLD:Font.PLAIN,
                12
        ));
        button.setFocusPainted(false);
        button.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
        button.setBorder(new EmptyBorder(9,12,9,12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void updateSidebarSelection(){
        for(Map.Entry<String,JButton> entry:sidebarRouteButtons.entrySet()){
            boolean active=entry.getKey().equalsIgnoreCase(
                    activeWorkspaceRoute==null?"":activeWorkspaceRoute);
            JButton button=entry.getValue();
            button.putClientProperty("northstar.sidebar.active",active);
            button.setForeground(active?Color.WHITE:Theme.muted());
            button.repaint();
        }
    }


    /**
     * Stable source-backed extension point for optional workspace modules.
     * Dynamic modules may register a route without reflecting into private
     * sidebar fields or private button factories.
     */
    public boolean registerWorkspaceExtensionRoute(
            String route,
            String label,
            String anchorRoute,
            boolean afterAnchor,
            Runnable action
    ){
        if(route==null||route.isBlank()||label==null||label.isBlank()||action==null)return false;
        JButton existing=sidebarRouteButtons.get(route);
        if(existing!=null&&existing.getParent()!=null)return true;
        JButton anchorButton=sidebarRouteButtons.get(anchorRoute);
        if(anchorButton==null||anchorButton.getParent()==null)return false;

        JButton button=createSidebarButton(label,false);
        button.addActionListener(e->action.run());
        sidebarRouteButtons.put(route,button);

        Container parent=anchorButton.getParent();
        int index=parent.getComponentCount();
        Component[] children=parent.getComponents();
        for(int i=0;i<children.length;i++){
            if(children[i]==anchorButton){index=i+(afterAnchor?1:0);break;}
        }
        parent.add(button,Math.max(0,Math.min(index,parent.getComponentCount())));
        parent.revalidate();
        parent.repaint();
        updateSidebarSelection();
        return true;
    }

    /** Mounts an extension-owned full workspace page through the canonical route lifecycle. */
    public boolean showWorkspaceExtensionRoute(String route,JComponent content){
        if(route==null||route.isBlank()||content==null||workspaceContentHost==null)return false;
        activeWorkspaceRoute=route;
        closeEmbeddedSettingsSession();
        releaseDashboardModules();
        workspaceContentHost.removeAll();
        workspaceContentHost.add(content,BorderLayout.CENTER);
        workspaceContentHost.revalidate();
        workspaceContentHost.repaint();
        updateSidebarSelection();
        return true;
    }

    /**
     * Mounts an identified dashboard extension without exposing dashboardBody.
     * Identity is tracked by the workspace owner, so extension code never needs
     * to scan the Swing component tree to detect or remove its own surface.
     */
    public boolean mountDashboardExtension(String id,JComponent component,int preferredIndex){
        if(id==null||id.isBlank()||component==null||dashboardGrid==null)return false;
        JComponent existing=dashboardExtensions.get(id);
        if(existing!=null&&existing.getParent()!=null)return true;
        String label="northstar.ai.compact".equalsIgnoreCase(id)
                ?"NorthStar Intelligence"
                :id;
        String defaultSpec="northstar.ai.compact".equalsIgnoreCase(id)
                ?"0,12,24,3"
                :"0,12,24,2";
        dashboardGrid.addTile(id,label,component,defaultSpec);
        dashboardExtensions.put(id,component);
        dashboardGrid.revalidate();
        dashboardGrid.repaint();
        return true;
    }

    /** Removes a dashboard extension previously mounted by id. */
    public boolean removeDashboardExtension(String id){
        if(id==null||id.isBlank())return false;
        dashboardExtensions.remove(id);
        return dashboardGrid!=null&&dashboardGrid.removeTile(id);
    }

    /** Mounts a compact extension inside the summary/header strip. */
    public boolean mountSummaryExtension(String id,JComponent component){
        if(id==null||id.isBlank()||component==null||summaryExtensionsHost==null)return false;
        JComponent existing=summaryExtensions.get(id);
        if(existing!=null&&existing.getParent()==summaryExtensionsHost)return true;
        summaryExtensionsHost.removeAll();
        summaryExtensionsHost.add(component,BorderLayout.CENTER);
        summaryExtensions.put(id,component);
        summaryExtensionsHost.revalidate();
        summaryExtensionsHost.repaint();
        return true;
    }

    public boolean removeSummaryExtension(String id){
        JComponent component=summaryExtensions.remove(id);
        if(component==null||summaryExtensionsHost==null)return false;
        summaryExtensionsHost.remove(component);
        summaryExtensionsHost.revalidate();
        summaryExtensionsHost.repaint();
        return true;
    }

    /** Returns the canonical active route for source-backed extension coordination. */
    public String activeWorkspaceRouteName(){
        return activeWorkspaceRoute==null?"":activeWorkspaceRoute;
    }


    /** Returns the live workspace configuration to trusted source-backed integrations. */
    public AppConfig workspaceConfigForExtensions(){
        return config;
    }

    private JComponent buildSummaryStrip(){
        JPanel strip=new JPanel(new BorderLayout(20,0));
        strip.setOpaque(false);

        JPanel greeting=new JPanel();
        greeting.setOpaque(false);
        greeting.setLayout(new BoxLayout(greeting,BoxLayout.Y_AXIS));
        UserAccount user=SessionManager.currentUser();
        String first=user==null?"Team":firstName(user.friendlyName());
        JLabel hello=new JLabel(greeting()+", "+first);
        hello.setForeground(Theme.text());
        hello.setFont(new Font(Font.SANS_SERIF,Font.BOLD,19));
        dateTimeLabel=new JLabel();
        dateTimeLabel.setForeground(Theme.muted());
        dateTimeLabel.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        greeting.add(hello);greeting.add(Box.createVerticalStrut(3));greeting.add(dateTimeLabel);
        strip.add(greeting,BorderLayout.WEST);

        JPanel quick=new JPanel(new GridLayout(1,2,12,0));
        quick.setOpaque(false);
        topWeatherLabel=quickSummary("WEATHER","--°F","Loading weather...");
        topTrafficLabel=quickSummary("TRAFFIC","Checking routes...","Live traffic");
        quick.add(topWeatherLabel.getParent());
        quick.add(topTrafficLabel.getParent());

        summaryExtensionsHost=new JPanel(new BorderLayout());
        summaryExtensionsHost.setOpaque(false);
        summaryExtensionsHost.setPreferredSize(new Dimension(330,54));

        JPanel rightSummary=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));
        rightSummary.setOpaque(false);
        rightSummary.add(summaryExtensionsHost);
        rightSummary.add(quick);
        strip.add(rightSummary,BorderLayout.EAST);
        return strip;
    }

    private JLabel quickSummary(String title,String value,String detail){
        JPanel box=new JPanel();
        box.setOpaque(false);
        box.setBorder(BorderFactory.createMatteBorder(
                0,1,0,0,Theme.border()));
        box.setLayout(new BoxLayout(box,BoxLayout.Y_AXIS));
        JLabel titleLabel=new JLabel(title);
        titleLabel.setForeground(Theme.muted());
        titleLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));
        JLabel valueLabel=new JLabel(value);
        valueLabel.setForeground(Theme.text());
        valueLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        JLabel detailLabel=new JLabel(detail);
        detailLabel.setForeground(Theme.muted());
        detailLabel.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
        box.add(titleLabel);box.add(valueLabel);box.add(detailLabel);
        box.setBorder(BorderFactory.createCompoundBorder(
                box.getBorder(),new EmptyBorder(0,14,0,22)));
        valueLabel.putClientProperty("detailLabel",detailLabel);
        return valueLabel;
    }

    private JComponent buildModuleGrid(){
        JPanel container=new JPanel(new BorderLayout(0,8));
        container.setOpaque(false);

        dashboardGrid=new DashboardGridPanel(
                config.workspaceDashboardLayout,
                ()->ConfigService.save(config)
        );

        if(moduleEnabled("WEATHER")){
            weatherModule=weatherCard();
            dashboardGrid.addTile("WEATHER","Local Weather",weatherModule,"0,0,6,8");
        }
        if(moduleEnabled("TRAFFIC_MAP"))
            dashboardGrid.addTile("SHOWCASE","Main Showcase",mapCard(),"6,0,12,8");
        if(moduleEnabled("UPCOMING_EVENTS")){
            eventsModule=eventsCard();
            dashboardGrid.addTile("UPCOMING_EVENTS","Upcoming Events",eventsModule,"18,0,6,4");
        }
        if(moduleEnabled("TEAM_CELEBRATIONS")){
            celebrationsModule=celebrationsCard();
            dashboardGrid.addTile("TEAM_CELEBRATIONS","Team Celebrations",celebrationsModule,"18,4,6,4");
        }
        if(config.workspaceInfoStripEnabled){
            infoStripModule=informationStripCard();
            dashboardGrid.addTile("INFORMATION","Information",infoStripModule,"0,8,24,2");
        }
        if(moduleEnabled("OPERATIONS_SNAPSHOT")){
            operationsModule=operationsSnapshotCard();
            dashboardGrid.addTile("OPERATIONS_SNAPSHOT","Operations Snapshot",operationsModule,"0,10,24,2");
        }


        container.add(dashboardGrid,BorderLayout.CENTER);
        return container;
    }

    private JPanel card(String title){
        RoundedPanel card=new RoundedPanel(14);
        card.setBackground(Theme.panel());
        card.putClientProperty("outlineColor",Theme.border());
        card.setLayout(new BorderLayout(0,10));
        card.setBorder(new EmptyBorder(12,12,12,12));
        JLabel heading=new JLabel(title.toUpperCase(Locale.ROOT));
        heading.setForeground(Theme.text());
        heading.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
        card.add(heading,BorderLayout.NORTH);
        return card;
    }

    private JPanel weatherCard(){
        JPanel card=card("Local Weather");
        card.setPreferredSize(new Dimension(340,340));
        card.setMinimumSize(new Dimension(300,300));
        card.setMaximumSize(new Dimension(430,340));
        renderWeatherCard(card);
        return card;
    }

    private void renderWeatherCard(JPanel card){
        Component north=((BorderLayout)card.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);
        card.removeAll();
        if(north!=null)card.add(north,BorderLayout.NORTH);

        if(weather==null){
            JPanel loading=new JPanel(new GridBagLayout());
            loading.setOpaque(false);
            JLabel label=new JLabel("Loading current conditions...");
            label.setForeground(Theme.muted());
            loading.add(label);
            card.add(loading,BorderLayout.CENTER);
            card.revalidate();
            card.repaint();
            return;
        }

        JPanel body=new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));

        JPanel current=new JPanel(new BorderLayout(12,0));
        current.setOpaque(false);
        current.setAlignmentX(Component.LEFT_ALIGNMENT);
        current.setMaximumSize(new Dimension(Integer.MAX_VALUE,94));

        JPanel words=new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));

        JLabel temp=new JLabel(Math.round(weather.temperatureF())+"°F");
        temp.setForeground(Theme.text());
        temp.setFont(new Font(Font.SANS_SERIF,Font.BOLD,38));

        JLabel condition=new JLabel(weather.condition());
        condition.setForeground(Theme.text());
        condition.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));

        JLabel feels=new JLabel(
                "Feels like "+Math.round(weather.apparentTemperatureF())+"°");
        feels.setForeground(Theme.muted());
        feels.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));

        words.add(temp);
        words.add(condition);
        words.add(Box.createVerticalStrut(2));
        words.add(feels);

        JLabel weatherIcon=new JLabel(
                new DashboardIcon(
                        DashboardIcon.weatherKind(weather.condition()),
                        62,
                        Theme.accent()
                ),
                SwingConstants.CENTER
        );
        weatherIcon.setPreferredSize(new Dimension(74,74));

        current.add(words,BorderLayout.CENTER);
        current.add(weatherIcon,BorderLayout.EAST);
        body.add(current);
        body.add(Box.createVerticalStrut(10));
        body.add(horizontalRule());
        body.add(Box.createVerticalStrut(10));

        JPanel stats=new JPanel(new GridLayout(2,2,18,7));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        stats.add(stat("High",Math.round(weather.highF())+"°"));
        stats.add(stat(
                "Humidity",
                Double.isFinite(weather.humidityPercent())
                        ?Math.round(weather.humidityPercent())+"%"
                        :"—"
        ));
        stats.add(stat("Low",Math.round(weather.lowF())+"°"));
        stats.add(stat("Wind",Math.round(weather.windMph())+" mph"));
        body.add(stats);

        body.add(Box.createVerticalStrut(10));
        body.add(horizontalRule());
        body.add(Box.createVerticalStrut(10));

        JPanel forecast=new JPanel(new BorderLayout(0,7));
        forecast.setOpaque(false);
        forecast.setAlignmentX(Component.LEFT_ALIGNMENT);
        forecast.setMaximumSize(new Dimension(Integer.MAX_VALUE,92));

        JPanel days=new JPanel(new GridLayout(1,5,6,0));
        days.setOpaque(false);

        List<WeatherSnapshot.DailyPoint> daily=weather.daily();
        int count=Math.min(5,daily==null?0:daily.size());

        if(count==0){
            forecast.add(empty("Forecast unavailable"),BorderLayout.CENTER);
        }else{
            for(int i=0;i<count;i++)
                days.add(dailyForecastCell(daily.get(i)));

            forecast.add(days,BorderLayout.CENTER);
        }

        body.add(forecast);
        body.add(Box.createVerticalGlue());

        card.add(body,BorderLayout.CENTER);
        card.revalidate();
        card.repaint();
    }

    private JComponent horizontalRule(){
        JSeparator separator=new JSeparator();
        separator.setForeground(Theme.border());
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        return separator;
    }

    private JPanel stat(String label,String value){
        JPanel p=new JPanel(new BorderLayout(8,0));
        p.setOpaque(false);

        JLabel l=new JLabel(label);
        l.setForeground(Theme.muted());
        l.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));

        JLabel v=new JLabel(value);
        v.setForeground(Theme.text());
        v.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));

        p.add(l,BorderLayout.WEST);
        p.add(v,BorderLayout.EAST);
        return p;
    }

    private JComponent dailyForecastCell(WeatherSnapshot.DailyPoint point){
        JPanel day=new JPanel();
        day.setOpaque(false);
        day.setLayout(new BoxLayout(day,BoxLayout.Y_AXIS));

        LocalDate date;
        try{date=LocalDate.parse(point.date());}
        catch(Exception ex){date=LocalDate.now();}

        JLabel weekday=new JLabel(
                date.format(DateTimeFormatter.ofPattern("EEE")).toUpperCase(),
                SwingConstants.CENTER
        );
        weekday.setForeground(Theme.muted());
        weekday.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));
        weekday.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel icon=new JLabel(
                new DashboardIcon(
                        DashboardIcon.weatherKind(
                                OpenMeteoService.condition(point.weatherCode())),
                        34,
                        Theme.accent()
                )
        );
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel high=new JLabel(Math.round(point.highF())+"°");
        high.setForeground(Theme.text());
        high.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
        high.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel low=new JLabel(Math.round(point.lowF())+"°");
        low.setForeground(Theme.muted());
        low.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));
        low.setAlignmentX(Component.CENTER_ALIGNMENT);

        day.add(weekday);
        day.add(Box.createVerticalStrut(5));
        day.add(icon);
        day.add(Box.createVerticalStrut(4));
        day.add(high);
        day.add(low);
        return day;
    }

    private JComponent mapCard(){
        JPanel card=card("Main Showcase");

        map=new TileMapPanel(config,http);
        map.setPreferredSize(new Dimension(760,500));
        map.setAlerts(alerts);

        /*
         * Reuse the proven Classic Main Showcase engine in the larger v4
         * center region. This preserves announcement/media rotation,
         * Operations Calendar slides, team-recognition slides and severe-
         * weather map priority without maintaining a second slideshow engine.
         */
        mainShowcase=new MainShowcasePanel(config,map);
        mainShowcase.setSevereWeatherActive(
                severeWeatherActive());
        mainShowcase.setCelebrationListener(active->{
            if(active&&overlayEffects!=null)
                overlayEffects.celebrate();
        });

        card.add(mainShowcase,BorderLayout.CENTER);

        JPanel legend=new JPanel(new BorderLayout());
        legend.setOpaque(false);

        JLabel text=new JLabel(
                config.mainShowcaseMediaEnabled
                        ?"Map + managed media rotate automatically • severe weather can pin the map"
                        :"Live TomTom traffic • enable Main Showcase media to rotate announcement slides"
        );
        text.setForeground(Theme.muted());
        text.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));

        JLabel interval=new JLabel(
                "Rotation: "+Math.max(5,config.mainShowcaseIntervalSeconds)+" sec"
        );
        interval.setForeground(Theme.muted());
        interval.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));

        legend.add(text,BorderLayout.WEST);
        legend.add(interval,BorderLayout.EAST);
        card.add(legend,BorderLayout.SOUTH);
        return card;
    }

    private JPanel eventsCard(){
        JPanel card=card("Upcoming Events");
        renderEvents(card);
        return card;
    }

    private void renderEvents(JPanel card){
        Component north=((BorderLayout)card.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);
        card.removeAll();
        if(north!=null)card.add(north,BorderLayout.NORTH);

        JPanel content=new JPanel(new BorderLayout(0,8));
        content.setOpaque(false);

        JPanel rows=new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));

        LocalDate today=LocalDate.now();
        List<OperationEvent> events=config.operationEvents.stream()
                .filter(OperationEvent::enabled)
                .filter(e->e.endDate()!=null&&!e.endDate().isBefore(today))
                .sorted(Comparator.comparing(OperationEvent::startDate))
                .limit(3)
                .toList();

        if(events.isEmpty()){
            rows.add(empty("No upcoming operations events"));
        }else{
            for(int i=0;i<events.size();i++){
                rows.add(eventRow(events.get(i)));
                if(i<events.size()-1)rows.add(rowSeparator());
            }
        }

        JButton fullCalendar=workspaceFooterButton("View Full Calendar");
        fullCalendar.addActionListener(e->openSettings("Operations Calendar"));

        content.add(rows,BorderLayout.CENTER);
        content.add(fullCalendar,BorderLayout.SOUTH);

        card.add(content,BorderLayout.CENTER);
        card.revalidate();
        card.repaint();
    }

    private JComponent eventRow(OperationEvent event){
        JPanel row=new JPanel(new BorderLayout(10,0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(7,0,7,0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,92));

        JPanel iconTile=new RoundedPanel(11);
        iconTile.setBackground(eventTagBackground(event));
        iconTile.setLayout(new GridBagLayout());
        iconTile.setPreferredSize(new Dimension(50,50));
        iconTile.setMaximumSize(new Dimension(50,50));

        JLabel icon=new JLabel(eventTagIcon(event,40),SwingConstants.CENTER);
        iconTile.add(icon);

        JPanel words=new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));

        JLabel name=new JLabel(event.name());
        name.setForeground(Theme.text());
        name.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));

        JLabel date=new JLabel(
                event.startDate().format(DAY_FORMAT)
                +"  •  "+event.type().display()
        );
        date.setForeground(Theme.muted());
        date.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));

        words.add(Box.createVerticalGlue());
        words.add(name);
        words.add(Box.createVerticalStrut(2));
        words.add(date);
        words.add(Box.createVerticalGlue());

        row.add(iconTile,BorderLayout.WEST);
        row.add(words,BorderLayout.CENTER);
        return row;
    }

    private String eventDashboardGlyphKey(OperationEvent event){
        String name=event==null||event.name()==null?"":event.name().toLowerCase(Locale.ROOT);
        if(name.contains("new year"))return "new_year";
        if(name.contains("martin luther king")||name.contains("mlk"))return "mlk";
        if(name.contains("president"))return "presidents_day";
        if(name.contains("memorial"))return "memorial_day";
        if(name.contains("independence")||name.contains("fourth of july")||name.contains("4th of july"))return "independence_day";
        if(name.contains("labor day"))return "labor_day";
        if(name.contains("columbus")||name.contains("indigenous peoples"))return "columbus_day";
        if(name.contains("veteran"))return "veterans_day";
        if(name.contains("thanksgiving"))return "thanksgiving";
        if(name.contains("christmas"))return "christmas";
        if(name.contains("halloween"))return "halloween";
        if(name.contains("valentine"))return "valentines_day";
        if(name.contains("st. patrick")||name.contains("st patrick"))return "st_patricks_day";
        if(name.contains("easter"))return "easter";
        if(name.contains("mother"))return "mothers_day";
        if(name.contains("father"))return "fathers_day";
        if(name.contains("juneteenth"))return "juneteenth";
        if(name.contains("safety"))return "safety_milestone";
        if(name.contains("project complete")||name.contains("project completion"))return "project_complete";
        if(name.contains("milestone"))return "milestone";
        if(name.contains("weather alert")||name.contains("severe weather")||name.contains("storm alert"))return "weather_alert";
        if(name.contains("holiday season"))return "holiday_season";
        if(name.contains("seasonal"))return "seasonal";
        if(name.contains("promotion"))return "promotion";
        if(name.contains("graduation"))return "graduation";
        if(name.contains("new hire"))return "new_hire";
        if(name.contains("welcome"))return "welcome";
        if(name.contains("congrat"))return "congratulations";
        if(name.contains("team success"))return "team_success";
        if(name.contains("thank you"))return "thank_you";
        if(name.contains("celebrat"))return "lets_celebrate";
        return "special_event";
    }

    private String eventGlyphAssetKey(OperationEvent event){
        String kind=eventVisualKind(event);
        return switch(kind){
            case "CHRISTMAS"->"christmas";
            case "THANKSGIVING"->"thanksgiving";
            case "INDEPENDENCE","NEW_YEAR"->"fireworks";
            case "HALLOWEEN"->"halloween";
            case "GOOD_FRIDAY"->"good_friday";
            case "EASTER"->"easter";
            case "LABOR","MEMORIAL","VETERANS"->"american";
            case "FOOD"->"food";
            case "TOAST"->"toast";
            default->"calendar_generic";
        };
    }

    private Color eventTagBackground(OperationEvent event){
        /*
         * Calendar artwork follows the supplied bold glyph-sheet language:
         * one neutral tile + one strong monochrome silhouette. Holiday color
         * coding from earlier builds has intentionally been removed.
         */
        return Theme.panel2();
    }

    private Icon eventTagIcon(OperationEvent event,int size){
        Icon supplied=NorthStarDashboardGlyphs.icon(eventDashboardGlyphKey(event),size);
        if(supplied!=null)return supplied;
        return NorthStarDashboardGlyphs.icon("special_event",size);
    }

    /* Legacy vector event glyph painting is retained below only for historical
     * source compatibility; dashboard Events no longer route through it. */
    private Icon legacyEventTagIcon(OperationEvent event,int size){
        BufferedImage image=new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();

        try{
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            g.setStroke(new BasicStroke(
                    Math.max(2f,size/18f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            String kind=eventVisualKind(event);
            Color foreground=Theme.text();
            g.setColor(foreground);

            int c=size/2;

            switch(kind){
                case "CHRISTMAS"->{
                    // Stylized evergreen tree.
                    Polygon top=new Polygon(
                            new int[]{c,c-size/5,c+size/5},
                            new int[]{size/7,size/2,size/2},
                            3
                    );
                    Polygon bottom=new Polygon(
                            new int[]{c,c-size/3,c+size/3},
                            new int[]{size/3,size*3/4,size*3/4},
                            3
                    );
                    g.fill(top);
                    g.fill(bottom);
                    g.fillRoundRect(
                            c-size/18,size*3/4,
                            size/9,size/7,2,2
                    );
                }
                case "THANKSGIVING"->drawTurkey(g,size);
                case "INDEPENDENCE","NEW_YEAR"->{
                    // Firework burst.
                    for(int i=0;i<8;i++){
                        double a=i*Math.PI/4;
                        int x1=(int)(c+Math.cos(a)*size*.12);
                        int y1=(int)(c+Math.sin(a)*size*.12);
                        int x2=(int)(c+Math.cos(a)*size*.34);
                        int y2=(int)(c+Math.sin(a)*size*.34);
                        g.drawLine(x1,y1,x2,y2);
                    }
                    g.fillOval(c-size/18,c-size/18,size/9,size/9);
                }
                case "HALLOWEEN"->{
                    // Pumpkin.
                    g.fillOval(size/6,size/4,size*2/3,size/2);
                    g.setColor(eventTagBackground(event));
                    g.fillPolygon(
                            new int[]{size/3,size*2/5,size/2-size/12},
                            new int[]{size/2,size/2+size/10,size/2},
                            3
                    );
                    g.fillPolygon(
                            new int[]{size*2/3,size*3/5,size/2+size/12},
                            new int[]{size/2,size/2+size/10,size/2},
                            3
                    );
                    g.setColor(foreground);
                    g.fillRect(c-size/20,size/6,size/10,size/8);
                }
                case "VALENTINE"->{
                    // Heart.
                    java.awt.geom.Path2D heart=new java.awt.geom.Path2D.Double();
                    heart.moveTo(c,size*4/5);
                    heart.curveTo(
                            size/8,size/2,
                            size/5,size/4,
                            c,size*2/5
                    );
                    heart.curveTo(
                            size*4/5,size/4,
                            size*7/8,size/2,
                            c,size*4/5
                    );
                    g.fill(heart);
                }
                case "ST_PATRICK"->{
                    // Four-leaf clover.
                    int d=size/4;
                    g.fillOval(c-d,c-d,d,d);
                    g.fillOval(c,c-d,d,d);
                    g.fillOval(c-d,c,d,d);
                    g.fillOval(c,c,d,d);
                    g.drawLine(c,c+size/10,c-size/10,size*5/6);
                }
                case "LABOR","PRESIDENTS"->drawAmericanFlag(g,size);
                case "MEMORIAL"->drawSalute(g,size);
                case "VETERANS"->{
                    drawAmericanFlag(g,size);
                    g.setColor(Theme.text());
                    drawStar(g,size*3/4,size/4,size*.13);
                }
                case "JUNETEENTH"->{
                    drawAmericanFlag(g,size);
                    g.setColor(Theme.text());
                    drawStar(g,c,c,size*.19);
                }
                case "MLK"->drawDove(g,size);
                case "EASTER"->drawEasterEgg(g,size);
                default->{
                    // Generic calendar image for company/custom events.
                    g.drawRoundRect(
                            size/6,size/5,size*2/3,size*3/5,
                            size/10,size/10
                    );
                    g.drawLine(size/6,size*2/5,size*5/6,size*2/5);
                    g.drawLine(size/3,size/7,size/3,size/4);
                    g.drawLine(size*2/3,size/7,size*2/3,size/4);
                    g.fillRect(size/3,size/2,size/9,size/9);
                    g.fillRect(size/2,size/2,size/9,size/9);
                }
            }
        }finally{
            g.dispose();
        }

        return new ImageIcon(image);
    }

    private void drawTurkey(Graphics2D g,int size){
        int c=size/2;
        Color glyph=Theme.text();
        Color knockout=Theme.panel2();

        // Fan tail feathers.
        g.setColor(glyph);
        for(int i=0;i<5;i++){
            double angle=Math.toRadians(-145+i*36);
            int fx=(int)(c+Math.cos(angle)*size*.20);
            int fy=(int)(size*.52+Math.sin(angle)*size*.20);
            g.fillOval(
                    fx-size/9,
                    fy-size/5,
                    size*2/9,
                    size*2/5
            );
        }

        // Body and head.
        g.fillOval(size/3,size*2/5,size/3,size*2/5);
        g.fillOval(size*9/20,size/4,size/5,size/5);

        // Eye and separation detail as negative space.
        g.setColor(knockout);
        g.fillOval(
                size*51/100,
                size*31/100,
                Math.max(3,size/18),
                Math.max(3,size/18)
        );

        // Beak + wattle remain part of the bold silhouette.
        g.setColor(glyph);
        g.fillPolygon(
                new int[]{size*13/20,size*4/5,size*13/20},
                new int[]{size*7/20,size*2/5,size*9/20},
                3
        );
        g.fillOval(size*3/5,size*9/20,size/10,size/5);
    }

    private void drawAmericanFlag(Graphics2D g,int size){
        int x=size/8;
        int y=size/5;
        int w=size*3/4;
        int h=size*3/5;

        Color glyph=Theme.text();
        Color knockout=Theme.panel2();

        // Pole.
        g.setColor(glyph);
        g.fillRoundRect(
                x-size/18,
                y-size/16,
                Math.max(3,size/18),
                h+size/5,
                size/30,
                size/30
        );

        // Filled flag body.
        g.fillRoundRect(x,y,w,h,size/18,size/18);

        // Knock out alternating stripes for the glyph-sheet silhouette style.
        g.setColor(knockout);
        int stripe=Math.max(2,h/7);
        for(int i=1;i<7;i+=2)
            g.fillRect(x+w*2/5,y+i*stripe,w*3/5,stripe);

        // Canton remains solid; cut tiny star dots into it.
        for(int row=0;row<3;row++){
            for(int col=0;col<3;col++){
                int sx=x+w/13+col*w/10;
                int sy=y+h/12+row*h/10;
                g.fillOval(
                        sx,sy,
                        Math.max(2,size/28),
                        Math.max(2,size/28)
                );
            }
        }
    }

    private void drawSalute(Graphics2D g,int size){
        g.setColor(Theme.text());

        // Head/shoulders.
        g.fillOval(size/4,size/6,size/3,size/3);
        g.drawArc(size/7,size/2,size*2/3,size/2,20,140);

        // Saluting arm and hand.
        g.setStroke(new BasicStroke(
                Math.max(3f,size/10f),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));
        g.drawLine(size*3/5,size*3/5,size*4/5,size*2/5);
        g.drawLine(size*4/5,size*2/5,size*2/3,size/3);
    }

    private void drawDove(Graphics2D g,int size){
        g.setColor(Theme.text());
        java.awt.geom.Path2D bird=new java.awt.geom.Path2D.Double();
        bird.moveTo(size*.18,size*.58);
        bird.curveTo(size*.38,size*.48,size*.38,size*.25,size*.56,size*.32);
        bird.curveTo(size*.67,size*.36,size*.73,size*.44,size*.82,size*.42);
        bird.curveTo(size*.72,size*.52,size*.65,size*.58,size*.58,size*.60);
        bird.curveTo(size*.45,size*.73,size*.31,size*.71,size*.18,size*.58);
        g.fill(bird);
        g.drawLine(
                (int)(size*.20),(int)(size*.59),
                (int)(size*.12),(int)(size*.70)
        );
    }

    private void drawEasterEgg(Graphics2D g,int size){
        g.setColor(Theme.text());
        g.fillOval(size/4,size/8,size/2,size*3/4);

        g.setColor(Theme.panel2());
        g.setStroke(new BasicStroke(Math.max(2f,size/18f)));
        g.drawArc(size/4,size*5/16,size/2,size/4,0,180);
        g.drawArc(size/4,size*7/16,size/2,size/4,180,180);
        g.fillOval(size*2/5,size/4,size/12,size/12);
        g.fillOval(size*3/5,size*5/8,size/12,size/12);
    }

    private void drawStar(Graphics2D g,int cx,int cy,double radius){
        Polygon star=new Polygon();
        for(int i=0;i<10;i++){
            double angle=-Math.PI/2+i*Math.PI/5;
            double r=(i%2==0)?radius:radius*.44;
            star.addPoint(
                    (int)Math.round(cx+Math.cos(angle)*r),
                    (int)Math.round(cy+Math.sin(angle)*r)
            );
        }
        g.fill(star);
    }

    private String eventVisualKind(OperationEvent event){
        String name=event==null||event.name()==null
                ?""
                :event.name().toLowerCase(Locale.ROOT);

        if(name.contains("christmas"))return "CHRISTMAS";
        if(name.contains("thanksgiving"))return "THANKSGIVING";
        if(name.contains("good friday"))return "GOOD_FRIDAY";
        if(name.contains("catering")
                ||name.contains("breakfast")
                ||name.contains("lunch")
                ||name.contains("dinner")
                ||name.contains("food"))return "FOOD";
        if(name.contains("toast")
                ||name.contains("celebration reception"))return "TOAST";
        if(name.contains("independence")
                ||name.contains("fourth of july")
                ||name.contains("4th of july"))return "INDEPENDENCE";
        if(name.contains("halloween"))return "HALLOWEEN";
        if(name.contains("valentine"))return "VALENTINE";
        if(name.contains("st. patrick")
                ||name.contains("st patrick"))return "ST_PATRICK";
        if(name.contains("labor day"))return "LABOR";
        if(name.contains("memorial"))return "MEMORIAL";
        if(name.contains("veteran"))return "VETERANS";
        if(name.contains("new year"))return "NEW_YEAR";
        if(name.contains("martin luther king")
                ||name.contains("mlk"))return "MLK";
        if(name.contains("president"))return "PRESIDENTS";
        if(name.contains("easter"))return "EASTER";
        if(name.contains("juneteenth"))return "JUNETEENTH";
        return "GENERIC";
    }

    private JPanel celebrationsCard(){
        JPanel card=card("Team Celebrations");
        renderCelebrations(card);
        return card;
    }

    private void renderCelebrations(JPanel card){
        Component north=((BorderLayout)card.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);
        card.removeAll();
        if(north!=null)card.add(north,BorderLayout.NORTH);

        JPanel content=new JPanel(new BorderLayout(0,8));
        content.setOpaque(false);

        JPanel rows=new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));

        List<UpcomingCelebration> upcoming=upcomingCelebrations(2);
        if(upcoming.isEmpty()){
            rows.add(empty("No upcoming team celebrations"));
        }else{
            for(int i=0;i<upcoming.size();i++){
                rows.add(celebrationRow(upcoming.get(i)));
                if(i<upcoming.size()-1)rows.add(rowSeparator());
            }
        }

        JButton viewAll=workspaceFooterButton("View All");
        viewAll.addActionListener(e->openSettings("Employees"));

        content.add(rows,BorderLayout.CENTER);
        content.add(viewAll,BorderLayout.SOUTH);

        card.add(content,BorderLayout.CENTER);
        card.revalidate();
        card.repaint();
    }

    private JComponent celebrationRow(UpcomingCelebration item){
        JPanel row=new JPanel(new BorderLayout(9,0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8,0,8,0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,96));

        JPanel recognitionIcon=new RoundedPanel(11);
        recognitionIcon.setBackground(Theme.panel2());
        recognitionIcon.setLayout(new GridBagLayout());
        recognitionIcon.setPreferredSize(new Dimension(64,64));
        recognitionIcon.setMinimumSize(new Dimension(64,64));
        recognitionIcon.setMaximumSize(new Dimension(64,64));

        JLabel glyph=new JLabel(
                celebrationTagIcon(item,48),
                SwingConstants.CENTER
        );
        recognitionIcon.add(glyph);

        JLabel avatar=new JLabel(employeeAvatar(item.person(),60));
        avatar.setPreferredSize(new Dimension(64,64));

        JPanel identity=new JPanel(new BorderLayout(8,0));
        identity.setOpaque(false);
        identity.add(avatar,BorderLayout.WEST);

        JPanel words=new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));

        JLabel type=new JLabel(item.type());
        type.setForeground(Theme.text());
        type.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));

        JLabel name=new JLabel(item.person().name());
        name.setForeground(Theme.muted());
        name.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));

        JLabel date=new JLabel(
                item.date().format(DateTimeFormatter.ofPattern("MMM d"))
        );
        date.setForeground(Theme.muted());
        date.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));

        words.add(type);
        words.add(Box.createVerticalStrut(2));
        words.add(name);
        words.add(date);

        identity.add(words,BorderLayout.CENTER);

        row.add(recognitionIcon,BorderLayout.WEST);
        row.add(identity,BorderLayout.CENTER);
        return row;
    }

    private Icon celebrationTagIcon(
            UpcomingCelebration item,
            int size
    ){
        String type=item==null||item.type()==null
                ?""
                :item.type().toLowerCase(Locale.ROOT);
        String dashboardKey=type.contains("anniversary")
                ?"work_anniversary"
                :type.contains("employee of the month")
                    ?"employee_of_month"
                    :type.contains("promotion")
                        ?"promotion"
                        :"birthday";
        Icon supplied=NorthStarDashboardGlyphs.icon(dashboardKey,size);
        if(supplied!=null)return supplied;
        return NorthStarDashboardGlyphs.icon("lets_celebrate",size);
    }

    private Icon legacyCelebrationTagIcon(
            UpcomingCelebration item,
            int size
    ){
        String type=item==null||item.type()==null
                ?""
                :item.type().toLowerCase(Locale.ROOT);
        /*
         * Legacy vector recognition artwork remains isolated here for source
         * history only; Team Celebrations no longer invokes it.
         * Birthday currently uses the built-in gift glyph. Paint it as vector
         * geometry at display time instead of rasterizing it into a 40px image;
         * this keeps the recognition tile crisp on Retina/HiDPI screens.
         */
        return new Icon(){
            @Override public int getIconWidth(){ return size; }
            @Override public int getIconHeight(){ return size; }

            @Override public void paintIcon(
                    Component component,
                    Graphics graphics,
                    int x,
                    int y
            ){
                Graphics2D g=(Graphics2D)graphics.create();
                try{
                    g.translate(x,y);
                    g.setRenderingHint(
                            RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(
                            RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    g.setStroke(new BasicStroke(
                            Math.max(2f,size/18f),
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    ));
                    if(type.contains("anniversary"))
                        drawConfettiPopper(g,size);
                    else
                        drawGift(g,size);
                }finally{
                    g.dispose();
                }
            }
        };
    }

    private void drawGift(Graphics2D g,int size){
        Color glyph=Theme.text();
        g.setColor(glyph);

        int boxX=size/7;
        int boxY=size*5/12;
        int boxW=size*5/7;
        int boxH=size*5/12;
        int ribbonW=Math.max(4,size/8);

        // Filled gift box and lid.
        g.fillRoundRect(
                boxX,
                boxY,
                boxW,
                boxH,
                size/12,
                size/12
        );
        g.fillRoundRect(
                boxX-size/30,
                boxY-size/10,
                boxW+size/15,
                size/7,
                size/14,
                size/14
        );

        // Knock the ribbon channels out with the tile color for the strong
        // silhouette/negative-space look used by the reference glyphs.
        g.setColor(Theme.panel2());
        g.fillRect(
                size/2-ribbonW/2,
                boxY-size/10,
                ribbonW,
                boxH+size/6
        );
        g.fillRect(
                boxX,
                boxY+size/9,
                boxW,
                Math.max(3,size/14)
        );

        // Bow loops in the foreground glyph color.
        g.setColor(glyph);
        int bowY=size/6;
        g.fillOval(
                size/2-size/4,
                bowY,
                size/4,
                size/5
        );
        g.fillOval(
                size/2,
                bowY,
                size/4,
                size/5
        );

        // Cut the inner bow holes.
        g.setColor(Theme.panel2());
        g.fillOval(
                size/2-size/5,
                bowY+size/22,
                size/9,
                size/10
        );
        g.fillOval(
                size/2+size/11,
                bowY+size/22,
                size/9,
                size/10
        );

        g.setColor(glyph);
        g.fillOval(
                size/2-size/14,
                bowY+size/15,
                size/7,
                size/7
        );
    }

    private void drawConfettiPopper(Graphics2D g,int size){
        Color glyph=Theme.text();
        Color knockout=Theme.panel2();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Strong filled party-popper cone based on the supplied glyph sheet.
        g.setColor(glyph);
        Polygon cone=new Polygon(
                new int[]{
                        size/7,
                        size*2/5,
                        size*3/5
                },
                new int[]{
                        size*6/7,
                        size*9/16,
                        size*13/16
                },
                3
        );
        g.fill(cone);

        // Negative-space decorative band.
        g.setColor(knockout);
        g.setStroke(new BasicStroke(
                Math.max(3f,size/11f),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));
        g.drawLine(
                size/4,
                size*3/4,
                size*7/15,
                size*11/16
        );

        // Monochrome burst/streamers.
        g.setColor(glyph);
        g.setStroke(new BasicStroke(
                Math.max(3f,size/15f),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        int[][] rays={
                {size/2,size/2,size/2,size/8},
                {size*9/16,size*7/16,size*3/4,size/5},
                {size*7/16,size*7/16,size/4,size/5},
                {size*3/5,size*2/5,size*7/8,size*2/5},
                {size*2/5,size*2/5,size/8,size*2/5}
        };
        for(int[] ray:rays)
            g.drawLine(ray[0],ray[1],ray[2],ray[3]);

        int dot=Math.max(4,size/9);
        int[][] dots={
                {size/5,size/7},
                {size*2/5,size/12},
                {size*3/5,size/8},
                {size*4/5,size/6},
                {size*4/5,size*2/5}
        };
        for(int[] point:dots)
            g.fillOval(
                    point[0]-dot/2,
                    point[1]-dot/2,
                    dot,
                    dot
            );
    }

    private JComponent rowSeparator(){
        JSeparator separator=new JSeparator();
        separator.setForeground(Theme.border());
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        return separator;
    }

    private JButton workspaceFooterButton(String text){
        JButton button=new JButton(text);
        button.setForeground(Theme.text());
        button.setBackground(Theme.panel2());
        button.setFocusPainted(false);
        button.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border(),1,true),
                new EmptyBorder(7,10,7,10)
        ));
        button.setPreferredSize(new Dimension(100,30));
        return button;
    }

    private JPanel informationStripCard(){
        JPanel card=card("Information");
        renderInformationStrip(card);
        return card;
    }

    private void renderInformationStrip(JPanel card){
        Component north=((BorderLayout)card.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);

        card.removeAll();
        if(north!=null)card.add(north,BorderLayout.NORTH);

        List<String> configured=config.widgetTypes.stream()
                .filter(Objects::nonNull)
                .filter(value->!value.isBlank())
                .toList();

        if(configured.isEmpty()){
            card.add(
                    empty("Configure Information Blocks from the workspace sidebar"),
                    BorderLayout.CENTER
            );
            stopInformationMovement();
            card.revalidate();
            card.repaint();
            return;
        }

        String movement=config.workspaceInfoMovementMode==null
                ?"STATIC"
                :config.workspaceInfoMovementMode.trim().toUpperCase();

        if("TICKER".equals(movement)){
            renderInformationTicker(card,configured);
            card.revalidate();
            card.repaint();
            return;
        }

        stopInformationTicker();

        int columns=informationMetricColumnCount();
        int visible=Math.max(
                1,
                Math.min(
                        Math.min(config.workspaceInfoBlockCount,columns),
                        configured.size()
                )
        );

        boolean paged="PAGED".equals(movement);

        if(informationPageStart>=configured.size())
            informationPageStart=0;

        JPanel metrics=new JPanel(new GridLayout(1,columns,10,0));
        metrics.setOpaque(false);

        int added=0;
        for(int slot=0;slot<visible;slot++){
            int absolute=informationPageStart+slot;

            if(!paged&&absolute>=configured.size())
                break;

            int index=absolute%configured.size();
            metrics.add(workspaceInfoMetric(configured.get(index)));
            added++;
        }

        while(added<columns){
            JPanel placeholder=new JPanel();
            placeholder.setOpaque(false);
            metrics.add(placeholder);
            added++;
        }

        card.add(metrics,BorderLayout.CENTER);

        if(paged&&configured.size()>visible){
            JLabel page=new JLabel(
                    informationPageLabel(
                            informationPageStart,
                            visible,
                            configured.size()
                    ),
                    SwingConstants.RIGHT
            );
            page.setForeground(Theme.muted());
            page.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,8));
            card.add(page,BorderLayout.SOUTH);
        }

        configureInformationRotation(
                configured.size(),
                visible,
                paged
        );

        card.revalidate();
        card.repaint();
    }

    private void renderInformationTicker(
            JPanel card,
            List<String> configured
    ){
        stopInformationRotation();
        stopInformationTicker();

        int visibleSlots=Math.max(
                1,
                Math.min(config.workspaceInfoBlockCount,informationMetricColumnCount())
        );
        int availableWidth=Math.max(
                1,
                card.getWidth()>0
                        ?card.getWidth()
                        :Math.max(900,getContentPane().getWidth()-260)
        );
        int slotWidth=Math.max(
                150,
                availableWidth/visibleSlots
        );

        informationTickerTrack=new JPanel();
        informationTickerTrack.setOpaque(false);
        informationTickerTrack.setLayout(
                new BoxLayout(informationTickerTrack,BoxLayout.X_AXIS));

        for(String type:configured){
            JComponent metric=workspaceInfoMetric(type);
            metric.setPreferredSize(new Dimension(slotWidth,76));
            metric.setMinimumSize(new Dimension(slotWidth,76));
            metric.setMaximumSize(new Dimension(slotWidth,76));
            informationTickerTrack.add(metric);
        }

        informationTickerTrack.add(Box.createHorizontalStrut(slotWidth/2));

        informationTickerCycleWidth=
                configured.size()*slotWidth+(slotWidth/2);

        /*
         * Duplicate one complete cycle so resetting the viewport position is
         * visually seamless rather than snapping the Information row.
         */
        for(String type:configured){
            JComponent metric=workspaceInfoMetric(type);
            metric.setPreferredSize(new Dimension(slotWidth,76));
            metric.setMinimumSize(new Dimension(slotWidth,76));
            metric.setMaximumSize(new Dimension(slotWidth,76));
            informationTickerTrack.add(metric);
        }

        informationTickerTrack.setPreferredSize(
                new Dimension(
                        informationTickerCycleWidth*2,
                        76
                )
        );

        informationTickerViewport=new JViewport();
        informationTickerViewport.setOpaque(false);
        informationTickerViewport.setView(informationTickerTrack);
        informationTickerViewport.setPreferredSize(new Dimension(100,76));

        card.add(informationTickerViewport,BorderLayout.CENTER);

        JLabel mode=new JLabel(
                "CONTINUOUS",
                SwingConstants.RIGHT
        );
        mode.setForeground(Theme.muted());
        mode.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,8));
        card.add(mode,BorderLayout.SOUTH);

        startInformationTicker();
    }

    private void startInformationTicker(){
        if(informationTickerViewport==null
                ||informationTickerTrack==null
                ||informationTickerCycleWidth<=0)
            return;

        final int delay=33;
        final double pixelsPerTick=
                Math.max(
                        8,
                        Math.min(
                                120,
                                config.workspaceInfoTickerPixelsPerSecond
                        )
                )*(delay/1000.0);

        final double[] x={0.0};

        informationTickerTimer=new javax.swing.Timer(delay,e->{
            if(informationTickerViewport==null
                    ||!informationTickerViewport.isShowing())
                return;

            x[0]+=pixelsPerTick;

            if(x[0]>=informationTickerCycleWidth)
                x[0]-=informationTickerCycleWidth;

            informationTickerViewport.setViewPosition(
                    new Point((int)Math.round(x[0]),0));
        });
        informationTickerTimer.setCoalesce(true);
        informationTickerTimer.start();
    }


    private JComponent workspaceInfoMetric(String type){
        JPanel p=new JPanel();
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(2,6,2,6));
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));

        String titleText=workspaceInfoTitle(type);
        JLabel label=new JLabel(titleText);
        label.setForeground(Theme.muted());
        label.setFont(new Font(
                Font.SANS_SERIF,
                Font.PLAIN,
                responsiveInfoFontSize(titleText,10,8,24)
        ));
        label.setAlignmentX(.5f);

        JPanel valueRow=new JPanel();
        valueRow.setOpaque(false);
        valueRow.setLayout(new BoxLayout(valueRow,BoxLayout.X_AXIS));
        valueRow.setAlignmentX(.5f);
        valueRow.setMaximumSize(
                new Dimension(Integer.MAX_VALUE,28));

        JLabel icon=new JLabel(workspaceInfoIcon(type));
        icon.setAlignmentY(.5f);

        String primaryText=workspaceInfoPrimary(type);
        JLabel value=new JLabel(primaryText);
        value.setForeground(Theme.text());
        value.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                responsiveInfoFontSize(primaryText,17,12,12)
        ));
        value.setAlignmentY(.5f);

        valueRow.add(Box.createHorizontalGlue());
        valueRow.add(icon);
        valueRow.add(Box.createHorizontalStrut(8));
        valueRow.add(value);
        valueRow.add(Box.createHorizontalGlue());

        String detailText=workspaceInfoDetail(type);
        JLabel status=new JLabel(detailText);
        status.setForeground(workspaceInfoDetailColor(type));
        status.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                responsiveInfoFontSize(detailText,8,7,32)
        ));
        status.setAlignmentX(.5f);

        p.add(label);
        p.add(Box.createVerticalStrut(1));
        p.add(valueRow);
        p.add(Box.createVerticalStrut(1));
        p.add(status);

        return p;
    }

    private void configureInformationRotation(
            int total,
            int visible,
            boolean paged
    ){
        boolean shouldRotate=paged
                &&total>visible
                &&infoStripModule!=null;

        if(!shouldRotate){
            stopInformationRotation();
            return;
        }

        int delay=Math.max(
                5,
                Math.min(60,config.workspaceInfoScrollSeconds)
        )*1000;

        if(informationRotationTimer!=null
                &&informationRotationTimer.getDelay()==delay
                &&informationRotationTimer.isRunning())
            return;

        stopInformationRotation();

        informationRotationTimer=new javax.swing.Timer(delay,e->{
            if(infoStripModule==null)return;

            List<String> items=config.widgetTypes.stream()
                    .filter(Objects::nonNull)
                    .filter(value->!value.isBlank())
                    .toList();

            if(items.size()<=visible){
                stopInformationRotation();
                return;
            }

            informationPageStart=
                    (informationPageStart+visible)%items.size();
            renderInformationStrip(infoStripModule);
        });
        informationRotationTimer.setRepeats(true);
        informationRotationTimer.start();
    }

    private void stopInformationRotation(){
        if(informationRotationTimer!=null){
            informationRotationTimer.stop();
            informationRotationTimer=null;
        }
    }

    private void stopInformationTicker(){
        if(informationTickerTimer!=null){
            informationTickerTimer.stop();
            informationTickerTimer=null;
        }
        informationTickerViewport=null;
        informationTickerTrack=null;
        informationTickerCycleWidth=0;
    }

    private void stopInformationMovement(){
        stopInformationRotation();
        stopInformationTicker();
    }

    private String informationPageLabel(
            int start,
            int visible,
            int total
    ){
        int first=Math.min(total,start+1);
        int last=Math.min(total,start+visible);
        return first+"–"+last+" of "+total+" • PAGED";
    }

    private static int responsiveInfoFontSize(
            String text,
            int preferred,
            int minimum,
            int comfortableCharacters
    ){
        if(text==null||text.isBlank())return preferred;

        int length=text.length();
        if(length<=comfortableCharacters)return preferred;

        double ratio=comfortableCharacters/(double)length;
        int scaled=(int)Math.round(preferred*Math.sqrt(ratio));
        return Math.max(minimum,Math.min(preferred,scaled));
    }

    private String workspaceInfoTitle(String type){
        if(type==null)return "STATUS";

        if(type.startsWith("ROUTE_")){
            int index=parseDynamicIndex(type,"ROUTE_");
            return index>=0&&index<config.routes.size()
                    ?"ROUTE • "+config.routes.get(index).name().toUpperCase()
                    :"ROUTE";
        }

        if(type.startsWith("WEATHER_LOCATION_")){
            int index=parseDynamicIndex(type,"WEATHER_LOCATION_");
            return index>=0&&index<config.monitored.size()
                    ?config.monitored.get(index).name().toUpperCase()+" WEATHER"
                    :"LOCATION WEATHER";
        }

        if(type.startsWith("SPORTS_")){
            int index=parseDynamicIndex(type,"SPORTS_");
            return index>=0&&index<config.sports.size()
                    ?config.sports.get(index).name().toUpperCase()
                    :"SPORTS";
        }

        return switch(type){
            case "WEATHER_PRIMARY"->config.primary.name().toUpperCase()+" WEATHER";
            case "ALERTS"->"SEVERE WEATHER";
            case "FORECAST_PRIMARY"->"TODAY'S OUTLOOK";
            case "WIND_PRIMARY"->"WIND & GUSTS";
            case "MEDIA"->"ANNOUNCEMENTS";
            default->"SYSTEM STATUS";
        };
    }

    private Icon workspaceInfoIcon(String type){
        if(type!=null&&type.startsWith("ROUTE_"))
            return new DashboardIcon(
                    DashboardIcon.Kind.CAR,
                    32,
                    routeInfoColor(type)
            );

        if("ALERTS".equals(type))
            return new DashboardIcon(
                    DashboardIcon.Kind.ALERT,30,
                    alerts.isEmpty()?Theme.muted():Theme.warn());

        if("WIND_PRIMARY".equals(type))
            return new DashboardIcon(
                    DashboardIcon.Kind.WIND,30,Theme.accent());

        if("MEDIA".equals(type))
            return new DashboardIcon(
                    DashboardIcon.Kind.MEDIA,30,Theme.accent());

        if(type!=null&&type.startsWith("SPORTS_")){
            int index=parseDynamicIndex(type,"SPORTS_");
            ImageIcon logo=sportsLogos.get(index);
            if(logo!=null)return logo;

            return new DashboardIcon(
                    DashboardIcon.Kind.STATUS,
                    30,
                    Theme.accent()
            );
        }

        if(type!=null&&(type.equals("WEATHER_PRIMARY")
                ||type.startsWith("WEATHER_LOCATION_")
                ||type.equals("FORECAST_PRIMARY"))){
            return new DashboardIcon(
                    DashboardIcon.weatherKind(
                            weather==null?"":weather.condition()),
                    32,
                    Theme.accent()
            );
        }

        return new DashboardIcon(
                DashboardIcon.Kind.STATUS,30,new Color(32,201,151));
    }

    private String workspaceInfoPrimary(String type){
        if(type==null)return "Ready";

        if(type.startsWith("ROUTE_")){
            int index=parseDynamicIndex(type,"ROUTE_");
            RouteStatus route=routeStatuses.get(index);
            return route==null||route.travelMinutes()<0
                    ?"—"
                    :route.travelMinutes()+" min";
        }

        if(type.equals("WEATHER_PRIMARY")
                ||type.startsWith("WEATHER_LOCATION_"))
            return weather==null
                    ?"—"
                    :Math.round(weather.temperatureF())+"°F";

        if("ALERTS".equals(type)){
            WeatherAlert alert=primaryWeatherAlert();
            return alert==null?"None":shortAlertName(alert.event());
        }

        if("FORECAST_PRIMARY".equals(type))
            return weather==null
                    ?"—"
                    :Math.round(weather.highF())+"° / "
                            +Math.round(weather.lowF())+"°";

        if("WIND_PRIMARY".equals(type))
            return weather==null
                    ?"—"
                    :Math.round(weather.windMph())+" mph";

        if("MEDIA".equals(type))
            return Integer.toString(
                    MediaService.list(MediaCategory.ANNOUNCEMENTS).size());

        if(type.startsWith("SPORTS_")){
            int index=parseDynamicIndex(type,"SPORTS_");
            SportsGame next=nextSportsGame(index);
            if(next==null||next.startTime()==null)return "Loading…";

            return next.startTime()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE, MMM d"));
        }

        return "Online";
    }

    private String workspaceInfoDetail(String type){
        if(type==null)return BrandIdentity.product()+" operational";

        if(type.startsWith("ROUTE_")){
            int index=parseDynamicIndex(type,"ROUTE_");
            RouteStatus route=routeStatuses.get(index);
            if(route==null)return "Loading traffic...";
            if(route.travelMinutes()<0)return "Traffic unavailable";
            String status=route.status()==null||route.status().isBlank()
                    ?"Traffic available"
                    :route.status();

            return route.delayMinutes()<=0
                    ?status
                    :status+" • +"+route.delayMinutes()+" min";
        }

        if(type.equals("WEATHER_PRIMARY")
                ||type.startsWith("WEATHER_LOCATION_"))
            return weather==null?"Loading weather...":weather.condition();

        if("ALERTS".equals(type)){
            WeatherAlert alert=primaryWeatherAlert();
            if(alert==null)return "No active alerts";

            String severity=alert.severity()==null||alert.severity().isBlank()
                    ?"Active alert"
                    :alert.severity();

            return severity+" • "+alerts.size()+" active";
        }

        if("FORECAST_PRIMARY".equals(type))
            return weather==null
                    ?"Loading forecast..."
                    :"High / Low";

        if("WIND_PRIMARY".equals(type))
            return weather==null
                    ?"Loading wind..."
                    :"Gusts "+Math.round(weather.gustMph())+" mph";

        if("MEDIA".equals(type))
            return "Managed announcement media";

        if(type.startsWith("SPORTS_")){
            int index=parseDynamicIndex(type,"SPORTS_");
            SportsGame next=nextSportsGame(index);

            if(next==null)
                return "Fetching next scheduled game…";

            SportsConfig team=index>=0&&index<config.sports.size()
                    ?config.sports.get(index)
                    :null;

            boolean home=teamMatchesSportsTeam(team,next.homeTeam());
            String opponent=home?next.awayTeam():next.homeTeam();
            String venue=home?"vs ":"at ";

            String time=next.startTime()==null
                    ?"TBD"
                    :next.startTime()
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("h:mm a"));

            return venue+shortSportsName(opponent)+" • "+time;
        }

        return "Services operational";
    }

    private Color workspaceInfoDetailColor(String type){
        if(type!=null&&type.startsWith("ROUTE_"))
            return routeInfoColor(type);

        if("ALERTS".equals(type)){
            WeatherAlert primary=primaryWeatherAlert();
            return primary==null?Theme.muted():weatherAlertColor(primary);
        }
        return Theme.muted();
    }

    private SportsGame nextSportsGame(int index){
        List<SportsGame> games=sportsSchedules.get(index);
        return games==null||games.isEmpty()?null:games.get(0);
    }

    private static String shortSportsName(String value){
        if(value==null||value.isBlank())return "TBD";
        String trimmed=value.trim();
        return trimmed.length()>22
                ?trimmed.substring(0,21)+"…"
                :trimmed;
    }

    /**
     * Selects the most operationally significant active NWS alert rather than
     * using whichever alert happens to appear first in the provider response.
     */
    private WeatherAlert primaryWeatherAlert(){
        return WeatherAlertPolicy.primary(alerts).orElse(null);
    }

    private int weatherAlertPriority(WeatherAlert alert){
        return WeatherAlertPolicy.priority(alert);
    }

    private static String shortAlertName(String event){
        return WeatherAlertPolicy.shortEventName(event);
    }

    private Color routeInfoColor(String type){
        int index=parseDynamicIndex(type,"ROUTE_");
        RouteStatus route=routeStatuses.get(index);
        if(route==null)return Theme.muted();

        if("HEAVY".equalsIgnoreCase(route.status())
                ||"SEVERE".equalsIgnoreCase(route.status()))
            return Theme.danger();

        if("MODERATE".equalsIgnoreCase(route.status()))
            return Theme.warn();

        return new Color(32,201,151);
    }

    private static int parseDynamicIndex(String value,String prefix){
        try{return Integer.parseInt(value.substring(prefix.length()));}
        catch(Exception ex){return -1;}
    }

    private JPanel operationsSnapshotCard(){
        JPanel card=card("Operations Snapshot");
        renderOperations(card);
        return card;
    }

    /** Information and KPI tiles own independent viewports in the grid layout. */
    private int informationMetricColumnCount(){
        return Math.max(1,Math.min(8,config.workspaceInfoBlockCount));
    }

    private void renderOperations(JPanel card){
        Component north=((BorderLayout)card.getLayout())
                .getLayoutComponent(BorderLayout.NORTH);
        card.removeAll();
        if(north!=null)card.add(north,BorderLayout.NORTH);

        List<OperationsKpiConfig> enabled=config.operationsKpis.stream()
                .filter(OperationsKpiConfig::enabled)
                .toList();

        if(enabled.isEmpty()){
            stopOperationsMovement();
            card.add(
                    empty("Configure KPI cards in Settings → Operations Workspace"),
                    BorderLayout.CENTER
            );
            card.revalidate();
            card.repaint();
            return;
        }

        String movement=config.workspaceKpiMovementMode==null
                ?"STATIC"
                :config.workspaceKpiMovementMode.trim().toUpperCase();

        if("TICKER".equals(movement)){
            renderOperationsTicker(card,enabled);
            card.revalidate();
            card.repaint();
            return;
        }

        stopOperationsTicker();

        int visible=Math.max(
                1,
                Math.min(config.workspaceKpiVisibleCount,enabled.size())
        );
        boolean paged="PAGED".equals(movement);
        if(operationsPageStart>=enabled.size())operationsPageStart=0;

        JPanel metrics=new JPanel(new GridLayout(1,visible,10,0));
        metrics.setOpaque(false);
        for(int slot=0;slot<visible;slot++){
            int absolute=operationsPageStart+slot;
            if(!paged&&absolute>=enabled.size())break;
            metrics.add(kpiCard(enabled.get(absolute%enabled.size())));
        }
        card.add(metrics,BorderLayout.CENTER);

        if(paged&&enabled.size()>visible){
            JLabel page=new JLabel(
                    operationsPageLabel(
                            operationsPageStart,visible,enabled.size()),
                    SwingConstants.RIGHT
            );
            page.setForeground(Theme.muted());
            page.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,8));
            card.add(page,BorderLayout.SOUTH);
        }

        configureOperationsRotation(enabled.size(),visible,paged);
        card.revalidate();
        card.repaint();
    }

    private void renderOperationsTicker(
            JPanel card,
            List<OperationsKpiConfig> enabled
    ){
        stopOperationsRotation();
        stopOperationsTicker();

        int visibleSlots=Math.max(
                1,
                Math.min(config.workspaceKpiVisibleCount,8)
        );
        int availableWidth=Math.max(
                1,
                card.getWidth()>0
                        ?card.getWidth()
                        :Math.max(900,getContentPane().getWidth()-260)
        );
        int slotWidth=Math.max(150,availableWidth/visibleSlots);

        operationsTickerTrack=new JPanel();
        operationsTickerTrack.setOpaque(false);
        operationsTickerTrack.setLayout(
                new BoxLayout(operationsTickerTrack,BoxLayout.X_AXIS));

        for(OperationsKpiConfig kpi:enabled){
            JComponent metric=kpiCard(kpi);
            metric.setPreferredSize(new Dimension(slotWidth,76));
            metric.setMinimumSize(new Dimension(slotWidth,76));
            metric.setMaximumSize(new Dimension(slotWidth,76));
            operationsTickerTrack.add(metric);
        }
        operationsTickerTrack.add(Box.createHorizontalStrut(slotWidth/2));
        operationsTickerCycleWidth=enabled.size()*slotWidth+(slotWidth/2);

        for(OperationsKpiConfig kpi:enabled){
            JComponent metric=kpiCard(kpi);
            metric.setPreferredSize(new Dimension(slotWidth,76));
            metric.setMinimumSize(new Dimension(slotWidth,76));
            metric.setMaximumSize(new Dimension(slotWidth,76));
            operationsTickerTrack.add(metric);
        }
        operationsTickerTrack.setPreferredSize(
                new Dimension(operationsTickerCycleWidth*2,76));

        operationsTickerViewport=new JViewport();
        operationsTickerViewport.setOpaque(false);
        operationsTickerViewport.setView(operationsTickerTrack);
        operationsTickerViewport.setPreferredSize(new Dimension(100,76));
        card.add(operationsTickerViewport,BorderLayout.CENTER);

        JLabel mode=new JLabel("CONTINUOUS",SwingConstants.RIGHT);
        mode.setForeground(Theme.muted());
        mode.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,8));
        card.add(mode,BorderLayout.SOUTH);
        startOperationsTicker();
    }

    private void startOperationsTicker(){
        if(operationsTickerViewport==null
                ||operationsTickerTrack==null
                ||operationsTickerCycleWidth<=0)return;

        final int delay=33;
        final double pixelsPerTick=Math.max(
                8,
                Math.min(120,config.workspaceKpiTickerPixelsPerSecond)
        )*(delay/1000.0);
        final double[] x={0.0};

        operationsTickerTimer=new javax.swing.Timer(delay,e->{
            if(operationsTickerViewport==null
                    ||!operationsTickerViewport.isShowing())return;
            x[0]+=pixelsPerTick;
            if(x[0]>=operationsTickerCycleWidth)
                x[0]-=operationsTickerCycleWidth;
            operationsTickerViewport.setViewPosition(
                    new Point((int)Math.round(x[0]),0));
        });
        operationsTickerTimer.setCoalesce(true);
        operationsTickerTimer.start();
    }

    private void configureOperationsRotation(
            int total,
            int visible,
            boolean paged
    ){
        boolean shouldRotate=paged&&total>visible&&operationsModule!=null;
        if(!shouldRotate){
            stopOperationsRotation();
            return;
        }

        int delay=Math.max(
                5,
                Math.min(60,config.workspaceKpiScrollSeconds)
        )*1000;
        if(operationsRotationTimer!=null
                &&operationsRotationTimer.getDelay()==delay
                &&operationsRotationTimer.isRunning())return;

        stopOperationsRotation();
        operationsRotationTimer=new javax.swing.Timer(delay,e->{
            if(operationsModule==null)return;
            List<OperationsKpiConfig> items=config.operationsKpis.stream()
                    .filter(OperationsKpiConfig::enabled)
                    .toList();
            if(items.size()<=visible){
                stopOperationsRotation();
                return;
            }
            operationsPageStart=(operationsPageStart+visible)%items.size();
            renderOperations(operationsModule);
        });
        operationsRotationTimer.setRepeats(true);
        operationsRotationTimer.start();
    }

    private void stopOperationsRotation(){
        if(operationsRotationTimer!=null){
            operationsRotationTimer.stop();
            operationsRotationTimer=null;
        }
    }

    private void stopOperationsTicker(){
        if(operationsTickerTimer!=null){
            operationsTickerTimer.stop();
            operationsTickerTimer=null;
        }
        operationsTickerViewport=null;
        operationsTickerTrack=null;
        operationsTickerCycleWidth=0;
    }

    private void stopOperationsMovement(){
        stopOperationsRotation();
        stopOperationsTicker();
    }

    private String operationsPageLabel(int start,int visible,int total){
        int first=Math.min(total,start+1);
        int last=Math.min(total,start+visible);
        return first+"–"+last+" of "+total+" • PAGED";
    }

    private JComponent kpiCard(OperationsKpiConfig source){
        double current="SYSTEM_ALERTS".equalsIgnoreCase(source.dataSourceId())
                ?alerts.size():source.currentValue();
        OperationsKpiConfig kpi=new OperationsKpiConfig(
                source.id(),source.label(),current,source.targetValue(),source.unit(),
                source.higherIsBetter(),source.enabled(),source.dataSourceId());

        JPanel p=new JPanel();p.setOpaque(false);p.setBorder(new EmptyBorder(7,6,7,6));
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        JLabel label=new JLabel(kpi.label());label.setForeground(Theme.muted());label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));label.setAlignmentX(.5f);
        JLabel value=new JLabel(formatValue(current)+safe(kpi.unit()));value.setForeground(Theme.text());value.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));value.setAlignmentX(.5f);
        JLabel status=new JLabel(kpiStatus(kpi));status.setAlignmentX(.5f);status.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));
        status.setForeground(kpi.targetConfigured()?(kpi.targetMet()?new Color(32,201,151):Theme.warn()):Theme.accent());
        p.add(label);p.add(Box.createVerticalStrut(4));p.add(value);p.add(Box.createVerticalStrut(4));p.add(status);return p;
    }

    private String kpiStatus(OperationsKpiConfig kpi){
        if(!kpi.targetConfigured())return "LIVE VALUE";
        String target="Target "+formatValue(kpi.targetValue())+safe(kpi.unit());
        if(kpi.targetMet())
            return "✓ TARGET MET • "+target;
        return (kpi.effectiveHigherIsBetter()
                ?"△ BELOW TARGET • "
                :"△ ABOVE TARGET • ")+target;
    }

    private void startRefreshers(){
        stopRefreshers();
        refreshExecutor=Executors.newScheduledThreadPool(5,r->{
            Thread t=new Thread(r,"northstar-workspace-refresh");
            t.setDaemon(true);
            return t;
        });

        reconcileAutomaticSevereWeatherState();
        rapidRefreshScheduled=severeWeatherActive();

        refreshExecutor.execute(this::refreshWeather);
        refreshExecutor.execute(this::refreshAlerts);
        refreshExecutor.execute(this::refreshRadar);
        refreshExecutor.execute(this::refreshTraffic);
        refreshExecutor.execute(this::refreshSports);

        scheduleWeatherMonitoring(rapidRefreshScheduled);
        trafficRefreshFuture=refreshExecutor.scheduleWithFixedDelay(
                this::refreshTraffic,
                Math.max(1,config.trafficRefreshMinutes),
                Math.max(1,config.trafficRefreshMinutes),
                TimeUnit.MINUTES
        );
        sportsRefreshFuture=refreshExecutor.scheduleWithFixedDelay(
                this::refreshSports,
                Math.max(1,config.sportsRefreshMinutes),
                Math.max(1,config.sportsRefreshMinutes),
                TimeUnit.MINUTES
        );
    }

    private synchronized void scheduleWeatherMonitoring(boolean rapid){
        if(refreshExecutor==null||refreshExecutor.isShutdown())return;
        cancelFuture(weatherRefreshFuture);
        cancelFuture(alertRefreshFuture);
        cancelFuture(radarRefreshFuture);

        SevereWeatherRefreshPolicy.Cadence cadence=
                SevereWeatherRefreshPolicy.cadence(
                        rapid,
                        config.weatherRefreshMinutes,
                        config.alertRefreshMinutes,
                        config.radarRefreshMinutes
                );

        weatherRefreshFuture=refreshExecutor.scheduleWithFixedDelay(
                this::refreshWeather,
                cadence.weatherMinutes(),cadence.weatherMinutes(),TimeUnit.MINUTES);
        alertRefreshFuture=refreshExecutor.scheduleWithFixedDelay(
                this::refreshAlerts,
                cadence.alertMinutes(),cadence.alertMinutes(),TimeUnit.MINUTES);
        radarRefreshFuture=refreshExecutor.scheduleWithFixedDelay(
                this::refreshRadar,
                cadence.radarMinutes(),cadence.radarMinutes(),TimeUnit.MINUTES);
    }

    private synchronized void rescheduleWeatherMonitoringIfNeeded(){
        boolean rapid=severeWeatherActive();
        if(rapid==rapidRefreshScheduled)return;
        rapidRefreshScheduled=rapid;
        scheduleWeatherMonitoring(rapid);
    }

    private static void cancelFuture(ScheduledFuture<?> future){
        if(future!=null)future.cancel(false);
    }



    private synchronized void stopRefreshers(){
        cancelFuture(weatherRefreshFuture);
        cancelFuture(alertRefreshFuture);
        cancelFuture(radarRefreshFuture);
        cancelFuture(trafficRefreshFuture);
        cancelFuture(sportsRefreshFuture);
        weatherRefreshFuture=null;
        alertRefreshFuture=null;
        radarRefreshFuture=null;
        trafficRefreshFuture=null;
        sportsRefreshFuture=null;
        if(refreshExecutor!=null){
            refreshExecutor.shutdownNow();
            refreshExecutor=null;
        }
        rapidRefreshScheduled=false;
        stopInformationMovement();
        stopOperationsMovement();
    }

    private void refreshWeather(){
        try{
            weather=weatherService.fetch(config.primary,config);
            SwingUtilities.invokeLater(()->{
                refreshTopSummaries();
                if(weatherModule!=null)renderWeatherCard(weatherModule);
                if(infoStripModule!=null)renderInformationStrip(infoStripModule);
            });
        }catch(Exception ignored){}
    }

    private void refreshAlerts(){
        try{
            alerts=List.copyOf(alertService.fetch(config.primary,config));
            reconcileAutomaticSevereWeatherState();
            rescheduleWeatherMonitoringIfNeeded();
            SwingUtilities.invokeLater(()->{
                refreshTopSummaries();
                if(map!=null)map.setAlerts(alerts);
                if(mainShowcase!=null)
                    mainShowcase.setSevereWeatherActive(severeWeatherActive());
                if(overlayEffects!=null)
                    overlayEffects.setSevereSuppressed(
                            severeWeatherPriorityActive());
                if(operationsModule!=null)renderOperations(operationsModule);
                if(infoStripModule!=null)renderInformationStrip(infoStripModule);
            });
        }catch(Exception ignored){}
    }

    private boolean severeWeatherActive(){
        return config.liveSevereWeatherMode||automaticSevereWeatherLatched;
    }

    private void reconcileAutomaticSevereWeatherState(){
        if(!config.automaticSevereWeatherMode){
            automaticSevereWeatherLatched=false;
            return;
        }

        if(WeatherAlertPolicy.hasAutomaticSevereAlert(alerts)){
            automaticSevereWeatherLatched=true;
        }else if(config.autoDisableSevereWeatherMode){
            automaticSevereWeatherLatched=false;
        }
    }

    private void refreshRadar(){
        try{
            RadarFrame frame=radarService.latest();
            SwingUtilities.invokeLater(()->{if(map!=null)map.setRadarFrame(frame);});
        }catch(Exception ignored){}
    }

    private void refreshTraffic(){
        for(int i=0;i<config.routes.size();i++){
            try{routeStatuses.put(i,trafficService.fetchRoute(config.routes.get(i),config.tomTomApiKey));}
            catch(Exception ignored){}
        }
        SwingUtilities.invokeLater(()->{
            refreshTopSummaries();
            if(infoStripModule!=null)
                renderInformationStrip(infoStripModule);
        });
    }

    private void refreshSports(){
        for(int i=0;i<config.sports.size();i++){
            try{
                SportsConfig team=config.sports.get(i);
                List<SportsGame> games=sportsService.fetchUpcoming(
                        team,
                        config.sportsApiKey,
                        3
                );
                sportsSchedules.put(i,games);

                if(team.showLogos()){
                    String badgeUrl="";
                    if(!games.isEmpty()){
                        SportsGame game=games.get(0);
                        boolean home=teamMatchesSportsTeam(team,game.homeTeam());
                        badgeUrl=home?game.homeBadgeUrl():game.awayBadgeUrl();
                    }

                    if(badgeUrl==null||badgeUrl.isBlank())
                        badgeUrl=sportsService.configuredTeamBadge(
                                team,
                                config.sportsApiKey
                        );

                    if(badgeUrl!=null&&!badgeUrl.isBlank())
                        sportsLogos.put(i,loadSportsLogo(badgeUrl,34));
                }
            }catch(Exception ignored){}
        }

        SwingUtilities.invokeLater(()->{
            if(infoStripModule!=null)
                renderInformationStrip(infoStripModule);
        });
    }

    private ImageIcon loadSportsLogo(String url,int size){
        try{
            byte[] bytes=http.getBytes(url);
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
            if(image==null)return null;

            return new ImageIcon(
                    image.getScaledInstance(size,size,Image.SCALE_SMOOTH)
            );
        }catch(Exception ex){
            return null;
        }
    }

    private static boolean teamMatchesSportsTeam(
            SportsConfig cfg,
            String teamName
    ){
        if(cfg==null||teamName==null)return false;
        if(teamName.equalsIgnoreCase(cfg.teamName()))return true;

        String configured=cfg.teamName()==null
                ?""
                :cfg.teamName().trim().toLowerCase(Locale.ROOT);

        return !configured.isBlank()
                &&teamName.toLowerCase(Locale.ROOT).contains(configured);
    }

    private void refreshTopSummaries(){
        if(topWeatherLabel!=null){
            JLabel detail=(JLabel)topWeatherLabel.getClientProperty("detailLabel");
            if(weather==null){
                topWeatherLabel.setText("--°F");
                if(detail!=null)detail.setText("Loading weather...");
            }else{
                topWeatherLabel.setText(Math.round(weather.temperatureF())+"°F");
                if(detail!=null)
                    detail.setText(weather.condition()+" • feels "
                            +Math.round(weather.apparentTemperatureF())+"°");
            }
        }

        if(topTrafficLabel!=null){
            JLabel detail=(JLabel)topTrafficLabel.getClientProperty("detailLabel");
            int worst=routeStatuses.values().stream()
                    .mapToInt(RouteStatus::delayMinutes).max().orElse(0);
            topTrafficLabel.setText(worst<=0?"Light":worst<10?"Moderate":"Delayed");
            if(detail!=null)
                detail.setText(worst<=0?"No major delays":"Worst route +"+worst+" min");
        }

        if(alertBadge!=null){
            int count=alerts==null?0:alerts.size();
            WeatherAlert primary=primaryWeatherAlert();
            if(config.liveSevereWeatherMode){
                alertBadge.setText(count==0
                        ?"●  SEVERE TEST"
                        :"●  "+count+" alert"+(count==1?"":"s")+" • TEST");
                alertBadge.setForeground(Theme.danger());
            }else{
                alertBadge.setText("●  "+count+" alert"+(count==1?"":"s"));
                alertBadge.setForeground(primary==null
                        ?Theme.muted()
                        :weatherAlertColor(primary));
            }
        }
        refreshHeaderTicker();
    }

    private void refreshVisibleModules(){
        refreshTopSummaries();
        if(weatherModule!=null)renderWeatherCard(weatherModule);
        if(eventsModule!=null)renderEvents(eventsModule);
        if(celebrationsModule!=null)renderCelebrations(celebrationsModule);
        if(infoStripModule!=null)renderInformationStrip(infoStripModule);
        if(operationsModule!=null)renderOperations(operationsModule);
        if(mainShowcase!=null)
            mainShowcase.setSevereWeatherActive(severeWeatherActive());
        if(overlayEffects!=null)
            overlayEffects.setSevereSuppressed(severeWeatherPriorityActive());
    }

    private void showDirectWorkspacePage(
            String routeTitle,
            JComponent page
    ){
        closeEmbeddedSettingsSession();
        releaseDashboardModules();
        activeWorkspaceRoute=routeTitle;
        updateSidebarSelection();

        JPanel shell=new JPanel(new BorderLayout(0,12));
        shell.setBackground(Theme.bg());
        shell.setBorder(new EmptyBorder(18,20,18,20));

        JPanel header=new JPanel(new BorderLayout(12,0));
        header.setOpaque(false);

        JLabel title=new JLabel(routeTitle);
        title.setForeground(Theme.text());
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));

        JButton dashboard=new JButton("← Dashboard");
        dashboard.addActionListener(e->showDashboardRoute());

        header.add(title,BorderLayout.WEST);
        header.add(dashboard,BorderLayout.EAST);

        shell.add(header,BorderLayout.NORTH);
        shell.add(page,BorderLayout.CENTER);

        ThemeStyler.apply(shell,Theme.active());

        if(workspaceContentHost!=null){
            workspaceContentHost.removeAll();
            workspaceContentHost.add(shell,BorderLayout.CENTER);
            workspaceContentHost.revalidate();
            workspaceContentHost.repaint();
        }
    }

    private void openSettings(String tab){
        Permission permission=permissionForSettingsTab(tab);
        openWorkspaceSettingsPage(
                tab==null?"General":tab,
                tab==null?"General":tab,
                permission
        );
    }

    /**
     * Settings pages are first-class workspace routes. The underlying form and
     * validation are shared with Classic Settings, but only the requested page
     * is mounted into the right-hand workspace surface.
     */
    private void openWorkspaceSettingsPage(
            String routeTitle,
            String settingsTab,
            Permission permission
    ){
        releaseDashboardModules();

        UserAccount account=SessionManager.currentUser();
        if(account==null){
            account=UserLoginDialog.authenticate(
                    this,
                    BrandIdentity.product()+" Settings",
                    "Sign in to administer the operations workspace.",
                    Theme.active(),
                    ""
            );
            if(account==null)return;
            SessionManager.login(account);
        }

        if(permission!=null&&!AuthorizationService.allowed(permission)){
            ThemedDialogs.message(
                    this,
                    "Your account does not have permission to access "
                            +routeTitle+".",
                    "Access Denied",
                    ThemedDialogs.Kind.WARNING
            );
            return;
        }

        if("Employees".equalsIgnoreCase(settingsTab)){
            showDirectWorkspacePage(
                    routeTitle,
                    new EmployeeOperationsPanel(config)
            );
            return;
        }

        boolean protectedApi=
                "API Providers".equalsIgnoreCase(settingsTab)
                ||"API Usage".equalsIgnoreCase(settingsTab);

        if(protectedApi&&config.protectApiSettings){
            UserAccount verified=UserLoginDialog.authenticate(
                    this,
                    "Unlock "+settingsTab,
                    "Re-enter an authorized account password to access "
                            +settingsTab+".",
                    Theme.active(),
                    account.username()
            );
            if(verified==null)return;

            Permission required="API Usage".equalsIgnoreCase(settingsTab)
                    ?Permission.API_USAGE
                    :Permission.API_ADMINISTRATION;

            if(!verified.has(required)){
                ThemedDialogs.message(
                        this,
                        "That account does not have permission to access "
                                +settingsTab+".",
                        "Access Denied",
                        ThemedDialogs.Kind.WARNING
                );
                return;
            }
        }

        closeEmbeddedSettingsSession();

        SettingsDialog session=new SettingsDialog(
                this,
                config,
                this::applyConfig
        );

        if(protectedApi)
            session.unlockProtectedForWorkspace();

        JComponent page=session.detachTabForWorkspace(settingsTab);
        if(page==null){
            session.discardEmbeddedPage();
            ThemedDialogs.message(
                    this,
                    routeTitle+" is not available for the current account.",
                    "Page Unavailable",
                    ThemedDialogs.Kind.INFO
            );
            return;
        }

        embeddedSettingsSession=session;
        activeWorkspaceRoute=routeTitle;
        updateSidebarSelection();

        JPanel shell=new JPanel(new BorderLayout(0,12));
        shell.setBackground(Theme.bg());
        shell.setBorder(new EmptyBorder(18,20,18,20));

        JPanel header=new JPanel(new BorderLayout(12,0));
        header.setOpaque(false);

        JPanel words=new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));

        JLabel title=new JLabel(routeTitle);
        title.setForeground(Theme.text());
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));

        JLabel subtitle=new JLabel(workspacePageDescription(routeTitle));
        subtitle.setForeground(Theme.muted());
        subtitle.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));

        words.add(title);
        words.add(Box.createVerticalStrut(3));
        words.add(subtitle);

        JButton dashboard=new JButton("← Dashboard");
        dashboard.setForeground(Theme.text());
        dashboard.setBackground(Theme.panel2());
        dashboard.setFocusPainted(false);
        dashboard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border(),1,true),
                new EmptyBorder(7,11,7,11)
        ));
        dashboard.addActionListener(e->showDashboardRoute());

        header.add(words,BorderLayout.WEST);
        header.add(dashboard,BorderLayout.EAST);

        RoundedPanel contentCard=new RoundedPanel(14);
        contentCard.setBackground(Theme.panel());
        contentCard.putClientProperty(
                "outlineColor",
                Theme.border()
        );
        contentCard.setLayout(new BorderLayout());
        contentCard.setBorder(new EmptyBorder(4,4,4,4));
        contentCard.add(page,BorderLayout.CENTER);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        actions.setOpaque(false);

        JButton discard=new JButton("Discard Changes");
        discard.addActionListener(e->showDashboardRoute());

        JButton save=new JButton("Save & Apply");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->{
            SettingsDialog current=embeddedSettingsSession;
            if(current!=null)
                current.saveEmbeddedPage();
        });

        ThemeStyler.apply(discard,Theme.active());
        ThemeStyler.apply(save,Theme.active());

        actions.add(discard);
        actions.add(save);

        shell.add(header,BorderLayout.NORTH);
        shell.add(contentCard,BorderLayout.CENTER);
        shell.add(actions,BorderLayout.SOUTH);

        if(workspaceContentHost!=null){
            workspaceContentHost.removeAll();
            workspaceContentHost.add(shell,BorderLayout.CENTER);
            workspaceContentHost.revalidate();
            workspaceContentHost.repaint();
        }
    }

    private void releaseDashboardModules(){
        finishDashboardLayoutEditing();
        stopInformationMovement();
        stopOperationsMovement();

        if(mainShowcase!=null){
            mainShowcase.disposeShowcase();
            mainShowcase=null;
        }

        if(map!=null){
            map.shutdown();
            map=null;
        }

        informationPageStart=0;
        operationsPageStart=0;
        weatherModule=null;
        eventsModule=null;
        celebrationsModule=null;
        infoStripModule=null;
        operationsModule=null;
    }

    private void closeEmbeddedSettingsSession(){
        SettingsDialog session=embeddedSettingsSession;
        embeddedSettingsSession=null;

        if(session!=null)
            session.discardEmbeddedPage();
    }

    private Permission permissionForSettingsTab(String tab){
        if(tab==null)return Permission.GENERAL_SETTINGS;
        return switch(tab){
            case "General"->Permission.GENERAL_SETTINGS;
            case "Pinned Locations"->Permission.PINNED_LOCATIONS;
            case "Routes"->Permission.ROUTES;
            case "Sports"->Permission.SPORTS;
            case "Employees"->Permission.EMPLOYEE_OPERATIONS;
            case "Operations Calendar"->Permission.OPERATIONS_CALENDAR;
            case "Dashboard Blocks","Operations Workspace"->Permission.DASHBOARD_LAYOUT;
            case "Main Showcase"->Permission.MAIN_SHOWCASE;
            case "Media Library"->Permission.MEDIA_LIBRARY;
            case "Users & Access","Security"->Permission.MANAGE_USERS;
            case "API Providers"->Permission.API_ADMINISTRATION;
            case "API Usage"->Permission.API_USAGE;
            case "Data & Refresh"->Permission.DATA_REFRESH;
            default->null;
        };
    }

    private String workspacePageDescription(String routeTitle){
        return switch(routeTitle){
            case "Weather"->"Weather refresh, severe-weather and provider-independent display settings";
            case "Traffic & Routes"->"Traffic-aware commute routes and destination configuration";
            case "Operations Calendar"->"Closures, limited service, modified hours and automatic announcements";
            case "Employee Operations"->"Management employee records, recognition preferences, training, attendance, performance and assignment eligibility";
            case "Pinned Locations"->"Locations used by maps, weather and operational monitoring";
            case "Sports"->"Upcoming team schedules available to North Star modules";
            case "Main Showcase"->"Announcement media rotation and severe-weather map priority";
            case "Operations Workspace"->"Choose home modules, information blocks and Operations Snapshot KPIs";
            case "Information Blocks"->"Configure the compact route, weather and status cards shown on the North Star dashboard";
            case "Media Library"->"Managed announcements, employee photos and employee showcase media";
            case "Users & Access"->"Local accounts, role templates, granular permissions and audit access";
            case "API Providers"->"Provider adapters and protected API credentials";
            case "API Usage"->"Locally tracked provider usage and configured limits";
            case "Data & Refresh"->"Refresh cadence and live severe-weather data behavior";
            case "My Account"->"Signed-in account and password management";
            default->BrandIdentity.product()+" workspace configuration";
        };
    }

    private void applyConfig(AppConfig updated){
        embeddedSettingsSession=null;
        config=updated;
        buildUi();
        startRefreshers();
    }

    private boolean moduleEnabled(String id){
        return config.workspaceModules.stream().anyMatch(id::equalsIgnoreCase);
    }

    private void updateClock(){
        if(dateTimeLabel!=null){
            LocalDateTime now=LocalDateTime.now();
            dateTimeLabel.setText(now.format(DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d, yyyy  •  h:mm:ss a")));
        }
    }

    private List<UpcomingCelebration> upcomingCelebrations(int maximum){
        LocalDate today=LocalDate.now();
        List<UpcomingCelebration> out=new ArrayList<>();
        for(CelebrationConfig person:config.celebrations){
            if(!person.enabled())continue;
            if(person.employeeOfMonthToday(today))
                out.add(new UpcomingCelebration(person,today,"Employee of the Month"));
            if(person.showBirthday()&&person.birthdayMonth()>0&&person.birthdayDay()>0){
                LocalDate date=nextDate(today,person.birthdayMonth(),person.birthdayDay());
                out.add(new UpcomingCelebration(person,date,"Happy Birthday"));
            }
            if(person.showAnniversary()&&person.hireDate()!=null){
                LocalDate date=nextDate(today,person.hireDate().getMonthValue(),person.hireDate().getDayOfMonth());
                int years=Math.max(1,date.getYear()-person.hireDate().getYear());
                out.add(new UpcomingCelebration(person,date,years+ordinal(years)+" Anniversary"));
            }
        }
        return out.stream()
                .filter(c->!c.date().isBefore(today))
                .sorted(Comparator.comparing(UpcomingCelebration::date))
                .limit(maximum).toList();
    }

    private static LocalDate nextDate(LocalDate today,int month,int day){
        LocalDate candidate;
        try{candidate=LocalDate.of(today.getYear(),month,day);}
        catch(Exception ex){return today.plusYears(10);}
        if(candidate.isBefore(today))candidate=candidate.plusYears(1);
        return candidate;
    }

    private ImageIcon employeeAvatar(CelebrationConfig person,int size){
        try{
            Path file=MediaService.resolve(MediaCategory.EMPLOYEE_PHOTOS,person.photoAsset());
            if(file!=null){
                BufferedImage source=OrientedImageLoader.load(file);
                if(source!=null){
                    BufferedImage avatar=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g=avatar.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setClip(new Ellipse2D.Double(0,0,size,size));
                    double scale=Math.max(size/(double)source.getWidth(),size/(double)source.getHeight());
                    int w=(int)Math.ceil(source.getWidth()*scale),h=(int)Math.ceil(source.getHeight()*scale);
                    g.drawImage(source,(size-w)/2,(size-h)/2,w,h,null);g.dispose();
                    return new ImageIcon(avatar);
                }
            }
        }catch(Exception ignored){}
        BufferedImage avatar=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=avatar.createGraphics();g.setColor(Theme.panel2());g.fillOval(0,0,size,size);g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,size/3));
        String initials=initials(person.name());FontMetrics fm=g.getFontMetrics();g.drawString(initials,(size-fm.stringWidth(initials))/2,(size+fm.getAscent()-fm.getDescent())/2);g.dispose();return new ImageIcon(avatar);
    }

    private JLabel empty(String text){JLabel label=new JLabel(text);label.setForeground(Theme.muted());label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));return label;}
    private JButton navUtilityButton(String text){JButton b=new JButton(text);b.setForeground(Theme.text());b.setBackground(Theme.panel2());b.setFocusPainted(false);b.setBorder(BorderFactory.createLineBorder(Theme.border(),1,true));b.setPreferredSize(new Dimension(40,34));return b;}
    private String greeting(){int h=LocalTime.now().getHour();return h<12?"Good morning":h<17?"Good afternoon":"Good evening";}
    private static String firstName(String name){if(name==null||name.isBlank())return "Team";return name.trim().split("\\s+")[0];}
    private static String shortHour(String value){try{return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("ha")).toLowerCase();}catch(Exception e){return value.length()>5?value.substring(value.length()-5):value;}}
    private static String safe(String s){return s==null?"":s;}
    private static String formatValue(double v){if(!Double.isFinite(v))return "—";if(Math.abs(v-Math.rint(v))<.00001)return String.format(Locale.US,"%,.0f",v);return String.format(Locale.US,"%,.1f",v);}
    private static String ordinal(int n){int m=n%100;if(m>=11&&m<=13)return "th";return switch(n%10){case 1->"st";case 2->"nd";case 3->"rd";default->"th";};}
    private static String initials(String name){if(name==null||name.isBlank())return "?";String[] parts=name.trim().split("\\s+");StringBuilder b=new StringBuilder();for(String p:parts)if(!p.isBlank())b.append(Character.toUpperCase(p.charAt(0)));return b.substring(0,Math.min(2,b.length()));}

    @Override public void dispose(){
        closeEmbeddedSettingsSession();
        stopRefreshers();
        if(map!=null)map.shutdown();
        if(clockTimer!=null)clockTimer.stop();
        super.dispose();
    }

    private record TickerEntry(String text,Color color){}

    private static final class HeaderTicker extends JPanel {
        private List<TickerEntry> entries=List.of();
        private final javax.swing.Timer timer;
        private int offset=0;

        private HeaderTicker(){
            setOpaque(false);
            setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
            timer=new javax.swing.Timer(35,e->{
                int width=Math.max(1,totalMessageWidth());
                int travel=Math.max(1,getWidth()+width+240);
                offset=(offset+1)%travel;
                repaint();
            });
            timer.setCoalesce(true);
        }

        private void setEntries(List<TickerEntry> values){
            entries=values==null?List.of():List.copyOf(values);
            offset=0;
            repaint();
        }

        private int totalMessageWidth(){
            FontMetrics metrics=getFontMetrics(getFont());
            int width=0;
            for(int i=0;i<entries.size();i++){
                width+=metrics.stringWidth(entries.get(i).text());
                if(i<entries.size()-1)
                    width+=metrics.stringWidth("     •     ");
            }
            return width;
        }

        @Override public void addNotify(){
            super.addNotify();
            timer.start();
        }

        @Override public void removeNotify(){
            timer.stop();
            super.removeNotify();
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            try{
                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics metrics=g2.getFontMetrics();
                int baseline=(getHeight()+metrics.getAscent()-metrics.getDescent())/2;
                int x=getWidth()+80-offset;
                for(int i=0;i<entries.size();i++){
                    TickerEntry entry=entries.get(i);
                    g2.setColor(entry.color());
                    g2.drawString(entry.text(),x,baseline);
                    x+=metrics.stringWidth(entry.text());
                    if(i<entries.size()-1){
                        String separator="     •     ";
                        g2.setColor(Theme.muted());
                        g2.drawString(separator,x,baseline);
                        x+=metrics.stringWidth(separator);
                    }
                }
            }finally{
                g2.dispose();
            }
        }
    }

    private static final class RoundedSidebarButton extends JButton {
        private RoundedSidebarButton(String text){
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
        }

        @Override protected void paintComponent(Graphics g){
            boolean active=Boolean.TRUE.equals(
                    getClientProperty("northstar.sidebar.active"));
            Graphics2D g2=(Graphics2D)g.create();
            try{
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if(active){
                    Color accent=Theme.accent();
                    g2.setColor(new Color(
                            accent.getRed(),
                            accent.getGreen(),
                            accent.getBlue(),
                            70
                    ));
                    g2.fillRoundRect(
                            2,2,
                            Math.max(0,getWidth()-4),
                            Math.max(0,getHeight()-4),
                            14,14
                    );
                    g2.setColor(new Color(
                            accent.getRed(),
                            accent.getGreen(),
                            accent.getBlue(),
                            145
                    ));
                    g2.drawRoundRect(
                            2,2,
                            Math.max(0,getWidth()-5),
                            Math.max(0,getHeight()-5),
                            14,14
                    );
                }
            }finally{
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private record UpcomingCelebration(
            CelebrationConfig person,
            LocalDate date,
            String type){}
}
