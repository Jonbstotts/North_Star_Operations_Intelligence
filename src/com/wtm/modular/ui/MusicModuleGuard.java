package com.wtm.modular.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wtm.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds the NorthStar Music & Audio workspace without modifying the native
 * OperationsWorkspaceFrame source tree. The module is provider-neutral at the
 * UI boundary; Apple Music is the first provider adapter.
 *
 * Apple Music playback is intentionally delegated to MusicKit running in a
 * loopback-only browser bridge. NorthStar never downloads or stores audio.
 */
public final class MusicModuleGuard {
    private static final String WORKSPACE_CLASS="com.wtm.ui.OperationsWorkspaceFrame";
    private static final String ROUTE="Music & Audio";
    private static final String ROUTE_LABEL="♫  Music & Audio";
    private static final String BUTTON_MARK="northstar.music.route";
    private static final String MINI_MARK="northstar.music.mini";
    private static boolean installed;

    private MusicModuleGuard(){}

    public static synchronized void install(){
        if(installed)return;
        installed=true;
        MusicService.instance().load();

        AWTEventListener listener=event->{
            if(event instanceof WindowEvent we && we.getID()==WindowEvent.WINDOW_OPENED){
                if(isWorkspace(we.getWindow()))scheduleInstall(we.getWindow());
                return;
            }
            if(event instanceof ContainerEvent ce && ce.getID()==ContainerEvent.COMPONENT_ADDED){
                Component child=ce.getChild();
                Window window=SwingUtilities.getWindowAncestor(child);
                if(isWorkspace(window))scheduleInstall(window);
                return;
            }
            if(event instanceof ActionEvent ae && ae.getSource() instanceof AbstractButton button){
                String text=clean(button.getText());
                if("Save & Apply".equalsIgnoreCase(text)){
                    Window source=SwingUtilities.getWindowAncestor(button);
                    Window workspace=findWorkspace(source);
                    if(workspace!=null)scheduleInstall(workspace);
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK|AWTEvent.ACTION_EVENT_MASK);

        for(Window window:Window.getWindows())if(isWorkspace(window))scheduleInstall(window);
    }

    private static void scheduleInstall(Window window){
        SwingUtilities.invokeLater(()->installWorkspace(window));
    }

    private static boolean isWorkspace(Window window){
        return window!=null&&WORKSPACE_CLASS.equals(window.getClass().getName());
    }

    private static Window findWorkspace(Window source){
        if(isWorkspace(source))return source;
        for(Window owner=source==null?null:source.getOwner();owner!=null;owner=owner.getOwner())
            if(isWorkspace(owner))return owner;
        for(Window window:Window.getWindows())if(isWorkspace(window)&&window.isDisplayable())return window;
        return null;
    }

    private static void installWorkspace(Window window){
        if(window==null||!window.isDisplayable())return;
        injectSidebar(window);
        injectDashboardMiniPlayer(window);
    }

    @SuppressWarnings("unchecked")
    private static void injectSidebar(Window window){
        try{
            Map<String,JButton> routes=(Map<String,JButton>)field(window,"sidebarRouteButtons");
            if(routes==null)return;
            JButton existing=routes.get(ROUTE);
            if(existing!=null&&existing.getParent()!=null)return;

            JButton anchor=findButton((Container)window,"Main Showcase");
            if(anchor==null||anchor.getParent()==null)return;
            Container parent=anchor.getParent();

            Method create=window.getClass().getDeclaredMethod("createSidebarButton",String.class,boolean.class);
            create.setAccessible(true);
            JButton music=(JButton)create.invoke(window,ROUTE_LABEL,false);
            music.putClientProperty(BUTTON_MARK,Boolean.TRUE);
            music.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
            music.putClientProperty("northstar.ui.skip",Boolean.TRUE);
            music.addActionListener(e->showMusicRoute(window,music));
            routes.put(ROUTE,music);

            int anchorIndex=indexOf(parent,anchor);
            int insert=Math.min(parent.getComponentCount(),Math.max(0,anchorIndex+1));
            parent.add(music,insert);
            parent.revalidate();
            parent.repaint();
        }catch(ReflectiveOperationException ignored){
            // Runtime may be a reduced deployment without the modern workspace.
        }
    }

    private static void showMusicRoute(Window window,JButton routeButton){
        try{
            Field active=window.getClass().getDeclaredField("activeWorkspaceRoute");
            active.setAccessible(true);
            active.set(window,ROUTE);

            invokeIfPresent(window,"closeEmbeddedSettingsSession");
            invokeIfPresent(window,"releaseDashboardModules");

            JPanel host=(JPanel)field(window,"workspaceContentHost");
            if(host==null)return;
            host.removeAll();
            host.add(new MusicAudioPanel(window),BorderLayout.CENTER);
            host.revalidate();
            host.repaint();
            invokeIfPresent(window,"updateSidebarSelection");
        }catch(ReflectiveOperationException ex){
            JOptionPane.showMessageDialog(window,
                    "Music & Audio could not open: "+ex.getMessage(),
                    "NorthStar Music",JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void injectDashboardMiniPlayer(Window window){
        MusicSettings settings=MusicService.instance().settings();
        if(!settings.dashboardPlayer)return;
        try{
            JPanel body=(JPanel)field(window,"dashboardBody");
            if(body==null||body.getParent()==null)return;
            for(Component c:body.getComponents())
                if(c instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty(MINI_MARK)))return;

            MiniPlayer mini=new MiniPlayer(window);
            mini.putClientProperty(MINI_MARK,Boolean.TRUE);
            mini.setAlignmentX(Component.LEFT_ALIGNMENT);
            mini.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));
            int insert=Math.min(2,body.getComponentCount());
            body.add(mini,insert);
            body.add(Box.createVerticalStrut(10),Math.min(insert+1,body.getComponentCount()));
            body.revalidate();
            body.repaint();
        }catch(ReflectiveOperationException ignored){}
    }

    private static Object field(Object target,String name)throws ReflectiveOperationException{
        Field f=target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void invokeIfPresent(Object target,String name){
        try{
            Method m=target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(target);
        }catch(ReflectiveOperationException ignored){}
    }

    private static int indexOf(Container parent,Component target){
        Component[] all=parent.getComponents();
        for(int i=0;i<all.length;i++)if(all[i]==target)return i;
        return all.length-1;
    }

    private static JButton findButton(Container root,String label){
        for(Component c:root.getComponents()){
            if(c instanceof JButton b&&clean(b.getText()).equalsIgnoreCase(label))return b;
            if(c instanceof Container child){
                JButton found=findButton(child,label);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static String clean(String text){
        if(text==null)return "";
        return text.replaceAll("^[^A-Za-z0-9]+","").trim();
    }

    // ---------------------------------------------------------------------
    // Provider-neutral domain
    // ---------------------------------------------------------------------

    public interface MusicProvider{
        String name();
        boolean connected();
        List<MusicPlaylist> playlists();
        PlaybackState playback();
        void command(String command);
    }

    public record MusicPlaylist(String id,String playId,String name,String artworkUrl){
        @Override public String toString(){return name==null||name.isBlank()?id:name;}
    }

    public record PlaybackState(
            String title,String artist,String album,String artworkUrl,String playlist,
            boolean playing,double current,double duration,double volume,String connection){
        static PlaybackState empty(){
            return new PlaybackState("Nothing Playing","","","","",false,0,0,.7,"Disconnected");
        }
    }

    public static final class MusicSettings{
        String developerToken="";
        boolean dashboardPlayer=true;
        String facilityZone="Main Facility PA";
        String eqPreset="Balanced";
        int bass=0,mid=0,treble=0;
    }

    public static final class MusicService implements MusicProvider{
        private static final MusicService INSTANCE=new MusicService();
        private final CopyOnWriteArrayList<MusicPlaylist> playlists=new CopyOnWriteArrayList<>();
        private final AtomicReference<PlaybackState> playback=new AtomicReference<>(PlaybackState.empty());
        private final AtomicReference<String> command=new AtomicReference<>("");
        private final MusicSettings settings=new MusicSettings();
        private final Path configPath=Paths.get(System.getProperty("user.home"),".northstar","music.properties");
        private HttpServer server;
        private int port;

        static MusicService instance(){return INSTANCE;}
        MusicSettings settings(){return settings;}

        @Override public String name(){return "Apple Music";}
        @Override public boolean connected(){return playback.get().connection().startsWith("Connected");}
        @Override public List<MusicPlaylist> playlists(){return List.copyOf(playlists);}
        @Override public PlaybackState playback(){return playback.get();}
        @Override public void command(String value){if(value!=null)command.set(value);}

        synchronized void load(){
            Properties p=new Properties();
            if(Files.isRegularFile(configPath))try(InputStream in=Files.newInputStream(configPath)){p.load(in);}catch(IOException ignored){}
            settings.developerToken=p.getProperty("apple.developerToken","").trim();
            settings.dashboardPlayer=Boolean.parseBoolean(p.getProperty("dashboard.player","true"));
            settings.facilityZone=p.getProperty("facility.zone","Main Facility PA");
            settings.eqPreset=p.getProperty("eq.preset","Balanced");
            settings.bass=parseInt(p.getProperty("eq.bass"),0);
            settings.mid=parseInt(p.getProperty("eq.mid"),0);
            settings.treble=parseInt(p.getProperty("eq.treble"),0);
        }

        synchronized void save(){
            Properties p=new Properties();
            p.setProperty("apple.developerToken",settings.developerToken==null?"":settings.developerToken.trim());
            p.setProperty("dashboard.player",Boolean.toString(settings.dashboardPlayer));
            p.setProperty("facility.zone",settings.facilityZone==null?"Main Facility PA":settings.facilityZone);
            p.setProperty("eq.preset",settings.eqPreset==null?"Balanced":settings.eqPreset);
            p.setProperty("eq.bass",Integer.toString(settings.bass));
            p.setProperty("eq.mid",Integer.toString(settings.mid));
            p.setProperty("eq.treble",Integer.toString(settings.treble));
            try{
                Files.createDirectories(configPath.getParent());
                try(OutputStream out=Files.newOutputStream(configPath,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){p.store(out,"NorthStar Music & Audio");}
                try{Files.setPosixFilePermissions(configPath,PosixFilePermissions.fromString("rw-------"));}catch(Exception ignored){}
            }catch(IOException ex){throw new IllegalStateException("Unable to save music settings",ex);}
        }

        synchronized URI ensureBridge()throws IOException{
            if(server!=null)return URI.create("http://127.0.0.1:"+port+"/");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            port=server.getAddress().getPort();
            server.createContext("/",this::handleHome);
            server.createContext("/command",this::handleCommand);
            server.createContext("/playlists",this::handlePlaylists);
            server.createContext("/state",this::handleState);
            server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"NorthStar-MusicKit");t.setDaemon(true);return t;}));
            server.start();
            return URI.create("http://127.0.0.1:"+port+"/");
        }

        void openPlayer(Component parent){
            if(settings.developerToken==null||settings.developerToken.isBlank()){
                JOptionPane.showMessageDialog(parent,
                        "Enter and save an Apple Music developer token first.\nNorthStar never stores your Apple ID password.",
                        "Apple Music Setup",JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try{
                URI uri=ensureBridge();
                if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("Desktop browser integration is unavailable.");
                Desktop.getDesktop().browse(uri);
            }catch(Exception ex){
                JOptionPane.showMessageDialog(parent,"Unable to open Apple Music player: "+ex.getMessage(),"Apple Music",JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleHome(HttpExchange x)throws IOException{
            String html=html(settings.developerToken);
            send(x,200,"text/html; charset=utf-8",html);
        }

        private void handleCommand(HttpExchange x)throws IOException{
            String value=command.getAndSet("");
            send(x,200,"text/plain; charset=utf-8",value);
        }

        private void handlePlaylists(HttpExchange x)throws IOException{
            if(!"POST".equalsIgnoreCase(x.getRequestMethod())){send(x,405,"text/plain","POST required");return;}
            Map<String,String> form=form(x);
            String packed=form.getOrDefault("items","");
            playlists.clear();
            if(!packed.isBlank()){
                for(String row:packed.split("\\n")){
                    String[] p=row.split("\\t",-1);
                    if(p.length>=3)playlists.add(new MusicPlaylist(p[0],p[1],p[2],p.length>3?p[3]:""));
                }
            }
            send(x,204,"text/plain","");
        }

        private void handleState(HttpExchange x)throws IOException{
            if(!"POST".equalsIgnoreCase(x.getRequestMethod())){send(x,405,"text/plain","POST required");return;}
            Map<String,String> f=form(x);
            playback.set(new PlaybackState(
                    f.getOrDefault("title","Nothing Playing"),
                    f.getOrDefault("artist",""),f.getOrDefault("album",""),
                    f.getOrDefault("artwork",""),f.getOrDefault("playlist",""),
                    Boolean.parseBoolean(f.getOrDefault("playing","false")),
                    parseDouble(f.get("current"),0),parseDouble(f.get("duration"),0),
                    parseDouble(f.get("volume"),.7),f.getOrDefault("connection","Connected • Apple Music")
            ));
            send(x,204,"text/plain","");
        }

        private static Map<String,String> form(HttpExchange x)throws IOException{
            String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            Map<String,String> out=new LinkedHashMap<>();
            for(String part:body.split("&")){
                int i=part.indexOf('=');
                String k=i<0?part:part.substring(0,i);
                String v=i<0?"":part.substring(i+1);
                out.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));
            }
            return out;
        }

        private static void send(HttpExchange x,int status,String contentType,String body)throws IOException{
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type",contentType);
            x.getResponseHeaders().set("Cache-Control","no-store");
            x.sendResponseHeaders(status,status==204?-1:bytes.length);
            if(status!=204)try(OutputStream out=x.getResponseBody()){out.write(bytes);}else x.close();
        }

        private static String html(String developerToken){
            String token=js(developerToken);
            return """
<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<title>NorthStar Apple Music Player</title><script src='https://js-cdn.music.apple.com/musickit/v3/musickit.js'></script>
<style>body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;background:#080b10;color:#e6e9ef;margin:0;padding:28px}.card{max-width:900px;margin:auto;background:#12161d;border:1px solid #4d596a;border-radius:16px;padding:24px}button{background:#173f7a;color:white;border:1px solid #2684ff;border-radius:9px;padding:10px 16px;margin:4px;font-size:15px}.muted{color:#8993a4}.now{font-size:24px;font-weight:700;margin-top:20px}</style></head>
<body><div class='card'><h1>NorthStar • Apple Music</h1><p class='muted'>This browser player streams directly from Apple Music. NorthStar stores no music files.</p><button id='auth'>Authorize Apple Music</button><button id='sync'>Refresh Playlists</button><div id='status' class='muted'>Waiting for authorization…</div><div id='now' class='now'>Nothing Playing</div><div id='artist' class='muted'></div></div>
<script>
let music=null; const DEV='"""+token+"""'; let names={};
async function configure(){try{await MusicKit.configure({developerToken:DEV,app:{name:'NorthStar Operations Intelligence',build:'2.1.30'}}); music=MusicKit.getInstance(); document.getElementById('status').textContent='MusicKit ready • authorize your account';}catch(e){document.getElementById('status').textContent='MusicKit configuration failed: '+e;}}
async function authorize(){try{await music.authorize(); document.getElementById('status').textContent='Connected • Apple Music'; await syncPlaylists();}catch(e){document.getElementById('status').textContent='Authorization failed: '+e;}}
async function syncPlaylists(){if(!music)return; try{if(!music.isAuthorized) await authorize(); const ut=music.musicUserToken; const res=await fetch('https://api.music.apple.com/v1/me/library/playlists?limit=100',{headers:{'Authorization':'Bearer '+DEV,'Music-User-Token':ut}}); if(!res.ok)throw new Error('Apple Music API '+res.status); const j=await res.json(); let rows=[]; names={}; for(const p of (j.data||[])){const a=p.attributes||{};const pp=a.playParams||{};const playId=pp.globalId||pp.id||p.id;const art=(a.artwork&&a.artwork.url)||'';rows.push([p.id,playId,a.name||p.id,art].join('\t'));names[playId]=a.name||p.id;} await post('/playlists',{items:rows.join('\n')}); document.getElementById('status').textContent='Connected • '+rows.length+' playlists synchronized';}catch(e){document.getElementById('status').textContent='Playlist sync failed: '+e;}}
async function post(path,data){await fetch(path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(data)});}
async function execute(c){if(!music||!c)return; try{if(c==='play')await music.play(); else if(c==='pause')await music.pause(); else if(c==='next')await music.skipToNextItem(); else if(c==='prev')await music.skipToPreviousItem(); else if(c.startsWith('playlist:')){const id=c.substring(9); await music.setQueue({playlist:id}); await music.play();} else if(c.startsWith('volume:'))music.volume=Math.max(0,Math.min(1,Number(c.substring(7)))); else if(c.startsWith('shuffle:'))music.shuffleMode=Number(c.substring(8)); else if(c.startsWith('repeat:'))music.repeatMode=Number(c.substring(7));}catch(e){document.getElementById('status').textContent='Playback command failed: '+e;}}
async function heartbeat(){try{const c=await (await fetch('/command',{cache:'no-store'})).text(); if(c)await execute(c); if(music){const n=music.nowPlayingItem; const title=n&&n.title||'Nothing Playing';const artist=n&&n.artistName||'';const album=n&&n.albumName||'';const art=n&&n.artworkURL||'';document.getElementById('now').textContent=title;document.getElementById('artist').textContent=artist;await post('/state',{title,artist,album,artwork:art,playlist:'',playing:String(!!music.isPlaying),current:String(music.currentPlaybackTime||0),duration:String(music.currentPlaybackDuration||0),volume:String(music.volume==null?.7:music.volume),connection:music.isAuthorized?'Connected • Apple Music':'MusicKit ready'});}}catch(e){} setTimeout(heartbeat,900);}
document.getElementById('auth').onclick=authorize;document.getElementById('sync').onclick=syncPlaylists;configure().then(heartbeat);
</script></body></html>""";
        }

        private static String js(String value){
            return (value==null?"":value).replace("\\","\\\\").replace("'","\\'").replace("\r","").replace("\n","");
        }
        private static int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
        private static double parseDouble(String s,double d){try{return Double.parseDouble(s);}catch(Exception e){return d;}}
    }

    // ---------------------------------------------------------------------
    // Full module UI
    // ---------------------------------------------------------------------

    private static final class MusicAudioPanel extends JPanel{
        private final MusicService service=MusicService.instance();
        private final Window workspace;
        private final DefaultListModel<MusicPlaylist> model=new DefaultListModel<>();
        private final JList<MusicPlaylist> playlistList=new JList<>(model);
        private final JLabel connection=new JLabel();
        private final JLabel nowTitle=new JLabel("Nothing Playing");
        private final JLabel nowArtist=new JLabel("");
        private final JLabel time=new JLabel("0:00 / 0:00");
        private final JProgressBar progress=new JProgressBar(0,1000);
        private final JSlider volume=new JSlider(0,100,70);
        private final JPasswordField token=new JPasswordField();
        private final JCheckBox dashboardPlayer=new JCheckBox("Show compact player on Dashboard");
        private final JTextField facilityZone=new JTextField();
        private final JComboBox<String> eqPreset=new JComboBox<>(new String[]{"Flat","Balanced","Speech Clarity","Bass Reduction","Warm","Custom"});
        private final JSlider bass=new JSlider(-12,12,0),mid=new JSlider(-12,12,0),treble=new JSlider(-12,12,0);
        private final Timer refresh;

        MusicAudioPanel(Window workspace){
            super(new BorderLayout());this.workspace=workspace;
            setBackground(Theme.bg());
            JPanel page=new JPanel();page.setBackground(Theme.bg());page.setLayout(new BoxLayout(page,BoxLayout.Y_AXIS));page.setBorder(new EmptyBorder(20,24,24,24));
            page.add(header());page.add(Box.createVerticalStrut(14));page.add(providerCard());page.add(Box.createVerticalStrut(12));page.add(content());
            JScrollPane scroll=new JScrollPane(page);scroll.setBorder(null);scroll.getViewport().setBackground(Theme.bg());scroll.getVerticalScrollBar().setUnitIncrement(18);add(scroll,BorderLayout.CENTER);
            refresh=new Timer(900,e->refresh());refresh.start();refresh();
        }

        @Override public void removeNotify(){if(refresh!=null)refresh.stop();super.removeNotify();}

        private JComponent header(){
            JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,62));
            JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));
            JLabel title=new JLabel("Music & Audio");title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,25));
            JLabel sub=new JLabel("Provider playlists, facility playback controls and audio-zone settings");sub.setForeground(Theme.muted());sub.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));words.add(title);words.add(sub);p.add(words,BorderLayout.WEST);
            return p;
        }

        private JComponent providerCard(){
            JPanel card=card("APPLE MUSIC • PROVIDER CONNECTION");card.setLayout(new BorderLayout(12,8));
            JPanel top=new JPanel(new BorderLayout(10,0));top.setOpaque(false);connection.setForeground(new Color(45,205,142));connection.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));top.add(connection,BorderLayout.EAST);
            JLabel help=new JLabel("Connect once in the browser; NorthStar imports playlist metadata and remotely controls the MusicKit player.");help.setForeground(Theme.muted());help.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));top.add(help,BorderLayout.WEST);card.add(top,BorderLayout.NORTH);
            JPanel form=new JPanel(new GridBagLayout());form.setOpaque(false);GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(5,4,5,4);g.fill=GridBagConstraints.HORIZONTAL;
            g.gridx=0;g.gridy=0;g.weightx=0;form.add(label("Developer token"),g);g.gridx=1;g.weightx=1;token.setText(service.settings().developerToken);styleField(token);form.add(token,g);
            JButton save=button("Save Token");save.addActionListener(e->{service.settings().developerToken=new String(token.getPassword()).trim();try{service.save();connection.setText("● TOKEN SAVED • authorize Apple Music");}catch(Exception ex){error(ex.getMessage());}});g.gridx=2;g.weightx=0;form.add(save,g);
            JButton open=button("Open / Authorize Apple Music");open.addActionListener(e->service.openPlayer(this));g.gridx=3;form.add(open,g);
            card.add(form,BorderLayout.CENTER);
            JLabel note=new JLabel("Apple Developer setup is required for the developer token. Apple ID credentials stay with Apple; NorthStar never receives the account password.");note.setForeground(Theme.muted());note.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));card.add(note,BorderLayout.SOUTH);
            return card;
        }

        private JComponent content(){
            JPanel split=new JPanel(new GridLayout(1,2,12,0));split.setOpaque(false);split.setMaximumSize(new Dimension(Integer.MAX_VALUE,620));split.add(libraryCard());split.add(controlColumn());return split;
        }

        private JComponent libraryCard(){
            JPanel card=card("PLAYLIST LIBRARY • READ ONLY");card.setLayout(new BorderLayout(0,8));
            playlistList.setBackground(Theme.panel2());playlistList.setForeground(Theme.text());playlistList.setSelectionBackground(Theme.accent());playlistList.setSelectionForeground(Color.WHITE);playlistList.setFixedCellHeight(34);
            JScrollPane sc=new JScrollPane(playlistList);sc.setBorder(BorderFactory.createLineBorder(Theme.border()));card.add(sc,BorderLayout.CENTER);
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));buttons.setOpaque(false);JButton play=button("Play Selected");play.addActionListener(e->{MusicPlaylist p=playlistList.getSelectedValue();if(p!=null)service.command("playlist:"+p.playId());});JButton refresh=button("Refresh from Apple Music");refresh.addActionListener(e->service.command("sync"));buttons.add(play);buttons.add(refresh);card.add(buttons,BorderLayout.SOUTH);return card;
        }

        private JComponent controlColumn(){
            JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.add(nowPlayingCard());p.add(Box.createVerticalStrut(12));p.add(playbackSettingsCard());p.add(Box.createVerticalStrut(12));p.add(audioZoneCard());return p;
        }

        private JComponent nowPlayingCard(){
            JPanel card=card("NOW PLAYING");card.setLayout(new BorderLayout(10,8));
            JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));nowTitle.setForeground(Theme.text());nowTitle.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));nowArtist.setForeground(Theme.muted());nowArtist.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));words.add(nowTitle);words.add(nowArtist);card.add(words,BorderLayout.NORTH);
            JPanel center=new JPanel();center.setOpaque(false);center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));progress.setStringPainted(false);progress.setForeground(Theme.accent());progress.setBackground(Theme.panel2());center.add(progress);time.setForeground(Theme.muted());time.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));center.add(time);center.add(Box.createVerticalStrut(10));
            JPanel controls=new JPanel(new FlowLayout(FlowLayout.CENTER,8,0));controls.setOpaque(false);for(String[] c:new String[][]{{"⏮","prev"},{"▶","play"},{"Ⅱ","pause"},{"⏭","next"}}){JButton b=button(c[0]);b.addActionListener(e->service.command(c[1]));controls.add(b);}center.add(controls);card.add(center,BorderLayout.CENTER);
            JPanel vol=new JPanel(new BorderLayout(8,0));vol.setOpaque(false);vol.add(label("Volume"),BorderLayout.WEST);volume.addChangeListener(e->{if(!volume.getValueIsAdjusting())service.command("volume:"+(volume.getValue()/100.0));});vol.add(volume,BorderLayout.CENTER);card.add(vol,BorderLayout.SOUTH);return card;
        }

        private JComponent playbackSettingsCard(){
            JPanel card=card("PLAYBACK SETTINGS");card.setLayout(new GridLayout(2,2,8,8));
            JToggleButton shuffle=new JToggleButton("Shuffle");styleButton(shuffle);shuffle.addActionListener(e->service.command("shuffle:"+(shuffle.isSelected()?1:0)));
            JComboBox<String> repeat=new JComboBox<>(new String[]{"Repeat Off","Repeat Track","Repeat Playlist"});repeat.addActionListener(e->service.command("repeat:"+repeat.getSelectedIndex()));
            dashboardPlayer.setOpaque(false);dashboardPlayer.setForeground(Theme.text());dashboardPlayer.setSelected(service.settings().dashboardPlayer);dashboardPlayer.addActionListener(e->{service.settings().dashboardPlayer=dashboardPlayer.isSelected();service.save();});
            JLabel readonly=new JLabel("Playlists are edited only in Apple Music");readonly.setForeground(Theme.muted());readonly.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));card.add(shuffle);card.add(repeat);card.add(dashboardPlayer);card.add(readonly);return card;
        }

        private JComponent audioZoneCard(){
            JPanel card=card("FACILITY AUDIO ZONE / EQ PROFILE");card.setLayout(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.fill=GridBagConstraints.HORIZONTAL;
            facilityZone.setText(service.settings().facilityZone);styleField(facilityZone);eqPreset.setSelectedItem(service.settings().eqPreset);bass.setValue(service.settings().bass);mid.setValue(service.settings().mid);treble.setValue(service.settings().treble);
            int y=0;g.gridy=y++;g.gridx=0;g.weightx=0;card.add(label("Audio zone"),g);g.gridx=1;g.weightx=1;card.add(facilityZone,g);g.gridy=y++;g.gridx=0;g.weightx=0;card.add(label("EQ preset"),g);g.gridx=1;g.weightx=1;card.add(eqPreset,g);
            for(Object[] row:new Object[][]{{"Bass",bass},{"Mid",mid},{"Treble",treble}}){g.gridy=y++;g.gridx=0;g.weightx=0;card.add(label((String)row[0]),g);g.gridx=1;g.weightx=1;card.add((JSlider)row[1],g);}
            JButton save=button("Save Facility Audio Profile");save.addActionListener(e->{service.settings().facilityZone=facilityZone.getText().trim();service.settings().eqPreset=String.valueOf(eqPreset.getSelectedItem());service.settings().bass=bass.getValue();service.settings().mid=mid.getValue();service.settings().treble=treble.getValue();service.save();});g.gridy=y++;g.gridx=0;g.gridwidth=2;card.add(save,g);
            JLabel note=new JLabel("EQ values are stored as facility DSP/output profiles. NorthStar does not modify the protected Apple Music stream; hardware/OS DSP adapters can consume this profile in a later phase.");note.setForeground(Theme.muted());note.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));g.gridy=y;card.add(note,g);return card;
        }

        private JPanel card(String title){JPanel p=new JPanel();p.setBackground(Theme.panel());p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(12,12,12,12)));p.setAlignmentX(Component.LEFT_ALIGNMENT);JLabel h=new JLabel(title);h.setForeground(Theme.text());h.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));p.setLayout(new BorderLayout(0,8));p.add(h,BorderLayout.NORTH);return p;}
        private JLabel label(String s){JLabel l=new JLabel(s);l.setForeground(Theme.text());l.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));return l;}
        private JButton button(String s){JButton b=new JButton(s);styleButton(b);return b;}
        private void styleButton(AbstractButton b){b.setFocusPainted(false);b.setForeground(Theme.text());b.setBackground(Theme.panel2());b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(7,11,7,11)));}
        private void styleField(JTextField f){f.setBackground(Theme.panel2());f.setForeground(Theme.text());f.setCaretColor(Theme.text());f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(7,8,7,8)));}
        private void error(String s){JOptionPane.showMessageDialog(this,s,"Music & Audio",JOptionPane.ERROR_MESSAGE);}

        private void refresh(){
            PlaybackState st=service.playback();connection.setText(service.connected()?"● CONNECTED • Apple Music":"○ NOT AUTHORIZED");nowTitle.setText(st.title());nowArtist.setText(st.artist().isBlank()?st.album():st.artist());double d=Math.max(0,st.duration()),c=Math.max(0,st.current());progress.setValue(d<=0?0:(int)Math.min(1000,1000*c/d));time.setText(format(c)+" / "+format(d));if(!volume.getValueIsAdjusting())volume.setValue((int)Math.round(Math.max(0,Math.min(1,st.volume()))*100));
            List<MusicPlaylist> latest=service.playlists();if(model.size()!=latest.size()||!same(model,latest)){model.clear();for(MusicPlaylist pl:latest)model.addElement(pl);}
        }
        private boolean same(DefaultListModel<MusicPlaylist> m,List<MusicPlaylist> l){if(m.size()!=l.size())return false;for(int i=0;i<l.size();i++)if(!Objects.equals(m.get(i).id(),l.get(i).id()))return false;return true;}
        private String format(double seconds){int s=(int)Math.max(0,seconds);return (s/60)+":"+String.format("%02d",s%60);}
    }

    // ---------------------------------------------------------------------
    // Dashboard compact player
    // ---------------------------------------------------------------------

    private static final class MiniPlayer extends JPanel{
        private final MusicService service=MusicService.instance();
        private final Window workspace;
        private final JLabel title=new JLabel("Nothing Playing"),artist=new JLabel("Apple Music not connected");
        private final JButton playPause=new JButton("▶");
        private final Timer timer;

        MiniPlayer(Window workspace){
            super(new BorderLayout(12,0));this.workspace=workspace;setBackground(Theme.panel());setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(10,14,10,14)));
            JLabel mark=new JLabel("♫");mark.setForeground(Theme.accent());mark.setFont(new Font(Font.SANS_SERIF,Font.BOLD,24));add(mark,BorderLayout.WEST);
            JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));artist.setForeground(Theme.muted());artist.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));words.add(title);words.add(artist);add(words,BorderLayout.CENTER);
            JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));controls.setOpaque(false);JButton prev=mini("⏮"),next=mini("⏭"),open=mini("Open");prev.addActionListener(e->service.command("prev"));next.addActionListener(e->service.command("next"));playPause.setFocusPainted(false);playPause.addActionListener(e->service.command(service.playback().playing()?"pause":"play"));open.addActionListener(e->showMusicRoute(workspace,null));controls.add(prev);controls.add(playPause);controls.add(next);controls.add(open);add(controls,BorderLayout.EAST);
            timer=new Timer(1000,e->refresh());timer.start();refresh();
        }
        @Override public void removeNotify(){timer.stop();super.removeNotify();}
        private JButton mini(String s){JButton b=new JButton(s);b.setFocusPainted(false);b.setForeground(Theme.text());b.setBackground(Theme.panel2());return b;}
        private void refresh(){PlaybackState s=service.playback();title.setText(s.title());artist.setText(s.artist().isBlank()?s.connection():s.artist());playPause.setText(s.playing()?"Ⅱ":"▶");}
    }
}
