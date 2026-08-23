package com.wtm.config;

import com.wtm.util.SecureFiles;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Properties;

/**
 * Local encrypted credential store for provider secrets.
 *
 * <p>Secrets are encrypted with AES-256-GCM before being written to disk. A
 * random installation key is generated once and stored in the private
 * application-data directory with owner-only permissions where the operating
 * system supports them. This protects credentials from accidental disclosure
 * through config files, backups, screenshots, or casual file inspection. It is
 * not a substitute for full-disk encryption or an enterprise managed secret
 * vault when the host OS account itself is compromised.</p>
 *
 * <p>The loader also supports environment-variable overrides so production
 * deployments can keep secrets out of the application-data directory entirely.
 * Existing plaintext credentials.properties files are migrated once and then
 * securely deleted on a best-effort basis.</p>
 */
public final class ApiCredentialService {
    private static final String FILE_NAME="credentials.enc";
    private static final String KEY_FILE="credentials.key";
    private static final String LEGACY_FILE="credentials.properties";
    private static final byte[] MAGIC="NSCRED1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD="NorthStar Operations Intelligence credentials v1"
            .getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM=new SecureRandom();

    private ApiCredentialService(){}

    private static Path file(){ return ConfigService.appDataDir().resolve(FILE_NAME); }
    private static Path keyFile(){ return ConfigService.appDataDir().resolve(KEY_FILE); }
    private static Path legacyFile(){ return ConfigService.appDataDir().resolve(LEGACY_FILE); }

    public static void loadInto(AppConfig cfg){
        try{
            SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
            migrateLegacyIfNeeded();

            Properties p=Files.exists(file())?decryptProperties():new Properties();
            cfg.weatherApiKey=value(p,"weatherApiKey",cfg.weatherApiKey);
            cfg.tomTomApiKey=value(p,"tomTomApiKey",cfg.tomTomApiKey);
            cfg.sportsApiKey=value(p,"sportsApiKey",cfg.sportsApiKey);
            cfg.twilioAccountSid=value(p,"twilioAccountSid",cfg.twilioAccountSid);
            cfg.twilioAuthToken=value(p,"twilioAuthToken",cfg.twilioAuthToken);
            cfg.sendGridApiKey=value(p,"sendGridApiKey",cfg.sendGridApiKey);
            cfg.fedexClientId=value(p,"fedexClientId",cfg.fedexClientId);
            cfg.fedexClientSecret=value(
                    p,"fedexClientSecret",cfg.fedexClientSecret);
            cfg.penskeApiToken=value(p,"penskeApiToken",cfg.penskeApiToken);
            cfg.trak4ApiKey=value(p,"trak4ApiKey",cfg.trak4ApiKey);

            // Environment variables intentionally override locally stored values.
            cfg.weatherApiKey=env("NORTHSTAR_WEATHER_API_KEY",cfg.weatherApiKey);
            cfg.tomTomApiKey=env("NORTHSTAR_TOMTOM_API_KEY",cfg.tomTomApiKey);
            cfg.sportsApiKey=env("NORTHSTAR_SPORTS_API_KEY",cfg.sportsApiKey);
            cfg.twilioAccountSid=env("NORTHSTAR_TWILIO_ACCOUNT_SID",cfg.twilioAccountSid);
            cfg.twilioAuthToken=env("NORTHSTAR_TWILIO_AUTH_TOKEN",cfg.twilioAuthToken);
            cfg.sendGridApiKey=env("NORTHSTAR_SENDGRID_API_KEY",cfg.sendGridApiKey);
            cfg.fedexClientId=env(
                    "NORTHSTAR_FEDEX_CLIENT_ID",cfg.fedexClientId);
            cfg.fedexClientSecret=env(
                    "NORTHSTAR_FEDEX_CLIENT_SECRET",cfg.fedexClientSecret);
            cfg.penskeApiToken=env(
                    "NORTHSTAR_PENSKE_API_TOKEN",cfg.penskeApiToken);
            cfg.trak4ApiKey=env(
                    "NORTHSTAR_TRAK4_API_KEY",cfg.trak4ApiKey);
        }catch(Exception ex){
            System.err.println("Encrypted credential store could not be loaded.");
        }
    }

    public static void saveFrom(AppConfig cfg){
        try{
            Properties p=new Properties();
            p.setProperty("weatherApiKey",safe(cfg.weatherApiKey));
            p.setProperty("tomTomApiKey",safe(cfg.tomTomApiKey));
            p.setProperty("sportsApiKey",safe(cfg.sportsApiKey));
            p.setProperty("twilioAccountSid",safe(cfg.twilioAccountSid));
            p.setProperty("twilioAuthToken",safe(cfg.twilioAuthToken));
            p.setProperty("sendGridApiKey",safe(cfg.sendGridApiKey));
            p.setProperty("fedexClientId",safe(cfg.fedexClientId));
            p.setProperty("fedexClientSecret",safe(cfg.fedexClientSecret));
            p.setProperty("penskeApiToken",safe(cfg.penskeApiToken));
            p.setProperty("trak4ApiKey",safe(cfg.trak4ApiKey));
            encryptProperties(p);
        }catch(Exception ex){
            throw new RuntimeException("Unable to save encrypted API credentials.",ex);
        }
    }

    private static void migrateLegacyIfNeeded() throws Exception {
        if(Files.exists(file())||!Files.exists(legacyFile()))return;

        Properties legacy=new Properties();
        try(InputStream in=new BufferedInputStream(Files.newInputStream(legacyFile()))){
            legacy.load(in);
        }
        encryptProperties(legacy);

        // Best effort: overwrite the old plaintext file before deletion.
        try{
            long size=Files.size(legacyFile());
            if(size>0&&size<4*1024*1024){
                byte[] wipe=new byte[(int)size];
                RANDOM.nextBytes(wipe);
                Files.write(legacyFile(),wipe,StandardOpenOption.TRUNCATE_EXISTING);
                Arrays.fill(wipe,(byte)0);
            }
        }catch(Exception ignored){}
        Files.deleteIfExists(legacyFile());
    }

    private static Properties decryptProperties() throws Exception {
        SecureFiles.restrictFile(file());
        byte[] raw=Files.readAllBytes(file());
        if(raw.length<MAGIC.length+1+12+16)
            throw new IOException("Credential store is invalid.");

        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw))){
            byte[] magic=in.readNBytes(MAGIC.length);
            if(!Arrays.equals(magic,MAGIC))
                throw new IOException("Credential store version is not supported.");
            int nonceLength=in.readUnsignedByte();
            if(nonceLength!=12)throw new IOException("Credential store nonce is invalid.");
            byte[] nonce=in.readNBytes(nonceLength);
            byte[] ciphertext=in.readAllBytes();

            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(loadOrCreateKey(),"AES"),
                    new GCMParameterSpec(128,nonce));
            cipher.updateAAD(AAD);
            byte[] plaintext=cipher.doFinal(ciphertext);
            try{
                Properties p=new Properties();
                try(InputStream pin=new ByteArrayInputStream(plaintext)){ p.load(pin); }
                return p;
            }finally{
                Arrays.fill(plaintext,(byte)0);
            }
        }finally{
            Arrays.fill(raw,(byte)0);
        }
    }

    private static void encryptProperties(Properties properties) throws Exception {
        ByteArrayOutputStream plainOut=new ByteArrayOutputStream();
        properties.store(plainOut,"North Star encrypted provider credentials");
        byte[] plaintext=plainOut.toByteArray();
        byte[] nonce=new byte[12];
        RANDOM.nextBytes(nonce);

        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(loadOrCreateKey(),"AES"),
                    new GCMParameterSpec(128,nonce));
            cipher.updateAAD(AAD);
            byte[] ciphertext=cipher.doFinal(plaintext);

            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(encoded)){
                out.write(MAGIC);
                out.writeByte(nonce.length);
                out.write(nonce);
                out.write(ciphertext);
            }
            SecureFiles.storeBytesAtomic(file(),encoded.toByteArray());
            SecureFiles.restrictFile(file());
            Arrays.fill(ciphertext,(byte)0);
        }finally{
            Arrays.fill(plaintext,(byte)0);
        }
    }

    private static byte[] loadOrCreateKey() throws IOException {
        SecureFiles.ensurePrivateDirectory(ConfigService.appDataDir());
        if(Files.exists(keyFile())){
            SecureFiles.restrictFile(keyFile());
            byte[] key=Files.readAllBytes(keyFile());
            if(key.length!=32)throw new IOException("Credential encryption key is invalid.");
            return key;
        }
        byte[] key=new byte[32];
        RANDOM.nextBytes(key);
        SecureFiles.storeBytesAtomic(keyFile(),key);
        SecureFiles.restrictFile(keyFile());
        return key;
    }

    private static String env(String name,String fallback){
        String value=System.getenv(name);
        return value==null||value.isBlank()?safe(fallback):value.trim();
    }

    private static String value(Properties p,String key,String fallback){
        return p.getProperty(key,safe(fallback)).trim();
    }

    private static String safe(String value){ return value==null?"":value; }
}
