from pathlib import Path
import re


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def replace_method(text, signature, replacement, label=None):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"{label or signature}: signature not found")
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f"{label or signature}: opening brace not found")
    depth = 0
    in_string = False
    in_char = False
    escaped = False
    i = brace
    while i < len(text):
        ch = text[i]
        if escaped:
            escaped = False
        elif ch == '\\' and (in_string or in_char):
            escaped = True
        elif ch == '"' and not in_char:
            in_string = not in_string
        elif ch == "'" and not in_string:
            in_char = not in_char
        elif not in_string and not in_char:
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement + text[i + 1:]
        i += 1
    raise SystemExit(f"{label or signature}: unterminated method")


# ---------------------------------------------------------------------------
# AppConfig: current grid defaults are current-version state, not legacy state.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/config/AppConfig.java'
text = read(path)
text = replace_once(
    text,
'''    /** Persisted 12-column dashboard tile geometry: x,y,width,height. */
    public final Map<String,String> workspaceDashboardLayout=new LinkedHashMap<>(Map.of(
            "WEATHER","0,0,3,6",
            "SHOWCASE","3,0,6,6",
            "UPCOMING_EVENTS","9,0,3,3",
            "TEAM_CELEBRATIONS","9,3,3,3",
            "INFORMATION","0,6,12,1",
            "OPERATIONS_SNAPSHOT","0,7,12,2"
    ));''',
'''    /** Persisted 24-column dashboard tile geometry: x,y,width,height. */
    public final Map<String,String> workspaceDashboardLayout=new LinkedHashMap<>(Map.of(
            "_gridVersion","2",
            "WEATHER","0,0,6,6",
            "SHOWCASE","6,0,12,6",
            "UPCOMING_EVENTS","18,0,6,3",
            "TEAM_CELEBRATIONS","18,3,6,3",
            "INFORMATION","0,6,24,1",
            "OPERATIONS_SNAPSHOT","0,7,24,2"
    ));''',
    'AppConfig dashboard defaults'
)
text = text.replace(
'''    /**
     * When more information choices are configured than fit in the compact
     * strip, automatically page through them without increasing dashboard
     * height. The interval is deliberately slow enough for warehouse viewing.
     */
    /**
     * Information movement behavior:''',
'''    /**
     * Information movement behavior:'''
)
text = re.sub(
    r'\n    /\*\*\n     \* Legacy compatibility value from the pre-grid dashboard\.[\s\S]*?\n    public int mapWidthPercent = 63;\n',
    '\n',
    text,
    count=1
)
if 'mapWidthPercent' in text:
    raise SystemExit('AppConfig: mapWidthPercent survived cleanup')
write(path, text)


# ---------------------------------------------------------------------------
# ConfigService: distinguish current defaults from persisted legacy layouts,
# and retire the obsolete pre-grid ratio property completely.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/config/ConfigService.java'
text = read(path)
old = '''            /*
             * Reload every persisted dashboard-layout entry, including private
             * metadata such as _gridVersion and future/dynamic tile IDs.
             * Dropping _gridVersion caused an already-migrated 24-column layout
             * to be treated as legacy again after each application restart.
             */
            final String layoutPrefix="workspace.layout.";
            for(String propertyName:p.stringPropertyNames()){
                if(!propertyName.startsWith(layoutPrefix))continue;
                String id=propertyName.substring(layoutPrefix.length()).trim();
                String saved=p.getProperty(propertyName);
                if(id.isBlank()||saved==null||saved.isBlank())continue;
                cfg.workspaceDashboardLayout.put(id,saved.trim());
            }
'''
new = '''            /*
             * A persisted layout replaces the current defaults as one map.
             * This preserves metadata/dynamic tile IDs and, critically, keeps an
             * unversioned persisted map distinguishable from a fresh versioned
             * default so DashboardGridPanel can migrate it exactly once.
             */
            final String layoutPrefix="workspace.layout.";
            Map<String,String> savedLayout=new LinkedHashMap<>();
            for(String propertyName:p.stringPropertyNames()){
                if(!propertyName.startsWith(layoutPrefix))continue;
                String id=propertyName.substring(layoutPrefix.length()).trim();
                String saved=p.getProperty(propertyName);
                if(id.isBlank()||saved==null||saved.isBlank())continue;
                savedLayout.put(id,saved.trim());
            }
            if(!savedLayout.isEmpty()){
                cfg.workspaceDashboardLayout.clear();
                cfg.workspaceDashboardLayout.putAll(savedLayout);
            }
'''
text = replace_once(text, old, new, 'ConfigService layout load')
text = re.sub(
    r'\n\s*cfg\.mapWidthPercent = Math\.max\(55, Math\.min\(75, integer\(p, "mapWidthPercent", cfg\.mapWidthPercent\)\)\);',
    '',
    text,
    count=1
)
text = re.sub(
    r'\n\s*p\.setProperty\("mapWidthPercent", Integer\.toString\(cfg\.mapWidthPercent\)\);',
    '',
    text,
    count=1
)
if 'mapWidthPercent' in text:
    raise SystemExit('ConfigService: mapWidthPercent survived cleanup')
write(path, text)


# ---------------------------------------------------------------------------
# DashboardGridPanel: legacy/current detection is a map-level invariant. Never
# reinterpret individual left-side tiles independently inside a 24-column map.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/ui/DashboardGridPanel.java'
text = read(path)
text = replace_method(
    text,
    '    private void migrateLegacyLayout()',
'''    private void migrateLegacyLayout(){
        if(GRID_VERSION.equals(layout.get(GRID_VERSION_KEY)))return;

        Map<String,GridSpec> parsed=new LinkedHashMap<>();
        for(Map.Entry<String,String> entry:layout.entrySet()){
            if(entry.getKey().startsWith("_"))continue;
            GridSpec spec=GridSpec.parse0(entry.getValue());
            if(spec!=null)parsed.put(entry.getKey(),spec);
        }

        /*
         * Grid width is a property of the entire persisted layout. If every
         * valid tile fits inside twelve columns, the map is legacy and every
         * tile is migrated together. If any tile extends beyond column twelve,
         * the whole unversioned map is already 24-column geometry and must not
         * be partially doubled.
         */
        boolean legacyTwelveColumn=!parsed.isEmpty()
                &&parsed.values().stream().allMatch(spec->spec.x+spec.w<=12);

        for(Map.Entry<String,GridSpec> entry:parsed.entrySet()){
            GridSpec spec=entry.getValue();
            if(legacyTwelveColumn)
                spec=new GridSpec(spec.x*2,spec.y,spec.w*2,spec.h);
            layout.put(entry.getKey(),spec.clamped().encode());
        }

        layout.put(GRID_VERSION_KEY,GRID_VERSION);
        persist.run();
    }''',
    'DashboardGridPanel migration'
)
write(path, text)


# ---------------------------------------------------------------------------
# MainShowcasePanel: severe state is source-agnostic. Manual and automatic mode
# both drive this single contract.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/ui/MainShowcasePanel.java'
text = read(path)
text = text.replace('automaticSevereWeatherActive', 'severeWeatherActive')
text = text.replace('setAutomaticSevereWeatherActive', 'setSevereWeatherActive')
write(path, text)


# ---------------------------------------------------------------------------
# OperationsWorkspaceFrame: one alert policy, one severe state, one refresh
# cadence owner, and source-owned presentation for badge/popup/ticker.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/ui/OperationsWorkspaceFrame.java'
text = read(path)
text = replace_once(
    text,
    'import com.wtm.alerts.NwsAlertService;\n',
    'import com.wtm.alerts.NwsAlertService;\nimport com.wtm.alerts.SevereWeatherRefreshPolicy;\nimport com.wtm.alerts.WeatherAlertPolicy;\n',
    'OperationsWorkspaceFrame alert imports'
)
text = replace_once(
    text,
'''    private ScheduledExecutorService refreshExecutor;
    private TileMapPanel map;''',
'''    private ScheduledExecutorService refreshExecutor;
    private ScheduledFuture<?> weatherRefreshFuture;
    private ScheduledFuture<?> alertRefreshFuture;
    private ScheduledFuture<?> radarRefreshFuture;
    private ScheduledFuture<?> trafficRefreshFuture;
    private ScheduledFuture<?> sportsRefreshFuture;
    private volatile boolean rapidRefreshScheduled=false;
    private volatile boolean automaticSevereWeatherLatched=false;
    private TileMapPanel map;''',
    'OperationsWorkspaceFrame refresh fields'
)
text = replace_once(
    text,
'''    private JLabel topTrafficLabel;
    private JLabel alertBadge;
    private JButton dashboardLayoutGear;''',
'''    private JLabel topTrafficLabel;
    private JButton alertBadge;
    private HeaderTicker headerTicker;
    private JButton dashboardLayoutGear;''',
    'OperationsWorkspaceFrame alert UI fields'
)
text = text.replace('hasSevereAutomaticPriority()', 'severeWeatherPriorityActive()')

text = replace_method(
    text,
    '    private boolean severeWeatherPriorityActive()',
'''    private boolean severeWeatherPriorityActive(){
        return config.severeWeatherMapPriority&&severeWeatherActive();
    }''',
    'severe priority state'
)

text = replace_method(
    text,
    '    private JComponent buildTopBar()',
'''    private JComponent buildTopBar(){
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
        alertBadge.addActionListener(e->showWeatherAlertMenu(alertBadge));
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
    }''',
    'buildTopBar'
)

text = replace_method(
    text,
    '    private JComponent buildHeaderTickerStrip()',
'''    private JComponent buildHeaderTickerStrip(){
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
    }''',
    'buildHeaderTickerStrip'
)

alert_ui = r'''    private void showWeatherAlertMenu(Component invoker){
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

'''
insert_at = text.find('    private void toggleDashboardLayoutFromGear()')
if insert_at < 0:
    raise SystemExit('alert UI insertion point not found')
text = text[:insert_at] + alert_ui + text[insert_at:]

text = replace_method(
    text,
    '    private Color workspaceInfoDetailColor(String type)',
'''    private Color workspaceInfoDetailColor(String type){
        if(type!=null&&type.startsWith("ROUTE_"))
            return routeInfoColor(type);

        if("ALERTS".equals(type)){
            WeatherAlert primary=primaryWeatherAlert();
            return primary==null?Theme.muted():weatherAlertColor(primary);
        }
        return Theme.muted();
    }''',
    'workspaceInfoDetailColor'
)

text = replace_method(
    text,
    '    private WeatherAlert primaryWeatherAlert()',
'''    private WeatherAlert primaryWeatherAlert(){
        return WeatherAlertPolicy.primary(alerts).orElse(null);
    }''',
    'primaryWeatherAlert'
)

text = replace_method(
    text,
    '    private int weatherAlertPriority(WeatherAlert alert)',
'''    private int weatherAlertPriority(WeatherAlert alert){
        return WeatherAlertPolicy.priority(alert);
    }''',
    'weatherAlertPriority'
)

text = replace_method(
    text,
    '    private static String shortAlertName(String event)',
'''    private static String shortAlertName(String event){
        return WeatherAlertPolicy.shortEventName(event);
    }''',
    'shortAlertName'
)

scheduler_block = '''    private void startRefreshers(){
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
    }'''
text = replace_method(text, '    private void startRefreshers()', scheduler_block, 'startRefreshers')
# replace_method replaced only startRefreshers; replace old stopRefreshers separately.
text = replace_method(
    text,
    '    private void stopRefreshers()',
'''    private synchronized void stopRefreshers(){
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
    }''',
    'stopRefreshers'
)

text = replace_method(
    text,
    '    private void refreshAlerts()',
'''    private void refreshAlerts(){
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
    }''',
    'refreshAlerts'
)

text = replace_method(
    text,
    '    private boolean automaticSevereWeatherActive()',
'''    private boolean severeWeatherActive(){
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
    }''',
    'automatic severe state'
)

text = replace_method(
    text,
    '    private void refreshTopSummaries()',
'''    private void refreshTopSummaries(){
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
    }''',
    'refreshTopSummaries'
)

text = replace_method(
    text,
    '    private void refreshVisibleModules()',
'''    private void refreshVisibleModules(){
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
    }''',
    'refreshVisibleModules'
)

text = replace_method(
    text,
    '    private void releaseDashboardModules()',
'''    private void releaseDashboardModules(){
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
    }''',
    'releaseDashboardModules'
)

# Comments in touched routing code should describe the invariant, not patch chronology.
text = text.replace(
'''        /*
         * v1.0.7: Information Blocks and the former Operations Workspace page
         * are one Settings page now. The sidebar must route to the exact tab
         * title created by SettingsDialog; otherwise detachTabForWorkspace()
         * returns null and incorrectly reports the page as unavailable.
         */''',
'''        /*
         * Information Blocks and Operations Snapshot configuration share the
         * Workspace Setup page, so the sidebar routes to that source-owned tab.
         */'''
)

old_header = '''    private static final class HeaderTicker extends JPanel {'''
start = text.find(old_header)
if start < 0:
    raise SystemExit('HeaderTicker class not found')
# Class is immediately followed by RoundedSidebarButton. Replace that span.
end_marker = '    private static final class RoundedSidebarButton extends JButton {'
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit('HeaderTicker end marker not found')
new_header = r'''    private record TickerEntry(String text,Color color){}

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

'''
text = text[:start] + new_header + text[end:]

# The legacy helper name may still be referenced by Information text methods;
# keep a thin policy delegation, but reject any duplicated weather semantics.
if 'automaticSevereWeatherActive()' in text:
    raise SystemExit('automaticSevereWeatherActive reference survived refactor')
if 'setAutomaticSevereWeatherActive' in text:
    raise SystemExit('old showcase severe API survived refactor')
write(path, text)


# ---------------------------------------------------------------------------
# Settings comments touched by this work describe current ownership only.
# ---------------------------------------------------------------------------
path = 'src/com/wtm/ui/SettingsDialog.java'
text = read(path)
text = text.replace(
'''        /*
         * v1.0.8: The unified Workspace Setup embeds this panel in a larger
         * GridBag page. Without an explicit selector viewport height,
         * BorderLayout.CENTER collapses to essentially one combo-box row.
         * Reserve enough vertical space for all configured Information Block
         * selectors while retaining an internal scrollbar on smaller screens.
         */''',
'''        /*
         * Workspace Setup embeds this panel in a larger GridBag page. Reserve a
         * selector viewport so BorderLayout.CENTER cannot collapse to one row,
         * while retaining an internal scrollbar on smaller screens.
         */'''
)
write(path, text)


# ---------------------------------------------------------------------------
# build.sh: make the foundational invariants permanent release gates.
# ---------------------------------------------------------------------------
path = 'build.sh'
text = read(path)
architecture_gate = r'''
# Dashboard layout and alert behavior have one canonical owner. Obsolete ratio
# state and duplicated severe-weather semantics must not return.
if grep -R -q --include='*.java' -E 'mapWidthPercent|FixedRatioLayout' src; then
  echo "ERROR: retired pre-grid map/information ratio state returned." >&2
  exit 1
fi
if [ ! -f src/com/wtm/alerts/WeatherAlertPolicy.java ] || \
   [ ! -f src/com/wtm/alerts/SevereWeatherRefreshPolicy.java ]; then
  echo "ERROR: canonical weather-alert policy/refresh ownership is missing." >&2
  exit 1
fi
if grep -qE 'tornado emergency|severe thunderstorm warning|flash flood warning' src/com/wtm/ui/OperationsWorkspaceFrame.java; then
  echo "ERROR: weather-alert domain semantics escaped WeatherAlertPolicy into UI code." >&2
  exit 1
fi
if grep -q 'setAutomaticSevereWeatherActive' src/com/wtm/ui/OperationsWorkspaceFrame.java src/com/wtm/ui/MainShowcasePanel.java; then
  echo "ERROR: automatic-only severe presentation contract returned." >&2
  exit 1
fi
'''
marker = '# Dashboard layout editing is owned by the top-right dashboard gear;'
idx = text.find(marker)
if idx < 0:
    raise SystemExit('build.sh architecture insertion marker not found')
text = text[:idx] + architecture_gate + '\n' + text[idx:]

smoke = r'''
# Recent dashboard/configuration repairs are protected by headless regression
# tests so persistence, migration, and weather policy cannot silently diverge.
rm -rf /tmp/ns-foundation-smoke
mkdir -p /tmp/ns-foundation-smoke
javac --release 21 -Xlint:unchecked -Werror -encoding UTF-8 -cp 'out:lib/*' -d /tmp/ns-foundation-smoke \
  ci/WeatherAlertPolicySmokeTest.java \
  ci/DashboardGridMigrationSmokeTest.java \
  ci/ConfigRoundTripSmokeTest.java
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest
java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest
'''
marker = 'java -Djava.awt.headless=true -cp \'/tmp/ns-glyph-smoke:out:lib/*\' GlyphSmokeTest\n'
if marker not in text:
    raise SystemExit('build.sh glyph smoke marker not found')
text = text.replace(marker, marker + smoke, 1)
write(path, text)
