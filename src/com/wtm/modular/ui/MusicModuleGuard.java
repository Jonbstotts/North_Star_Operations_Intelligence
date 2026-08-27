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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
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
 * Provider-neutral control is exposed by MusicProvider. Apple Music is the
 * first provider. Playback is performed by MusicKit in a browser page served
 * only on 127.0.0.1; NorthStar never downloads or stores audio files.
 */
public final class MusicModuleGuard {
    private static final String WORKSPACE_CLASS="com.wtm.ui.OperationsWorkspaceFrame";
    private static final String ROUTE="Music & Audio";
    private static final String ROUTE_LABEL="♫  Music & Audio";
    private static final String MINI_MARK="northstar.music.mini";
    private static boolean installed;

    private MusicModuleGuard(){}

    public static synchronized void install(){
        if(installed)return;
        installed=true;
        MusicService.instance().load();

        AWTEventListener listener=event->{
            if(event instanceof WindowEvent we&&we.getID()==WindowEvent.WINDOW_OPENED){
                if(isWorkspace(we.getWindow()))schedule(we.getWindow());
            }else if(event instanceof ContainerEvent ce&&ce.getID()==ContainerEvent.COMPONENT_ADDED){
                Window w=SwingUtilities.getWindowAncestor(ce.getChild());
                if(isWorkspace(w))schedule(w);
            }else if(event instanceof ActionEvent ae&&ae.getSource() instanceof AbstractButton b){
                if("Save & Apply".equalsIgnoreCase(clean(b.getText()))){
                    Window w=findWorkspace(SwingUtilities.getWindowAncestor(b));
                    if(w!=null)schedule(w);
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK|AWTEvent.ACTION_EVENT_MASK);
        for(Window w:Window.getWindows())if(isWorkspace(w))schedule(w);
    }

    private static void schedule(Window w){SwingUtilities.invokeLater(()->installWorkspace(w));}
    private static boolean isWorkspace(Window w){return w!=null&&WORKSPACE_CLASS.equals(w.getClass().getName());}
    private static Window findWorkspace(Window source){
        if(isWorkspace(source))return source;
        for(Window o=source==null?null:source.getOwner();o!=null;o=o.getOwner())if(isWorkspace(o))return o;
        for(Window w:Window.getWindows())if(isWorkspace(w)&&w.isDisplayable())return w;
        return null;
    }

    private static void installWorkspace(Window w){
        if(w==null||!w.isDisplayable())return;
        injectSidebar(w);
        injectDashboardPlayer(w);
    }

    @SuppressWarnings("unchecked")
    private static void injectSidebar(Window w){
        try{
            Map<String,JButton> routes=(Map<String,JButton>)field(w,"sidebarRouteButtons");
            if(routes==null)return;
            JButton current=routes.get(ROUTE);
            if(current!=null&&current.getParent()!=null)return;
            JButton anchor=findButton((Container)w,"Main Showcase");
            if(anchor==null||anchor.getParent()==null)return;

            Method create=w.getClass().getDeclaredMethod("createSidebarButton",String.class,boolean.class);
            create.setAccessible(true);
            JButton music=(JButton)create.invoke(w,ROUTE_LABEL,false);
            music.putClientProperty("northstar.sidebar.route",Boolean.TRUE);
            music.putClientProperty("northstar.ui.skip",Boolean.TRUE);
            music.addActionListener(e->showMusic(w));
            routes.put(ROUTE,music);

            Container p=anchor.getParent();
            int index=indexOf(p,anchor);
            p.add(music,Math.min(p.getComponentCount(),index+1));
            p.revalidate();p.repaint();
        }catch(ReflectiveOperationException ignored){}
    }

    private static void showMusic(Window w){
        try{
            Field active=w.getClass().getDeclaredField("activeWorkspaceRoute");
            active.setAccessible(true);active.set(w,ROUTE);
            invoke(w,"closeEmbeddedSettingsSession");
            invoke(w,"releaseDashboardModules");
            JPanel host=(JPanel)field(w,"workspaceContentHost");
            if(host==null)return;
            host.removeAll();host.add(new MusicPanel(w),BorderLayout.CENTER);host.revalidate();host.repaint();
            invoke(w,"updateSidebarSelection");
        }catch(ReflectiveOperationException ex){
            JOptionPane.showMessageDialog(w,"Music & Audio could not open: "+ex.getMessage(),"NorthStar Music",JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void injectDashboardPlayer(Window w){
        if(!MusicService.instance().settings.dashboardPlayer)return;
        try{
            JPanel body=(JPanel)field(w,"dashboardBody");
            if(body==null||body.getParent()==null)return;
            for(Component c:body.getComponents())if(c instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty(MINI_MARK)))return;
            MiniPlayer mini=new MiniPlayer(w);mini.putClientProperty(MINI_MARK,Boolean.TRUE);mini.setAlignmentX(Component.LEFT_ALIGNMENT);mini.setMaximumSize(new Dimension(Integer.MAX_VALUE,72));
            int at=Math.min(2,body.getComponentCount());body.add(mini,at);body.add(Box.createVerticalStrut(10),Math.min(at+1,body.getComponentCount()));body.revalidate();body.repaint();
        }catch(ReflectiveOperationException ignored){}
    }

    private static Object field(Object o,String name)throws ReflectiveOperationException{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(ReflectiveOperationException ignored){}}
    private static int indexOf(Container p,Component target){Component[] a=p.getComponents();for(int i=0;i<a.length;i++)if(a[i]==target)return i;return a.length-1;}
    private static JButton findButton(Container root,String name){for(Component c:root.getComponents()){if(c instanceof JButton b&&clean(b.getText()).equalsIgnoreCase(name))return b;if(c instanceof Container cc){JButton x=findButton(cc,name);if(x!=null)return x;}}return null;}
    private static String clean(String s){return s==null?"":s.replaceAll("^[^A-Za-z0-9]+","").trim();}

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
        private final Path config=Paths.get(System.getProperty("user.home"),".northstar","music.properties");
        private HttpServer server;private int port;
        static MusicService instance(){return INSTANCE;}
        @Override public String name(){return "Apple Music";}
        @Override public boolean connected(){return playback.get().connection().startsWith("Connected");}
        @Override public List<MusicPlaylist> playlists(){return List.copyOf(playlists);}
        @Override public PlaybackState playback(){return playback.get();}
        @Override public void command(String c){if(c!=null)command.set(c);}

        synchronized void load(){
            Properties p=new Properties();if(Files.isRegularFile(config))try(InputStream in=Files.newInputStream(config)){p.load(in);}catch(IOException ignored){}
            settings.developerToken=p.getProperty("apple.developerToken","").trim();settings.dashboardPlayer=Boolean.parseBoolean(p.getProperty("dashboard.player","true"));settings.facilityZone=p.getProperty("facility.zone","Main Facility PA");settings.eqPreset=p.getProperty("eq.preset","Balanced");settings.bass=intValue(p.getProperty("eq.bass"));settings.mid=intValue(p.getProperty("eq.mid"));settings.treble=intValue(p.getProperty("eq.treble"));
        }
        synchronized void save(){
            Properties p=new Properties();p.setProperty("apple.developerToken",settings.developerToken==null?"":settings.developerToken.trim());p.setProperty("dashboard.player",Boolean.toString(settings.dashboardPlayer));p.setProperty("facility.zone",settings.facilityZone);p.setProperty("eq.preset",settings.eqPreset);p.setProperty("eq.bass",String.valueOf(settings.bass));p.setProperty("eq.mid",String.valueOf(settings.mid));p.setProperty("eq.treble",String.valueOf(settings.treble));
            try{Files.createDirectories(config.getParent());try(OutputStream out=Files.newOutputStream(config,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){p.store(out,"NorthStar Music & Audio");}try{Files.setPosixFilePermissions(config,PosixFilePermissions.fromString("rw-------"));}catch(Exception ignored){}}catch(IOException e){throw new IllegalStateException("Unable to save music settings",e);}
        }
        synchronized URI bridge()throws IOException{
            if(server!=null)return URI.create("http://127.0.0.1:"+port+"/");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);port=server.getAddress().getPort();server.createContext("/",this::home);server.createContext("/command",this::commands);server.createContext("/playlists",this::receivePlaylists);server.createContext("/state",this::receiveState);server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"NorthStar-MusicKit");t.setDaemon(true);return t;}));server.start();return URI.create("http://127.0.0.1:"+port+"/");
        }
        void open(Component parent){
            if(settings.developerToken==null||settings.developerToken.isBlank()){JOptionPane.showMessageDialog(parent,"Enter and save an Apple Music developer token first.\nNorthStar never receives your Apple ID password.","Apple Music Setup",JOptionPane.INFORMATION_MESSAGE);return;}
            try{URI u=bridge();if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IOException("Browser integration unavailable");Desktop.getDesktop().browse(u);}catch(Exception e){JOptionPane.showMessageDialog(parent,"Unable to open Apple Music: "+e.getMessage(),"Apple Music",JOptionPane.ERROR_MESSAGE);}
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
async function setup(){try{await MusicKit.configure({developerToken:DEV,app:{name:'NorthStar Operations Intelligence',build:'2.1.30'}});music=MusicKit.getInstance();status('MusicKit ready • authorize Apple Music');}catch(e){status('MusicKit setup failed: '+e);}}
function status(s){document.getElementById('status').textContent=s;}
async function auth(){try{await music.authorize();status('Connected • Apple Music');await sync();}catch(e){status('Authorization failed: '+e);}}
async function sync(){try{if(!music)return;if(!music.isAuthorized)await music.authorize();const ut=music.musicUserToken;const r=await fetch('https://api.music.apple.com/v1/me/library/playlists?limit=100',{headers:{Authorization:'Bearer '+DEV,'Music-User-Token':ut}});if(!r.ok)throw new Error('Apple Music API '+r.status);const j=await r.json();const rows=(j.data||[]).map(p=>{const a=p.attributes||{},pp=a.playParams||{};return [p.id,pp.globalId||pp.id||p.id,a.name||p.id,(a.artwork&&a.artwork.url)||''].join('\t');});await post('/playlists',{items:rows.join('\n')});status('Connected • '+rows.length+' playlists synchronized');}catch(e){status('Playlist sync failed: '+e);}}
async function post(path,data){await fetch(path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(data)});}
async function exec(c){if(!music||!c)return;try{if(c==='sync')await sync();else if(c==='play')await music.play();else if(c==='pause')await music.pause();else if(c==='next')await music.skipToNextItem();else if(c==='prev')await music.skipToPreviousItem();else if(c.startsWith('playlist:')){await music.setQueue({playlist:c.substring(9)});await music.play();}else if(c.startsWith('volume:'))music.volume=Math.max(0,Math.min(1,Number(c.substring(7))));else if(c.startsWith('shuffle:'))music.shuffleMode=Number(c.substring(8));else if(c.startsWith('repeat:'))music.repeatMode=Number(c.substring(7));}catch(e){status('Playback command failed: '+e);}}
async function beat(){try{const c=await (await fetch('/command',{cache:'no-store'})).text();if(c)await exec(c);if(music){const n=music.nowPlayingItem;const title=n&&n.title||'Nothing Playing',artist=n&&n.artistName||'',album=n&&n.albumName||'',artwork=n&&n.artworkURL||'';document.getElementById('now').textContent=title;document.getElementById('artist').textContent=artist;await post('/state',{title,artist,album,artwork,playing:String(!!music.isPlaying),current:String(music.currentPlaybackTime||0),duration:String(music.currentPlaybackDuration||0),volume:String(music.volume==null?.7:music.volume),connection:music.isAuthorized?'Connected • Apple Music':'MusicKit ready'});}}catch(e){}setTimeout(beat,900);}
document.getElementById('auth').onclick=auth;document.getElementById('sync').onclick=sync;setup().then(beat);
</script></body></html>
""";
            return html.replace("__NORTHSTAR_DEV_TOKEN__",js(developerToken));
        }
    }

    // Full workspace --------------------------------------------------------

    private static final class MusicPanel extends JPanel{
        private final MusicService s=MusicService.instance();
        private final DefaultListModel<MusicPlaylist> model=new DefaultListModel<>();
        private final JList<MusicPlaylist> list=new JList<>(model);
        private final JLabel status=new JLabel(),title=new JLabel("Nothing Playing"),artist=new JLabel(),time=new JLabel("0:00 / 0:00");
        private final JProgressBar progress=new JProgressBar(0,1000);
        private final JSlider volume=new JSlider(0,100,70),bass=new JSlider(-12,12,0),mid=new JSlider(-12,12,0),treble=new JSlider(-12,12,0);
        private final JPasswordField token=new JPasswordField();private final JCheckBox dashboard=new JCheckBox("Show compact player on Dashboard");
        private final JTextField zone=new JTextField();private final JComboBox<String> preset=new JComboBox<>(new String[]{"Flat","Balanced","Speech Clarity","Bass Reduction","Warm","Custom"});
        private final javax.swing.Timer timer;

        MusicPanel(Window w){
            super(new BorderLayout());setBackground(Theme.bg());JPanel page=new JPanel();page.setBackground(Theme.bg());page.setLayout(new BoxLayout(page,BoxLayout.Y_AXIS));page.setBorder(new EmptyBorder(20,24,24,24));
            page.add(header());page.add(Box.createVerticalStrut(12));page.add(provider());page.add(Box.createVerticalStrut(12));JPanel columns=new JPanel(new GridLayout(1,2,12,0));columns.setOpaque(false);columns.add(library());columns.add(controls());columns.setMaximumSize(new Dimension(Integer.MAX_VALUE,650));page.add(columns);
            JScrollPane sc=new JScrollPane(page);sc.setBorder(null);sc.getViewport().setBackground(Theme.bg());sc.getVerticalScrollBar().setUnitIncrement(18);add(sc,BorderLayout.CENTER);timer=new javax.swing.Timer(900,e->refresh());timer.start();refresh();
        }
        @Override public void removeNotify(){timer.stop();super.removeNotify();}
        private JComponent header(){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setAlignmentX(Component.LEFT_ALIGNMENT);JLabel h=new JLabel("Music & Audio");h.setForeground(Theme.text());h.setFont(new Font(Font.SANS_SERIF,Font.BOLD,25));JLabel sub=new JLabel("Apple Music playlists, playback control, facility audio zones and provider-ready audio settings");sub.setForeground(Theme.muted());sub.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));p.add(h);p.add(sub);return p;}
        private JComponent provider(){JPanel c=card("APPLE MUSIC • PROVIDER CONNECTION");c.setLayout(new BorderLayout(8,8));JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);JLabel help=muted("Authorize in the browser. NorthStar receives playlist metadata and playback state only.");top.add(help,BorderLayout.WEST);status.setForeground(new Color(45,205,142));top.add(status,BorderLayout.EAST);c.add(top,BorderLayout.NORTH);JPanel row=new JPanel(new GridBagLayout());row.setOpaque(false);GridBagConstraints g=g();g.gridx=0;row.add(text("Developer token"),g);g.gridx=1;g.weightx=1;token.setText(s.settings.developerToken);fieldStyle(token);row.add(token,g);g.gridx=2;g.weightx=0;JButton save=button("Save Token");save.addActionListener(e->{s.settings.developerToken=new String(token.getPassword()).trim();s.save();});row.add(save,g);g.gridx=3;JButton open=button("Open / Authorize Apple Music");open.addActionListener(e->s.open(this));row.add(open,g);c.add(row,BorderLayout.CENTER);c.add(muted("Requires an Apple Music developer token. Apple ID credentials remain with Apple and are never entered into NorthStar."),BorderLayout.SOUTH);return c;}
        private JComponent library(){JPanel c=card("PLAYLIST LIBRARY • READ ONLY");c.setLayout(new BorderLayout(0,8));list.setBackground(Theme.panel2());list.setForeground(Theme.text());list.setSelectionBackground(Theme.accent());list.setSelectionForeground(Color.WHITE);list.setFixedCellHeight(34);JScrollPane sc=new JScrollPane(list);sc.setBorder(BorderFactory.createLineBorder(Theme.border()));c.add(sc,BorderLayout.CENTER);JPanel b=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));b.setOpaque(false);JButton play=button("Play Selected");play.addActionListener(e->{MusicPlaylist p=list.getSelectedValue();if(p!=null)s.command("playlist:"+p.playId());});JButton sync=button("Refresh from Apple Music");sync.addActionListener(e->s.command("sync"));b.add(play);b.add(sync);c.add(b,BorderLayout.SOUTH);return c;}
        private JComponent controls(){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.add(now());p.add(Box.createVerticalStrut(10));p.add(playback());p.add(Box.createVerticalStrut(10));p.add(audio());return p;}
        private JComponent now(){JPanel c=card("NOW PLAYING");c.setLayout(new BorderLayout(8,8));JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));artist.setForeground(Theme.muted());words.add(title);words.add(artist);c.add(words,BorderLayout.NORTH);JPanel center=new JPanel();center.setOpaque(false);center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));progress.setForeground(Theme.accent());progress.setBackground(Theme.panel2());center.add(progress);time.setForeground(Theme.muted());center.add(time);JPanel ctl=new JPanel(new FlowLayout(FlowLayout.CENTER,8,6));ctl.setOpaque(false);for(String[] q:new String[][]{{"⏮","prev"},{"▶","play"},{"Ⅱ","pause"},{"⏭","next"}}){JButton b=button(q[0]);b.addActionListener(e->s.command(q[1]));ctl.add(b);}center.add(ctl);c.add(center,BorderLayout.CENTER);JPanel v=new JPanel(new BorderLayout(8,0));v.setOpaque(false);v.add(text("Volume"),BorderLayout.WEST);volume.addChangeListener(e->{if(!volume.getValueIsAdjusting())s.command("volume:"+(volume.getValue()/100.0));});v.add(volume,BorderLayout.CENTER);c.add(v,BorderLayout.SOUTH);return c;}
        private JComponent playback(){JPanel c=card("PLAYBACK SETTINGS");c.setLayout(new GridLayout(2,2,8,8));JToggleButton shuffle=new JToggleButton("Shuffle");style(shuffle);shuffle.addActionListener(e->s.command("shuffle:"+(shuffle.isSelected()?1:0)));JComboBox<String> repeat=new JComboBox<>(new String[]{"Repeat Off","Repeat Track","Repeat Playlist"});repeat.addActionListener(e->s.command("repeat:"+repeat.getSelectedIndex()));dashboard.setOpaque(false);dashboard.setForeground(Theme.text());dashboard.setSelected(s.settings.dashboardPlayer);dashboard.addActionListener(e->{s.settings.dashboardPlayer=dashboard.isSelected();s.save();});c.add(shuffle);c.add(repeat);c.add(dashboard);c.add(muted("Playlist editing remains in Apple Music"));return c;}
        private JComponent audio(){JPanel c=card("FACILITY AUDIO ZONE / EQ PROFILE");c.setLayout(new GridBagLayout());zone.setText(s.settings.facilityZone);fieldStyle(zone);preset.setSelectedItem(s.settings.eqPreset);bass.setValue(s.settings.bass);mid.setValue(s.settings.mid);treble.setValue(s.settings.treble);int y=0;for(Object[] r:new Object[][]{{"Audio zone",zone},{"EQ preset",preset},{"Bass",bass},{"Mid",mid},{"Treble",treble}}){GridBagConstraints g=g();g.gridy=y;g.gridx=0;c.add(text((String)r[0]),g);g.gridx=1;g.weightx=1;c.add((Component)r[1],g);y++;}JButton save=button("Save Facility Audio Profile");save.addActionListener(e->{s.settings.facilityZone=zone.getText().trim();s.settings.eqPreset=String.valueOf(preset.getSelectedItem());s.settings.bass=bass.getValue();s.settings.mid=mid.getValue();s.settings.treble=treble.getValue();s.save();});GridBagConstraints g=g();g.gridy=y++;g.gridx=0;g.gridwidth=2;c.add(save,g);g.gridy=y;c.add(muted("EQ is stored as an output/DSP profile. This build does not alter the protected Apple Music stream; a hardware or OS DSP adapter can consume these settings later."),g);return c;}
        private void refresh(){PlaybackState st=s.playback();status.setText(s.connected()?"● CONNECTED • Apple Music":"○ NOT AUTHORIZED");title.setText(st.title());artist.setText(st.artist().isBlank()?st.connection():st.artist());double d=Math.max(0,st.duration()),cur=Math.max(0,st.current());progress.setValue(d<=0?0:(int)Math.min(1000,1000*cur/d));time.setText(clock(cur)+" / "+clock(d));if(!volume.getValueIsAdjusting())volume.setValue((int)Math.round(Math.max(0,Math.min(1,st.volume()))*100));List<MusicPlaylist> p=s.playlists();if(!same(p)){model.clear();for(MusicPlaylist x:p)model.addElement(x);}}
        private boolean same(List<MusicPlaylist> p){if(model.size()!=p.size())return false;for(int i=0;i<p.size();i++)if(!Objects.equals(model.get(i).id(),p.get(i).id()))return false;return true;}
        private static String clock(double sec){int n=(int)Math.max(0,sec);return n/60+":"+String.format("%02d",n%60);}
        private JPanel card(String title){JPanel p=new JPanel(new BorderLayout(0,8));p.setBackground(Theme.panel());p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(12,12,12,12)));p.setAlignmentX(Component.LEFT_ALIGNMENT);JLabel h=text(title);h.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));p.add(h,BorderLayout.NORTH);return p;}
        private JLabel text(String s){JLabel l=new JLabel(s);l.setForeground(Theme.text());l.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));return l;}private JLabel muted(String s){JLabel l=new JLabel(s);l.setForeground(Theme.muted());l.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));return l;}private JButton button(String s){JButton b=new JButton(s);style(b);return b;}private void style(AbstractButton b){b.setFocusPainted(false);b.setForeground(Theme.text());b.setBackground(Theme.panel2());b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(7,10,7,10)));}private void fieldStyle(JTextField f){f.setBackground(Theme.panel2());f.setForeground(Theme.text());f.setCaretColor(Theme.text());f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(7,8,7,8)));}private GridBagConstraints g(){GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.fill=GridBagConstraints.HORIZONTAL;return g;}
    }

    // Dashboard compact controls ------------------------------------------

    private static final class MiniPlayer extends JPanel{
        private final MusicService s=MusicService.instance();private final Window w;private final JLabel title=new JLabel("Nothing Playing"),artist=new JLabel("Apple Music not connected");private final JButton play=new JButton("▶");private final javax.swing.Timer timer;
        MiniPlayer(Window w){super(new BorderLayout(10,0));this.w=w;setBackground(Theme.panel());setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),new EmptyBorder(9,12,9,12)));JLabel icon=new JLabel("♫");icon.setForeground(Theme.accent());icon.setFont(new Font(Font.SANS_SERIF,Font.BOLD,23));add(icon,BorderLayout.WEST);JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));title.setForeground(Theme.text());title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));artist.setForeground(Theme.muted());artist.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));words.add(title);words.add(artist);add(words,BorderLayout.CENTER);JPanel ctl=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));ctl.setOpaque(false);JButton prev=new JButton("⏮"),next=new JButton("⏭"),open=new JButton("Open");prev.addActionListener(e->s.command("prev"));next.addActionListener(e->s.command("next"));play.addActionListener(e->s.command(s.playback().playing()?"pause":"play"));open.addActionListener(e->showMusic(w));ctl.add(prev);ctl.add(play);ctl.add(next);ctl.add(open);add(ctl,BorderLayout.EAST);timer=new javax.swing.Timer(1000,e->refresh());timer.start();refresh();}
        @Override public void removeNotify(){timer.stop();super.removeNotify();}
        private void refresh(){PlaybackState st=s.playback();title.setText(st.title());artist.setText(st.artist().isBlank()?st.connection():st.artist());play.setText(st.playing()?"Ⅱ":"▶");}
    }
}
