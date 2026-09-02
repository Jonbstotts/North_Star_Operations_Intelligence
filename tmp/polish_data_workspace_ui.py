from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"{label} anchor not found in {path}")
    file.write_text(text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Data Collection: keep the ingestion controls top-anchored inside their card.
# BoxLayout previously allowed the FlowLayout rows to consume excess vertical
# space, making the buttons appear lower than the explanatory content.
# ---------------------------------------------------------------------------
replace_once(
    "src/com/wtm/ui/DataCollectionPanel.java",
    '''        JPanel controls = card();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel heading = sectionTitle("FILE & DVIEW EXPORT INGESTION");
        controls.add(heading);
        controls.add(Box.createVerticalStrut(6));
        JLabel copy = new JLabel(
                "<html>Import recognized CSV exports directly or place files in the managed incoming folder. " +
                "Unknown schemas are held for review instead of being guessed.</html>");
        controls.add(copy);
        controls.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton importCsv = new JButton("Import CSV");
        JButton openIncoming = new JButton("Open Incoming Folder");
        JButton scan = new JButton("Scan Folder Now");
        actions.add(importCsv);
        actions.add(openIncoming);
        actions.add(scan);
        controls.add(actions);
        controls.add(Box.createVerticalStrut(8));

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.setOpaque(false);
        JCheckBox watch = new JCheckBox("Watch incoming folder", ingestion.watchEnabled());
        JCheckBox autoImport = new JCheckBox("Validated auto-import", ingestion.autoImport());
        options.add(watch);
        options.add(autoImport);
        controls.add(options);''',
    '''        JPanel controls = card();
        controls.setLayout(new BorderLayout());

        JPanel controlContent = new JPanel();
        controlContent.setOpaque(false);
        controlContent.setLayout(new BoxLayout(controlContent, BoxLayout.Y_AXIS));

        JLabel heading = sectionTitle("FILE & DVIEW EXPORT INGESTION");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlContent.add(heading);
        controlContent.add(Box.createVerticalStrut(6));

        JLabel copy = new JLabel(
                "<html>Import recognized CSV exports directly or place files in the managed incoming folder. " +
                "Unknown schemas are held for review instead of being guessed.</html>");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlContent.add(copy);
        controlContent.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton importCsv = new JButton("Import CSV");
        JButton openIncoming = new JButton("Open Incoming Folder");
        JButton scan = new JButton("Scan Folder Now");
        actions.add(importCsv);
        actions.add(openIncoming);
        actions.add(scan);
        actions.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, actions.getPreferredSize().height));
        controlContent.add(actions);
        controlContent.add(Box.createVerticalStrut(8));

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.setOpaque(false);
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox watch = new JCheckBox("Watch incoming folder", ingestion.watchEnabled());
        JCheckBox autoImport = new JCheckBox("Validated auto-import", ingestion.autoImport());
        options.add(watch);
        options.add(autoImport);
        options.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, options.getPreferredSize().height));
        controlContent.add(options);

        // BorderLayout.NORTH deliberately owns the vertical anchor. The card may
        // grow with the surrounding workspace, but its controls stay at the top.
        controls.add(controlContent, BorderLayout.NORTH);''',
    "Data Collection import controls"
)


# ---------------------------------------------------------------------------
# Workspace Setup: the Information row is now a core dashboard surface. Remove
# the obsolete visibility checkbox/card and explain behavior directly where the
# row is configured.
# ---------------------------------------------------------------------------
replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''    private final JCheckBox workspaceOperationsSnapshot=new JCheckBox("Operations Snapshot");
    private final JCheckBox workspaceInfoStrip=
            new JCheckBox("Custom Information Blocks");
    private JPanel workspaceModulesPanel;''',
    '''    private final JCheckBox workspaceOperationsSnapshot=new JCheckBox("Operations Snapshot");
    private JPanel workspaceModulesPanel;''',
    "obsolete Information checkbox declaration"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        JLabel help=new JLabel(
                "<html>The Operations Workspace is modular. Disable any module that should not "
              + "appear on the home dashboard. KPI values are editable directly below; "
              + "the Data Source field is the integration hook for future SQL/report feeds.</html>");''',
    '''        JLabel help=new JLabel(
                "<html>The Operations Workspace combines optional dashboard modules with two core "
              + "operational rows. Use the module choices below for the main cards. Configure the "
              + "always-available <b>Information Row</b> and the Operations Snapshot behavior in "
              + "their sections below; tile size and position remain controlled by the dashboard "
              + "grid editor. KPI Data Source values remain the integration hook for future "
              + "SQL/report feeds.</html>");''',
    "Workspace Setup overview"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        RoundedPanel infoStrip=new RoundedPanel(14);
        infoStrip.setLayout(new BorderLayout(12,8));
        infoStrip.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));

        JPanel infoTop=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        infoTop.setOpaque(false);
        infoTop.add(workspaceInfoStrip);

        JLabel infoHelp=new JLabel(
                "<html><b>Information is now configured in one place.</b> "
              + "Use the <b>Information Row</b> section directly below to choose the "
              + "configured items, how many are visible at once, and whether the row is "
              + "Static, Paged Rotation, or a Continuous Ticker. Dashboard tile size and "
              + "position are controlled separately by the dashboard grid editor.</html>"
        );

        infoStrip.add(infoTop,BorderLayout.NORTH);
        infoStrip.add(infoHelp,BorderLayout.CENTER);
        addFull(p,y++,infoStrip);

        JPanel informationSetup=widgets();''',
    '''        JPanel informationSetup=widgets();''',
    "obsolete Information visibility card"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        JLabel unifiedHelp=new JLabel(
                "<html>This page is the single source of truth for the dashboard "
              + "<b>Information</b> row. <b>Configured items</b> determines how many "
              + "selectors appear below. <b>Visible at once</b> controls the fixed viewport. "
              + "If 12 items are configured and Continuous Ticker is selected, all 12 travel "
              + "through that viewport and loop continuously.</html>"
        );''',
    '''        JLabel unifiedHelp=new JLabel(
                "<html><b>Information Row:</b> this core dashboard tile stays available and is "
              + "configured entirely here. <b>Configured items</b> determines how many selectors "
              + "appear below, while <b>Visible at once</b> controls the fixed viewport. Choose "
              + "Static, Paged Rotation, or Continuous Ticker for movement. In ticker mode every "
              + "configured item travels through the viewport and loops continuously. Tile size "
              + "and position are managed separately with the dashboard grid editor.</html>"
        );''',
    "Information Row current help"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        workspaceOperationsSnapshot.setSelected(cfg.workspaceModules.contains("OPERATIONS_SNAPSHOT"));
        workspaceInfoStrip.setSelected(cfg.workspaceInfoStripEnabled);
        workspaceInfoBlockCount.setSelectedItem(''',
    '''        workspaceOperationsSnapshot.setSelected(cfg.workspaceModules.contains("OPERATIONS_SNAPSHOT"));
        workspaceInfoBlockCount.setSelectedItem(''',
    "Information checkbox load binding"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''            cfg.workspaceInfoStripEnabled=workspaceInfoStrip.isSelected();
            Object infoCount=workspaceInfoBlockCount.getSelectedItem();''',
    '''            // Information is a core dashboard row; keep the legacy flag true for
            // compatibility with older configuration files and extension code.
            cfg.workspaceInfoStripEnabled=true;
            Object infoCount=workspaceInfoBlockCount.getSelectedItem();''',
    "Information checkbox save binding"
)


# ---------------------------------------------------------------------------
# Make the Information row invariant explicit across configuration and runtime.
# The old property is retained as a compatibility write, but false values from
# older installs no longer hide the row.
# ---------------------------------------------------------------------------
replace_once(
    "src/com/wtm/config/AppConfig.java",
    '''    /**
     * Compact Information row placed between the primary workspace modules and
     * Operations Snapshot. Information Blocks is the single configuration
     * surface for item count, selected content, viewport size and movement.
     */
    public boolean workspaceInfoStripEnabled = true;''',
    '''    /**
     * Compatibility flag for the core Information row. The row is always active
     * in current builds; the field remains true so older extensions/configuration
     * readers can migrate without a breaking schema change.
     */
    public boolean workspaceInfoStripEnabled = true;''',
    "Information compatibility flag documentation"
)

replace_once(
    "src/com/wtm/config/ConfigService.java",
    '''            cfg.workspaceInfoStripEnabled=bool(
                    p,"workspace.infoStrip.enabled",true);''',
    '''            // The Information row is a core dashboard surface. Ignore legacy
            // false values while keeping the property readable by older builds.
            cfg.workspaceInfoStripEnabled=true;''',
    "Information load invariant"
)

replace_once(
    "src/com/wtm/config/ConfigService.java",
    '''            p.setProperty(
                    "workspace.infoStrip.enabled",
                    Boolean.toString(cfg.workspaceInfoStripEnabled));''',
    '''            p.setProperty(
                    "workspace.infoStrip.enabled",
                    "true");''',
    "Information save invariant"
)

replace_once(
    "src/com/wtm/ui/OperationsWorkspaceFrame.java",
    '''        if(config.workspaceInfoStripEnabled){
            infoStripModule=informationStripCard();
            dashboardGrid.addTile("INFORMATION","Information",infoStripModule,"0,8,24,2");
        }''',
    '''        // Information is a core dashboard row. Visibility is no longer a
        // separate setting; content/movement are configured in Workspace Setup.
        infoStripModule=informationStripCard();
        dashboardGrid.addTile("INFORMATION","Information",infoStripModule,"0,8,24,2");''',
    "Information runtime invariant"
)


# ---------------------------------------------------------------------------
# Regression: save must canonicalize the old flag to true, and loading an older
# config that explicitly contains false must still restore the core row.
# ---------------------------------------------------------------------------
replace_once(
    "ci/ConfigRoundTripSmokeTest.java",
    '''            source.workspaceInfoBlockCount = 8;
            source.basemapProvider = "OPENSTREETMAP";''',
    '''            source.workspaceInfoStripEnabled = false;
            source.workspaceInfoBlockCount = 8;
            source.basemapProvider = "OPENSTREETMAP";''',
    "Information invariant smoke source"
)

replace_once(
    "ci/ConfigRoundTripSmokeTest.java",
    '''            ConfigService.save(source);
            AppConfig loaded = ConfigService.load();''',
    '''            ConfigService.save(source);

            Path configFile = ConfigService.appDataDir().resolve("config.properties");
            String canonical = Files.readString(configFile);
            require(canonical.contains("workspace.infoStrip.enabled=true"),
                    "core Information row was not canonicalized active on save");

            // Simulate an older installation where the retired checkbox was off.
            Files.writeString(
                    configFile,
                    canonical.replace(
                            "workspace.infoStrip.enabled=true",
                            "workspace.infoStrip.enabled=false"));

            AppConfig loaded = ConfigService.load();''',
    "Information legacy migration smoke setup"
)

replace_once(
    "ci/ConfigRoundTripSmokeTest.java",
    '''            require(loaded.workspaceInfoBlockCount == 8,
                    "information visible-count did not round-trip");''',
    '''            require(loaded.workspaceInfoStripEnabled,
                    "legacy disabled Information row was not migrated active");
            require(loaded.workspaceInfoBlockCount == 8,
                    "information visible-count did not round-trip");''',
    "Information migration smoke assertion"
)

print("Data Collection and Workspace Setup polish staged")
