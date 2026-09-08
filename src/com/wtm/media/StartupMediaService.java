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
 * Newly imported intro movies create their final-frame PNG before Settings
 * accepts them. Startup itself never decodes a legacy movie merely to discover
 * that poster: it reads the cache only and, when needed, lets a background
 * migration prepare it. This keeps application launch responsive even when an
 * existing high-resolution intro predates the poster cache.
 */
public final class StartupMediaService {
    private static final String POSTER_CACHE_DIR=".poster-cache";
    private static final double FINAL_FRAME_LOOKBACK_SECONDS=2.0;
    private static final int FINAL_FRAME_LOOKBACK_FRAMES=90;

    private StartupMediaService(){}

    /**
     * Imports a startup video and creates its lossless final-frame poster before
     * returning it to Settings. An unsupported/corrupt movie is rejected before
     * it can become the configured startup source.
     */
    public static Path importVideo(Path source) throws IOException {
        Path managed=MediaService.importStartupVideo(source);
        try{
            BufferedImage poster=ensurePosterFor(managed);
            if(poster==null)
                throw new IOException("The startup video did not contain a readable video frame.");
            return managed;
        }catch(IOException ex){
            removePoster(managed);
            Files.deleteIfExists(managed);
            throw ex;
        }
    }

    /**
     * Reads a fresh cached final frame without decoding the movie.
     *
     * Returning {@code null} means the cache is missing or stale. This method is
     * intentionally safe for the application-launch path.
     */
    public static BufferedImage cachedPosterFor(Path managedVideo) throws IOException {
        Path video=validatedManagedVideo(managedVideo);
        if(video==null)return null;
        return readFreshPoster(video,posterPath(video));
    }

    /**
     * Returns the final-frame poster, decoding only when the cache is absent.
     * Use this for user-initiated import or background migration, not inline on
     * the startup critical path.
     */
    public static BufferedImage ensurePosterFor(Path managedVideo) throws IOException {
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
     * Saves the exact final frame observed during a complete normal playback.
     * Incomplete/skipped playback must not call this method with an early frame.
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

    /** Resolves and ensures the poster for one managed startup asset name. */
    public static BufferedImage posterForAsset(String assetName) throws IOException {
        Path video=MediaService.resolve(MediaCategory.STARTUP_MEDIA,assetName);
        return video==null?null:ensurePosterFor(video);
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

    /**
     * Extracts the actual final decodable frame without a precise full-stream
     * seek. Sloppy seek positions at a nearby key frame and only decodes the
     * short tail, which is dramatically cheaper for large legacy intro videos.
     */
    private static BufferedImage decodeFinalFrame(Path video) throws IOException {
        SeekableByteChannel channel=null;
        try{
            channel=NIOUtils.readableChannel(video.toFile());
            FrameGrab grab=FrameGrab.createFrameGrab(channel);
            DemuxerTrackMeta meta=grab.getVideoTrack().getMeta();

            if(meta!=null&&meta.getTotalDuration()>0){
                grab.seekToSecondSloppy(Math.max(
                        0,meta.getTotalDuration()-FINAL_FRAME_LOOKBACK_SECONDS));
            }else if(meta!=null&&meta.getTotalFrames()>1){
                grab.seekToFrameSloppy(Math.max(
                        0,meta.getTotalFrames()-FINAL_FRAME_LOOKBACK_FRAMES));
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
