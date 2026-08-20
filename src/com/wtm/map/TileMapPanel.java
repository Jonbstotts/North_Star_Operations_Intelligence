package com.wtm.map;

import com.wtm.config.*;
import com.wtm.model.*;
import com.wtm.net.HttpService;
import com.wtm.util.GeoUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dependency-free slippy-map renderer.
 *
 * Base, radar, and traffic layers all share Web Mercator tile coordinates.
 * Tiles are loaded off the Swing event thread and cached locally so a fixed TV
 * display does not repeatedly redownload unchanged map imagery.
 */
public final class TileMapPanel extends JPanel {
    private static final int TILE=256;

    /** Prevent long-running/panned displays from retaining every tile forever. */
    private static final int MAX_MEMORY_TILES=900;
    private static final int MAX_DISK_TILES=2500;
    private static final Duration MAX_CACHE_AGE=Duration.ofDays(14);
    private final HttpService http;
    private final ExecutorService loader = Executors.newFixedThreadPool(5, r -> { Thread t=new Thread(r,"map-tile-loader"); t.setDaemon(true); return t; });
    private final Map<String,BufferedImage> memory = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Path cacheDir = ConfigService.appDataDir().resolve("cache");

    private AppConfig config;
    private RadarFrame radarFrame;
    private List<WeatherAlert> alerts=List.of();
    private double centerLat, centerLon;
    private int zoom=9;
    private Point dragStart;
    private double dragStartX, dragStartY;

    public TileMapPanel(AppConfig config, HttpService http) {
        this.config=config; this.http=http; this.centerLat=config.primary.latitude(); this.centerLon=config.primary.longitude();
        setOpaque(true); setBackground(new Color(20,24,30));
        try {
            Files.createDirectories(cacheDir);
            cleanupDiskCache();
        } catch (IOException ignored) {
            // Cache is an optimization only; map rendering can continue without it.
        }
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e){ dragStart=e.getPoint(); dragStartX=GeoUtils.lonToWorldX(centerLon,zoom); dragStartY=GeoUtils.latToWorldY(centerLat,zoom); }
            @Override public void mouseDragged(MouseEvent e){ if(dragStart==null)return; double wx=dragStartX-(e.getX()-dragStart.x), wy=dragStartY-(e.getY()-dragStart.y); centerLon=worldXToLon(wx,zoom); centerLat=worldYToLat(wy,zoom); repaint(); }
            @Override public void mouseWheelMoved(MouseWheelEvent e){ int old=zoom; zoom=Math.max(6,Math.min(13,zoom-e.getWheelRotation())); if(old!=zoom) repaint(); }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
    }

    public void updateConfig(AppConfig c){
        this.config=c;
    }

    /**
     * Called when Settings rebuilds the dashboard. Older releases left each
     * discarded map's five tile-loader threads alive for the rest of the
     * process, so repeated Settings changes could accumulate idle executors.
     */
    public void shutdown(){
        loader.shutdownNow();
        loading.clear();
        memory.clear();
    }

    private void putMemory(String key,BufferedImage image){
        if(key==null||image==null)return;
        memory.put(key,image);
        trimMemoryCache();
    }

    private void trimMemoryCache(){
        int excess=memory.size()-MAX_MEMORY_TILES;
        if(excess<=0)return;

        Iterator<String> keys=memory.keySet().iterator();
        while(excess>0&&keys.hasNext()){
            memory.remove(keys.next());
            excess--;
        }
    }

    /**
     * Java String.hashCode() can collide, which could make two different map
     * tiles share the same disk file. SHA-256 avoids cross-tile collisions and
     * also keeps provider keys/URLs out of cache filenames.
     */
    private Path cacheFile(String key){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hash=digest.digest(key.getBytes(StandardCharsets.UTF_8));

            StringBuilder name=new StringBuilder(64);
            for(byte b:hash)
                name.append(String.format("%02x",b));

            return cacheDir.resolve(name+".png");
        }catch(Exception ex){
            // SHA-256 is required by the Java platform; this is defensive only.
            return cacheDir.resolve(
                    Integer.toUnsignedString(key.hashCode(),16)+".png");
        }
    }

    private void cleanupDiskCache(){
        try(var stream=Files.list(cacheDir)){
            List<Path> files=stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe))
                    .toList();

            Instant cutoff=Instant.now().minus(MAX_CACHE_AGE);
            int keepFrom=Math.max(0,files.size()-MAX_DISK_TILES);

            for(int i=0;i<files.size();i++){
                Path file=files.get(i);
                Instant modified=Instant.ofEpochMilli(lastModifiedSafe(file));

                if(i<keepFrom||modified.isBefore(cutoff)){
                    try{Files.deleteIfExists(file);}
                    catch(IOException ignored){}
                }
            }
        }catch(IOException ignored){
        }
    }

    private long lastModifiedSafe(Path file){
        try{return Files.getLastModifiedTime(file).toMillis();}
        catch(IOException ex){return 0L;}
    }
    public void centerOnPrimary(){ centerLat=config.primary.latitude(); centerLon=config.primary.longitude(); zoom=9; repaint(); }
    public void setRadarFrame(RadarFrame f){ radarFrame=f; repaint(); }
    public void setAlerts(List<WeatherAlert> a){ alerts=a==null?List.of():List.copyOf(a); repaint(); }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0); Graphics2D g=(Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        drawTiles(g); drawAlertPolygons(g); drawLocations(g); drawLegend(g); g.dispose();
    }

    private void drawTiles(Graphics2D g){
        int w=getWidth(), h=getHeight(); if(w<=0||h<=0)return;
        double worldX=GeoUtils.lonToWorldX(centerLon,zoom), worldY=GeoUtils.latToWorldY(centerLat,zoom);
        int left=(int)Math.floor(worldX-w/2.0), top=(int)Math.floor(worldY-h/2.0);
        int tx0=Math.floorDiv(left,TILE), ty0=Math.floorDiv(top,TILE);
        int tx1=Math.floorDiv(left+w,TILE), ty1=Math.floorDiv(top+h,TILE);
        int max=1<<zoom;
        for(int ty=ty0;ty<=ty1;ty++) for(int tx=tx0;tx<=tx1;tx++) {
            if(ty<0||ty>=max)continue; int wrapped=((tx%max)+max)%max;
            int sx=tx*TILE-left, sy=ty*TILE-top;
            String base=config.darkMode ? "https://a.basemaps.cartocdn.com/dark_all/"+zoom+"/"+wrapped+"/"+ty+".png" : "https://tile.openstreetmap.org/"+zoom+"/"+wrapped+"/"+ty+".png";
            drawTile(g,base,sx,sy,"base");
            if(config.showRadar && radarFrame!=null) {
                drawRadarTile(g, wrapped, ty, sx, sy);
            }
            if(config.showTraffic && config.tomTomApiKey!=null && !config.tomTomApiKey.isBlank()) {
                String style=config.darkMode?"relative0-dark":"relative0";
                String traffic="https://api.tomtom.com/traffic/map/4/tile/flow/"+style+"/"+zoom+"/"+wrapped+"/"+ty+".png?key="+URLEncoder.encode(config.tomTomApiKey,StandardCharsets.UTF_8);
                // Traffic tiles are intentionally not persisted to disk because the provider marks them no-cache.
                drawTransientTile(g,traffic,sx,sy,"traffic:"+style+":"+zoom+":"+wrapped+":"+ty);
            }
        }
    }

    /**
     * Draws RainViewer radar without requesting an unsupported zoom level.
     *
     * RainViewer radar tiles currently support zoom levels only through z=7,
     * while the road map is intentionally allowed to zoom farther in. When
     * the map is above z=7, we fetch the correct z=7 parent radar tile and
     * render only the subsection that corresponds to the current higher-zoom
     * road tile. This prevents RainViewer's "Zoom Level Not Supported" error
     * image from ever being painted over the map.
     */
    private void drawRadarTile(Graphics2D g, int mapTileX, int mapTileY, int screenX, int screenY) {
        final int radarZoom = Math.min(zoom, 7);
        final int scale = 1 << (zoom - radarZoom);
        final int radarX = Math.floorDiv(mapTileX, scale);
        final int radarY = Math.floorDiv(mapTileY, scale);

        String url = "https://tilecache.rainviewer.com" + radarFrame.path()
                + "/512/" + radarZoom + "/" + radarX + "/" + radarY + "/6/1_1.png";
        String key = "radar-" + radarFrame.unixTime() + ":" + url;

        BufferedImage img = getOrLoadTile(key, url, true);
        if (img == null) {
            return; // Transparent while radar loads; never obscure the base map.
        }

        // RainViewer also publishes 512px tiles. Using those here gives us twice
        // the source resolution of the original implementation before any
        // unavoidable enlargement above RainViewer's maximum radar zoom.
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (scale == 1) {
            g.drawImage(img, screenX, screenY, TILE, TILE, null);
            if (oldInterpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            return;
        }

        int localX = Math.floorMod(mapTileX, scale);
        int localY = Math.floorMod(mapTileY, scale);

        int srcX1 = (int)Math.round(localX * (img.getWidth() / (double)scale));
        int srcY1 = (int)Math.round(localY * (img.getHeight() / (double)scale));
        int srcX2 = (int)Math.round((localX + 1) * (img.getWidth() / (double)scale));
        int srcY2 = (int)Math.round((localY + 1) * (img.getHeight() / (double)scale));

        g.drawImage(img,
                screenX, screenY, screenX + TILE, screenY + TILE,
                srcX1, srcY1, srcX2, srcY2, null);

        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    /**
     * Returns a cached tile or starts an asynchronous download.
     * Radar tiles are persisted because a frame is immutable once published.
     */
    private BufferedImage getOrLoadTile(String key, String url, boolean persist) {
        BufferedImage img = memory.get(key);
        Path file=cacheFile(key);

        if (img == null && persist && Files.exists(file)) {
            try {
                img = ImageIO.read(file.toFile());
                if (img != null) putMemory(key,img);
            } catch (Exception ignored) {}
        }

        if (img == null && loading.add(key)) {
            loader.submit(() -> {
                try {
                    byte[] data = http.getBytes(url);
                    BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
                    if (bi != null) {
                        putMemory(key,bi);
                        if (persist) {
                            try { ImageIO.write(bi, "png", file.toFile()); }
                            catch (Exception ignored) {}
                        }
                        SwingUtilities.invokeLater(this::repaint);
                    }
                } catch (Exception ignored) {
                    // Leave the layer transparent; the next repaint/refresh can retry.
                } finally {
                    loading.remove(key);
                }
            });
        }
        return img;
    }

    private void drawTile(Graphics2D g,String url,int x,int y,String namespace){
        String key=namespace+":"+url;
        BufferedImage img=memory.get(key);
        if(img==null){
            Path file=cacheFile(key);
            if(Files.exists(file)){ try{img=ImageIO.read(file.toFile()); if(img!=null)putMemory(key,img);}catch(Exception ignored){} }
            if(img==null && loading.add(key)) loader.submit(() -> { try { byte[] data=http.getBytes(url); BufferedImage bi=ImageIO.read(new ByteArrayInputStream(data)); if(bi!=null){putMemory(key,bi); try{ImageIO.write(bi,"png",file.toFile());}catch(Exception ignored){} SwingUtilities.invokeLater(this::repaint);} } catch(Exception ignored){} finally{loading.remove(key);} });
        }
        if(img!=null) g.drawImage(img,x,y,TILE,TILE,null); else { g.setColor(config.darkMode?new Color(28,33,40):new Color(225,228,232)); g.fillRect(x,y,TILE,TILE); }
    }

    private void drawTransientTile(Graphics2D g,String url,int x,int y,String key){
        BufferedImage img=memory.get(key); if(img==null && loading.add(key)) loader.submit(() -> { try { BufferedImage bi=ImageIO.read(new ByteArrayInputStream(http.getBytes(url))); if(bi!=null){putMemory(key,bi); SwingUtilities.invokeLater(this::repaint); scheduleTrafficEviction(key);} } catch(Exception ignored){} finally{loading.remove(key);} });
        if(img!=null)g.drawImage(img,x,y,TILE,TILE,null);
    }
    private void scheduleTrafficEviction(String key){ CompletableFuture.delayedExecutor(Math.max(1,config.trafficRefreshMinutes),TimeUnit.MINUTES).execute(() -> memory.remove(key)); }

    private void drawAlertPolygons(Graphics2D g){
        if(!config.showAlertsOnMap)return; int w=getWidth(), h=getHeight();
        for(WeatherAlert a:alerts){
            Color c=severityColor(a.severity());
            g.setStroke(new BasicStroke(3f));
            for(List<double[]> ring:a.polygons()){
                if(ring.size()<3)continue; Polygon p=new Polygon();
                for(double[] ll:ring){Point pt=GeoUtils.screenPoint(ll[0],ll[1],centerLat,centerLon,zoom,w,h);p.addPoint(pt.x,pt.y);} 
                g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),60)); g.fillPolygon(p); g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),210)); g.drawPolygon(p);
            }
        }
    }

    private void drawLocations(Graphics2D g){
        List<Location> points=new ArrayList<>(config.monitored); if(points.stream().noneMatch(l->l.name().equals(config.primary.name())))points.add(config.primary);
        for(Location l:points){ Point p=GeoUtils.screenPoint(l.latitude(),l.longitude(),centerLat,centerLon,zoom,getWidth(),getHeight());
            g.setColor(new Color(20,20,24,210)); g.fillRoundRect(p.x-7,p.y-29,Math.max(70,l.name().length()*8+16),24,10,10);
            g.setColor(Color.WHITE); g.setFont(getFont().deriveFont(Font.BOLD,12f)); g.drawString(l.name(),p.x+2,p.y-12);
            g.setColor(new Color(55,170,255)); g.fillOval(p.x-5,p.y-5,10,10); g.setColor(Color.WHITE); g.drawOval(p.x-5,p.y-5,10,10);
        }
    }

    private void drawLegend(Graphics2D g){
        String radar=radarFrame==null?"Radar: waiting":"Radar: "+Instant.ofEpochSecond(radarFrame.unixTime()).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
        String traffic=(config.tomTomApiKey==null||config.tomTomApiKey.isBlank())?"Traffic: add TomTom key in Settings":"Traffic: live layer enabled";
        String text=radar+"   •   "+traffic+"   •   Mouse wheel = zoom, drag = pan";
        g.setFont(getFont().deriveFont(12f)); int sw=g.getFontMetrics().stringWidth(text); int x=Math.max(10,getWidth()-sw-18), y=getHeight()-18;
        g.setColor(new Color(10,13,18,190)); g.fillRoundRect(x-8,y-16,sw+16,23,10,10); g.setColor(new Color(225,230,236)); g.drawString(text,x,y);
        g.setFont(getFont().deriveFont(10f)); String attr="© OpenStreetMap contributors • CARTO • RainViewer • TomTom"; g.drawString(attr,10,getHeight()-8);
    }

    private static Color severityColor(String s){ if("Extreme".equalsIgnoreCase(s))return new Color(220,45,45); if("Severe".equalsIgnoreCase(s))return new Color(245,110,30); if("Moderate".equalsIgnoreCase(s))return new Color(245,190,40); return new Color(70,150,255); }
    private static double worldXToLon(double x,int z){return x/(256.0*(1<<z))*360.0-180.0;}
    private static double worldYToLat(double y,int z){double n=Math.PI-2*Math.PI*y/(256.0*(1<<z));return Math.toDegrees(Math.atan(Math.sinh(n)));}
}
