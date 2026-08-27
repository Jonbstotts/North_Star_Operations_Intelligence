package com.wtm.media;

import com.wtm.config.ConfigService;
import com.wtm.security.AuditService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.OrientedImageLoader;
import com.wtm.util.SecureFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Managed application media storage.
 *
 * Users choose a file once; the application validates and copies it into its
 * own organized data directory. Dashboard/employee records can therefore use
 * stable local assets instead of arbitrary external absolute paths.
 */
public final class MediaService {
    private MediaService(){}

    public static Path root(){
        return ConfigService.appDataDir().resolve("media-library");
    }

    public static Path directory(MediaCategory category){
        return root().resolve(category.folder());
    }

    public static void ensureDirectories() throws IOException {
        SecureFiles.ensurePrivateDirectory(root());
        for(MediaCategory category:MediaCategory.values())
            SecureFiles.ensurePrivateDirectory(directory(category));
    }

    public static Path importImage(MediaCategory category,Path source)
            throws IOException {
        Objects.requireNonNull(category,"category");
        Objects.requireNonNull(source,"source");
        requireMutationPermission(category);

        ensureDirectories();

        // Reuse the production decoder's size/dimension safety checks.
        if(OrientedImageLoader.load(source)==null)
            throw new IOException("Unsupported or unreadable image.");

        String original=source.getFileName().toString();
        String extension=extension(original);
        String base=sanitizeBase(stripExtension(original));

        if(base.isBlank())base="media";

        String sourceHash=sha256(source);
        Path duplicate=findByHash(category,sourceHash);

        if(duplicate!=null){
            AuditService.record(
                    "Skipped duplicate "+category.display()+" media: "
                    +source.getFileName()+" matches "+duplicate.getFileName()
            );
            return duplicate.toAbsolutePath();
        }

        Path target=uniqueTarget(directory(category),base,extension);
        Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES);

        AuditService.record(
                "Imported "+category.display()+" media: "+target.getFileName()
        );
        return target.toAbsolutePath();
    }

    /** Resolves a managed asset filename without exposing arbitrary paths. */
    public static Path resolve(MediaCategory category,String assetName){
        if(category==null||assetName==null||assetName.isBlank())return null;

        Path root=directory(category).toAbsolutePath().normalize();
        Path target=root.resolve(Path.of(assetName).getFileName()).normalize();

        if(!target.startsWith(root)||!Files.isRegularFile(target))return null;
        return target;
    }

    public static String assetName(Path managedFile){
        return managedFile==null?"":managedFile.getFileName().toString();
    }

    public static List<Path> list(MediaCategory category){
        try{
            ensureDirectories();
            try(Stream<Path> stream=Files.list(directory(category))){
                return stream
                        .filter(Files::isRegularFile)
                        .filter(MediaService::supported)
                        .sorted(Comparator.comparing(
                                p->p.getFileName().toString().toLowerCase()))
                        .limit(500)
                        .toList();
            }
        }catch(IOException ex){
            return List.of();
        }
    }

    /**
     * Removes byte-for-byte duplicate images from a managed collection.
     *
     * The first file in deterministic filename order is retained. This makes
     * migration cleanup safe even when the legacy and normalized filenames
     * differ. No image is removed based on its name alone.
     *
     * @return number of duplicate files removed
     */
    public static int removeDuplicates(MediaCategory category) throws IOException {
        Objects.requireNonNull(category,"category");
        requireMutationPermission(category);
        ensureDirectories();

        Map<String,Path> retained=new LinkedHashMap<>();
        int removed=0;

        for(Path file:list(category)){
            String hash=sha256(file);
            Path original=retained.putIfAbsent(hash,file);

            if(original!=null){
                Files.deleteIfExists(file);
                removed++;
                AuditService.record(
                        "Removed duplicate "+category.display()+" media: "
                        +file.getFileName()+" (same content as "
                        +original.getFileName()+")"
                );
            }
        }

        return removed;
    }

    /** Returns true when another managed file has identical image bytes. */
    public static boolean hasDuplicate(MediaCategory category,Path file)
            throws IOException {
        if(category==null||file==null||!Files.isRegularFile(file))return false;
        String hash=sha256(file);

        for(Path candidate:list(category)){
            if(candidate.toAbsolutePath().normalize()
                    .equals(file.toAbsolutePath().normalize()))
                continue;
            if(hash.equals(sha256(candidate)))return true;
        }
        return false;
    }

    public static void delete(MediaCategory category,Path file)
            throws IOException {
        requireMutationPermission(category);
        if(file==null)return;

        Path root=directory(category).toAbsolutePath().normalize();
        Path target=file.toAbsolutePath().normalize();

        if(!target.startsWith(root))
            throw new IOException("Refusing to delete media outside the managed library.");

        Files.deleteIfExists(target);
        AuditService.record(
                "Deleted "+category.display()+" media: "+target.getFileName()
        );
    }

    private static void requireMutationPermission(MediaCategory category){
        boolean allowed=AuthorizationService.allowed(Permission.MEDIA_LIBRARY)
                ||(category==MediaCategory.EMPLOYEE_PHOTOS
                    &&(
                        AuthorizationService.allowed(
                                Permission.EMPLOYEE_INFORMATION)
                        ||AuthorizationService.allowed(
                                Permission.EMPLOYEE_OPERATIONS)
                    ));

        if(!allowed)
            throw new SecurityException(
                    "The current user does not have permission to modify "
                    +category.display()+"."
            );
    }

    private static Path findByHash(MediaCategory category,String hash)
            throws IOException {
        if(hash==null||hash.isBlank())return null;

        try(Stream<Path> stream=Files.list(directory(category))){
            for(Path candidate:stream
                    .filter(Files::isRegularFile)
                    .filter(MediaService::supported)
                    .sorted(Comparator.comparing(
                            p->p.getFileName().toString().toLowerCase()))
                    .toList()){
                if(hash.equals(sha256(candidate)))
                    return candidate;
            }
        }
        return null;
    }

    private static String sha256(Path file) throws IOException {
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(file)){
                byte[] buffer=new byte[8192];
                int read;
                while((read=in.read(buffer))!=-1)
                    digest.update(buffer,0,read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException ex){
            throw new IOException("SHA-256 is unavailable.",ex);
        }
    }

    private static Path uniqueTarget(
            Path directory,
            String base,
            String extension
    ){
        Path candidate=directory.resolve(base+extension);
        int suffix=2;
        while(Files.exists(candidate)){
            candidate=directory.resolve(base+"-"+suffix+extension);
            suffix++;
        }
        return candidate;
    }

    private static boolean supported(Path path){
        String n=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png")||n.endsWith(".jpg")
                ||n.endsWith(".jpeg")||n.endsWith(".gif");
    }

    private static String extension(String filename){
        int dot=filename.lastIndexOf('.');
        if(dot<0)return ".jpg";

        String ext=filename.substring(dot).toLowerCase(Locale.ROOT);
        return Set.of(".png",".jpg",".jpeg",".gif").contains(ext)
                ?ext
                :".jpg";
    }

    private static String stripExtension(String filename){
        int dot=filename.lastIndexOf('.');
        return dot<=0?filename:filename.substring(0,dot);
    }

    private static String sanitizeBase(String value){
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+","-")
                .replaceAll("^-+|-+$","");
    }
}
