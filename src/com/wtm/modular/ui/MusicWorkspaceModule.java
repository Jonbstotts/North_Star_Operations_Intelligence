package com.wtm.modular.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wtm.config.ConfigService;
import com.wtm.ui.Theme;
import com.wtm.util.SecureFiles;
import com.wtm.ui.OperationsWorkspaceFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NorthStar Music & Audio workspace.
 *
 * <p>This class is intentionally passive with respect to application lifecycle.
 * WorkspaceLifecycleV3 owns activation and Save & Apply boundaries and calls the
 * explicit methods below. No global AWT listener, component-added reaction, or
 * delayed UI scheduler lives here.</p>
 *
 * Provider-neutral control is exposed by MusicProvider. Apple Music is the
 * first provider. Playback is performed by MusicKit in a browser page served
 * only on 127.0.0.1; NorthStar never downloads or stores audio files.
 */
public final class MusicWorkspaceModule {
    private static final String ROUTE="Music & Audio";
    private static final String ROUTE_LABEL="♫  Music & Audio";
    private static final String PLAYER_MARK="northstar.music.player";

    private MusicWorkspaceModule(){}

    public static void loadSettings(){
        MusicService.instance().load();
    }

    public static boolean dashboardPlayerEnabled(){
        return MusicService.instance().settings.dashboardPlayer;
    }

    public static void setDashboardPlayerEnabled(boolean enabled){
        MusicService service=MusicService.instance();
        service.settings.dashboardPlayer=enabled;
        service.save();
    }

    public static void installWorkspace(Window w){
        if(w==null)return;
        injectSidebar(w);
        injectDashboardPlayer(w);
    }

    private static void injectSidebar(Window w){
        if(!(w instanceof OperationsWorkspaceFrame workspace))return;
        workspace.registerWorkspaceExtensionRoute(
                ROUTE,ROUTE_LABEL,"Main Showcase",
                true,()->showMusic(workspace));
    }

    private static void showMusic(Window w){
        if(!(w instanceof OperationsWorkspaceFrame workspace))return;
        if(!workspace.showWorkspaceExtensionRoute(ROUTE,new MusicPanel(workspace))){
            JOptionPane.showMessageDialog(
                    w,"Music & Audio could not open.",
                    "NorthStar Music",JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void injectDashboardPlayer(Window w){
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

    // Provider-neutral model ------------------------------------------------

    public interface MusicProvider{
        String name(); boolean connected(); List<MusicPlaylist> playlists(); PlaybackState playback(); void command(String command);
    }
    public record MusicPlaylist(String id,String playId,String name,String artworkUrl){@Override public String toString(){return name==null||name.isBlank()?id:name;}}
    public record PlaybackState(String title,String artist,String album,String artworkUrl,boolean playing,double current,double duration,double volume,String connection){
        static PlaybackState empty(){return new PlaybackState("Nothing Playing","","","",false,0,0,.7,"Disconnected");}
    }
    private static final class MusicSettings{
        String developerToken="";boolean dashboardPlayer=true;String facilityZone="Main Facility PA";String eqPreset="Balanced";int bass,mid,treble;
    }

    /** Apple Music provider + loopback MusicKit bridge. */
    private static final class MusicService implements MusicProvider{
        private static final MusicService INSTANCE=new MusicService();
        private final CopyOnWriteArrayList<MusicPlaylist> playlists=new CopyOnWriteArrayList<>();
        private final AtomicReference<PlaybackState> playback=new AtomicReference<>(PlaybackState.empty());
        private final AtomicReference<String> command=new AtomicReference<>("");
        private final MusicSettings settings=new MusicSettings();
        private final Path config=ConfigService.appDataDir().resolve("music.properties");
        private final Path legacyConfig=Paths.get(System.getProperty("user.home"),".northstar","music.properties");
        private HttpServer server;private int port;
        static MusicService instance(){return INSTANCE;}
        @Override public String name(){return "Apple Music";}
        @Override public boolean connected(){return playback.get().connection().startsWith("Connected");}
        @Override public List<MusicPlaylist> playlists(){return List.copyOf(playlists);}
        @Override public PlaybackState playback(){return playback.get();}
        @Override public void command(String c){if(c!=null)command.set(c);}

        synchronized void load(){
            migrateLegacyConfig();
            Properties p=new Properties();if(Files.isRegularFile(config))try(InputStream in=Files.newInputStream(config)){p.load(in);SecureFiles.restrictFile(config);}catch(IOException ignored){}
            settings.developerToken=p.getProperty("apple.developerToken","").trim();settings.dashboardPlayer=Boolean.parseBoolean(p.getProperty("dashboard.player","true"));settings.facilityZone=p.getProperty("facility.zone","Main Facility PA");settings.eqPreset=p.getProperty("eq.preset","Balanced");settings.bass=intValue(p.getProperty("eq.bass"));settings.mid=intValue(p.getProperty("eq.mid"));settings.treble=intValue(p.getProperty("eq.treble"));
        }
        synchronized void save(){
            Properties p=new Properties();p.setProperty("apple.developerToken",settings.developerToken==null?"":settings.developerToken.trim());p.setProperty("dashboard.player",Boolean.toString(settings.dashboardPlayer));p.setProperty("facility.zone",settings.facilityZone);p.setProperty("eq.preset",settings.eqPreset);p.setProperty("eq.bass",String.valueOf(settings.bass));p.setProperty("eq.mid",String.valueOf(settings.mid));p.setProperty("eq.treble",String.valueOf(settings.treble));
            try{SecureFiles.storePropertiesAtomic(config,p,"NorthStar Music & Audio");}catch(IOException e){throw new IllegalStateException("Unable to save music settings",e);}
        }
        private void migrateLegacyConfig(){
            if(Files.isRegularFile(config)||!Files.isRegularFile(legacyConfig))return;
            try{
                SecureFiles.ensurePrivateDirectory(config.getParent());
                Files.copy(legacyConfig,config);
                SecureFiles.restrictFile(config);
            }catch(IOException ignored){}
        }
        synchronized URI bridge()throws IOException{
            if(server!=null)return URI.create("http://127.0.0.1:"+port+"/");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);port=server.getAddress().getPort();server.createContext("/",this::home);server.createContext("/command",this::commands);server.createContext("/playlists",this::receivePlaylists);server.createContext("/state",this::receiveState);server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"NorthStar-MusicKit");t.setDaemon(true);return t;}));server.start();return URI.create("http://127.0.0.1:"+port+"/");
        }
        void open(Component parent){
            if(settings.developerToken==null||settings.developerToken.isBlank()){JOptionPane.showMessageDialog(parent,"Enter and save an Apple Music developer token first.\nNorthStar never receives your Apple ID password.","Apple Music Setup",JOptionPane.INFORMATION_MESSAGE);return;}
            try{URI u=bridge();if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IOException("Browser integration unavailable");Desktop.getDesktop().browse(u);}catch(Exception e){JOptionPane.showMessageDialog(parent,"Unable to open Apple Music: "+e.getMessage(),"NorthStar Music",JOptionPane.ERROR_MESSAGE);}
        }
        private void home(HttpExchange x)throws IOException{send(x,200,"text/html; charset=utf-8",playerHtml(settings.developerToken));}
        private void commands(HttpExchange x)throws IOException{send(x,200,"text/plain; charset=utf-8",command.getAndSet(""));}
        private void receivePlaylists(HttpExchange x)throws IOException{
            if(!"POST".equalsIgnoreCase(x.getRequestMethod())){send(x,405,"text/plain","POST required");return;}Map<String,String> f=form(x);playlists.clear();String raw=f.getOrDefault("items","");for(String row:raw.split("\\n")){if(row.isBlank())continue;String[] q=row.split("\\t",-1);if(q.length>=3)playlists.add(new MusicPlaylist(q[0],q[1],q[2],q.length>3?q[3]:""));}send(x,204,"text/plain","");
        }
        private void receiveState(HttpExchange x)throws IOException{
            if(!"POST".equalsIgnoreCase(x.getRequestMethod())){send(x,405,"text/plain","POST required");return;}Map<String,String> f=form(x);playback.set(new PlaybackState(f.getOrDefault("title","Nothing Playing"),f.getOrDefault("artist",""),f.getOrDefault("album",""),f.getOrDefault("artwork",""),Boolean.parseBoolean(f.getOrDefault("playing","false")),number(f.get("current"),0),number(f.get("duration"),0),number(f.get("volume"),.7),f.getOrDefault("connection","Connected • Apple Music")));send(x,204,"text/plain","");
        }
        private static Map<String,String> form(HttpExchange x)throws IOException{String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> out=new LinkedHashMap<>();for(String part:body.split("&")){int i=part.indexOf('=');String k=i<0?part:part.substring(0,i);String v=i<0?"":part.substring(i+1);out.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));}return out;}
        private static void send(HttpExchange x,int status,String type,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(status,status==204?-1:b.length);if(status==204){x.close();return;}try(OutputStream out=x.getResponseBody()){out.write(b);}}
        private static int intValue(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
        private static double number(String s,double d){try{return Double.parseDouble(s);}catch(Exception e){return d;}}
        private static String js(String s){return (s==null?"":s).replace("\\","\\\\").replace("'","\\'").replace("\r","").replace("\n","");}

        private static String playerHtml(String developerToken){
            String html="""
<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<title>NorthStar Apple Music</title><script src='https://js-cdn.music.apple.com/musickit/v3/musickit.js'></script>
<style>body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;background:#080b10;color:#e7ebf2;margin:0;padding:28px}.card{max-width:900px;margin:auto;background:#12161d;border:1px solid #4d596a;border-radius:16px;padding:24px}button{background:#173f7a;color:#fff;border:1px solid #2684ff;border-radius:9px;padding:10px 16px;margin:4px;font-size:15px}.muted{color:#8993a4}.now{font-size:24px;font-weight:700;margin-top:20px}</style></head>
<body><div class='card'><h1>NorthStar • Apple Music</h1><p class='muted'>Music streams directly from Apple Music. NorthStar stores no audio files.</p><button id='auth'>Authorize Apple Music</button><button id='sync'>Refresh Playlists</button><div id='status' class='muted'>Waiting for MusicKit…</div><div id='now' class='now'>Nothing Playing</div><div id='artist' class='muted'></div></div>
<script>
let music=null;const DEV='__NORTHSTAR_DEV_TOKEN__';
async function setup(){try{await MusicKit.configure({developerToken:DEV,app:{name:'NorthStar Operations Intelligence',build:'2.1.34'}});music=MusicKit.getInstance();status('MusicKit ready • authorize Apple Music');}catch(e){status('MusicKit setup failed: '+e);}}
function status(s){document.getElementById('status').textContent=s;}
async function auth(){try{await music.authorize();status('Connected • Apple Music');await sync();}catch(e){status('Authorization failed: '+e);}}
async function sync(){try{if(!music)return;if(!music.isAuthorized)await music.authorize();const ut=music.musicUserToken;const r=await fetch('https://api.music.apple.com/v1/me/library/playlists?limit=100',{headers:{Authorization:'Bearer '+DEV,'Music-User-Token':ut}});const j=await r.json();const rows=(j.data||[]).map(x=>[x.id,(x.attributes||{}).playParams?.id||x.id,(x.attributes||{}).name||'Playlist',(x.attributes||{}).artwork?.url||''].join('\\t')).join('\\n');await post('/playlists',{items:rows});status('Connected • '+(j.data||[]).length+' playlists');}catch(e){status('Playlist sync failed: '+e);}}
async function post(url,o){const body=Object.entries(o).map(([k,v])=>encodeURIComponent(k)+'='+encodeURIComponent(v??'')).join('&');await fetch(url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});}
async function pump(){try{const r=await fetch('/command',{cache:'no-store'});const c=(await r.text()).trim();if(c&&music){if(c==='play')await music.play();else if(c==='pause')await music.pause();else if(c==='next')await music.skipToNextItem();else if(c==='prev')await music.skipToPreviousItem();else if(c.startsWith('playlist:')){const id=c.substring(9);await music.setQueue({playlist:id});await music.play();}else if(c.startsWith('volume:'))music.volume=Math.max(0,Math.min(1,Number(c.substring(7))));}if(music){const item=music.nowPlayingItem;const title=item?.title||'Nothing Playing';const artist=item?.artistName||'';document.getElementById('now').textContent=title;document.getElementById('artist').textContent=artist;await post('/state',{title,artist,album:item?.albumName||'',artwork:item?.artworkURL||'',playing:music.playbackState===2,current:music.currentPlaybackTime||0,duration:music.currentPlaybackDuration||0,volume:music.volume||0,connection:music.isAuthorized?'Connected • Apple Music':'Disconnected'});}}catch(e){}setTimeout(pump,1000);}
document.getElementById('auth').onclick=auth;document.getElementById('sync').onclick=sync;document.addEventListener('musickitloaded',setup);if(window.MusicKit)setup();pump();
</script></body></html>
""";
            return html.replace("__NORTHSTAR_DEV_TOKEN__",js(developerToken));
        }
    }

    // Swing UI --------------------------------------------------------------

    static final class MusicPanel extends JPanel{
        private final Window owner;private final MusicService service=MusicService.instance();
        private final DefaultComboBoxModel<MusicPlaylist> playlistModel=new DefaultComboBoxModel<>();
        private final JComboBox<MusicPlaylist> playlists=new JComboBox<>(playlistModel);
        private final JLabel state=new JLabel("Apple Music • not connected");
        private final JTextField token=new JTextField();
        MusicPanel(Window owner){super(new BorderLayout());this.owner=owner;setBackground(Theme.bg());setBorder(new EmptyBorder(22,22,22,22));build();refresh();}
        private void build(){
            JPanel top=new JPanel(new BorderLayout(14,14));top.setOpaque(false);JLabel title=new JLabel("Music & Audio");title.setForeground(Theme.text());title.setFont(title.getFont().deriveFont(Font.BOLD,26f));top.add(title,BorderLayout.WEST);state.setForeground(Theme.muted());top.add(state,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
            JPanel body=new JPanel();body.setOpaque(false);body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setBorder(new EmptyBorder(22,0,0,0));
            body.add(section("Apple Music",applePanel()));body.add(Box.createVerticalStrut(14));body.add(section("Playback",playbackPanel()));body.add(Box.createVerticalStrut(14));body.add(section("Facility Audio",facilityPanel()));
            JScrollPane scroll=new JScrollPane(body);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(18);prepareScroll(scroll,body);add(scroll,BorderLayout.CENTER);
        }
        private JPanel applePanel(){
            JPanel p=flow();token.setText(service.settings.developerToken);token.setPreferredSize(new Dimension(430,34));p.add(new JLabel("Developer Token"));p.add(token);p.add(button("Save",()->{service.settings.developerToken=token.getText().trim();service.save();refresh();}));p.add(button("Authorize / Open Apple Music",()->service.open(this)));p.add(button("Refresh",this::refresh));return p;
        }
        private JPanel playbackPanel(){
            JPanel p=flow();styleCombo(playlists);playlists.setPreferredSize(new Dimension(280,34));p.add(playlists);p.add(button("Play Playlist",()->{MusicPlaylist x=(MusicPlaylist)playlists.getSelectedItem();if(x!=null)service.command("playlist:"+x.playId());}));p.add(button("◀",()->service.command("prev")));p.add(button("▶",()->service.command("play")));p.add(button("❚❚",()->service.command("pause")));p.add(button("▶▶",()->service.command("next")));return p;
        }
        private JPanel facilityPanel(){
            JPanel p=flow();JTextField zone=new JTextField(service.settings.facilityZone,18);JComboBox<String> eq=new JComboBox<>(new String[]{"Balanced","Voice","Bass Boost","Bright"});styleCombo(eq);eq.setSelectedItem(service.settings.eqPreset);p.add(new JLabel("Zone"));p.add(zone);p.add(new JLabel("EQ"));p.add(eq);p.add(button("Save",()->{service.settings.facilityZone=zone.getText().trim();service.settings.eqPreset=Objects.toString(eq.getSelectedItem(),"Balanced");service.save();}));return p;
        }
        private JPanel section(String name,JComponent inner){JPanel p=new JPanel(new BorderLayout());p.setBackground(Theme.panel());p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(14,14,14,14)));JLabel h=new JLabel(name);h.setForeground(Theme.text());h.setFont(h.getFont().deriveFont(Font.BOLD,17f));p.add(h,BorderLayout.NORTH);p.add(inner,BorderLayout.CENTER);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,180));return p;}
        private JPanel flow(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,9,10));p.setOpaque(false);return p;}
        private void styleCombo(JComboBox<?> combo){combo.setBackground(Theme.panel2());combo.setForeground(Theme.text());}
        private void prepareScroll(JScrollPane scroll,JComponent body){
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getHorizontalScrollBar().setEnabled(false);
            scroll.getViewport().setBackground(Theme.bg());
            Runnable fit=()->{int width=scroll.getViewport().getExtentSize().width;if(width<=0)width=Math.max(620,scroll.getWidth()-20);Dimension pref=body.getPreferredSize();int height=pref==null?760:Math.max(pref.height,720);body.setMinimumSize(new Dimension(0,height));body.setPreferredSize(new Dimension(Math.max(600,width),height));body.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));body.revalidate();};
            fit.run();
            scroll.getViewport().addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){fit.run();}});
        }
        private JButton button(String s,Runnable r){JButton b=new JButton(s);b.addActionListener(e->r.run());return b;}
        private void refresh(){playlistModel.removeAllElements();for(MusicPlaylist x:service.playlists())playlistModel.addElement(x);PlaybackState ps=service.playback();state.setText((service.connected()?"Connected":"Not connected")+" • "+ps.title()+("".equals(ps.artist())?"":" — "+ps.artist()));repaint();}
    }

    private static final class DashboardPlayer extends JPanel{
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
            connection.setForeground(connected?Theme.good():Theme.muted());

            double duration=Math.max(0,state.duration());
            double current=Math.max(0,state.current());
            int value=duration<=0?0:(int)Math.round(Math.min(1,current/duration)*1000);
            progress.setValue(Math.max(0,Math.min(1000,value)));
        }
    }
}
