package com.wtm.security;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Append-only local administrative audit trail.
 *
 * The log intentionally records actions and usernames, never passwords,
 * API keys, or other secret values.
 */
public final class AuditService {
    private static final DateTimeFormatter TS=
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuditService(){}

    private static Path file(){
        return ConfigService.appDataDir().resolve("audit.log");
    }

    public static synchronized void record(String action){
        UserAccount user=SessionManager.currentUser();
        record(user==null?"SYSTEM":user.username(),action);
    }

    public static synchronized void record(String username,String action){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            String safeUser=sanitize(username);
            String safeAction=sanitize(action);

            String line=LocalDateTime.now().format(TS)
                    +" | "+safeUser+" | "+safeAction
                    +System.lineSeparator();

            Files.writeString(
                    file(),
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
            SecureFiles.restrictFile(file());
        }catch(Exception ex){
            System.err.println("Audit event could not be recorded.");
        }
    }

    public static String readRecent(int maxLines){
        if(!Files.isRegularFile(file()))return "No audit entries yet.";
        try{
            var lines=Files.readAllLines(file(),StandardCharsets.UTF_8);
            int from=Math.max(0,lines.size()-Math.max(1,maxLines));
            return String.join(System.lineSeparator(),lines.subList(from,lines.size()));
        }catch(Exception ex){
            return "Audit log could not be read.";
        }
    }

    private static String sanitize(String value){
        if(value==null)return "";
        return value.replace('\r',' ').replace('\n',' ').trim();
    }
}
