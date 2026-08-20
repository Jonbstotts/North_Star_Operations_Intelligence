package com.wtm.config;

import com.wtm.util.SecureFiles;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Stores API credentials separately from ordinary dashboard configuration.
 *
 * This is intentionally a local-file solution, not a cryptographic vault.
 * Linux/macOS permissions are restricted to the current OS user, writes are
 * atomic, and secrets are never written into the ordinary configuration file.
 */
public final class ApiCredentialService {
    private static final String FILE_NAME="credentials.properties";

    private ApiCredentialService(){}

    private static Path file(){
        return ConfigService.appDataDir().resolve(FILE_NAME);
    }

    public static void loadInto(AppConfig cfg){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            if(!Files.exists(file()))return;

            SecureFiles.restrictFile(file());

            Properties properties=new Properties();
            try(InputStream in=new BufferedInputStream(Files.newInputStream(file()))){
                properties.load(in);
            }

            cfg.weatherApiKey=properties.getProperty("weatherApiKey","").trim();
            cfg.tomTomApiKey=properties.getProperty("tomTomApiKey","").trim();
            cfg.sportsApiKey=
                    properties.getProperty("sportsApiKey",cfg.sportsApiKey).trim();
            cfg.twilioAccountSid=
                    properties.getProperty("twilioAccountSid","").trim();
            cfg.twilioAuthToken=
                    properties.getProperty("twilioAuthToken","");
            cfg.sendGridApiKey=
                    properties.getProperty("sendGridApiKey","");
        }catch(Exception ex){
            System.err.println("Credential file could not be loaded.");
        }
    }

    public static void saveFrom(AppConfig cfg){
        try{
            Properties properties=new Properties();
            properties.setProperty("weatherApiKey",safe(cfg.weatherApiKey));
            properties.setProperty("tomTomApiKey",safe(cfg.tomTomApiKey));
            properties.setProperty("sportsApiKey",safe(cfg.sportsApiKey));
            properties.setProperty("twilioAccountSid",safe(cfg.twilioAccountSid));
            properties.setProperty("twilioAuthToken",safe(cfg.twilioAuthToken));
            properties.setProperty("sendGridApiKey",safe(cfg.sendGridApiKey));

            SecureFiles.storePropertiesAtomic(
                    file(),
                    properties,
                    "NORTH STAR API credentials - keep private"
            );
        }catch(IOException ex){
            throw new RuntimeException("Unable to save API credentials.",ex);
        }
    }

    private static String safe(String value){
        return value==null?"":value;
    }
}
