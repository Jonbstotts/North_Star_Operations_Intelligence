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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

/**
 * Owns startup-video validation and the lossless resting-frame cache.
 *
 * The selected intro movie is the single source of truth for startup identity:
 * its final decodable frame is cached as PNG once at import time and reused by
 * the login screen, Skip Intro, and future startups. Startup therefore never
 * needs to seek through the movie merely to reconstruct the resting artwork.
 */
public final class StartupMediaService {
    private static final String POSTER_CACHE_DIR=".poster-cache";

    private StartupMediaService(){}

    /**
     * Imports a startup video and creates its lossless final-frame poster before
     * returning it to Settings. An unsupported/corrupt movie is rejected before
     * it can become the configured startup source.
     */
    public static Path importVideo(Path source) throws IOException {
        Path managed=MediaService.copyStartupVideo(source);
        try{
            BufferedImage poster=posterFor(managed);
            if(poster==null)
                throw new IOException("The startup video did not contain a readable video frame.");
            return managed;
        }catch(IOException ex){
            removePoster(managed);
            Files.deleteIfExists(managed);
            throw ex;
        }
    }

    /** Returns the cached final frame, generating it once for pre-cache imports. */
    public static BufferedImage posterFor(Path managedVideo) throws IOException {
        Path video=validatedManagedVideo(managedVideo);
        if(video==null)return null;

        Path poster=posterPath(video);
        BufferedImage cached=readFreshPoster(video,poster);
        if(cached!=null)return cached;

        BufferedImage decoded=decodeFinalFrame(video);
        if(decoded==null)return null;
        writePosterAtomic(poster,decoded);
        AuditService.record("Prepared startup resting frame: "+video.getFileName());
        return decoded;
    }

    /**
     * Saves the exact final frame observed during normal playback. This is a
     * repair path for older media whose poster cache was missing or stale.
     */
    public static void cachePoster(Path managedVideo,BufferedImage image){
        if(image==null)return;
        try{
            Path video=validatedManagedVideo(managedVideo);
            if(video==null)return;
            writePosterAtomic(posterPath(video),image);
        }catch(IOException ex){
            AuditService.record(
                    "Startup resting-frame cache update failed: "+ex.getClass().getSimpleName());
        }
    }

    /** Resolves and returns the cached poster for one managed asset name. */
    public static BufferedImage posterForAsset(String assetName) throws IOException {
        Path video=MediaService.resolve(MediaCategory.STARTUP_MEDIA,assetName);
        return video==null?null:posterFor(video);
    }

    private static BufferedImage readFreshPoster(Path video,Path poster){
        try{
            if(!Files.isRegularFile(poster)||Files.size(poster)<=0)return null;
            if(Files.getLastModifiedTime(poster).compareTo(
                    Files.getLastModifiedTime(video))<0)return null;
            BufferedImage image=ImageIO.read(poster.toFile());
            return image!=null&&image.getWidth()>0&&image.getHeight()>0?image:null;
        }catch(IOException ex){
            return null;
        }
    }

    private static BufferedImage decodeFinalFrame(Path video) throws IOException {
        SeekableByteChannel channel=null;
        try{
            channel=NIOUtils.readableChannel(video.toFile());
            FrameGrab grab=FrameGrab.createFrameGrab(channel);
            DemuxerTrackMeta meta=grab.getVideoTrack().getMeta();

            if(meta!=null&&meta.getTotalFrames()>1){
                grab.seekToFramePrecise(Math.max(0,meta.getTotalFrames()-1));
            }else if(meta!=null&&meta.getTotalDuration()>0){
                grab.seekToSecondPrecise(Math.max(0,meta.getTotalDuration()-.25));
            }

            BufferedImage last=null;
            PictureWithMetadata decoded;
            while((decoded=grab.getNativeFrameWithMetadata())!=null)
                last=AWTUtil.toBufferedImage(decoded.getPicture());
            return last;
        }catch(Exception ex){
            throw new IOException("Unable to decode the final startup-video frame.",ex);
        }finally{
            NIOUtils.closeQuietly(channel);
        }
    }

    private static void writePosterAtomic(Path target,BufferedImage image) throws IOException {
        Objects.requireNonNull(target,"target");
        Objects.requireNonNull(image,"image");
        Path parent=target.toAbsolutePath().getParent();
        if(parent==null)throw new IOException("Startup poster cache has no parent directory.");
        SecureFiles.ensurePrivateDirectory(parent);

        Path temp=Files.createTempFile(parent,"startup-poster-",".png.tmp");
        boolean moved=false;
        try{
            SecureFiles.restrictFile(temp);
            if(!ImageIO.write(image,"png",temp.toFile()))
                throw new IOException("PNG writer is unavailable for startup poster cache.");
            try{
                Files.move(temp,target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
            moved=true;
            SecureFiles.restrictFile(target);
        }finally{
            if(!moved)Files.deleteIfExists(temp);
        }
    }

    private static Path posterPath(Path video) throws IOException {
        Path cache=MediaService.directory(MediaCategory.STARTUP_MEDIA)
                .resolve(POSTER_CACHE_DIR).toAbsolutePath().normalize();
        SecureFiles.ensurePrivateDirectory(cache);
        String name=video.getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]+","_");
        return cache.resolve(name+".poster.png");
    }

    private static void removePoster(Path managedVideo){
        try{
            Path video=validatedManagedVideo(managedVideo);
            if(video!=null)Files.deleteIfExists(posterPath(video));
        }catch(IOException ignored){}
    }

    private static Path validatedManagedVideo(Path candidate) throws IOException {
        if(candidate==null)return null;
        Path root=MediaService.directory(MediaCategory.STARTUP_MEDIA)
                .toAbsolutePath().normalize();
        Path video=candidate.toAbsolutePath().normalize();
        if(!video.startsWith(root)||!Files.isRegularFile(video)||!Files.isReadable(video))
            throw new IOException("Startup media must be a readable managed application asset.");
        return video;
    }
}
