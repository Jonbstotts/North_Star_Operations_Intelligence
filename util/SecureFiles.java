package com.wtm.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * Small file-system hardening helper.
 *
 * Configuration is written to a temporary file first and then replaced in one
 * move. This prevents a power loss or process crash from leaving a half-written
 * properties file. On POSIX systems (Linux/macOS), application data is limited
 * to the current OS user.
 */
public final class SecureFiles {
    private SecureFiles(){}

    private static final Set<PosixFilePermission> DIR_PERMISSIONS=EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );

    private static final Set<PosixFilePermission> FILE_PERMISSIONS=EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    public static void ensurePrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        try{
            Files.setPosixFilePermissions(directory,DIR_PERMISSIONS);
        }catch(UnsupportedOperationException ignored){
            // Windows/non-POSIX file system. OS account ACLs remain authoritative.
        }catch(IOException ignored){
            // Some mounted file systems expose POSIX APIs but reject chmod.
        }
    }

    public static void storePropertiesAtomic(
            Path target,
            Properties properties,
            String comment
    ) throws IOException {
        Objects.requireNonNull(target,"target");
        Objects.requireNonNull(properties,"properties");

        Path parent=target.toAbsolutePath().getParent();
        if(parent==null)
            throw new IOException("Target has no parent directory: "+target);

        ensurePrivateDirectory(parent);

        Path temp=Files.createTempFile(
                parent,
                target.getFileName().toString()+".",
                ".tmp"
        );

        boolean moved=false;
        try{
            restrictFile(temp);

            try(OutputStream out=new BufferedOutputStream(
                    Files.newOutputStream(
                            temp,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    ))){
                properties.store(out,comment);
                out.flush();
            }

            try{
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            moved=true;
            restrictFile(target);
        }finally{
            if(!moved) Files.deleteIfExists(temp);
        }
    }

    /** Atomically stores opaque binary data using the same private-file policy. */
    public static void storeBytesAtomic(Path target,byte[] data) throws IOException {
        Objects.requireNonNull(target,"target");
        Objects.requireNonNull(data,"data");
        Path parent=target.toAbsolutePath().getParent();
        if(parent==null)throw new IOException("Target has no parent directory: "+target);
        ensurePrivateDirectory(parent);
        Path temp=Files.createTempFile(parent,target.getFileName().toString()+".",".tmp");
        boolean moved=false;
        try{
            restrictFile(temp);
            Files.write(temp,data,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
            try{
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
            moved=true;
            restrictFile(target);
        }finally{
            if(!moved)Files.deleteIfExists(temp);
        }
    }

    public static void restrictFile(Path file){
        try{
            Files.setPosixFilePermissions(file,FILE_PERMISSIONS);
        }catch(UnsupportedOperationException ignored){
        }catch(IOException ignored){
        }
    }
}
