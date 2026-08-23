package com.wtm.ui;

import com.wtm.config.*;
import com.wtm.model.*;
import com.wtm.security.*;
import com.wtm.employee.*;
import com.wtm.media.*;
import com.wtm.importer.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Settings remain separate from the passive TV dashboard.
 *
 * Locations and routes are table-driven so a facility can add or remove as
 * many pins and commute destinations as needed without changing source code.
 */
public final class SettingsDialog extends JDialog {
    private final AppConfig cfg;
    private final Consumer<AppConfig> onSave;
    private final AppTheme originalTheme;
    private final JTabbedPane tabs=new JTabbedPane();

    private final JCheckBox loginRequiredOnStartup=new JCheckBox(
            "Require administrator login when the application starts");
    private final JCheckBox protectApiSettings=new JCheckBox(
            "Require administrator login to open API Providers and API Usage");
    private boolean apiUnlockedThisSettingsSession=false;
    private boolean suppressProtectedTabChange=false;
    private int lastAllowedTabIndex=0;

    private ProtectedContentPanel protectedApiProviders;
    private ProtectedContentPanel protectedApiUsage;

    private final JTextField header=new JTextField();
    private final JTextField ticker=new JTextField();
    private final JCheckBox showHeader=new JCheckBox("Show title/header");
    private final JCheckBox showTicker=new JCheckBox("Show scrolling ticker");
    private final JCheckBox showWeatherAlertsInTicker=new JCheckBox(
            "Include active weather alerts in the ticker");
    private final JCheckBox fullscreen=new JCheckBox("Fullscreen on startup");
    private final JComboBox<AppTheme> themeSelector=new JComboBox<>(AppTheme.values());
    private final JPanel themePreview=new JPanel();
    private final JCheckBox automaticHolidayThemes=new JCheckBox(
            "Automatically switch to holiday / seasonal themes");
    private final JCheckBox themeEffects=new JCheckBox("Enable theme overlay effects");
    private final JComboBox<String> overlayIntensity=new JComboBox<>(new String[]{"LOW","MEDIUM","HIGH"});
    private final JCheckBox radar=new JCheckBox("Show radar layer");
    private final JCheckBox traffic=new JCheckBox("Show traffic layer");
    private final JCheckBox alertMap=new JCheckBox("Show severe-weather polygons on map");
    private final JCheckBox liveSevereWeather=new JCheckBox(
            "Manual Live Severe Weather Mode — rapid weather/radar/alert monitoring");
    private final JCheckBox automaticSevereWeather=new JCheckBox(
            "Automatically enable Live Severe Weather Mode for qualifying NWS alerts");
    private final JCheckBox autoDisableSevereWeather=new JCheckBox(
            "Automatically return to normal refresh rates after severe alerts clear");

    private final JTextField primaryName=new JTextField();
    private final JTextField primaryLat=new JTextField();
    private final JTextField primaryLon=new JTextField();

    private final JPasswordField tomTom=new JPasswordField();
    private final JPasswordField weatherKey=new JPasswordField();
    private final JComboBox<String> weatherProvider=new JComboBox<>(new String[]{
            "Open-Meteo Free (no key)", "Open-Meteo Customer (API key)"});
    private final JComboBox<String> alertProvider=new JComboBox<>(new String[]{"National Weather Service (NWS)"});
    private final JComboBox<String> radarProvider=new JComboBox<>(new String[]{"RainViewer Public Radar"});
    private final JComboBox<String> trafficProvider=new JComboBox<>(new String[]{"TomTom Traffic & Routing"});
    private final JComboBox<String> sportsProvider=new JComboBox<>(new String[]{"TheSportsDB"});
    private final JPasswordField sportsKey=new JPasswordField();
    private final JCheckBox sportsPremium=new JCheckBox(
            "Use TheSportsDB Premium access for enhanced team search (if available)");
    private final JTextField nwsUserAgent=new JTextField();

    private final JCheckBox showcaseMedia=new JCheckBox(
            "Cycle company announcement media with the live map");
    private final JCheckBox severeMapPriority=new JCheckBox(
            "Keep live map persistent while Automatic Severe Weather Mode is active");
    private final JComboBox<Integer> showcaseInterval =
            new JComboBox<>(new Integer[]{10,15,20,30,45,60,90,120,180,300});

    /** Quick production refresh controls. Values are in minutes. */
    private final JComboBox<Integer> routeRefresh =
            new JComboBox<>(new Integer[]{2,5,10,15,20,30});
    private final JComboBox<Integer> weatherRefresh =
            new JComboBox<>(new Integer[]{5,10,15,20,30,60});
    private final JComboBox<Integer> radarRefresh =
            new JComboBox<>(new Integer[]{2,5,10,15});
    private final JComboBox<Integer> alertRefresh =
            new JComboBox<>(new Integer[]{1,2,5,10,15});
    private final JComboBox<Integer> sportsRefresh =
            new JComboBox<>(new Integer[]{15,30,60,120,240});

    private final DefaultTableModel locationModel = new DefaultTableModel(
            new Object[]{"Pinned location","Latitude","Longitude"},0);
    private final JTable locationTable = new JTable(locationModel);

    private final DefaultTableModel routeModel = new DefaultTableModel(
            new Object[]{"Route name","Destination","Latitude","Longitude"},0);
    private final JTable routeTable = new JTable(routeModel);

    private final DefaultTableModel sportsModel = new DefaultTableModel(
            new Object[]{"Block name","Sport","League ID","Team ID","Team name","Show logos"},0){
        @Override public Class<?> getColumnClass(int column){ return column==5?Boolean.class:String.class; }
    };
    private final JTable sportsTable = new JTable(sportsModel);

    private final JCheckBox celebrationsEnabled=new JCheckBox(
            "Automatically add enabled team-recognition slides to Main Showcase");
    private final JLabel employeeOfMonthStatus=new JLabel();
    private boolean updatingEmployeeOfMonthSelection=false;

    private final DefaultTableModel celebrationModel=new DefaultTableModel(
            new Object[]{
                    "Name","Birthday (MM-DD)","Hire Date (YYYY-MM-DD)","Photo",
                    "Birthday","Anniversary","Employee of Month","Confetti","Enabled"
            },0){
        @Override public Class<?> getColumnClass(int column){
            return column>=4?Boolean.class:String.class;
        }
    };
    private final JTable celebrationTable=new JTable(celebrationModel);

    private final JCheckBox operationsAnnouncementsEnabled=new JCheckBox(
            "Automatically add Operations Calendar announcements to Main Showcase");
    private final JComboBox<Integer> operationsDefaultLeadDays=
            new JComboBox<>(new Integer[]{3,7,14,21,30,45});
    private final JTextField normalOperatingStart=new JTextField();
    private final JTextField normalOperatingEnd=new JTextField();

    private final JCheckBox normalMon=new JCheckBox("Mon");
    private final JCheckBox normalTue=new JCheckBox("Tue");
    private final JCheckBox normalWed=new JCheckBox("Wed");
    private final JCheckBox normalThu=new JCheckBox("Thu");
    private final JCheckBox normalFri=new JCheckBox("Fri");
    private final JCheckBox normalSat=new JCheckBox("Sat");
    private final JCheckBox normalSun=new JCheckBox("Sun");

    private final DefaultTableModel operationModel=new DefaultTableModel(
            new Object[]{
                    "Event / Holiday","Start Date","End Date","Operation Type",
                    "Start Time","End Time","Lead Days","Enabled"
            },0){
        @Override public Class<?> getColumnClass(int column){
            return column==7?Boolean.class:Object.class;
        }
    };
    private final JTable operationTable=new JTable(operationModel);

    private final JCheckBox workspaceWeather=new JCheckBox("Local Weather");
    private final JCheckBox workspaceTrafficMap=new JCheckBox("Traffic Map");
    private final JCheckBox workspaceEvents=new JCheckBox("Upcoming Events");
    private final JCheckBox workspaceCelebrations=new JCheckBox("Team Celebrations");
    private final JCheckBox workspaceOperationsSnapshot=new JCheckBox("Operations Snapshot");
    private final JCheckBox workspaceInfoStrip=
            new JCheckBox("Show Information Row");
    private final JComboBox<Integer> workspaceInfoBlockCount=
            new JComboBox<>(new Integer[]{2,3,4,5,6,7,8});
    private final JComboBox<String> workspaceInfoMovementMode=
            new JComboBox<>(new String[]{
                    "Static",
                    "Paged Rotation",
                    "Continuous Ticker"
            });
    private final JComboBox<Integer> workspaceInfoScrollSeconds=
            new JComboBox<>(new Integer[]{5,8,10,15,20,30,45,60});
    private final JComboBox<Integer> workspaceInfoTickerSpeed=
            new JComboBox<>(new Integer[]{12,18,24,28,36,48,60,80});
    private final JComboBox<Integer> workspaceOpsBlockCount=
            new JComboBox<>(new Integer[]{2,3,4,5,6,7,8});
    private final JComboBox<String> workspaceOpsMovementMode=
            new JComboBox<>(new String[]{"Static","Paged Rotation","Continuous Ticker"});
    private final JComboBox<Integer> workspaceOpsScrollSeconds=
            new JComboBox<>(new Integer[]{5,8,10,15,20,30,45,60});
    private final JComboBox<Integer> workspaceOpsTickerSpeed=
            new JComboBox<>(new Integer[]{12,18,24,28,36,48,60,80});

    private final DefaultTableModel workspaceKpiModel=new DefaultTableModel(
            new Object[]{
                    "Metric","Current","Target","Unit","Target Direction",
                    "Data Source","Enabled"
            },0){
        @Override public Class<?> getColumnClass(int column){
            return column==6?Boolean.class:Object.class;
        }

        @Override public boolean isCellEditable(int row,int column){
            return true;
        }
    };
    private final JTable workspaceKpiTable=new JTable(workspaceKpiModel);
    private final JLabel workspaceKpiImportStatus=new JLabel(KpiHistoryStore.latestSummary());

    private final JComboBox<Integer> blockCount =
            new JComboBox<>(new Integer[]{6,8,10,12});

    /**
     * Controlled map/card resizing. Unlike a draggable split pane, this value
     * changes only through Settings and remains locked during normal display.
     */
    private final JPanel widgetRows = new JPanel(new GridBagLayout());
    private final List<JComboBox<WidgetChoice>> widgetBoxes = new ArrayList<>();

    public SettingsDialog(JFrame owner, AppConfig cfg, Consumer<AppConfig> onSave){
        super(owner,"NORTH STAR • Settings",true);
        this.cfg=cfg;
        this.onSave=onSave;
        this.originalTheme=AppTheme.fromId(cfg.themeId);
        Theme.setActive(originalTheme.id());
        setTitle(BrandIdentity.product()+" • Settings");

        setMinimumSize(new Dimension(860,650));
        setLayout(new BorderLayout());

        /*
         * Settings has grown into a multi-module administration screen.
         * SCROLL_TAB_LAYOUT keeps every settings category reachable even when
         * the display is too narrow to show all tab headers at once.
         */
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        /*
         * Tabs are authorization-aware. A user never receives a control merely
         * disabled by convention; unauthorized administration areas are not
         * inserted into the Settings navigation at all.
         */
        addPermittedTab("Appearance",general(),Permission.GENERAL_SETTINGS);
        tabs.addTab("My Account",scrollableSettingsPage(myAccount()));

        if(AuthorizationService.allowed(Permission.MANAGE_USERS)){
            tabs.addTab("Security",scrollableSettingsPage(security()));
            tabs.addTab("Users & Access",scrollableSettingsPage(new UsersAccessPanel()));
        }

        if(AuthorizationService.allowed(Permission.PINNED_LOCATIONS)||
                AuthorizationService.allowed(Permission.ROUTES)){
            tabs.addTab(
                    "Locations & Routes",
                    scrollableSettingsPage(locationsAndRoutes())
            );
        }
        addPermittedTab("Sports",sports(),Permission.SPORTS);
        if(AuthorizationService.allowed(Permission.EMPLOYEE_OPERATIONS))
            tabs.addTab(
                    "Employees",
                    scrollableSettingsPage(new EmployeeOperationsPanel(cfg))
            );

        addPermittedTab(
                "Operations Calendar",
                operationsCalendar(),
                Permission.OPERATIONS_CALENDAR
        );
        addPermittedTab(
                "Workspace Setup",
                operationsWorkspace(),
                Permission.DASHBOARD_LAYOUT
        );
        addPermittedTab(
                "Main Showcase",
                showcase(),
                Permission.MAIN_SHOWCASE
        );

        if(AuthorizationService.allowed(Permission.MEDIA_LIBRARY))
            tabs.addTab("Media Library",scrollableSettingsPage(new MediaLibraryPanel()));

        /*
         * Sensitive API pages are still privacy-shielded when step-up
         * authentication is enabled, but they are created only for users that
         * have the underlying provider/usage permission.
         */
        if(AuthorizationService.allowed(Permission.API_ADMINISTRATION)){
            protectedApiProviders=new ProtectedContentPanel(scrollableSettingsPage(apiProviders()));
            tabs.addTab("API Providers",protectedApiProviders);
        }

        if(AuthorizationService.allowed(Permission.API_USAGE)){
            protectedApiUsage=new ProtectedContentPanel(scrollableSettingsPage(new ApiUsagePanel(cfg)));
            tabs.addTab("API Usage",protectedApiUsage);
        }

        addPermittedTab("Data & Refresh",data(),Permission.DATA_REFRESH);

        installProtectedTabGuard();

        JPanel settingsCenter=new JPanel(new BorderLayout(0,8));
        settingsCenter.add(buildSettingsBrandBar(),BorderLayout.NORTH);
        settingsCenter.add(tabs,BorderLayout.CENTER);

        add(settingsCenter,BorderLayout.CENTER);
        add(buttons(),BorderLayout.SOUTH);

        /*
         * Choose the initial dialog size from the actual usable monitor area.
         * Large displays open wide enough to expose the full navigation row;
         * smaller monitors remain safely within their work area and rely on
         * the scrollable tab header instead of clipping categories.
         */
        applyResponsiveWindowSize(owner,tabs);

        automaticSevereWeather.addActionListener(e->updateAutomaticSevereControls());
        themeSelector.addActionListener(e->{
            updateThemePreview();
            AppTheme selected=(AppTheme)themeSelector.getSelectedItem();
            if(selected!=null)applySettingsTheme(selected);
        });

        loadValues();
        installEmployeeOfMonthSelectionGuard();
        normalizeEmployeeOfMonthSelection();
        updateEmployeeOfMonthStatus();
        installOperationTypeBehavior();
        updateAutomaticSevereControls();
        applySettingsTheme(AppTheme.fromId(cfg.themeId));
    }

    private JPanel buildSettingsBrandBar(){
        RoundedPanel bar=new RoundedPanel(18);
        bar.putClientProperty("surfaceRole","header");
        bar.setLayout(new BorderLayout(12,0));
        bar.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));

        JLabel icon=new JLabel(BrandIdentity.symbol(36));

        JLabel subtitle=new JLabel("SYSTEM ADMINISTRATION");
        subtitle.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
        subtitle.setForeground(Theme.muted());

        JPanel left=new JPanel(new BorderLayout(10,0));
        left.setOpaque(false);
        left.add(icon,BorderLayout.WEST);
        left.add(subtitle,BorderLayout.CENTER);

        UserAccount current=SessionManager.currentUser();
        JLabel session=new JLabel(
                current==null
                        ?"No active user"
                        :"Signed in as "+current.friendlyName()
                                +" • "+current.role().display()
        );
        session.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        session.setForeground(Theme.muted());

        bar.add(left,BorderLayout.WEST);
        bar.add(session,BorderLayout.EAST);
        return bar;
    }

    /**
     * Sizes Settings to the current monitor rather than a fixed 980px width.
     *
     * The width attempts to accommodate the full tab strip when practical,
     * while respecting the monitor's usable work area (menu bar/dock/taskbar).
     */
    private void addPermittedTab(
            String title,
            JComponent component,
            Permission permission
    ){
        if(AuthorizationService.allowed(permission))
            tabs.addTab(title,scrollableSettingsPage(component));
    }

    /**
     * Gives every ordinary Settings page its own vertical scrolling surface.
     * The content tracks viewport width, so narrower windows scroll only
     * vertically instead of introducing a second horizontal navigation model.
     */
    private JComponent scrollableSettingsPage(JComponent content){
        JPanel holder=new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(content,BorderLayout.NORTH);

        JScrollPane scroll=new JScrollPane(
                holder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(90);

        // Force holder width to follow the viewport while preserving content
        // preferred height. This keeps form controls full-width and allows all
        // lower controls to be reached without resizing the Settings dialog.
        scroll.getViewport().addChangeListener(e->{
            int width=scroll.getViewport().getExtentSize().width;
            if(width>0){
                Dimension preferred=holder.getPreferredSize();
                holder.setPreferredSize(new Dimension(width,preferred.height));
            }
        });
        return scroll;
    }

    private void applyResponsiveWindowSize(JFrame owner,JTabbedPane tabs){
        GraphicsConfiguration gc=owner!=null
                ?owner.getGraphicsConfiguration()
                :getGraphicsConfiguration();

        Rectangle screen=gc!=null
                ?new Rectangle(gc.getBounds())
                :GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getMaximumWindowBounds();

        Insets insets=gc!=null
                ?Toolkit.getDefaultToolkit().getScreenInsets(gc)
                :new Insets(0,0,0,0);

        int usableX=screen.x+insets.left;
        int usableY=screen.y+insets.top;
        int usableW=Math.max(900,screen.width-insets.left-insets.right);
        int usableH=Math.max(680,screen.height-insets.top-insets.bottom);

        /*
         * 1320px comfortably fits the current ten categories on typical
         * desktop font metrics. Preferred tab width is also considered so
         * future categories naturally influence the initial size.
         */
        int desiredW=Math.max(1180,Math.min(1500,tabs.getPreferredSize().width+80));
        int desiredH=840;

        int width=Math.min(desiredW,(int)(usableW*.94));
        int height=Math.min(desiredH,(int)(usableH*.92));

        width=Math.max(Math.min(860,usableW),width);
        height=Math.max(Math.min(650,usableH),height);

        int x=usableX+Math.max(0,(usableW-width)/2);
        int y=usableY+Math.max(0,(usableH-height)/2);

        setBounds(x,y,width,height);
    }

    /**
     * Process-wide login policy is administrator-controlled. Individual
     * password hashes are managed by UserService and never enter AppConfig.
     */
    private JPanel myAccount(){
        JPanel p=form();
        int y=0;

        UserAccount user=SessionManager.currentUser();

        JLabel title=new JLabel("My Account");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(p,y++,title);

        if(user!=null){
            addFull(p,y++,new JLabel(
                    "<html><b>"+escapeHtml(user.friendlyName())+"</b><br>"
                    +escapeHtml(user.username())+" • "
                    +escapeHtml(user.role().display())+"</html>"
            ));
        }

        JPasswordField current=new JPasswordField();
        JPasswordField next=new JPasswordField();
        JPasswordField confirm=new JPasswordField();

        addRow(p,y++,"Current password",current);
        addRow(p,y++,"New password",next);
        addRow(p,y++,"Confirm new password",confirm);

        JButton change=new JButton("Change My Password");
        change.addActionListener(e->{
            char[] a=current.getPassword();
            char[] b=next.getPassword();
            char[] c=confirm.getPassword();

            try{
                if(!java.util.Arrays.equals(b,c))
                    throw new IllegalArgumentException(
                            "New password and confirmation do not match."
                    );

                UserService.changeOwnPassword(a,b);
                ThemedDialogs.message(
                        this,
                        "Your password has been changed.",
                        "Password Updated",
                        ThemedDialogs.Kind.INFO
                );
            }catch(Exception ex){
                ThemedDialogs.message(
                        this,
                        ex.getMessage(),
                        "Password Change Failed",
                        ThemedDialogs.Kind.ERROR
                );
            }finally{
                java.util.Arrays.fill(a,'\0');
                java.util.Arrays.fill(b,'\0');
                java.util.Arrays.fill(c,'\0');
                current.setText("");
                next.setText("");
                confirm.setText("");
            }
        });
        addFull(p,y++,change);

        JLabel note=new JLabel(
                "<html>Password changes are stored as a new salted PBKDF2 hash. "
              + "Administrators can reset another user's password from "
              + "<b>Users & Access</b> without seeing the old password.</html>");
        addFull(p,y++,note);

        return p;
    }

    private JPanel security(){
        AuthorizationService.require(Permission.MANAGE_USERS);

        JPanel p=form();
        int y=0;

        UserAccount user=SessionManager.currentUser();

        JLabel title=new JLabel("Application Security");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(p,y++,title);

        JLabel session=new JLabel(
                "<html>Signed in as <b>"
                +escapeHtml(user==null?"Unknown":user.friendlyName())
                +"</b> ("+escapeHtml(user==null?"":user.username())+"). "
                +"Passwords and access privileges are managed from "
                +"<b>Users & Access</b>.</html>"
        );
        addFull(p,y++,session);

        JTextArea intro=new JTextArea(
                "Startup Login controls whether a user must sign in before the "
              + "dashboard appears. API Step-Up Protection requires another "
              + "password verification before an authorized user can view API "
              + "provider credentials or usage data. Tab visibility is always "
              + "controlled by each user's permissions.");
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setEditable(false);
        intro.setOpaque(false);
        addFull(p,y++,intro);

        loginRequiredOnStartup.setText(
                "Require user login when the application starts");
        protectApiSettings.setText(
                "Require password re-verification before protected API tabs");

        addFull(p,y++,loginRequiredOnStartup);
        addFull(p,y++,protectApiSettings);

        JButton lockApi=new JButton(
                "Re-lock API tabs for this Settings session");
        lockApi.addActionListener(e->{
            apiUnlockedThisSettingsSession=false;
            lockProtectedApiContent();
            ThemedDialogs.message(
                    this,
                    "Protected API tabs are locked again.",
                    "API Settings Locked",
                    ThemedDialogs.Kind.INFO
            );
        });
        addFull(p,y++,lockApi);

        JLabel note=new JLabel(
                "<html>Use <b>Users & Access</b> to add accounts, disable users, "
              + "reset passwords, assign role templates, or grant/revoke "
              + "individual privileges.</html>");
        addFull(p,y++,note);

        return p;
    }

    /**
     * API tabs are protected independently from startup login. Successful
     * authentication unlocks them only until this Settings dialog is closed.
     */
    private void installProtectedTabGuard(){
        tabs.addChangeListener(event->{
            if(suppressProtectedTabChange)return;

            int selected=tabs.getSelectedIndex();
            if(selected<0)return;

            String title=tabs.getTitleAt(selected);
            boolean protectedTab="API Providers".equals(title)
                    ||"API Usage".equals(title);

            if(!protectedTab){
                lastAllowedTabIndex=selected;
                return;
            }

            Permission required="API Usage".equals(title)
                    ?Permission.API_USAGE
                    :Permission.API_ADMINISTRATION;

            if(!AuthorizationService.allowed(required)){
                lockProtectedApiContent();
                suppressProtectedTabChange=true;
                try{
                    tabs.setSelectedIndex(Math.max(0,lastAllowedTabIndex));
                }finally{
                    suppressProtectedTabChange=false;
                }

                ThemedDialogs.message(
                        this,
                        "Your account no longer has permission to access "+title+".",
                        "Access Denied",
                        ThemedDialogs.Kind.WARNING
                );
                return;
            }

            if(!cfg.protectApiSettings){
                unlockProtectedApiContent();
                lastAllowedTabIndex=selected;
                return;
            }

            if(apiUnlockedThisSettingsSession){
                unlockProtectedApiContent();
                lastAllowedTabIndex=selected;
                return;
            }

            /*
             * Lock and paint the privacy surface before opening the modal
             * password prompt. invokeLater gives Swing one paint cycle so the
             * sensitive page can never remain readable behind the dialog.
             */
            lockProtectedApiContent();
            refreshProtectedPrivacySurface();

            SwingUtilities.invokeLater(()->promptForProtectedApiTab(selected));
        });
    }

    private void promptForProtectedApiTab(int selectedIndex){
        if(apiUnlockedThisSettingsSession)return;
        if(selectedIndex!=tabs.getSelectedIndex())return;

        UserAccount current=SessionManager.currentUser();
        UserAccount verified=UserLoginDialog.authenticate(
                this,
                "Unlock Protected API Settings",
                "Re-enter an authorized account password to access protected "
                        +"API information.",
                currentSettingsTheme(),
                current==null?"":current.username()
        );

        String title=tabs.getTitleAt(selectedIndex);
        Permission required="API Usage".equals(title)
                ?Permission.API_USAGE
                :Permission.API_ADMINISTRATION;

        if(verified!=null && verified.has(required)){
            apiUnlockedThisSettingsSession=true;
            unlockProtectedApiContent();
            lastAllowedTabIndex=selectedIndex;
            AuditService.record(
                    verified.username(),
                    "Unlocked protected "+title+" settings"
            );
            return;
        }

        if(verified!=null){
            ThemedDialogs.message(
                    this,
                    "That account does not have permission to access "+title+".",
                    "Access Denied",
                    ThemedDialogs.Kind.WARNING
            );
        }

        lockProtectedApiContent();

        suppressProtectedTabChange=true;
        try{
            tabs.setSelectedIndex(Math.max(0,lastAllowedTabIndex));
        }finally{
            suppressProtectedTabChange=false;
        }
    }

    private void lockProtectedApiContent(){
        if(protectedApiProviders!=null)protectedApiProviders.lock();
        if(protectedApiUsage!=null)protectedApiUsage.lock();
    }

    private void unlockProtectedApiContent(){
        if(protectedApiProviders!=null)protectedApiProviders.unlock();
        if(protectedApiUsage!=null)protectedApiUsage.unlock();
    }

    private void refreshProtectedPrivacySurface(){
        if(protectedApiProviders!=null)
            protectedApiProviders.refreshPrivacySurface();
        if(protectedApiUsage!=null)
            protectedApiUsage.refreshPrivacySurface();

        revalidate();
        repaint();
    }

    /**
     * Package-visible theme accessor used by the privacy shield. Keeping this
     * read-only avoids coupling ProtectedContentPanel to Settings internals.
     */
    AppTheme activeThemeForProtectedContent(){
        return currentSettingsTheme();
    }

    /** Validates changes to process-wide login policy. */
    private void applySecurityChanges(){
        boolean togglesChanged=
                loginRequiredOnStartup.isSelected()!=cfg.loginRequiredOnStartup
                ||protectApiSettings.isSelected()!=cfg.protectApiSettings;

        if(togglesChanged){
            AuthorizationService.require(Permission.MANAGE_USERS);
            AuditService.record(
                    "Changed login/security policy: startupLogin="
                    +loginRequiredOnStartup.isSelected()
                    +", apiStepUp="+protectApiSettings.isSelected()
            );
        }
    }

    private JPanel general(){
        JPanel p=form(); int y=0;
        JLabel title=new JLabel("Appearance & Display");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f)); addFull(p,y++,title);
        JLabel intro=new JLabel("<html>Control North Star branding, dashboard display chrome, fullscreen behavior, and seasonal visual effects. Header and ticker options below are live dashboard features.</html>"); addFull(p,y++,intro);

        RoundedPanel display=new RoundedPanel(14); display.setLayout(new GridBagLayout());
        display.setBorder(BorderFactory.createEmptyBorder(14,14,14,14)); int d=0;
        addRow(display,d++,"Dashboard header",header); addFull(display,d++,showHeader);
        addRow(display,d++,"Dashboard ticker message",ticker); addFull(display,d++,showTicker);
        addFull(display,d++,showWeatherAlertsInTicker);
        addFull(display,d++,fullscreen); addFull(p,y++,display);

        RoundedPanel visual=new RoundedPanel(14); visual.setLayout(new GridBagLayout());
        visual.setBorder(BorderFactory.createEmptyBorder(14,14,14,14)); int v=0;
        addRow(visual,v++,"Interface theme",themeSelector);
        themePreview.setPreferredSize(new Dimension(600,42)); themePreview.setMinimumSize(new Dimension(300,42));
        themePreview.setBorder(BorderFactory.createTitledBorder("Theme preview")); addFull(visual,v++,themePreview);
        addFull(visual,v++,automaticHolidayThemes); addFull(visual,v++,themeEffects);
        addRow(visual,v++,"Overlay intensity",overlayIntensity);
        addFull(p,y++,visual);

        JLabel help=new JLabel("<html><b>Top display:</b> the header identifies the site/workspace. The ticker scrolls your configured message and can include active weather alerts. Critical severe-weather alerts always take priority so they cannot be missed. Seasonal overlays automatically yield to severe-weather map priority.</html>"); addFull(p,y++,help);
        return p;
    }

    /**
     * Live-preview the chosen theme across the entire Settings window.
     * Nothing is persisted until Save & Apply.
     */
    private void applySettingsTheme(AppTheme theme){
        if(theme==null)theme=originalTheme;
        ThemeStyler.apply(this,theme);

        // Dashboard Block selectors are dynamic, so explicitly include their
        // container in every live-preview theme update.
        ThemeStyler.apply(widgetRows,theme);

        if(protectedApiProviders!=null)
            protectedApiProviders.refreshPrivacySurface();
        if(protectedApiUsage!=null)
            protectedApiUsage.refreshPrivacySurface();

        revalidate();
        repaint();
    }

    private void updateThemePreview(){
        AppTheme t=(AppTheme)themeSelector.getSelectedItem();
        if(t==null)t=AppTheme.DARK;

        themePreview.removeAll();
        themePreview.setLayout(new GridLayout(1,4,8,8));
        themePreview.setBackground(t.bg());

        themePreview.add(previewSwatch("Background",t.bg(),t.text()));
        themePreview.add(previewSwatch("Card",t.panel(),t.text()));
        themePreview.add(previewSwatch("Accent",t.accent(),Color.WHITE));
        themePreview.add(previewSwatch("Outline",t.panel2(),t.text()));

        themePreview.revalidate();
        themePreview.repaint();
    }

    private static JPanel previewSwatch(String label,Color bg,Color fg){
        JPanel p=new JPanel(new GridBagLayout());
        p.setBackground(bg);
        p.setBorder(BorderFactory.createLineBorder(fg,1,true));
        JLabel l=new JLabel(label);
        l.setForeground(fg);
        l.setFont(l.getFont().deriveFont(Font.BOLD,12f));
        p.add(l);
        return p;
    }

    /**
     * Unified facility/location/route configuration.
     *
     * Pinned monitoring locations and traffic routes use the same geographic
     * foundation, so this page presents them as one workflow instead of two
     * nearly identical settings destinations.
     */
    private JPanel locationsAndRoutes(){
        JPanel outer=new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer,BoxLayout.Y_AXIS));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JLabel title=new JLabel("Locations & Routes");
        title.setFont(title.getFont().deriveFont(Font.BOLD,18f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.add(title);
        outer.add(Box.createVerticalStrut(4));

        JLabel subtitle=new JLabel(
                "Manage the primary facility, monitoring locations and commute routes in one place.");
        subtitle.setForeground(Theme.muted());
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.add(subtitle);
        outer.add(Box.createVerticalStrut(14));

        boolean canPins=AuthorizationService.allowed(
                Permission.PINNED_LOCATIONS);
        boolean canRoutes=AuthorizationService.allowed(
                Permission.ROUTES);

        if(canPins){
            RoundedPanel primaryCard=new RoundedPanel(16);
            primaryCard.setLayout(new BorderLayout(10,10));
            primaryCard.setBorder(BorderFactory.createEmptyBorder(
                    14,14,14,14));
            primaryCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            primaryCard.setMaximumSize(new Dimension(
                    Integer.MAX_VALUE,220));

            JPanel primary=form();
            int y=0;
            JLabel primaryTitle=new JLabel(
                    "Primary Facility / Map Center");
            primaryTitle.setFont(primaryTitle.getFont().deriveFont(
                    Font.BOLD,15f));
            addFull(primary,y++,primaryTitle);
            addRow(primary,y++,"Primary location name",primaryName);
            addRow(primary,y++,"Primary latitude",primaryLat);
            addRow(primary,y++,"Primary longitude",primaryLon);

            JButton findPrimary=new JButton("Find Primary Location");
            findPrimary.addActionListener(e->findPrimaryLocation());
            JPanel primaryActions=new JPanel(
                    new FlowLayout(FlowLayout.LEFT,0,0));
            primaryActions.setOpaque(false);
            primaryActions.add(findPrimary);
            addFull(primary,y++,primaryActions);

            primaryCard.add(primary,BorderLayout.CENTER);
            outer.add(primaryCard);
            outer.add(Box.createVerticalStrut(12));
        }

        JPanel dataRow=new JPanel(new GridLayout(
                1,
                (canPins&&canRoutes)?2:1,
                12,0
        ));
        dataRow.setOpaque(false);
        dataRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        if(canPins){
            RoundedPanel pinsCard=new RoundedPanel(16);
            pinsCard.setLayout(new BorderLayout(8,8));
            pinsCard.setBorder(BorderFactory.createEmptyBorder(
                    14,14,14,14));

            JLabel pinsTitle=new JLabel("Pinned Locations");
            pinsTitle.setFont(pinsTitle.getFont().deriveFont(
                    Font.BOLD,15f));

            JTextArea pinsHelp=new JTextArea(
                    "Pinned locations appear on the map and can feed weather "
                  + "and information cards. A selected pin can also become a route.");
            pinsHelp.setLineWrap(true);
            pinsHelp.setWrapStyleWord(true);
            pinsHelp.setEditable(false);
            pinsHelp.setOpaque(false);
            pinsHelp.setForeground(Theme.muted());

            JPanel pinsHeader=new JPanel();
            pinsHeader.setOpaque(false);
            pinsHeader.setLayout(new BoxLayout(
                    pinsHeader,BoxLayout.Y_AXIS));
            pinsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            pinsHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
            pinsHeader.add(pinsTitle);
            pinsHeader.add(Box.createVerticalStrut(4));
            pinsHeader.add(pinsHelp);

            locationTable.setFillsViewportHeight(true);
            locationTable.setRowHeight(28);
            JScrollPane pinsScroll=new JScrollPane(locationTable);
            pinsScroll.setPreferredSize(new Dimension(520,300));

            JPanel controls=new JPanel(new FlowLayout(
                    FlowLayout.LEFT,6,0));
            controls.setOpaque(false);
            JButton add=new JButton("+ Add");
            add.addActionListener(e->locationModel.addRow(
                    new Object[]{"New Location","",""}));
            JButton find=new JButton("Find Location");
            find.addActionListener(e->findPinnedLocation());
            JButton routeFromPin=new JButton("Create Route");
            routeFromPin.addActionListener(
                    e->addRouteFromPinnedLocation());
            JButton remove=new JButton("Remove");
            remove.addActionListener(
                    e->removeSelected(locationTable,locationModel));
            controls.add(add);
            controls.add(find);
            if(canRoutes)controls.add(routeFromPin);
            controls.add(remove);

            pinsCard.add(pinsHeader,BorderLayout.NORTH);
            pinsCard.add(pinsScroll,BorderLayout.CENTER);
            pinsCard.add(controls,BorderLayout.SOUTH);
            dataRow.add(pinsCard);
        }

        if(canRoutes){
            RoundedPanel routesCard=new RoundedPanel(16);
            routesCard.setLayout(new BorderLayout(8,8));
            routesCard.setBorder(BorderFactory.createEmptyBorder(
                    14,14,14,14));

            JLabel routesTitle=new JLabel("Traffic Routes");
            routesTitle.setFont(routesTitle.getFont().deriveFont(
                    Font.BOLD,15f));

            JTextArea routesHelp=new JTextArea(
                    "Routes originate at the Primary Facility and provide "
                  + "traffic-aware travel times to dashboard information cards.");
            routesHelp.setLineWrap(true);
            routesHelp.setWrapStyleWord(true);
            routesHelp.setEditable(false);
            routesHelp.setOpaque(false);
            routesHelp.setForeground(Theme.muted());

            JPanel routesHeader=new JPanel();
            routesHeader.setOpaque(false);
            routesHeader.setLayout(new BoxLayout(
                    routesHeader,BoxLayout.Y_AXIS));
            routesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            routesHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
            routesHeader.add(routesTitle);
            routesHeader.add(Box.createVerticalStrut(4));
            routesHeader.add(routesHelp);

            routeTable.setFillsViewportHeight(true);
            routeTable.setRowHeight(28);
            JScrollPane routeScroll=new JScrollPane(routeTable);
            routeScroll.setPreferredSize(new Dimension(520,300));

            JPanel controls=new JPanel(new FlowLayout(
                    FlowLayout.LEFT,6,0));
            controls.setOpaque(false);
            JButton add=new JButton("+ Add");
            add.addActionListener(e->routeModel.addRow(
                    new Object[]{"New Route","Destination","",""}));
            JButton find=new JButton("Find Destination");
            find.addActionListener(e->findRouteDestination());
            JButton remove=new JButton("Remove");
            remove.addActionListener(
                    e->removeSelected(routeTable,routeModel));
            controls.add(add);
            controls.add(find);
            controls.add(remove);

            routesCard.add(routesHeader,BorderLayout.NORTH);
            routesCard.add(routeScroll,BorderLayout.CENTER);
            routesCard.add(controls,BorderLayout.SOUTH);
            dataRow.add(routesCard);
        }

        dataRow.setMaximumSize(new Dimension(
                Integer.MAX_VALUE,430));
        outer.add(dataRow);
        outer.add(Box.createVerticalGlue());

        ThemeStyler.apply(outer,Theme.active());
        return outer;
    }

    private JPanel locations(){
        JPanel outer=new JPanel(new BorderLayout(10,10));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JPanel primary=form();
        int y=0;
        JLabel title=new JLabel("Primary facility / map center");
        title.setFont(title.getFont().deriveFont(Font.BOLD,15f));
        addFull(primary,y++,title);
        addRow(primary,y++,"Primary location name",primaryName);
        addRow(primary,y++,"Primary latitude",primaryLat);
        addRow(primary,y++,"Primary longitude",primaryLon);

        JTextArea help=new JTextArea(
                "Pinned locations appear on the map and become available as weather cards. "
              + "Use Find Location to search a city/place and fill coordinates automatically. "
              + "Manual latitude/longitude remains available for very specific points.");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);
        addFull(primary,y++,help);

        outer.add(primary,BorderLayout.NORTH);

        locationTable.setFillsViewportHeight(true);
        locationTable.setRowHeight(26);
        outer.add(new JScrollPane(locationTable),BorderLayout.CENTER);

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("+ Add pinned location");
        add.addActionListener(e->locationModel.addRow(new Object[]{"New Location","",""}));

        JButton find=new JButton("Find Location");
        find.setToolTipText("Search for a city/place and fill latitude/longitude automatically.");
        find.addActionListener(e->findPinnedLocation());

        JButton primarySearch=new JButton("Find Primary Location");
        primarySearch.setToolTipText("Search and fill the Primary Location fields above.");
        primarySearch.addActionListener(e->findPrimaryLocation());

        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->removeSelected(locationTable,locationModel));

        controls.add(add);
        controls.add(find);
        controls.add(primarySearch);
        controls.add(remove);
        outer.add(controls,BorderLayout.SOUTH);
        return outer;
    }

    private JPanel routes(){
        JPanel outer=new JPanel(new BorderLayout(10,10));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTextArea help=new JTextArea(
                "Routes originate at the Primary Location. Use Find Destination to search a city/place "
              + "and fill route coordinates automatically, or create a route from an existing pin. "
              + "Manual coordinates remain available as a fallback.");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);
        outer.add(help,BorderLayout.NORTH);

        routeTable.setFillsViewportHeight(true);
        routeTable.setRowHeight(26);
        outer.add(new JScrollPane(routeTable),BorderLayout.CENTER);

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("+ Add route");
        add.addActionListener(e->routeModel.addRow(new Object[]{"New Route","Destination","",""}));

        JButton findDestination=new JButton("Find Destination");
        findDestination.setToolTipText("Search for a route destination and fill coordinates automatically.");
        findDestination.addActionListener(e->findRouteDestination());

        JButton addFromPin=new JButton("+ Route from selected pin");
        addFromPin.addActionListener(e->addRouteFromPinnedLocation());

        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->removeSelected(routeTable,routeModel));

        controls.add(add);
        controls.add(findDestination);
        controls.add(addFromPin);
        controls.add(remove);
        outer.add(controls,BorderLayout.SOUTH);
        return outer;
    }

    private JPanel sports(){
        JPanel outer=new JPanel(new BorderLayout(10,10));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTextArea help=new JTextArea(
                "Create sports selections here; each saved selection becomes an Upcoming Schedule choice "
              + "under Dashboard Blocks, just like a configured route. Use Find Team to search the "
              + "configured sports provider and automatically fill Team ID, League ID, Sport, and "
              + "Team Name. TheSportsDB currently reserves general team-name search for premium access, "
              + "so the free key may require manual IDs for teams other than its supported test search. "
              + "The dashboard now focuses on upcoming scheduled games rather than live scores or recent results." );
        help.setLineWrap(true);help.setWrapStyleWord(true);help.setEditable(false);help.setOpaque(false);
        outer.add(help,BorderLayout.NORTH);

        sportsTable.setFillsViewportHeight(true);sportsTable.setRowHeight(26);
        outer.add(new JScrollPane(sportsTable),BorderLayout.CENTER);

        JPanel bottom=new JPanel();bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("+ Add sports selection");
        add.addActionListener(e->{
            sportsModel.addRow(new Object[]{"New Sports Block","American Football","","","Team",Boolean.TRUE});
            rebuildWidgetRows();
        });
        JButton findTeam=new JButton("Find Team");
        findTeam.setToolTipText("Search the sports provider and fill IDs/details automatically.");
        findTeam.addActionListener(e->findSportsTeam());

        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->removeSelected(sportsTable,sportsModel));

        controls.add(add);
        controls.add(findTeam);
        controls.add(remove);

        JPanel refreshRow=new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshRow.add(new JLabel("Schedule refresh (minutes):"));refreshRow.add(sportsRefresh);
        refreshRow.add(new JLabel("30–60 minutes is recommended for upcoming schedules."));
        bottom.add(controls);bottom.add(refreshRow);
        outer.add(bottom,BorderLayout.SOUTH);
        return outer;
    }

    /**
     * Opens the provider-backed team finder. If a sports row is already
     * selected, that row is populated. Otherwise a new sports row is created.
     */
    private void findSportsTeam(){
        stopTableEditing(sportsTable);

        int selected=sportsTable.getSelectedRow();
        int modelRow=selected<0 ? -1 : sportsTable.convertRowIndexToModel(selected);

        String initial="";
        if(modelRow>=0){
            initial=cell(sportsModel,modelRow,4).trim();
            if(initial.equalsIgnoreCase("Team")) initial="";
        }

        String key=new String(sportsKey.getPassword()).trim();
        if(key.isBlank()) key=cfg.sportsApiKey==null?"123":cfg.sportsApiKey.trim();
        if(key.isBlank()) key="123";

        boolean premium=sportsPremium.isSelected();

        TeamSearchDialog dialog=new TeamSearchDialog(
                this,
                initial,
                key,
                premium,
                result->applyTeamSearchResult(modelRow,result)
        );
        dialog.setVisible(true);
    }

    private void applyTeamSearchResult(int existingRow,TeamSearchResult result){
        int row=existingRow;

        if(row<0 || row>=sportsModel.getRowCount()){
            String blockName=result.teamName();
            if(result.sport()!=null && !result.sport().isBlank())
                blockName+=" "+shortSportLabel(result.sport());

            sportsModel.addRow(new Object[]{
                    blockName,
                    result.sport(),
                    result.leagueId(),
                    result.teamId(),
                    result.teamName(),
                    Boolean.TRUE
            });
            row=sportsModel.getRowCount()-1;
        }else{
            sportsModel.setValueAt(result.sport(),row,1);
            sportsModel.setValueAt(result.leagueId(),row,2);
            sportsModel.setValueAt(result.teamId(),row,3);
            sportsModel.setValueAt(result.teamName(),row,4);
            sportsModel.setValueAt(Boolean.TRUE,row,5);

            String currentName=cell(sportsModel,row,0).trim();
            if(currentName.isBlank() || currentName.equalsIgnoreCase("New Sports Block"))
                sportsModel.setValueAt(result.teamName()+" "+shortSportLabel(result.sport()),row,0);
        }

        rebuildWidgetRows();

        int viewRow=sportsTable.convertRowIndexToView(row);
        if(viewRow>=0){
            sportsTable.setRowSelectionInterval(viewRow,viewRow);
            sportsTable.scrollRectToVisible(sportsTable.getCellRect(viewRow,0,true));
        }
    }

    private static String shortSportLabel(String sport){
        if(sport==null||sport.isBlank()) return "Sports";
        if("American Football".equalsIgnoreCase(sport)) return "Football";
        if("Ice Hockey".equalsIgnoreCase(sport)) return "Hockey";
        return sport;
    }

    private JPanel celebrations(){
        JPanel outer=new JPanel(new BorderLayout(10,10));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JPanel top=form();
        int y=0;

        JLabel title=new JLabel("Automatic Team Celebrations");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(top,y++,title);

        JTextArea help=new JTextArea(
                "Keep one row per team member and independently enable Birthday, Anniversary, "
              + "or Employee of the Month recognition. Birthday and anniversary slides appear only "
              + "on matching dates. Employee of the Month is a single selection for the current "
              + "month and appears throughout that month. Photos are optional; initials are used "
              + "when no photo is supplied. Confetti can be enabled or disabled per team member.");
        help.setLineWrap(true);help.setWrapStyleWord(true);
        help.setEditable(false);help.setOpaque(false);
        addFull(top,y++,help);

        addFull(top,y++,celebrationsEnabled);

        employeeOfMonthStatus.setFont(
                employeeOfMonthStatus.getFont().deriveFont(Font.BOLD,13f));
        addFull(top,y++,employeeOfMonthStatus);

        JLabel managedPhotos=new JLabel(
                "<html><b>Employee photos:</b> managed by the built-in Media Library. "
              + "Use “Upload / Choose Photo for Selected” below.</html>");
        addFull(top,y++,managedPhotos);

        outer.add(top,BorderLayout.NORTH);

        celebrationTable.setFillsViewportHeight(true);
        celebrationTable.setRowHeight(28);
        outer.add(new JScrollPane(celebrationTable),BorderLayout.CENTER);

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton add=new JButton("+ Add Team Member");
        add.addActionListener(e->celebrationModel.addRow(new Object[]{
                "Team Member","","","",
                Boolean.TRUE,Boolean.TRUE,Boolean.FALSE,Boolean.TRUE,Boolean.TRUE
        }));

        JButton choosePhoto=new JButton("Upload / Choose Photo for Selected");
        choosePhoto.addActionListener(e->chooseCelebrationPhoto());

        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->{
            removeSelected(celebrationTable,celebrationModel);
            updateEmployeeOfMonthStatus();
        });

        controls.add(add);
        controls.add(choosePhoto);
        controls.add(remove);
        outer.add(controls,BorderLayout.SOUTH);

        return outer;
    }

    /**
     * Employee of the Month behaves like a radio-button selection inside the
     * table: checking one employee immediately clears every other employee.
     */
    private void installEmployeeOfMonthSelectionGuard(){
        celebrationModel.addTableModelListener(event->{
            if(updatingEmployeeOfMonthSelection)return;

            // Name edits should immediately update the current-recipient label.
            if(event.getColumn()==0){
                updateEmployeeOfMonthStatus();
                return;
            }

            if(event.getColumn()!=6)return;

            int row=event.getFirstRow();
            if(row<0||row>=celebrationModel.getRowCount())return;

            if(Boolean.TRUE.equals(celebrationModel.getValueAt(row,6))){
                updatingEmployeeOfMonthSelection=true;
                try{
                    for(int i=0;i<celebrationModel.getRowCount();i++){
                        if(i!=row && Boolean.TRUE.equals(
                                celebrationModel.getValueAt(i,6))){
                            celebrationModel.setValueAt(Boolean.FALSE,i,6);
                        }
                    }
                }finally{
                    updatingEmployeeOfMonthSelection=false;
                }
            }

            updateEmployeeOfMonthStatus();
        });
    }

    /** Ensures old/corrupt configuration can never show multiple recipients. */
    private void normalizeEmployeeOfMonthSelection(){
        int selected=-1;

        updatingEmployeeOfMonthSelection=true;
        try{
            for(int i=0;i<celebrationModel.getRowCount();i++){
                if(!Boolean.TRUE.equals(celebrationModel.getValueAt(i,6)))
                    continue;

                if(selected<0) selected=i;
                else celebrationModel.setValueAt(Boolean.FALSE,i,6);
            }
        }finally{
            updatingEmployeeOfMonthSelection=false;
        }
    }

    private void updateEmployeeOfMonthStatus(){
        int selected=-1;
        for(int i=0;i<celebrationModel.getRowCount();i++){
            if(Boolean.TRUE.equals(celebrationModel.getValueAt(i,6))){
                selected=i;
                break;
            }
        }

        String month=YearMonth.now().format(
                java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));

        if(selected<0){
            employeeOfMonthStatus.setText(
                    "Employee of the Month • "+month+": No recipient selected");
        }else{
            String name=cell(celebrationModel,selected,0).trim();
            employeeOfMonthStatus.setText(
                    "Employee of the Month • "+month+": "
                  + (name.isBlank()?"Team Member":name));
        }
    }

    private void chooseCelebrationPhoto(){
        AuthorizationService.require(Permission.EMPLOYEE_INFORMATION);

        int view=celebrationTable.getSelectedRow();
        if(view<0){
            ThemedDialogs.message(
                    this,
                    "Select a team member first.",
                    "Select Team Member",
                    ThemedDialogs.Kind.INFO
            );
            return;
        }

        int row=celebrationTable.convertRowIndexToModel(view);

        String[] options={
                "Upload New Photo",
                "Choose Existing Managed Photo",
                "Clear Photo",
                "Cancel"
        };

        int action=ThemedDialogs.options(
                this,
                "Choose how to update the selected employee photo.",
                "Employee Photo",
                options,
                0
        );

        if(action==0){
            JFileChooser chooser=ThemedFileChooser.chooseImages(this,false);
            if(chooser==null)return;

            try{
                Path target=MediaService.importImage(
                        MediaCategory.EMPLOYEE_PHOTOS,
                        chooser.getSelectedFile().toPath()
                );
                celebrationModel.setValueAt(
                        MediaService.assetName(target),
                        row,
                        3
                );
            }catch(Exception ex){
                ThemedDialogs.message(
                        this,
                        "Unable to import employee photo: "+ex.getMessage(),
                        "Photo Import Failed",
                        ThemedDialogs.Kind.ERROR
                );
            }
            return;
        }

        if(action==1){
            java.util.List<Path> photos=
                    MediaService.list(MediaCategory.EMPLOYEE_PHOTOS);

            if(photos.isEmpty()){
                ThemedDialogs.message(
                        this,
                        "No managed employee photos have been uploaded yet.",
                        "Employee Photos",
                        ThemedDialogs.Kind.INFO
                );
                return;
            }

            JComboBox<Path> picker=new JComboBox<>(
                    photos.toArray(new Path[0])
            );
            picker.setRenderer(new DefaultListCellRenderer(){
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list,Object value,int index,
                        boolean selected,boolean focus
                ){
                    String text=value instanceof Path p
                            ?p.getFileName().toString()
                            :String.valueOf(value);
                    return super.getListCellRendererComponent(
                            list,text,index,selected,focus);
                }
            });

            ThemeStyler.apply(picker,currentSettingsTheme());
            boolean selectedPhoto=ThemedDialogs.confirmForm(
                    this,
                    picker,
                    "Choose Managed Employee Photo",
                    "Use Photo"
            );

            if(selectedPhoto
                    &&picker.getSelectedItem() instanceof Path selected){
                celebrationModel.setValueAt(
                        MediaService.assetName(selected),
                        row,
                        3
                );
            }
            return;
        }

        if(action==2)
            celebrationModel.setValueAt("",row,3);
    }

    private JPanel operationsCalendar(){
        JPanel outer=new JPanel(new BorderLayout(10,10));
        outer.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JPanel top=form();
        int y=0;

        JLabel title=new JLabel("Holiday & Operations Calendar");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(top,y++,title);

        JTextArea help=new JTextArea(
                "Use one calendar for Full Closure, Limited Service, and Modified Hours. "
              + "Start/End Date may be the same for a one-day event or span multiple days. "
              + "Connected dates are combined automatically into one Main Showcase announcement. "
              + "Full Closure ignores time fields. Limited Service and Modified Hours require "
              + "Start/End Time. Leave Lead Days blank or 0 to use the site default.");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);
        addFull(top,y++,help);

        addFull(top,y++,operationsAnnouncementsEnabled);
        addRow(top,y++,"Default announcement lead (days)",operationsDefaultLeadDays);
        addRow(top,y++,"Normal operating start",normalOperatingStart);
        addRow(top,y++,"Normal operating end",normalOperatingEnd);

        JPanel days=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        days.setOpaque(false);
        days.add(normalMon);days.add(normalTue);days.add(normalWed);
        days.add(normalThu);days.add(normalFri);days.add(normalSat);days.add(normalSun);
        addRow(top,y++,"Normal operating days",days);

        outer.add(top,BorderLayout.NORTH);

        operationTable.setFillsViewportHeight(true);
        operationTable.setRowHeight(28);

        JComboBox<OperationType> types=new JComboBox<>(OperationType.values());
        operationTable.getColumnModel().getColumn(3).setCellEditor(
                new DefaultCellEditor(types));

        operationTable.getColumnModel().getColumn(0).setPreferredWidth(190);
        operationTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        operationTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        operationTable.getColumnModel().getColumn(3).setPreferredWidth(125);
        operationTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        operationTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        operationTable.getColumnModel().getColumn(6).setPreferredWidth(70);

        outer.add(new JScrollPane(operationTable),BorderLayout.CENTER);

        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton add=new JButton("+ Add Operations Event");
        add.addActionListener(e->{
            operationModel.addRow(new Object[]{
                    "New Operations Event",
                    LocalDate.now().plusDays(7).toString(),
                    LocalDate.now().plusDays(7).toString(),
                    OperationType.MODIFIED_HOURS,
                    formatTimeForSettings(parseTimeOrDefault(
                            normalOperatingStart.getText(),LocalTime.of(7,30))),
                    formatTimeForSettings(parseTimeOrDefault(
                            normalOperatingEnd.getText(),LocalTime.of(16,0))),
                    "",
                    Boolean.TRUE
            });
        });

        JButton remove=new JButton("Remove selected");
        remove.addActionListener(e->removeSelected(operationTable,operationModel));

        controls.add(add);
        controls.add(remove);

        JLabel note=new JLabel(
                "Generated slides remove themselves automatically after the final event date.");
        controls.add(note);

        outer.add(controls,BorderLayout.SOUTH);
        return outer;
    }

    private void installOperationTypeBehavior(){
        operationModel.addTableModelListener(event->{
            if(event.getColumn()!=3)return;
            int row=event.getFirstRow();
            if(row<0||row>=operationModel.getRowCount())return;

            OperationType type=OperationType.from(
                    String.valueOf(operationModel.getValueAt(row,3)));

            if(type==OperationType.FULL_CLOSURE){
                operationModel.setValueAt("",row,4);
                operationModel.setValueAt("",row,5);
            }else{
                if(cell(operationModel,row,4).trim().isBlank())
                    operationModel.setValueAt(
                            normalOperatingStart.getText().trim(),row,4);
                if(cell(operationModel,row,5).trim().isBlank())
                    operationModel.setValueAt(
                            normalOperatingEnd.getText().trim(),row,5);
            }
        });
    }

    private JPanel operationsWorkspace(){
        JPanel p=form();
        int y=0;

        JLabel title=new JLabel("Dashboard Workspace");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(p,y++,title);

        JLabel help=new JLabel(
                "<html>Build the home dashboard from a few consistent sections. Choose which modules are visible, then configure the Information and Operations Snapshot rows independently. Each row can remain static, rotate by page, or scroll continuously.</html>");
        addFull(p,y++,help);

        RoundedPanel modules=new RoundedPanel(14);
        modules.setLayout(new GridLayout(0,2,10,8));
        modules.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        modules.add(workspaceWeather);
        modules.add(workspaceTrafficMap);
        modules.add(workspaceEvents);
        modules.add(workspaceCelebrations);
        modules.add(workspaceOperationsSnapshot);
        addFull(p,y++,modules);

        RoundedPanel infoStrip=new RoundedPanel(14);
        infoStrip.setLayout(new BorderLayout(12,8));
        infoStrip.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));

        JPanel infoTop=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        infoTop.setOpaque(false);
        infoTop.add(workspaceInfoStrip);

        JLabel infoHelp=new JLabel(
                "<html>Enable or hide the dashboard Information row. Its cards, movement, "
              + "and ticker behavior are configured immediately below.</html>"
        );

        infoStrip.add(infoTop,BorderLayout.NORTH);
        infoStrip.add(infoHelp,BorderLayout.CENTER);
        addFull(p,y++,infoStrip);

        JPanel informationSetup=widgets();
        informationSetup.setBorder(BorderFactory.createEmptyBorder());
        addFull(p,y++,informationSetup);

        RoundedPanel opsDisplay=new RoundedPanel(16);
        opsDisplay.setLayout(new BorderLayout(12,10));
        opsDisplay.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));

        JLabel opsTitle=new JLabel("Operations Snapshot Display");
        opsTitle.setFont(opsTitle.getFont().deriveFont(Font.BOLD,14f));

        JPanel opsGrid=new JPanel(new GridLayout(1,4,12,0));
        opsGrid.setOpaque(false);
        opsGrid.add(workspaceDisplaySetting(
                "Visible at once",workspaceOpsBlockCount,null));
        opsGrid.add(workspaceDisplaySetting(
                "Movement",workspaceOpsMovementMode,null));
        opsGrid.add(workspaceDisplaySetting(
                "Page interval",workspaceOpsScrollSeconds,"sec"));
        opsGrid.add(workspaceDisplaySetting(
                "Ticker speed",workspaceOpsTickerSpeed,"px/sec"));

        JLabel opsHelp=new JLabel(
                "<html>These controls intentionally match the Information row. "
              + "The KPI table below decides which metrics are available and enabled; "
              + "these settings only control how the enabled KPI cards move through "
              + "the dashboard viewport.</html>");
        opsHelp.setForeground(Theme.muted());

        JPanel opsHelpRow=new JPanel(
                new FlowLayout(FlowLayout.LEFT,0,0));
        opsHelpRow.setOpaque(false);
        opsHelpRow.add(opsHelp);

        JPanel opsContent=new JPanel();
        opsContent.setOpaque(false);
        opsContent.setLayout(new BoxLayout(opsContent,BoxLayout.Y_AXIS));
        opsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        opsHelpRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        opsContent.add(opsGrid);
        opsContent.add(Box.createVerticalStrut(10));
        opsContent.add(opsHelpRow);

        opsDisplay.add(opsTitle,BorderLayout.NORTH);
        opsDisplay.add(opsContent,BorderLayout.CENTER);
        addFull(p,y++,opsDisplay);

        workspaceKpiTable.setRowHeight(30);
        workspaceKpiTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workspaceKpiTable.setCellSelectionEnabled(true);
        workspaceKpiTable.setFillsViewportHeight(false);
        workspaceKpiTable.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);

        workspaceKpiTable.getColumnModel().getColumn(0).setPreferredWidth(190);
        workspaceKpiTable.getColumnModel().getColumn(1).setPreferredWidth(85);
        workspaceKpiTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        workspaceKpiTable.getColumnModel().getColumn(3).setPreferredWidth(75);
        workspaceKpiTable.getColumnModel().getColumn(4).setPreferredWidth(135);
        workspaceKpiTable.getColumnModel().getColumn(5).setPreferredWidth(125);
        workspaceKpiTable.getColumnModel().getColumn(6).setPreferredWidth(70);

        workspaceKpiTable.getColumnModel().getColumn(4).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(new String[]{
                        "Higher is better","Lower is better"})));

        JComboBox<String> sourceEditor=new JComboBox<>(new String[]{
                "MANUAL","SYSTEM_ALERTS"});
        sourceEditor.setEditable(true);
        workspaceKpiTable.getColumnModel().getColumn(5).setCellEditor(
                new DefaultCellEditor(sourceEditor));

        JScrollPane tableScroll=new JScrollPane(workspaceKpiTable);
        tableScroll.setPreferredSize(new Dimension(900,280));
        tableScroll.setMinimumSize(new Dimension(500,220));
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getVerticalScrollBar().setUnitIncrement(18);
        addFull(p,y++,tableScroll);

        JButton add=new JButton("+ Add KPI");
        JButton remove=new JButton("Remove selected");

        add.addActionListener(e->{
            stopTableEditing(workspaceKpiTable);
            int row=workspaceKpiModel.getRowCount();
            workspaceKpiModel.addRow(new Object[]{
                    "New KPI","0","","","Higher is better","MANUAL",Boolean.TRUE
            });

            int viewRow=workspaceKpiTable.convertRowIndexToView(row);
            if(viewRow>=0){
                workspaceKpiTable.setRowSelectionInterval(viewRow,viewRow);
                workspaceKpiTable.setColumnSelectionInterval(0,0);
                workspaceKpiTable.scrollRectToVisible(
                        workspaceKpiTable.getCellRect(viewRow,0,true));
                SwingUtilities.invokeLater(()->{
                    workspaceKpiTable.editCellAt(viewRow,0);
                    Component editor=workspaceKpiTable.getEditorComponent();
                    if(editor!=null)editor.requestFocusInWindow();
                });
            }
        });

        remove.addActionListener(e->{
            stopTableEditing(workspaceKpiTable);
            int row=workspaceKpiTable.getSelectedRow();
            if(row>=0){
                workspaceKpiModel.removeRow(
                        workspaceKpiTable.convertRowIndexToModel(row));
            }
        });

        JButton importKpiCsv=new JButton("Import KPI CSV");
        importKpiCsv.setToolTipText(
                "Import Daily LHY / LPH or Floor Denials CSV reports. "
              + "The importer is profile-based so future KPI reports can use the same pipeline.");
        importKpiCsv.addActionListener(e->importKpiCsv());

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(add);
        actions.add(remove);
        actions.add(importKpiCsv);
        addFull(p,y++,actions);

        workspaceKpiImportStatus.setForeground(Theme.muted());
        workspaceKpiImportStatus.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        addFull(p,y++,workspaceKpiImportStatus);

        JLabel editHelp=new JLabel(
                "<html><b>Editing:</b> double-click any Metric, Current, Target, Unit, "
              + "Target Direction, or Data Source cell. Use the Enabled checkbox to hide "
              + "a KPI without deleting its configuration.</html>");
        addFull(p,y++,editHelp);

        JLabel integration=new JLabel(
                "<html><b>Integration-ready:</b> LHY, shipped lines, damages, floor denials, "
              + "and future metrics can later be populated automatically by a KPI provider "
              + "without redesigning the dashboard cards.</html>");
        addFull(p,y++,integration);

        // A final weighted spacer keeps the form anchored to the top while the
        // enclosing Settings scroll pane owns vertical overflow.
        GridBagConstraints spacer=new GridBagConstraints();
        spacer.gridx=0;spacer.gridy=y;spacer.gridwidth=2;
        spacer.weighty=1;spacer.fill=GridBagConstraints.VERTICAL;
        p.add(Box.createVerticalGlue(),spacer);

        return p;
    }

    /*
     * Dashboard width is intentionally fixed in OperationsWorkspaceFrame.
     * Keep Workspace Setup focused on content and movement configuration;
     * do not reintroduce a second width-balance control here.
     */
    private JPanel widgets(){
        JPanel outer=new JPanel(new BorderLayout(10,12));
        outer.setBorder(BorderFactory.createEmptyBorder());
        outer.setOpaque(false);

        JPanel controls=new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));

        RoundedPanel informationControls=new RoundedPanel(16);
        informationControls.setLayout(new BorderLayout(12,10));
        informationControls.setBorder(
                BorderFactory.createEmptyBorder(14,16,14,16));
        informationControls.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel displayTitle=new JLabel("Information Row Display");
        displayTitle.setFont(displayTitle.getFont().deriveFont(Font.BOLD,14f));

        JPanel displayGrid=new JPanel(new GridLayout(1,5,12,0));
        displayGrid.setOpaque(false);
        displayGrid.add(workspaceDisplaySetting(
                "Cards configured",blockCount,null));
        displayGrid.add(workspaceDisplaySetting(
                "Visible at once",workspaceInfoBlockCount,null));
        displayGrid.add(workspaceDisplaySetting(
                "Movement",workspaceInfoMovementMode,null));
        displayGrid.add(workspaceDisplaySetting(
                "Page interval",workspaceInfoScrollSeconds,"sec"));
        displayGrid.add(workspaceDisplaySetting(
                "Ticker speed",workspaceInfoTickerSpeed,"px/sec"));

        JLabel unifiedHelp=new JLabel(
                "<html>Choose how the dashboard <b>Information</b> row behaves. "
              + "<b>Cards configured</b> controls how many selectors appear below. "
              + "<b>Visible at once</b> controls the viewport. Paged Rotation advances "
              + "a group at a time; Continuous Ticker moves every configured card "
              + "through the viewport and loops continuously.</html>");
        unifiedHelp.setForeground(Theme.muted());

        JPanel informationHelpRow=new JPanel(
                new FlowLayout(FlowLayout.LEFT,0,0));
        informationHelpRow.setOpaque(false);
        informationHelpRow.add(unifiedHelp);

        JPanel informationContent=new JPanel();
        informationContent.setOpaque(false);
        informationContent.setLayout(new BoxLayout(informationContent,BoxLayout.Y_AXIS));
        displayGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        informationHelpRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        informationContent.add(displayGrid);
        informationContent.add(Box.createVerticalStrut(10));
        informationContent.add(informationHelpRow);

        informationControls.add(displayTitle,BorderLayout.NORTH);
        informationControls.add(informationContent,BorderLayout.CENTER);
        controls.add(informationControls);
        controls.add(Box.createVerticalStrut(14));

        outer.add(controls,BorderLayout.NORTH);



        RoundedPanel selectorCard=new RoundedPanel(16);
        selectorCard.setLayout(new BorderLayout(10,10));
        selectorCard.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));

        JPanel selectorHeading=new JPanel(new BorderLayout(12,0));
        selectorHeading.setOpaque(false);
        JLabel selectorTitle=new JLabel("Information Cards");
        selectorTitle.setFont(selectorTitle.getFont().deriveFont(Font.BOLD,14f));
        JLabel selectorHint=new JLabel(
                "Choose what each card displays. Cards appear on the dashboard in this order.");
        selectorHint.setForeground(Theme.muted());
        selectorHint.setFont(selectorHint.getFont().deriveFont(11f));
        selectorHeading.add(selectorTitle,BorderLayout.WEST);
        selectorHeading.add(selectorHint,BorderLayout.CENTER);

        JScrollPane scroll=new JScrollPane(
                widgetRows,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setPreferredSize(new Dimension(900,340));
        scroll.setMinimumSize(new Dimension(500,280));

        selectorCard.add(selectorHeading,BorderLayout.NORTH);
        selectorCard.add(scroll,BorderLayout.CENTER);
        outer.add(selectorCard,BorderLayout.CENTER);

        outer.setPreferredSize(new Dimension(900,600));
        outer.setMinimumSize(new Dimension(500,520));

        blockCount.addActionListener(e->rebuildWidgetRows());
        return outer;
    }

    private JPanel workspaceDisplaySetting(
            String label,
            JComponent control,
            String suffix
    ){
        JPanel cell=new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell,BoxLayout.Y_AXIS));

        JLabel title=new JLabel(label);
        title.setForeground(Theme.muted());
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setPreferredSize(new Dimension(155,42));
        control.setMinimumSize(new Dimension(120,42));
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));

        cell.add(title);
        cell.add(Box.createVerticalStrut(5));
        cell.add(control);

        if(suffix!=null&&!suffix.isBlank()){
            JLabel unit=new JLabel(suffix);
            unit.setForeground(Theme.muted());
            unit.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
            unit.setAlignmentX(Component.LEFT_ALIGNMENT);
            cell.add(Box.createVerticalStrut(3));
            cell.add(unit);
        }
        return cell;
    }

    private JPanel showcase(){
        JPanel p=form();
        int y=0;

        JLabel title=new JLabel("Main Map / Announcement Showcase");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(p,y++,title);

        JTextArea intro=new JTextArea(
                "The large left-side region can remain a live map at all times or cycle "
              + "between the live map and company announcement images. The smaller Media "
              + "dashboard block remains independent.");
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setEditable(false);
        intro.setOpaque(false);
        addFull(p,y++,intro);

        addFull(p,y++,showcaseMedia);
        addRow(p,y++,"Showcase interval (seconds)",showcaseInterval);
        JLabel libraryInfo=new JLabel(
                "<html>Upload and manage <b>Announcements</b> and "
              + "<b>Employee Showcase</b> images from the Media Library tab. "
              + "The application owns and organizes their storage automatically.</html>");
        addFull(p,y++,libraryInfo);
        addFull(p,y++,severeMapPriority);

        JLabel priorityNote=new JLabel(
                "<html><b>Recommended:</b> leave map priority enabled. If Automatic Severe "
              + "Weather Mode enters <b>AUTO LIVE</b>, the showcase immediately returns to "
              + "the live map and pauses announcement rotation until the alert clears. "
              + "Disable only for troubleshooting or deliberate media-cycle testing.</html>");
        addFull(p,y++,priorityNote);

        JTextArea formats=new JTextArea(
                "Supported Main Showcase media: PNG, JPG, JPEG, and GIF. "
              + "Files are cycled in filename order.");
        formats.setLineWrap(true);
        formats.setWrapStyleWord(true);
        formats.setEditable(false);
        formats.setOpaque(false);
        addFull(p,y++,formats);

        return p;
    }

    /**
     * Provider/credential settings are intentionally separate from refresh-rate
     * controls. New provider adapters can be added here later without changing
     * the rest of the Settings layout.
     */
    private JPanel apiProviders(){
        JPanel p=form();
        int y=0;

        JLabel title=new JLabel("API Providers & Credentials");
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        addFull(p,y++,title);

        JTextArea intro=new JTextArea(
                "Choose the installed provider adapter for each data type and manage its credentials. "
              + "Open-Meteo Free, NWS, and RainViewer do not require API keys. Open-Meteo Customer "
              + "and TomTom use credentials. A different future vendor will require a provider adapter "
              + "for that vendor's response format, but this Settings structure will remain the same.");
        intro.setLineWrap(true); intro.setWrapStyleWord(true); intro.setEditable(false); intro.setOpaque(false);
        addFull(p,y++,intro);

        addRow(p,y++,"Weather provider",weatherProvider);
        addRow(p,y++,"Open-Meteo customer API key",weatherKey);
        addRow(p,y++,"Alert provider",alertProvider);
        addRow(p,y++,"NWS User-Agent",nwsUserAgent);
        addRow(p,y++,"Radar provider",radarProvider);
        addRow(p,y++,"Traffic / routing provider",trafficProvider);
        addRow(p,y++,"TomTom API key",tomTom);
        addRow(p,y++,"Sports provider",sportsProvider);
        addRow(p,y++,"TheSportsDB API key",sportsKey);
        addFull(p,y++,sportsPremium);

        JLabel security=new JLabel(
                "<html><b>Credential storage:</b> API keys are saved separately in "
              + "<code>credentials.enc</code> under the local application-data folder. "
              + "On Linux/macOS the app attempts owner-only file permissions.</html>");
        addFull(p,y++,security);

        return p;
    }

    private JPanel data(){
        JPanel p=form();
        int y=0;
        addFull(p,y++,radar);
        addFull(p,y++,traffic);
        addFull(p,y++,alertMap);
        addFull(p,y++,liveSevereWeather);
        addFull(p,y++,automaticSevereWeather);
        addFull(p,y++,autoDisableSevereWeather);

        JLabel liveNote=new JLabel(
                "<html><b>Live mode:</b> weather every 2 min • radar every 2 min • "
              + "NWS alerts every 1 min. Traffic/routing keeps its normal interval.<br>"
              + "<b>Automatic trigger:</b> Tornado Warning/Watch, Tornado Emergency, "
              + "Severe Thunderstorm Warning/Watch, Flash Flood Warning, Extreme Wind Warning, "
              + "and other NWS alerts classified as Extreme.</html>");
        addFull(p,y++,liveNote);

        addRow(p,y++,"Route refresh (minutes)",routeRefresh);
        addRow(p,y++,"Weather refresh (minutes)",weatherRefresh);
        addRow(p,y++,"Radar refresh (minutes)",radarRefresh);
        addRow(p,y++,"NWS alert refresh (minutes)",alertRefresh);
        addRow(p,y++,"Sports schedule refresh (minutes)",sportsRefresh);

        JTextArea note=new JTextArea(
                "Refresh changes take effect immediately after Save & Apply. "
              + "When Live Severe Weather Mode is enabled, its rapid weather/radar/alert "
              + "intervals temporarily override the normal values below. "
              + "For normal workday operation, a 10-minute route refresh is a good balance "
              + "between fresh commute information and conservative TomTom API usage. "
              + "Provider selection and credentials are managed on the API Providers tab. "
              + "These controls only determine refresh cadence.");
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setOpaque(false);
        addFull(p,y++,note);
        return p;
    }

    private JPanel buttons(){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exit=new JButton("Exit Application");
        exit.addActionListener(e->System.exit(0));
        JButton cancel=new JButton("Cancel");
        cancel.addActionListener(e->{
            Theme.setActive(originalTheme.id());
            dispose();
        });
        JButton save=new JButton("Save & Apply");
        save.putClientProperty("primaryAction",Boolean.TRUE);
        save.addActionListener(e->save());
        p.add(exit);
        p.add(cancel);
        p.add(save);
        return p;
    }

    private void updateAutomaticSevereControls(){
        autoDisableSevereWeather.setEnabled(automaticSevereWeather.isSelected());
    }

    private void loadValues(){
        header.setText(cfg.headerText);
        ticker.setText(cfg.tickerText);
        showHeader.setSelected(cfg.showHeader);
        showTicker.setSelected(cfg.showTicker);
        showWeatherAlertsInTicker.setSelected(cfg.showWeatherAlertsInTicker);
        fullscreen.setSelected(cfg.fullscreen);
        loginRequiredOnStartup.setSelected(cfg.loginRequiredOnStartup);
        protectApiSettings.setSelected(cfg.protectApiSettings);
        themeSelector.setSelectedItem(AppTheme.fromId(cfg.themeId));
        updateThemePreview();
        automaticHolidayThemes.setSelected(cfg.automaticHolidayThemes);
        themeEffects.setSelected(cfg.themeOverlayEffects);
        overlayIntensity.setSelectedItem(cfg.overlayIntensity);
        radar.setSelected(cfg.showRadar);
        traffic.setSelected(cfg.showTraffic);
        alertMap.setSelected(cfg.showAlertsOnMap);
        liveSevereWeather.setSelected(cfg.liveSevereWeatherMode);
        automaticSevereWeather.setSelected(cfg.automaticSevereWeatherMode);
        autoDisableSevereWeather.setSelected(cfg.autoDisableSevereWeatherMode);
        tomTom.setText(cfg.tomTomApiKey);
        weatherKey.setText(cfg.weatherApiKey);
        weatherProvider.setSelectedIndex("OPEN_METEO_CUSTOMER".equalsIgnoreCase(cfg.weatherProvider)?1:0);
        alertProvider.setSelectedIndex(0);
        radarProvider.setSelectedIndex(0);
        trafficProvider.setSelectedIndex(0);
        sportsProvider.setSelectedIndex(0);
        sportsKey.setText(cfg.sportsApiKey);
        sportsPremium.setSelected(cfg.sportsPremiumLiveScores);
        nwsUserAgent.setText(cfg.nwsUserAgent);
        showcaseMedia.setSelected(cfg.mainShowcaseMediaEnabled);
        severeMapPriority.setSelected(cfg.severeWeatherMapPriority);
        selectInteger(showcaseInterval,cfg.mainShowcaseIntervalSeconds);
        selectInteger(routeRefresh,cfg.trafficRefreshMinutes);
        selectInteger(weatherRefresh,cfg.weatherRefreshMinutes);
        selectInteger(radarRefresh,cfg.radarRefreshMinutes);
        selectInteger(alertRefresh,cfg.alertRefreshMinutes);
        selectInteger(sportsRefresh,cfg.sportsRefreshMinutes);

        primaryName.setText(cfg.primary.name());
        primaryLat.setText(Double.toString(cfg.primary.latitude()));
        primaryLon.setText(Double.toString(cfg.primary.longitude()));

        locationModel.setRowCount(0);
        for(Location l:cfg.monitored)
            locationModel.addRow(new Object[]{l.name(),l.latitude(),l.longitude()});

        routeModel.setRowCount(0);
        for(RouteConfig r:cfg.routes)
            routeModel.addRow(new Object[]{
                    r.name(),r.destination().name(),
                    r.destination().latitude(),r.destination().longitude()
            });

        sportsModel.setRowCount(0);
        for(SportsConfig sport:cfg.sports)
            sportsModel.addRow(new Object[]{sport.name(),sport.sport(),sport.leagueId(),sport.teamId(),sport.teamName(),sport.showLogos()});

        celebrationsEnabled.setSelected(cfg.celebrationsEnabled);
        celebrationModel.setRowCount(0);
        for(CelebrationConfig c:cfg.celebrations){
            String birthday=(c.birthdayMonth()>0&&c.birthdayDay()>0)
                    ?String.format("%02d-%02d",c.birthdayMonth(),c.birthdayDay())
                    :"";
            celebrationModel.addRow(new Object[]{
                    c.name(),
                    birthday,
                    c.hireDate()==null?"":c.hireDate().toString(),
                    c.photoAsset(),
                    c.showBirthday(),
                    c.showAnniversary(),
                    c.employeeOfMonth(YearMonth.now()),
                    c.celebrationEffect(),
                    c.enabled()
            });
        }

        operationsAnnouncementsEnabled.setSelected(
                cfg.operationsAnnouncementsEnabled);
        selectInteger(operationsDefaultLeadDays,cfg.operationsDefaultLeadDays);
        normalOperatingStart.setText(
                formatTimeForSettings(cfg.normalOperatingStart));
        normalOperatingEnd.setText(
                formatTimeForSettings(cfg.normalOperatingEnd));

        normalMon.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.MONDAY));
        normalTue.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.TUESDAY));
        normalWed.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.WEDNESDAY));
        normalThu.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.THURSDAY));
        normalFri.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.FRIDAY));
        normalSat.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.SATURDAY));
        normalSun.setSelected(cfg.normalOperatingDays.contains(DayOfWeek.SUNDAY));

        operationModel.setRowCount(0);
        for(OperationEvent event:cfg.operationEvents){
            operationModel.addRow(new Object[]{
                    event.name(),
                    event.startDate().toString(),
                    event.endDate().toString(),
                    event.type(),
                    event.startTime()==null?"":formatTimeForSettings(event.startTime()),
                    event.endTime()==null?"":formatTimeForSettings(event.endTime()),
                    event.leadDays()<=0?"":Integer.toString(event.leadDays()),
                    event.enabled()
            });
        }

        workspaceWeather.setSelected(cfg.workspaceModules.contains("WEATHER"));
        workspaceTrafficMap.setSelected(cfg.workspaceModules.contains("TRAFFIC_MAP"));
        workspaceEvents.setSelected(cfg.workspaceModules.contains("UPCOMING_EVENTS"));
        workspaceCelebrations.setSelected(cfg.workspaceModules.contains("TEAM_CELEBRATIONS"));
        workspaceOperationsSnapshot.setSelected(cfg.workspaceModules.contains("OPERATIONS_SNAPSHOT"));
        workspaceInfoStrip.setSelected(cfg.workspaceInfoStripEnabled);
        workspaceInfoBlockCount.setSelectedItem(
                Math.max(2,Math.min(8,cfg.workspaceInfoBlockCount)));
        workspaceInfoMovementMode.setSelectedItem(
                switch(cfg.workspaceInfoMovementMode){
                    case "PAGED"->"Paged Rotation";
                    case "TICKER"->"Continuous Ticker";
                    default->"Static";
                });
        workspaceInfoScrollSeconds.setSelectedItem(
                Math.max(5,Math.min(60,cfg.workspaceInfoScrollSeconds)));
        workspaceInfoTickerSpeed.setSelectedItem(
                Math.max(
                        12,
                        Math.min(
                                80,
                                cfg.workspaceInfoTickerPixelsPerSecond
                        )
                ));

        workspaceOpsBlockCount.setSelectedItem(Math.max(2,Math.min(8,cfg.workspaceOpsVisibleCount)));
        workspaceOpsMovementMode.setSelectedItem(switch(cfg.workspaceOpsMovementMode){case "PAGED"->"Paged Rotation"; case "TICKER"->"Continuous Ticker"; default->"Static";});
        workspaceOpsScrollSeconds.setSelectedItem(Math.max(5,Math.min(60,cfg.workspaceOpsScrollSeconds)));
        workspaceOpsTickerSpeed.setSelectedItem(Math.max(12,Math.min(80,cfg.workspaceOpsTickerPixelsPerSecond)));

        workspaceKpiModel.setRowCount(0);
        for(OperationsKpiConfig kpi:cfg.operationsKpis){
            workspaceKpiModel.addRow(new Object[]{
                    kpi.label(),
                    formatSettingsNumber(kpi.currentValue()),
                    Double.isFinite(kpi.targetValue())
                            ?formatSettingsNumber(kpi.targetValue()):"",
                    kpi.unit(),
                    kpi.effectiveHigherIsBetter()?"Higher is better":"Lower is better",
                    kpi.dataSourceId(),
                    kpi.enabled()
            });
        }

        int count=Math.max(6,Math.min(12,cfg.visibleWidgetCount));
        if(count!=6&&count!=8&&count!=10&&count!=12) count=10;
        blockCount.setSelectedItem(count);
        rebuildWidgetRows();

        for(int i=0;i<widgetBoxes.size();i++){
            String id=i<cfg.widgetTypes.size()?cfg.widgetTypes.get(i):"STATUS";
            selectWidgetId(widgetBoxes.get(i),id);
        }
    }

    private void importKpiCsv(){
        stopTableEditing(workspaceKpiTable);

        Path selected=NorthStarCsvFileDialog.choose(
                this,
                Path.of(System.getProperty("user.home",".")).resolve("Documents"));
        if(selected==null)return;

        final KpiImportResult result;
        final KpiCsvImporter importer=new KpiCsvImporter();
        final EmployeeDamagePerformanceImporter.Result[] damagePerformance=new EmployeeDamagePerformanceImporter.Result[1];

        try{
            result=importer.previewKpis(selected);
        }catch(Exception ex){
            ThemedDialogs.message(this,
                    "KPI CSV preview failed:\n"+ex.getMessage(),
                    "KPI Import Failed",ThemedDialogs.Kind.ERROR);
            return;
        }
        if("Damages".equalsIgnoreCase(result.profileName())){try{damagePerformance[0]=EmployeeDamagePerformanceImporter.preview(selected,EmployeeService.load());}catch(Exception ex){damagePerformance[0]=new EmployeeDamagePerformanceImporter.Result(List.of(),List.of("Employee damage mapping preview failed: "+ex.getMessage()));}}
        String previewSummary=result.summary();
        if(damagePerformance[0]!=null)previewSummary+="\n\nEmployee performance links: "+damagePerformance[0].records().size()+"\nUnmatched employee warnings: "+damagePerformance[0].warnings().size();

        JTextArea preview=new JTextArea(previewSummary);
        preview.setEditable(false);
        preview.setLineWrap(false);
        preview.setOpaque(false);
        preview.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        preview.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        JScrollPane previewScroll=new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(570,260));

        if(!ThemedDialogs.confirmForm(
                this,previewScroll,"KPI CSV Import Preview","Import"))return;

        KpiImportProgressDialog progress=new KpiImportProgressDialog(this);
        long started=System.currentTimeMillis();

        SwingWorker<Void,Integer> worker=new SwingWorker<>(){
            @Override protected Void doInBackground()throws Exception{
                setProgress(10);
                Thread.sleep(90);
                setProgress(30);
                importer.commit(result);
                setProgress(65);
                Thread.sleep(90);
                long elapsed=System.currentTimeMillis()-started;
                if(elapsed<650)Thread.sleep(650-elapsed);
                setProgress(90);
                return null;
            }

            @Override protected void done(){
                try{
                    get();
                    for(KpiImportedMetric metric:result.metrics())
                        applyImportedMetric(metric);
                    if(damagePerformance[0]!=null&&!damagePerformance[0].records().isEmpty()){EmployeeStore.Snapshot es=EmployeeService.load();EmployeeDamagePerformanceImporter.apply(es,damagePerformance[0]);EmployeeService.save(es,cfg);}

                    workspaceKpiImportStatus.setText(
                            KpiHistoryStore.latestSummary());

                    AuditService.record(
                            "Imported KPI CSV: "+result.profileName()
                            +" ("+result.rowsRead()+" rows)");

                    progress.updateProgress(100,"Import complete.");
                    Timer closeTimer=new Timer(260,e->{
                        ((Timer)e.getSource()).stop();
                        progress.dispose();
                        ThemedDialogs.message(
                                SettingsDialog.this,
                                "KPI CSV values have been loaded into Operations Workspace.\n"
                                +"Choose Save & Apply to persist them to the dashboard.\n\n"
                                +result.summary(),
                                "KPI CSV Imported",
                                ThemedDialogs.Kind.INFO);
                    });
                    closeTimer.setRepeats(false);
                    closeTimer.start();
                }catch(Exception ex){
                    progress.dispose();
                    Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    ThemedDialogs.message(
                            SettingsDialog.this,
                            "KPI CSV import failed:\n"+cause.getMessage(),
                            "KPI Import Failed",
                            ThemedDialogs.Kind.ERROR);
                }
            }
        };

        worker.addPropertyChangeListener(evt->{
            if(!"progress".equals(evt.getPropertyName()))return;
            int value=(Integer)evt.getNewValue();
            String message=value<25?"Validating report…"
                    :value<60?"Updating KPI history…"
                    :value<90?"Applying dashboard values…"
                    :"Finalizing import…";
            progress.updateProgress(value,message);
        });

        worker.execute();
        SwingUtilities.invokeLater(()->progress.setVisible(true));
    }

    private void applyImportedMetric(KpiImportedMetric metric){
        String id=metric.metricId()==null?"":metric.metricId().trim();
        int row=findWorkspaceKpiRow(id,metric.label());
        if(row<0){
            workspaceKpiModel.addRow(new Object[]{metric.label(),formatSettingsNumber(metric.value()),"",metric.unit(),"Higher is better",metric.sourceId(),Boolean.TRUE});
            row=workspaceKpiModel.getRowCount()-1;
        }else{
            workspaceKpiModel.setValueAt(formatSettingsNumber(metric.value()),row,1);
            if(metric.unit()!=null&&!metric.unit().isBlank())workspaceKpiModel.setValueAt(metric.unit(),row,3);
            workspaceKpiModel.setValueAt(metric.sourceId(),row,5);
        }
        if("floor_denials".equalsIgnoreCase(id)||"damages".equalsIgnoreCase(id)||"alerts".equalsIgnoreCase(id))
            workspaceKpiModel.setValueAt("Lower is better",row,4);
        int viewRow=workspaceKpiTable.convertRowIndexToView(row);
        if(viewRow>=0){workspaceKpiTable.setRowSelectionInterval(viewRow,viewRow);workspaceKpiTable.scrollRectToVisible(workspaceKpiTable.getCellRect(viewRow,0,true));}
    }

    private int findWorkspaceKpiRow(String metricId,String label){
        String wantedId=normalizeMetricKey(metricId),wantedLabel=normalizeMetricKey(label);
        for(int row=0;row<workspaceKpiModel.getRowCount();row++){
            String existing=normalizeMetricKey(cell(workspaceKpiModel,row,0));
            if(existing.equals(wantedLabel)||existing.equals(wantedId)
                    ||("lhy".equals(wantedId)&&existing.contains("lhy"))
                    ||("lines".equals(wantedId)&&(existing.contains("linesshipped")||existing.equals("lines")))
                    ||("floordenials".equals(wantedId)&&existing.contains("floordenial")))return row;
        }
        return -1;
    }

    private static String normalizeMetricKey(String value){
        return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }

    /**
     * Returns the theme currently being previewed in Settings. Dynamic controls
     * created after the initial ThemeStyler pass must use this theme immediately
     * instead of waiting for another full-dialog theme refresh.
     */
    private AppTheme currentSettingsTheme(){
        return AppTheme.NORTH_STAR;
    }

    private void rebuildWidgetRows(){
        int count=(Integer)blockCount.getSelectedItem();
        List<String> oldIds=new ArrayList<>();
        for(JComboBox<WidgetChoice> box:widgetBoxes){
            WidgetChoice c=(WidgetChoice)box.getSelectedItem();
            oldIds.add(c==null?"STATUS":c.id());
        }

        widgetRows.removeAll();
        widgetBoxes.clear();
        widgetRows.setLayout(new GridLayout(0,2,12,10));
        widgetRows.setBorder(BorderFactory.createEmptyBorder(4,2,4,2));
        widgetRows.setOpaque(false);

        List<WidgetChoice> choices=widgetChoices();
        AppTheme activeTheme=currentSettingsTheme();

        for(int i=0;i<count;i++){
            JComboBox<WidgetChoice> box=
                    new JComboBox<>(choices.toArray(new WidgetChoice[0]));
            ThemeStyler.apply(box,activeTheme);
            box.setPreferredSize(new Dimension(360,42));
            box.setMinimumSize(new Dimension(220,42));
            widgetBoxes.add(box);

            RoundedPanel slot=new RoundedPanel(12);
            slot.setLayout(new BorderLayout(10,6));
            slot.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));

            JLabel label=new JLabel("Card "+(i+1));
            label.setForeground(Theme.muted());
            label.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));

            slot.add(label,BorderLayout.WEST);
            slot.add(box,BorderLayout.CENTER);
            widgetRows.add(slot);

            String desired=i<oldIds.size()?oldIds.get(i):
                    (i<cfg.widgetTypes.size()?cfg.widgetTypes.get(i):"STATUS");
            selectWidgetId(box,desired);
        }

        if((count&1)==1){
            JPanel filler=new JPanel();
            filler.setOpaque(false);
            widgetRows.add(filler);
        }

        ThemeStyler.apply(widgetRows,activeTheme);
        widgetRows.revalidate();
        widgetRows.repaint();
    }

    private List<WidgetChoice> widgetChoices(){
        List<WidgetChoice> out=new ArrayList<>();
        out.add(new WidgetChoice("WEATHER_PRIMARY","Current Weather • "+primaryNameValue()));
        out.add(new WidgetChoice("FORECAST_PRIMARY","Hourly Outlook • "+primaryNameValue()));
        out.add(new WidgetChoice("WIND_PRIMARY","Wind & Gusts • "+primaryNameValue()));
        out.add(new WidgetChoice("ALERTS","Severe Weather Alerts"));
        out.add(new WidgetChoice("MEDIA","Media / Announcements"));
        out.add(new WidgetChoice("STATUS","System Status"));

        for(int i=0;i<locationModel.getRowCount();i++){
            String name=cell(locationModel,i,0);
            if(!name.isBlank())
                out.add(new WidgetChoice("WEATHER_LOCATION_"+i,"Current Weather • "+name));
        }
        for(int i=0;i<routeModel.getRowCount();i++){
            String name=cell(routeModel,i,0);
            if(!name.isBlank())
                out.add(new WidgetChoice("ROUTE_"+i,"Route Time • "+name));
        }
        for(int i=0;i<sportsModel.getRowCount();i++){
            String name=cell(sportsModel,i,0);
            if(!name.isBlank())
                out.add(new WidgetChoice("SPORTS_"+i,"Upcoming Schedule • "+name));
        }
        return out;
    }

    private void findPrimaryLocation(){
        String initial=primaryName.getText().trim();
        if(initial.equalsIgnoreCase("Primary Location")) initial="";

        LocationSearchDialog dialog=new LocationSearchDialog(
                this,
                initial,
                result->{
                    primaryName.setText(result.name());
                    primaryLat.setText(Double.toString(result.latitude()));
                    primaryLon.setText(Double.toString(result.longitude()));
                }
        );
        dialog.setVisible(true);
    }

    private void findPinnedLocation(){
        stopTableEditing(locationTable);

        int selected=locationTable.getSelectedRow();
        int modelRow=selected<0 ? -1 : locationTable.convertRowIndexToModel(selected);

        String initial="";
        if(modelRow>=0){
            initial=cell(locationModel,modelRow,0).trim();
            if(initial.equalsIgnoreCase("New Location")) initial="";
        }

        final int targetRow=modelRow;
        LocationSearchDialog dialog=new LocationSearchDialog(
                this,
                initial,
                result->applyPinnedLocationResult(targetRow,result)
        );
        dialog.setVisible(true);
    }

    private void applyPinnedLocationResult(int existingRow,LocationSearchResult result){
        int row=existingRow;
        if(row<0 || row>=locationModel.getRowCount()){
            locationModel.addRow(new Object[]{
                    result.name(),result.latitude(),result.longitude()
            });
            row=locationModel.getRowCount()-1;
        }else{
            locationModel.setValueAt(result.name(),row,0);
            locationModel.setValueAt(result.latitude(),row,1);
            locationModel.setValueAt(result.longitude(),row,2);
        }

        rebuildWidgetRows();
        int viewRow=locationTable.convertRowIndexToView(row);
        if(viewRow>=0){
            locationTable.setRowSelectionInterval(viewRow,viewRow);
            locationTable.scrollRectToVisible(locationTable.getCellRect(viewRow,0,true));
        }
    }

    private void findRouteDestination(){
        stopTableEditing(routeTable);

        int selected=routeTable.getSelectedRow();
        int modelRow=selected<0 ? -1 : routeTable.convertRowIndexToModel(selected);

        String initial="";
        if(modelRow>=0){
            initial=cell(routeModel,modelRow,1).trim();
            if(initial.equalsIgnoreCase("Destination")) initial="";
        }

        final int targetRow=modelRow;
        LocationSearchDialog dialog=new LocationSearchDialog(
                this,
                initial,
                result->applyRouteLocationResult(targetRow,result)
        );
        dialog.setVisible(true);
    }

    private void applyRouteLocationResult(int existingRow,LocationSearchResult result){
        int row=existingRow;
        if(row<0 || row>=routeModel.getRowCount()){
            routeModel.addRow(new Object[]{
                    result.name(),result.name(),result.latitude(),result.longitude()
            });
            row=routeModel.getRowCount()-1;
        }else{
            String currentRoute=cell(routeModel,row,0).trim();
            if(currentRoute.isBlank() || currentRoute.equalsIgnoreCase("New Route"))
                routeModel.setValueAt(result.name(),row,0);

            routeModel.setValueAt(result.name(),row,1);
            routeModel.setValueAt(result.latitude(),row,2);
            routeModel.setValueAt(result.longitude(),row,3);
        }

        rebuildWidgetRows();
        int viewRow=routeTable.convertRowIndexToView(row);
        if(viewRow>=0){
            routeTable.setRowSelectionInterval(viewRow,viewRow);
            routeTable.scrollRectToVisible(routeTable.getCellRect(viewRow,0,true));
        }
    }

    private void addRouteFromPinnedLocation(){
        int row=locationTable.getSelectedRow();
        if(row<0){
            ThemedDialogs.message(
                    this,
                    "Select a pinned location on the Pinned Locations tab first.",
                    "Select a Location",
                    ThemedDialogs.Kind.INFO
            );
            return;
        }
        String name=cell(locationModel,row,0);
        routeModel.addRow(new Object[]{name,name,cell(locationModel,row,1),cell(locationModel,row,2)});
        rebuildWidgetRows();
    }

    private void removeSelected(JTable table, DefaultTableModel model){
        int[] rows=table.getSelectedRows();
        for(int i=rows.length-1;i>=0;i--) model.removeRow(rows[i]);
        rebuildWidgetRows();
    }

    private void save(){
        try{
            stopTableEditing(locationTable);
            stopTableEditing(routeTable);
            stopTableEditing(sportsTable);
            stopTableEditing(celebrationTable);
            stopTableEditing(operationTable);
            stopTableEditing(workspaceKpiTable);

            applySecurityChanges();

            cfg.headerText=header.getText().trim();
            cfg.tickerText=ticker.getText().trim();
            cfg.showHeader=showHeader.isSelected();
            cfg.showTicker=showTicker.isSelected();
            cfg.showWeatherAlertsInTicker=showWeatherAlertsInTicker.isSelected();
            cfg.fullscreen=fullscreen.isSelected();
            cfg.loginRequiredOnStartup=loginRequiredOnStartup.isSelected();
            cfg.protectApiSettings=protectApiSettings.isSelected();
            AppTheme selected=(AppTheme)themeSelector.getSelectedItem();
            if(selected==null)selected=AppTheme.NORTH_STAR;
            cfg.themeId=selected.id();
            cfg.automaticHolidayThemes=automaticHolidayThemes.isSelected();
            cfg.darkMode=selected.dark();
            cfg.themeOverlayEffects=themeEffects.isSelected();
            cfg.overlayIntensity=String.valueOf(overlayIntensity.getSelectedItem());
            cfg.showRadar=radar.isSelected();
            cfg.showTraffic=traffic.isSelected();
            cfg.showAlertsOnMap=alertMap.isSelected();
            cfg.liveSevereWeatherMode=liveSevereWeather.isSelected();
            cfg.automaticSevereWeatherMode=automaticSevereWeather.isSelected();
            cfg.autoDisableSevereWeatherMode=autoDisableSevereWeather.isSelected();
            cfg.weatherProvider=weatherProvider.getSelectedIndex()==1?"OPEN_METEO_CUSTOMER":"OPEN_METEO_FREE";
            cfg.alertProvider="NWS";
            cfg.radarProvider="RAINVIEWER";
            cfg.trafficProvider="TOMTOM";
            cfg.sportsProvider="THESPORTSDB";
            cfg.sportsPremiumLiveScores=sportsPremium.isSelected();
            cfg.sportsApiKey=new String(sportsKey.getPassword()).trim();
            if(cfg.sportsApiKey.isBlank()) cfg.sportsApiKey="123";
            cfg.weatherApiKey=new String(weatherKey.getPassword()).trim();
            cfg.tomTomApiKey=new String(tomTom.getPassword()).trim();
            cfg.nwsUserAgent=nwsUserAgent.getText().trim();
            if(cfg.nwsUserAgent.isBlank())
                cfg.nwsUserAgent="WeatherTrafficMonitor/1.6 (workplace-display; contact=local-admin)";
            cfg.trafficRefreshMinutes=(Integer)routeRefresh.getSelectedItem();
            cfg.weatherRefreshMinutes=(Integer)weatherRefresh.getSelectedItem();
            cfg.radarRefreshMinutes=(Integer)radarRefresh.getSelectedItem();
            cfg.alertRefreshMinutes=(Integer)alertRefresh.getSelectedItem();
            cfg.sportsRefreshMinutes=(Integer)sportsRefresh.getSelectedItem();
            cfg.mainShowcaseMediaEnabled=showcaseMedia.isSelected();
            cfg.severeWeatherMapPriority=severeMapPriority.isSelected();
            cfg.mainShowcaseIntervalSeconds=(Integer)showcaseInterval.getSelectedItem();
            cfg.celebrationsEnabled=celebrationsEnabled.isSelected();
            cfg.operationsAnnouncementsEnabled=
                    operationsAnnouncementsEnabled.isSelected();
            cfg.operationsDefaultLeadDays=
                    (Integer)operationsDefaultLeadDays.getSelectedItem();
            cfg.normalOperatingStart=parseTime(
                    normalOperatingStart.getText(),
                    "Normal operating start");
            cfg.normalOperatingEnd=parseTime(
                    normalOperatingEnd.getText(),
                    "Normal operating end");

            cfg.normalOperatingDays.clear();
            if(normalMon.isSelected())cfg.normalOperatingDays.add(DayOfWeek.MONDAY);
            if(normalTue.isSelected())cfg.normalOperatingDays.add(DayOfWeek.TUESDAY);
            if(normalWed.isSelected())cfg.normalOperatingDays.add(DayOfWeek.WEDNESDAY);
            if(normalThu.isSelected())cfg.normalOperatingDays.add(DayOfWeek.THURSDAY);
            if(normalFri.isSelected())cfg.normalOperatingDays.add(DayOfWeek.FRIDAY);
            if(normalSat.isSelected())cfg.normalOperatingDays.add(DayOfWeek.SATURDAY);
            if(normalSun.isSelected())cfg.normalOperatingDays.add(DayOfWeek.SUNDAY);

            if(cfg.normalOperatingDays.isEmpty())
                throw new IllegalArgumentException(
                        "Select at least one normal operating day.");

            if(!cfg.normalOperatingEnd.isAfter(cfg.normalOperatingStart))
                throw new IllegalArgumentException(
                        "Normal operating end time must be after start time.");


            cfg.primary=new Location(
                    required(primaryName.getText(),"Primary location name"),
                    number(primaryLat.getText(),"Primary latitude"),
                    number(primaryLon.getText(),"Primary longitude")
            );

            cfg.monitored.clear();
            for(int i=0;i<locationModel.getRowCount();i++){
                String name=cell(locationModel,i,0).trim();
                if(name.isBlank()) continue;
                cfg.monitored.add(new Location(
                        name,
                        number(cell(locationModel,i,1),name+" latitude"),
                        number(cell(locationModel,i,2),name+" longitude")
                ));
            }
            if(cfg.monitored.isEmpty()) cfg.monitored.add(cfg.primary);

            cfg.routes.clear();
            for(int i=0;i<routeModel.getRowCount();i++){
                String routeName=cell(routeModel,i,0).trim();
                String destName=cell(routeModel,i,1).trim();
                if(routeName.isBlank()||destName.isBlank()) continue;
                Location d=new Location(
                        destName,
                        number(cell(routeModel,i,2),destName+" latitude"),
                        number(cell(routeModel,i,3),destName+" longitude")
                );
                cfg.routes.add(new RouteConfig(routeName,cfg.primary,d));
            }

            /*
             * Employee Operations is authoritative for identity/recognition.
             * Rebuild the compatibility celebration projection from that store
             * rather than persisting the retired duplicate Team Celebrations
             * editor model.
             */
            com.wtm.employee.EmployeeService.syncCelebrations(
                    cfg,
                    com.wtm.employee.EmployeeService.loadForSystem()
            );

            cfg.operationEvents.clear();
            for(int i=0;i<operationModel.getRowCount();i++){
                String name=cell(operationModel,i,0).trim();
                if(name.isBlank())continue;

                LocalDate start;
                LocalDate end;
                try{
                    start=LocalDate.parse(cell(operationModel,i,1).trim());
                }catch(Exception ex){
                    throw new IllegalArgumentException(
                            name+" start date must use YYYY-MM-DD.");
                }

                String endValue=cell(operationModel,i,2).trim();
                try{
                    end=endValue.isBlank()?start:LocalDate.parse(endValue);
                }catch(Exception ex){
                    throw new IllegalArgumentException(
                            name+" end date must use YYYY-MM-DD.");
                }

                if(end.isBefore(start))
                    throw new IllegalArgumentException(
                            name+" end date cannot be before its start date.");

                OperationType type=OperationType.from(
                        String.valueOf(operationModel.getValueAt(i,3)));

                LocalTime eventStart=null;
                LocalTime eventEnd=null;

                if(type!=OperationType.FULL_CLOSURE){
                    eventStart=parseTime(
                            cell(operationModel,i,4),
                            name+" start time");
                    eventEnd=parseTime(
                            cell(operationModel,i,5),
                            name+" end time");

                    if(!eventEnd.isAfter(eventStart))
                        throw new IllegalArgumentException(
                                name+" end time must be after start time.");
                }

                int leadDays=0;
                String lead=cell(operationModel,i,6).trim();
                if(!lead.isBlank()){
                    try{leadDays=Integer.parseInt(lead);}
                    catch(Exception ex){
                        throw new IllegalArgumentException(
                                name+" Lead Days must be a whole number.");
                    }
                    if(leadDays<0)
                        throw new IllegalArgumentException(
                                name+" Lead Days cannot be negative.");
                }

                boolean enabled=Boolean.TRUE.equals(
                        operationModel.getValueAt(i,7));

                cfg.operationEvents.add(new OperationEvent(
                        name,start,end,type,eventStart,eventEnd,leadDays,enabled));
            }

            cfg.sports.clear();
            for(int i=0;i<sportsModel.getRowCount();i++){
                String name=cell(sportsModel,i,0).trim();
                String sport=cell(sportsModel,i,1).trim();
                String leagueId=cell(sportsModel,i,2).trim();
                String teamId=cell(sportsModel,i,3).trim();
                String teamName=cell(sportsModel,i,4).trim();
                boolean showLogos=Boolean.TRUE.equals(sportsModel.getValueAt(i,5));
                if(name.isBlank()||teamId.isBlank()||teamName.isBlank()) continue;
                cfg.sports.add(new SportsConfig(name,sport,leagueId,teamId,teamName,showLogos));
            }

            cfg.workspaceModules.clear();
            if(workspaceWeather.isSelected())cfg.workspaceModules.add("WEATHER");
            if(workspaceTrafficMap.isSelected())cfg.workspaceModules.add("TRAFFIC_MAP");
            if(workspaceEvents.isSelected())cfg.workspaceModules.add("UPCOMING_EVENTS");
            if(workspaceCelebrations.isSelected())cfg.workspaceModules.add("TEAM_CELEBRATIONS");
            if(workspaceOperationsSnapshot.isSelected())cfg.workspaceModules.add("OPERATIONS_SNAPSHOT");

            cfg.workspaceInfoStripEnabled=workspaceInfoStrip.isSelected();
            Object infoCount=workspaceInfoBlockCount.getSelectedItem();
            cfg.workspaceInfoBlockCount=infoCount instanceof Integer value
                    ?Math.max(2,Math.min(8,value))
                    :4;
            String movement=Objects.toString(
                    workspaceInfoMovementMode.getSelectedItem(),
                    "Static"
            );
            cfg.workspaceInfoMovementMode=switch(movement){
                case "Paged Rotation"->"PAGED";
                case "Continuous Ticker"->"TICKER";
                default->"STATIC";
            };

            Object scrollSeconds=workspaceInfoScrollSeconds.getSelectedItem();
            cfg.workspaceInfoScrollSeconds=scrollSeconds instanceof Integer seconds
                    ?Math.max(5,Math.min(60,seconds))
                    :10;

            Object tickerSpeed=workspaceInfoTickerSpeed.getSelectedItem();
            cfg.workspaceInfoTickerPixelsPerSecond=
                    tickerSpeed instanceof Integer speed
                            ?Math.max(8,Math.min(120,speed))
                            :28;

            cfg.workspaceOpsVisibleCount=(Integer)workspaceOpsBlockCount.getSelectedItem();
            String opsMovement=String.valueOf(workspaceOpsMovementMode.getSelectedItem());
            cfg.workspaceOpsMovementMode=switch(opsMovement){case "Paged Rotation"->"PAGED"; case "Continuous Ticker"->"TICKER"; default->"STATIC";};
            cfg.workspaceOpsScrollSeconds=(Integer)workspaceOpsScrollSeconds.getSelectedItem();
            cfg.workspaceOpsTickerPixelsPerSecond=(Integer)workspaceOpsTickerSpeed.getSelectedItem();

            cfg.operationsKpis.clear();
            for(int i=0;i<workspaceKpiModel.getRowCount();i++){
                String label=cell(workspaceKpiModel,i,0).trim();
                if(label.isBlank())continue;
                double current=workspaceNumber(
                        cell(workspaceKpiModel,i,1),label+" current value",0);
                String targetText=cell(workspaceKpiModel,i,2).trim();
                double target=targetText.isBlank()?Double.NaN:workspaceNumber(
                        targetText,label+" target",Double.NaN);
                String unit=cell(workspaceKpiModel,i,3);
                boolean higher=!"Lower is better".equalsIgnoreCase(
                        cell(workspaceKpiModel,i,4));
                String source=cell(workspaceKpiModel,i,5).trim().toUpperCase();
                if(source.isBlank())source="MANUAL";
                boolean enabled=Boolean.TRUE.equals(
                        workspaceKpiModel.getValueAt(i,6));
                String id=label.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+","_")
                        .replaceAll("^_+|_+$","");
                if(id.isBlank())id="kpi_"+i;
                cfg.operationsKpis.add(new OperationsKpiConfig(
                        id,label,current,target,unit,higher,enabled,source));
            }

            cfg.visibleWidgetCount=(Integer)blockCount.getSelectedItem();
            cfg.widgetTypes.clear();
            for(JComboBox<WidgetChoice> box:widgetBoxes){
                WidgetChoice choice=(WidgetChoice)box.getSelectedItem();
                cfg.widgetTypes.add(choice==null?"STATUS":choice.id());
            }

            ConfigService.save(cfg);
            AuditService.record("Saved application settings");
            onSave.accept(cfg);
            dispose();
        }catch(Exception ex){
            ThemedDialogs.message(
                    this,
                    "Unable to save settings:\n"+ex.getMessage(),
                    "Settings Error",
                    ThemedDialogs.Kind.ERROR
            );
        }
    }

    private static void stopTableEditing(JTable table){
        if(table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    private static double number(String text,String label){
        try{return Double.parseDouble(text.trim());}
        catch(Exception e){throw new IllegalArgumentException(label+" must be a valid number.");}
    }

    private static String required(String text,String label){
        String s=text.trim();
        if(s.isBlank()) throw new IllegalArgumentException(label+" cannot be blank.");
        return s;
    }

    private String primaryNameValue(){
        String s=primaryName.getText().trim();
        return s.isBlank()?cfg.primary.name():s;
    }

    private static String escapeHtml(String value){
        return value==null?"":value
                .replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;");
    }

    private static String cell(DefaultTableModel m,int row,int col){
        Object v=m.getValueAt(row,col);
        return v==null?"":v.toString();
    }

    private static void selectWidgetId(JComboBox<WidgetChoice> box,String id){
        for(int i=0;i<box.getItemCount();i++){
            WidgetChoice c=box.getItemAt(i);
            if(c.id().equals(id)){box.setSelectedIndex(i);return;}
        }
        // Missing IDs can occur after a route/location was deleted. Fall back safely.
        for(int i=0;i<box.getItemCount();i++)
            if(box.getItemAt(i).id().equals("STATUS")){box.setSelectedIndex(i);return;}
    }


    private static final DateTimeFormatter SETTINGS_TIME=
            DateTimeFormatter.ofPattern("h:mm a");

    private static String formatTimeForSettings(LocalTime time){
        return time==null?"":time.format(SETTINGS_TIME);
    }

    private static LocalTime parseTime(String value,String field){
        String text=value==null?"":value.trim().toUpperCase();
        if(text.isBlank())
            throw new IllegalArgumentException(field+" is required.");

        DateTimeFormatter[] formats={
                DateTimeFormatter.ofPattern("h:mm a"),
                DateTimeFormatter.ofPattern("h:mma"),
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("h a")
        };

        for(DateTimeFormatter format:formats){
            try{return LocalTime.parse(text,format);}
            catch(Exception ignored){}
        }

        throw new IllegalArgumentException(
                field+" must use a time such as 7:30 AM, 11:00 AM, or 16:00.");
    }

    private static LocalTime parseTimeOrDefault(String value,LocalTime fallback){
        try{return parseTime(value,"Time");}
        catch(Exception ignored){return fallback;}
    }

    /**
     * Detaches one authorized Settings page so the Operations Workspace can
     * present it as an in-application route rather than displaying the entire
     * North Star settings window. The SettingsDialog instance must remain alive
     * while the returned component is in use because its controls and save
     * logic are intentionally shared.
     */
    public JComponent detachTabForWorkspace(String title){
        if(title==null||title.isBlank())return null;

        for(int i=0;i<tabs.getTabCount();i++){
            if(title.equalsIgnoreCase(tabs.getTitleAt(i))){
                Component component=tabs.getComponentAt(i);
                tabs.removeTabAt(i);
                return component instanceof JComponent jc?jc:null;
            }
        }
        return null;
    }

    /** Saves an embedded workspace settings page through the centralized validation path. */
    public void saveEmbeddedPage(){
        save();
    }

    /** Discards an embedded settings session without altering persisted configuration. */
    public void discardEmbeddedPage(){
        Theme.setActive(originalTheme.id());
        dispose();
    }

    /**
     * Protected API pages still require permission/step-up authentication in
     * the workspace. Once that has been performed by the workspace shell, this
     * exposes the protected content for the detached page.
     */
    public void unlockProtectedForWorkspace(){
        apiUnlockedThisSettingsSession=true;
        unlockProtectedApiContent();
    }

    public void selectTab(String title){
        if(title==null||title.isBlank())return;
        for(int i=0;i<tabs.getTabCount();i++){
            if(title.equalsIgnoreCase(tabs.getTitleAt(i))){
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String formatSettingsNumber(double value){
        if(!Double.isFinite(value))return "";
        if(Math.abs(value-Math.rint(value))<0.000001)
            return String.format(Locale.US,"%.0f",value);
        return String.format(Locale.US,"%.2f",value);
    }

    private static double workspaceNumber(
            String value,String label,double blankDefault){
        String text=value==null?"":value.trim().replace(",","");
        if(text.isBlank())return blankDefault;
        try{return Double.parseDouble(text);}
        catch(Exception ex){throw new IllegalArgumentException(
                label+" must be a number.");}
    }

    private static void selectInteger(JComboBox<Integer> box,int value){
        for(int i=0;i<box.getItemCount();i++){
            if(box.getItemAt(i)==value){
                box.setSelectedIndex(i);
                return;
            }
        }
        // Preserve old/custom configurations by adding their current value.
        box.addItem(value);
        box.setSelectedItem(value);
    }

    private static JPanel form(){
        JPanel p=new JPanel(new GridBagLayout());
        // Consistent page breathing room across all North Star settings.
        p.setBorder(BorderFactory.createEmptyBorder(22,24,24,24));
        return p;
    }

    private static void addRow(JPanel p,int y,String label,JComponent field){
        GridBagConstraints a=new GridBagConstraints();
        a.gridx=0;a.gridy=y;a.anchor=GridBagConstraints.WEST;
        a.insets=new Insets(8,4,8,18);
        p.add(new JLabel(label),a);

        GridBagConstraints b=new GridBagConstraints();
        b.gridx=1;
        b.gridy=y;
        b.weightx=1;
        b.fill=GridBagConstraints.HORIZONTAL;
        b.anchor=GridBagConstraints.CENTER;
        b.insets=new Insets(8,4,8,4);
        normalizeSettingsControl(field);
        p.add(field,b);
    }

    private static void addFull(JPanel p,int y,JComponent c){
        GridBagConstraints b=new GridBagConstraints();
        b.gridx=0;b.gridy=y;b.gridwidth=2;b.weightx=1;b.fill=GridBagConstraints.HORIZONTAL;
        b.insets=new Insets(8,4,8,4);
        p.add(c,b);
    }

    private static void normalizeSettingsControl(JComponent component){
        int height=component instanceof JComboBox<?>
                ?ThemedComboBoxUI.CONTROL_HEIGHT
                :38;
        if(component instanceof JTextField
                ||component instanceof JPasswordField
                ||component instanceof JComboBox<?>
                ||component instanceof JSpinner){
            Dimension preferred=component.getPreferredSize();
            int width=Math.max(160,preferred==null?160:preferred.width);
            component.setPreferredSize(new Dimension(width,height));
            component.setMinimumSize(new Dimension(120,height));
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
        }
    }

    private record WidgetChoice(String id,String label){
        @Override public String toString(){return label;}
    }
}
