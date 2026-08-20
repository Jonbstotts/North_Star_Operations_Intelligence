package com.wtm.security;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Legacy v3.0.x administrator-password reader.
 *
 * v3.1+ uses UserService and named accounts. This class exists only to verify
 * an existing v3.0 password once during migration, then delete auth.properties.
 */
public final class AuthService {
    private static final String FILE_NAME="auth.properties";
    private static final String ALGORITHM="PBKDF2WithHmacSHA256";
    private static final int KEY_BITS=256;

    private AuthService(){}

    private static Path file(){
        return ConfigService.appDataDir().resolve(FILE_NAME);
    }

    public static boolean hasPassword(){
        return loadRecord()!=null;
    }

    public static boolean verify(char[] password){
        if(password==null||password.length==0)return false;

        AuthRecord record=loadRecord();
        if(record==null)return false;

        byte[] candidate=null;
        try{
            candidate=derive(password,record.salt(),record.iterations());
            return MessageDigest.isEqual(candidate,record.hash());
        }catch(GeneralSecurityException ex){
            throw new IllegalStateException(
                    "Legacy password verification is unavailable.",
                    ex
            );
        }finally{
            if(candidate!=null)Arrays.fill(candidate,(byte)0);
        }
    }

    public static void removeLegacyRecord(){
        try{
            Files.deleteIfExists(file());
        }catch(IOException ex){
            System.err.println("Legacy authentication file could not be removed.");
        }
    }

    private static byte[] derive(char[] password,byte[] salt,int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,iterations,KEY_BITS);
        try{
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();
        }finally{
            spec.clearPassword();
        }
    }

    private static AuthRecord loadRecord(){
        Path path=file();
        if(!Files.isRegularFile(path))return null;

        try{
            SecureFiles.restrictFile(path);

            Properties properties=new Properties();
            try(InputStream in=new BufferedInputStream(Files.newInputStream(path))){
                properties.load(in);
            }

            if(!ALGORITHM.equals(properties.getProperty("algorithm","")))
                return null;

            int iterations=Integer.parseInt(
                    properties.getProperty("iterations","0")
            );
            if(iterations<100_000||iterations>2_000_000)return null;

            byte[] salt=Base64.getDecoder().decode(
                    properties.getProperty("salt","")
            );
            byte[] hash=Base64.getDecoder().decode(
                    properties.getProperty("hash","")
            );

            if(salt.length<16||hash.length<32)return null;
            return new AuthRecord(salt,hash,iterations);
        }catch(Exception ex){
            System.err.println("Legacy authentication data could not be loaded.");
            return null;
        }
    }

    private record AuthRecord(byte[] salt,byte[] hash,int iterations){}
}
