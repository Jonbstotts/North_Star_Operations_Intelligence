package com.wtm.media;

import com.wtm.security.AuditService;
import com.wtm.util.SecureFiles;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.*;

/**
 * Owns the display-ready startup animation cache.
 *
 * JCodec remains the portable source decoder, but it is deliberately kept out
 * of real-time playback after a cache has been prepared. Source frames are
 * decoded once, sampled at no more than 30 presentation frames per second,
 * scaled to the exact startup artwork viewport, and stored as lossless PNGs.
 * Subsequent startups only perform sequential PNG reads, which removes H.264
 * software-decoding throughput from the presentation clock.
 */
public final class StartupPlaybackCacheService {
    private static final int CACHE_VERSION=1;
    private static final int MAX_PRESENTATION_FPS=30;
    private static final String CACHE_DIR=".playback-cache";
    private static final String META_ENTRY="META-INF/startup-playback.properties";
    private static final String FRAME_PREFIX="frames/";

    private StartupPlaybackCacheService(){}

    public record Cache(Path path,int width,int height,int frameCount,double durationSeconds){}
    public record Frame(BufferedImage image,double timestamp,double duration){}

    /** Opens a fresh cache without decoding the source movie. */
    public static Cache openFresh(Path managedVideo,Dimension target) throws IOException {
        Path video=validatedManagedVideo(managedVideo);
        Dimension size=validatedTarget(target);
        Path cache=cachePath(video,size);
        if(!Files.isRegularFile(cache)||Files.size(cache)<=0)return null;
        return readFreshMetadata(video,cache,size);
    }

    /**
     * Returns a fresh cache, building it once when needed. The caller should run
     * this method off the Swing event thread because first-time preparation is
     * intentionally more expensive than playback.
     */
    public static Cache ensure(
            Path managedVideo,
            Dimension target,
            IntConsumer progress
    ) throws IOException {
        Path video=validatedManagedVideo(managedVideo);
        Dimension size=validatedTarget(target);
        Cache existing=openFresh(video,size);
        if(existing!=null)return existing;

        Path destination=cachePath(video,size);
        Path parent=destination.getParent();
        SecureFiles.ensurePrivateDirectory(parent);
        Path temp=Files.createTempFile(parent,"startup-playback-",".zip.tmp");
        boolean moved=false;
        try{
            Cache built=build(video,temp,size,progress==null?value->{}:progress);
            try{
                Files.move(temp,destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
            }
            moved=true;
            SecureFiles.restrictFile(destination);
            AuditService.record("Prepared startup playback cache: "+video.getFileName());
            return new Cache(
                    destination,built.width(),built.height(),
                    built.frameCount(),built.durationSeconds());
        }finally{
            if(!moved)Files.deleteIfExists(temp);
        }
    }

    /** Opens a sequential reader for one validated cache. */
    public static Reader openReader(Cache cache) throws IOException {
        if(cache==null||cache.path()==null)
            throw new IOException("Startup playback cache is unavailable.");
        return new Reader(cache);
    }

    private static Cache build(
            Path video,
            Path target,
            Dimension size,
            IntConsumer progress
    ) throws IOException {
        progress.accept(0);
        SeekableByteChannel channel=null;
        BufferedImage lastFullFrame=null;
        BufferedImage lastDisplayFrame=null;
        double lastDecodedTimestamp=0;
        double lastDecodedDuration=1.0/MAX_PRESENTATION_FPS;
        double lastWrittenTimestamp=Double.NaN;
        double nextSampleTimestamp=Double.NaN;
        int decodedCount=0;
        int writtenCount=0;
        int totalFrames=0;
        double totalDuration=0;

        try(ZipOutputStream zip=new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(target)))){
            zip.setLevel(Deflater.NO_COMPRESSION);
            try{
                channel=NIOUtils.readableChannel(video.toFile());
                FrameGrab grab=FrameGrab.createFrameGrab(channel);
                DemuxerTrackMeta meta=grab.getVideoTrack().getMeta();
                if(meta!=null){
                    totalFrames=Math.max(0,meta.getTotalFrames());
                    totalDuration=Math.max(0,meta.getTotalDuration());
                }

                PictureWithMetadata decoded;
                while((decoded=grab.getNativeFrameWithMetadata())!=null){
                    if(Thread.currentThread().isInterrupted())
                        throw new InterruptedIOException("Startup playback cache preparation cancelled.");

                    decodedCount++;
                    BufferedImage full=AWTUtil.toBufferedImage(decoded.getPicture());
                    lastFullFrame=full;
                    double timestamp=Math.max(0,decoded.getTimestamp());
                    double duration=Math.max(.001,decoded.getDuration());
                    lastDecodedTimestamp=timestamp;
                    lastDecodedDuration=duration;

                    if(Double.isNaN(nextSampleTimestamp))nextSampleTimestamp=timestamp;
                    double midpoint=timestamp+(duration*.5);
                    boolean sample=writtenCount==0||midpoint+1e-6>=nextSampleTimestamp;
                    if(sample){
                        while(nextSampleTimestamp<=midpoint+1e-6)
                            nextSampleTimestamp+=1.0/MAX_PRESENTATION_FPS;
                        BufferedImage display=scaleContained(
                                full,size.width,size.height);
                        lastDisplayFrame=display;
                        double presentationDuration=Math.max(
                                duration,1.0/MAX_PRESENTATION_FPS);
                        writeFrame(zip,writtenCount,display,timestamp,presentationDuration);
                        lastWrittenTimestamp=timestamp;
                        writtenCount++;
                    }

                    if(totalFrames>0){
                        int percent=Math.min(97,(int)Math.round(
                                decodedCount*97.0/Math.max(1,totalFrames)));
                        progress.accept(percent);
                    }
                }
            }catch(InterruptedIOException ex){
                throw ex;
            }catch(Exception ex){
                throw new IOException("Unable to prepare the startup playback cache.",ex);
            }finally{
                NIOUtils.closeQuietly(channel);
            }

            if(lastFullFrame==null||writtenCount==0)
                throw new IOException("The startup video did not contain a readable frame.");

            /* Always land on the exact final source frame, even when 60 -> 30
             * fps sampling would otherwise leave the final frame between slots. */
            if(Double.isNaN(lastWrittenTimestamp)
                    ||Math.abs(lastDecodedTimestamp-lastWrittenTimestamp)>.001){
                lastDisplayFrame=scaleContained(lastFullFrame,size.width,size.height);
                writeFrame(
                        zip,writtenCount,lastDisplayFrame,
                        lastDecodedTimestamp,
                        Math.max(lastDecodedDuration,1.0/MAX_PRESENTATION_FPS));
                writtenCount++;
            }

            StartupMediaService.cachePoster(video,lastFullFrame);
            double duration=totalDuration>0
                    ?totalDuration
                    :lastDecodedTimestamp+Math.max(lastDecodedDuration,1.0/MAX_PRESENTATION_FPS);
            writeMetadata(zip,video,size,writtenCount,duration);
            progress.accept(100);
            return new Cache(target,size.width,size.height,writtenCount,duration);
        }
    }

    private static void writeFrame(
            ZipOutputStream zip,
            int index,
            BufferedImage image,
            double timestamp,
            double duration
    ) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(256*1024);
        if(!ImageIO.write(image,"png",bytes))
            throw new IOException("PNG writer is unavailable for startup playback cache.");
        long timestampMicros=Math.max(0,Math.round(timestamp*1_000_000.0));
        long durationMicros=Math.max(1,Math.round(duration*1_000_000.0));
        String name=String.format(
                Locale.ROOT,FRAME_PREFIX+"%06d_%015d_%012d.png",
                index,timestampMicros,durationMicros);
        writeStoredEntry(zip,name,bytes.toByteArray());
    }

    private static void writeMetadata(
            ZipOutputStream zip,
            Path video,
            Dimension size,
            int frameCount,
            double duration
    ) throws IOException {
        Properties properties=new Properties();
        properties.setProperty("version",Integer.toString(CACHE_VERSION));
        properties.setProperty("sourceSize",Long.toString(Files.size(video)));
        properties.setProperty(
                "sourceModified",Long.toString(Files.getLastModifiedTime(video).toMillis()));
        properties.setProperty("width",Integer.toString(size.width));
        properties.setProperty("height",Integer.toString(size.height));
        properties.setProperty("frameCount",Integer.toString(frameCount));
        properties.setProperty("durationSeconds",Double.toString(duration));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        properties.store(bytes,"NorthStar startup playback cache");
        writeStoredEntry(zip,META_ENTRY,bytes.toByteArray());
    }

    private static void writeStoredEntry(
            ZipOutputStream zip,
            String name,
            byte[] bytes
    ) throws IOException {
        CRC32 crc=new CRC32();
        crc.update(bytes);
        ZipEntry entry=new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static Cache readFreshMetadata(
            Path video,
            Path cache,
            Dimension target
    ){
        try(ZipFile zip=new ZipFile(cache.toFile())){
            ZipEntry entry=zip.getEntry(META_ENTRY);
            if(entry==null)return null;
            Properties p=new Properties();
            try(InputStream in=zip.getInputStream(entry)){p.load(in);}
            if(integer(p,"version",-1)!=CACHE_VERSION)return null;
            if(longValue(p,"sourceSize",-1)!=Files.size(video))return null;
            if(longValue(p,"sourceModified",-1)
                    !=Files.getLastModifiedTime(video).toMillis())return null;
            int width=integer(p,"width",-1);
            int height=integer(p,"height",-1);
            if(width!=target.width||height!=target.height)return null;
            int frameCount=integer(p,"frameCount",0);
            double duration=doubleValue(p,"durationSeconds",0);
            if(frameCount<=0||duration<=0)return null;
            return new Cache(cache,width,height,frameCount,duration);
        }catch(Exception ex){
            return null;
        }
    }

    private static Path cachePath(Path video,Dimension size) throws IOException {
        Path root=MediaService.directory(MediaCategory.STARTUP_MEDIA)
                .resolve(CACHE_DIR).toAbsolutePath().normalize();
        SecureFiles.ensurePrivateDirectory(root);
        String name=video.getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]+","_");
        return root.resolve(name+"."+size.width+"x"+size.height+".v"+CACHE_VERSION+".zip");
    }

    private static Path validatedManagedVideo(Path candidate) throws IOException {
        if(candidate==null)throw new IOException("Startup video is unavailable.");
        Path root=MediaService.directory(MediaCategory.STARTUP_MEDIA)
                .toAbsolutePath().normalize();
        Path video=candidate.toAbsolutePath().normalize();
        if(!video.startsWith(root)||!Files.isRegularFile(video)||!Files.isReadable(video))
            throw new IOException("Startup media must be a readable managed application asset.");
        return video;
    }

    private static Dimension validatedTarget(Dimension target) throws IOException {
        if(target==null||target.width<=0||target.height<=0)
            throw new IOException("Startup playback target has invalid dimensions.");
        return new Dimension(target);
    }

    private static BufferedImage scaleContained(BufferedImage source,int width,int height){
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try{
            g.setColor(Color.BLACK);
            g.fillRect(0,0,width,height);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            double scale=Math.min(width/(double)source.getWidth(),height/(double)source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale));
            int h=Math.max(1,(int)Math.round(source.getHeight()*scale));
            g.drawImage(source,(width-w)/2,(height-h)/2,w,h,null);
        }finally{g.dispose();}
        return out;
    }

    private static int integer(Properties p,String key,int fallback){
        try{return Integer.parseInt(p.getProperty(key,""));}
        catch(Exception ex){return fallback;}
    }
    private static long longValue(Properties p,String key,long fallback){
        try{return Long.parseLong(p.getProperty(key,""));}
        catch(Exception ex){return fallback;}
    }
    private static double doubleValue(Properties p,String key,double fallback){
        try{return Double.parseDouble(p.getProperty(key,""));}
        catch(Exception ex){return fallback;}
    }

    public static final class Reader implements AutoCloseable {
        private final ZipFile zip;
        private final Iterator<ZipEntry> entries;

        private Reader(Cache cache) throws IOException {
            zip=new ZipFile(cache.path().toFile());
            List<ZipEntry> frames=zip.stream()
                    .map(ZipEntry.class::cast)
                    .filter(entry->!entry.isDirectory()&&entry.getName().startsWith(FRAME_PREFIX))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            if(frames.size()!=cache.frameCount()){
                zip.close();
                throw new IOException("Startup playback cache frame count is inconsistent.");
            }
            entries=frames.iterator();
        }

        public Frame next() throws IOException {
            if(!entries.hasNext())return null;
            ZipEntry entry=entries.next();
            BufferedImage image;
            try(InputStream in=new BufferedInputStream(zip.getInputStream(entry))){
                image=ImageIO.read(in);
            }
            if(image==null)throw new IOException("Startup playback cache contains an unreadable frame.");
            String file=entry.getName().substring(FRAME_PREFIX.length());
            int dot=file.lastIndexOf('.');
            if(dot>0)file=file.substring(0,dot);
            String[] parts=file.split("_");
            if(parts.length!=3)throw new IOException("Startup playback cache frame timing is invalid.");
            try{
                double timestamp=Long.parseLong(parts[1])/1_000_000.0;
                double duration=Math.max(.001,Long.parseLong(parts[2])/1_000_000.0);
                return new Frame(image,timestamp,duration);
            }catch(NumberFormatException ex){
                throw new IOException("Startup playback cache frame timing is invalid.",ex);
            }
        }

        @Override public void close() throws IOException {zip.close();}
    }
}
