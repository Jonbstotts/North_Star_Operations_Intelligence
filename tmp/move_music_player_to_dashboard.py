from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"missing target for {label} in {path}")
    if text.count(old) != 1:
        raise SystemExit(f"expected one target for {label} in {path}, found {text.count(old)}")
    file.write_text(text.replace(old, new, 1))


music = "src/com/wtm/modular/ui/MusicWorkspaceModule.java"
lifecycle = "src/com/wtm/modular/ui/WorkspaceLifecycleV3.java"
workspace = "src/com/wtm/ui/OperationsWorkspaceFrame.java"

replace_once(
    music,
    '    private static final String MINI_MARK="northstar.music.mini";\n',
    '    private static final String PLAYER_MARK="northstar.music.player";\n',
    "music tile marker",
)

replace_once(
    music,
    '''    private static void injectDashboardPlayer(Window w){
        if(!MusicService.instance().settings.dashboardPlayer
                ||!(w instanceof OperationsWorkspaceFrame workspace))return;
        MiniPlayer mini=new MiniPlayer(workspace);
        mini.setPreferredSize(new Dimension(320,52));
        mini.setMaximumSize(new Dimension(340,54));
        workspace.mountSummaryExtension(MINI_MARK,mini);
    }
''',
    '''    private static void injectDashboardPlayer(Window w){
        if(!(w instanceof OperationsWorkspaceFrame workspace))return;
        if(!MusicService.instance().settings.dashboardPlayer){
            workspace.removeDashboardExtension(PLAYER_MARK);
            return;
        }
        workspace.mountDashboardExtension(
                PLAYER_MARK,
                new DashboardPlayer(workspace),
                0
        );
    }
''',
    "music dashboard injection",
)

text = Path(music).read_text()
marker = '    private static final class MiniPlayer extends JPanel{\n'
start = text.find(marker)
if start < 0:
    raise SystemExit("missing MiniPlayer class")
# MiniPlayer is the final nested class in this source. Replace it through the
# outer class closing brace so the migration cannot leave stale header-player code.
replacement = '''    private static final class DashboardPlayer extends JPanel{
        private final MusicService service=MusicService.instance();
        private final Window owner;
        private final JLabel now=new JLabel("Nothing Playing");
        private final JLabel detail=new JLabel("Open Music & Audio to connect Apple Music");
        private final JLabel connection=new JLabel();
        private final JProgressBar progress=new JProgressBar(0,1000);
        private final javax.swing.Timer refreshTimer;

        DashboardPlayer(Window owner){
            super(new BorderLayout(10,8));
            this.owner=owner;
            setBackground(Theme.panel());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.border(),1,true),
                    new EmptyBorder(10,12,10,12)
            ));

            JButton open=new JButton("Music & Audio");
            open.setMargin(new Insets(3,9,3,9));
            open.setToolTipText("Open the full Music & Audio workspace");
            open.addActionListener(e->showMusic(owner));

            connection.setForeground(Theme.muted());
            connection.setFont(connection.getFont().deriveFont(Font.BOLD,9f));
            connection.setHorizontalAlignment(SwingConstants.RIGHT);

            JPanel top=new JPanel(new BorderLayout(8,0));
            top.setOpaque(false);
            top.add(open,BorderLayout.WEST);
            top.add(connection,BorderLayout.EAST);
            add(top,BorderLayout.NORTH);

            now.setForeground(Theme.text());
            now.setFont(now.getFont().deriveFont(Font.BOLD,16f));
            detail.setForeground(Theme.muted());
            detail.setFont(detail.getFont().deriveFont(10f));

            progress.setOpaque(false);
            progress.setBorderPainted(false);
            progress.setStringPainted(false);
            progress.setPreferredSize(new Dimension(120,5));
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE,5));

            JPanel track=new JPanel();
            track.setOpaque(false);
            track.setLayout(new BoxLayout(track,BoxLayout.Y_AXIS));
            track.add(now);
            track.add(Box.createVerticalStrut(2));
            track.add(detail);
            track.add(Box.createVerticalStrut(5));
            track.add(progress);
            add(track,BorderLayout.CENTER);

            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
            buttons.setOpaque(false);
            for(String[] control:new String[][]{
                    {"◀","prev"},{"▶","play"},{"❚❚","pause"},{"▶▶","next"}
            }){
                JButton button=new JButton(control[0]);
                button.setMargin(new Insets(2,8,2,8));
                button.addActionListener(e->service.command(control[1]));
                buttons.add(button);
            }
            add(buttons,BorderLayout.SOUTH);

            refreshTimer=new javax.swing.Timer(1000,e->refresh());
            refreshTimer.setCoalesce(true);
            refresh();
        }

        @Override public void addNotify(){
            super.addNotify();
            refreshTimer.start();
        }

        @Override public void removeNotify(){
            refreshTimer.stop();
            super.removeNotify();
        }

        private void refresh(){
            PlaybackState state=service.playback();
            String title=state.title()==null||state.title().isBlank()
                    ?"Nothing Playing":state.title();
            now.setText(title);

            String artist=state.artist()==null?"":state.artist().trim();
            String album=state.album()==null?"":state.album().trim();
            String secondary=artist;
            if(!album.isBlank())secondary=secondary.isBlank()?album:secondary+" • "+album;
            if(secondary.isBlank())secondary="Open Music & Audio to choose a playlist";
            detail.setText(secondary);

            boolean connected=service.connected();
            connection.setText(connected?"● APPLE MUSIC CONNECTED":"○ APPLE MUSIC OFFLINE");
            connection.setForeground(connected?Theme.success():Theme.muted());

            double duration=Math.max(0,state.duration());
            double current=Math.max(0,state.current());
            int value=duration<=0?0:(int)Math.round(Math.min(1,current/duration)*1000);
            progress.setValue(Math.max(0,Math.min(1000,value)));
        }
    }
}
'''
Path(music).write_text(text[:start] + replacement)

replace_once(
    lifecycle,
    '    private static final String MUSIC_MINI_MARKER = "northstar.music.mini";\n',
    '    private static final String MUSIC_PLAYER_MARKER = "northstar.music.player";\n',
    "lifecycle music marker",
)
replace_once(
    lifecycle,
    '        if (!musicDashboardEnabled()) workspace.removeSummaryExtension(MUSIC_MINI_MARKER);\n',
    '        if (!musicDashboardEnabled()) workspace.removeDashboardExtension(MUSIC_PLAYER_MARKER);\n',
    "remove disabled music tile",
)
replace_once(
    lifecycle,
    '                MUSIC_CHECK,"Music Compact Player",musicDashboardEnabled());\n',
    '                MUSIC_CHECK,"Music Player",musicDashboardEnabled());\n',
    "workspace toggle label",
)

replace_once(
    workspace,
    '''        String label="northstar.ai.compact".equalsIgnoreCase(id)
                ?"NorthStar Intelligence"
                :id;
        String defaultSpec="northstar.ai.compact".equalsIgnoreCase(id)
                ?"0,12,24,3"
                :"0,12,24,2";
''',
    '''        boolean intelligence="northstar.ai.compact".equalsIgnoreCase(id);
        boolean music="northstar.music.player".equalsIgnoreCase(id);
        String label=intelligence
                ?"NorthStar Intelligence"
                :music?"Music Player":id;
        String defaultSpec=intelligence
                ?"0,12,24,3"
                :music?"0,15,8,3":"0,12,24,2";
''',
    "dynamic tile identity",
)

replace_once(
    workspace,
    '''        summaryExtensionsHost=new JPanel(new BorderLayout());
        summaryExtensionsHost.setOpaque(false);
        summaryExtensionsHost.setPreferredSize(new Dimension(330,54));

        JPanel rightSummary=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));
''',
    '''        summaryExtensionsHost=new JPanel(new BorderLayout());
        summaryExtensionsHost.setOpaque(false);
        // Summary extensions are collapsed when empty. The former music player
        // now lives in the dashboard grid, so the header gives that space back.
        summaryExtensionsHost.setVisible(false);

        JPanel rightSummary=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));
''',
    "collapse empty summary extension host",
)

replace_once(
    workspace,
    '''        summaryExtensionsHost.removeAll();
        summaryExtensionsHost.add(component,BorderLayout.CENTER);
        summaryExtensions.put(id,component);
        summaryExtensionsHost.revalidate();
''',
    '''        summaryExtensionsHost.removeAll();
        summaryExtensionsHost.add(component,BorderLayout.CENTER);
        summaryExtensionsHost.setVisible(true);
        summaryExtensions.put(id,component);
        summaryExtensionsHost.revalidate();
''',
    "show mounted summary extension",
)

replace_once(
    workspace,
    '''        summaryExtensionsHost.remove(component);
        summaryExtensionsHost.revalidate();
        summaryExtensionsHost.repaint();
        return true;
''',
    '''        summaryExtensionsHost.remove(component);
        if(summaryExtensionsHost.getComponentCount()==0)
            summaryExtensionsHost.setVisible(false);
        summaryExtensionsHost.revalidate();
        summaryExtensionsHost.repaint();
        return true;
''',
    "collapse removed summary extension",
)

print("Music player dashboard migration staged")
