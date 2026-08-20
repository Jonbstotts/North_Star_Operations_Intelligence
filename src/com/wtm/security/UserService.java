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
 * Self-contained local account store for the desktop/Pi deployment.
 *
 * Passwords are salted PBKDF2-HMAC-SHA256 hashes. The file contains no
 * recoverable plaintext passwords and is protected with owner-only POSIX
 * permissions where supported.
 */
public final class UserService {
    private static final String FILE_NAME="users.properties";
    private static final String ALGORITHM="PBKDF2WithHmacSHA256";
    private static final int ITERATIONS=310_000;
    private static final int KEY_BITS=256;
    private static final int SALT_BYTES=16;
    public static final int MIN_PASSWORD_LENGTH=8;

    private static int failedAttempts=0;
    private static long lockedUntil=0L;

    private UserService(){}

    private static Path file(){
        return ConfigService.appDataDir().resolve(FILE_NAME);
    }

    public static synchronized List<UserAccount> listUsers(){
        AuthorizationService.require(Permission.MANAGE_USERS);
        Store store=loadStore();
        return store.records.values().stream()
                .map(UserService::publicAccount)
                .sorted(Comparator.comparing(UserAccount::username))
                .toList();
    }

    public static synchronized boolean hasUsers(){
        return !loadStore().records.isEmpty();
    }

    public static synchronized UserAccount find(String username){
        AuthorizationService.require(Permission.MANAGE_USERS);
        if(username==null)return null;
        Record record=loadStore().records.get(normalize(username));
        return record==null?null:publicAccount(record);
    }

    public static synchronized UserAccount authenticate(
            String username,
            char[] password
    ){
        if(lockoutSecondsRemaining()>0)return null;
        if(username==null||password==null){
            recordFailure();
            return null;
        }

        Record record=loadStore().records.get(normalize(username));
        if(record==null||!record.enabled){
            recordFailure();
            return null;
        }

        byte[] candidate=null;
        try{
            candidate=derive(password,record.salt,record.iterations);
            if(!MessageDigest.isEqual(candidate,record.hash)){
                recordFailure();
                return null;
            }

            failedAttempts=0;
            lockedUntil=0L;
            return publicAccount(record);
        }catch(GeneralSecurityException ex){
            throw new IllegalStateException("User authentication is unavailable.",ex);
        }finally{
            if(candidate!=null)Arrays.fill(candidate,(byte)0);
        }
    }

    public static synchronized int lockoutSecondsRemaining(){
        long remaining=lockedUntil-System.currentTimeMillis();
        if(remaining<=0){
            if(lockedUntil!=0L){
                lockedUntil=0L;
                failedAttempts=0;
            }
            return 0;
        }
        return (int)Math.ceil(remaining/1000.0);
    }

    private static void recordFailure(){
        failedAttempts++;
        if(failedAttempts>=5){
            failedAttempts=0;
            lockedUntil=System.currentTimeMillis()+30_000L;
        }
    }

    public static synchronized void createUser(
            String username,
            String displayName,
            UserRole role,
            Set<Permission> permissions,
            boolean enabled,
            char[] password
    ){
        AuthorizationService.require(Permission.MANAGE_USERS);
        String key=normalize(username);
        validateUsername(key);
        validatePassword(password);

        Store store=loadStore();
        if(store.records.containsKey(key))
            throw new IllegalArgumentException("That username already exists.");

        Record record=newRecord(
                key,
                displayName,
                role,
                permissions,
                enabled,
                password
        );
        store.records.put(key,record);
        saveStore(store);
        AuditService.record("Created user "+key+" ("+role.display()+")");
    }

    /**
     * Creates the very first administrator. This path is permitted only when no
     * accounts exist, preventing later callers from bypassing MANAGE_USERS.
     */
    public static synchronized UserAccount createFirstAdministrator(
            String username,
            String displayName,
            char[] password
    ){
        Store store=loadStore();
        if(!store.records.isEmpty())
            throw new IllegalStateException("User accounts are already configured.");

        String key=normalize(username);
        validateUsername(key);
        validatePassword(password);

        Record record=newRecord(
                key,
                displayName,
                UserRole.ADMINISTRATOR,
                EnumSet.allOf(Permission.class),
                true,
                password
        );
        store.records.put(key,record);
        saveStore(store);

        UserAccount account=publicAccount(record);
        AuditService.record(key,"Created initial administrator account");
        return account;
    }

    public static synchronized void updateProfile(
            String username,
            String displayName,
            UserRole role,
            Set<Permission> permissions,
            boolean enabled
    ){
        AuthorizationService.require(Permission.MANAGE_USERS);

        Store store=loadStore();
        String key=normalize(username);
        Record old=requireRecord(store,key);

        if(old.role==UserRole.ADMINISTRATOR && !enabled
                &&countEnabledAdmins(store)<=1)
            throw new IllegalArgumentException(
                    "The final enabled administrator cannot be disabled."
            );

        Set<Permission> effective=role==UserRole.ADMINISTRATOR
                ?EnumSet.allOf(Permission.class)
                :permissionSet(permissions);

        UserAccount current=SessionManager.currentUser();
        if(current!=null&&current.username().equalsIgnoreCase(key)){
            if(!enabled)
                throw new IllegalArgumentException(
                        "The currently signed-in account cannot disable itself."
                );

            if(role!=old.role||!effective.equals(old.permissions))
                throw new IllegalArgumentException(
                        "For session safety, another administrator must change "
                        +"your role or permissions."
                );
        }

        Record updated=new Record(
                old.username,
                cleanDisplay(displayName),
                role,
                enabled,
                effective,
                old.salt,
                old.hash,
                old.iterations
        );

        store.records.put(key,updated);
        saveStore(store);

        if(current!=null&&current.username().equalsIgnoreCase(key))
            SessionManager.login(publicAccount(updated));

        AuditService.record("Updated access for user "+key);
    }

    public static synchronized void changeOwnPassword(
            char[] currentPassword,
            char[] newPassword
    ){
        UserAccount current=SessionManager.currentUser();
        if(current==null)
            throw new SecurityException("No user is signed in.");

        validatePassword(newPassword);

        UserAccount verified=authenticate(current.username(),currentPassword);
        if(verified==null)
            throw new IllegalArgumentException("Current password is incorrect.");

        Store store=loadStore();
        Record old=requireRecord(store,normalize(current.username()));

        Record updated=newRecord(
                old.username,
                old.displayName,
                old.role,
                old.permissions,
                old.enabled,
                newPassword
        );

        store.records.put(old.username,updated);
        saveStore(store);
        SessionManager.login(publicAccount(updated));
        AuditService.record("Changed own password");
    }

    public static synchronized void resetPassword(
            String username,
            char[] newPassword
    ){
        AuthorizationService.require(Permission.MANAGE_USERS);
        validatePassword(newPassword);

        Store store=loadStore();
        String key=normalize(username);
        Record old=requireRecord(store,key);

        Record updated=newRecord(
                old.username,
                old.displayName,
                old.role,
                old.permissions,
                old.enabled,
                newPassword
        );
        store.records.put(key,updated);
        saveStore(store);
        AuditService.record("Reset password for user "+key);
    }

    public static synchronized void deleteUser(String username){
        AuthorizationService.require(Permission.MANAGE_USERS);

        Store store=loadStore();
        String key=normalize(username);
        Record old=requireRecord(store,key);

        if(old.role==UserRole.ADMINISTRATOR
                &&old.enabled
                &&countEnabledAdmins(store)<=1)
            throw new IllegalArgumentException(
                    "The final enabled administrator cannot be deleted."
            );

        UserAccount current=SessionManager.currentUser();
        if(current!=null&&current.username().equalsIgnoreCase(key))
            throw new IllegalArgumentException(
                    "The currently signed-in account cannot delete itself."
            );

        store.records.remove(key);
        saveStore(store);
        AuditService.record("Deleted user "+key);
    }

    private static int countEnabledAdmins(Store store){
        int count=0;
        for(Record r:store.records.values())
            if(r.enabled&&r.role==UserRole.ADMINISTRATOR)count++;
        return count;
    }

    private static Record requireRecord(Store store,String key){
        Record record=store.records.get(key);
        if(record==null)throw new IllegalArgumentException("User was not found.");
        return record;
    }

    private static Record newRecord(
            String username,
            String displayName,
            UserRole role,
            Set<Permission> permissions,
            boolean enabled,
            char[] password
    ){
        byte[] salt=new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        try{
            byte[] hash=derive(password,salt,ITERATIONS);
            Set<Permission> effective=role==UserRole.ADMINISTRATOR
                    ?EnumSet.allOf(Permission.class)
                    :permissionSet(
                            permissions==null
                                    ?role.defaultPermissions()
                                    :permissions
                    );

            return new Record(
                    username,
                    cleanDisplay(displayName),
                    role,
                    enabled,
                    effective,
                    salt,
                    hash,
                    ITERATIONS
            );
        }catch(GeneralSecurityException ex){
            Arrays.fill(salt,(byte)0);
            throw new IllegalStateException("Password hashing is unavailable.",ex);
        }
    }

    private static EnumSet<Permission> permissionSet(
            Collection<Permission> permissions
    ){
        EnumSet<Permission> set=EnumSet.noneOf(Permission.class);
        if(permissions!=null)set.addAll(permissions);
        return set;
    }

    private static void validatePassword(char[] password){
        if(password==null||password.length<MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException(
                    "Password must be at least "
                    +MIN_PASSWORD_LENGTH+" characters."
            );
    }

    private static void validateUsername(String username){
        if(!username.matches("[a-z0-9._-]{3,40}"))
            throw new IllegalArgumentException(
                    "Username must be 3-40 characters using letters, numbers, ., _, or -."
            );
    }

    private static String normalize(String username){
        return username==null?"":username.trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanDisplay(String display){
        String value=display==null?"":display.trim();
        return value.length()>80?value.substring(0,80):value;
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

    private static UserAccount publicAccount(Record record){
        return new UserAccount(
                record.username,
                record.displayName,
                record.role,
                record.enabled,
                record.permissions
        );
    }

    private static Store loadStore(){
        Store store=new Store();
        Path path=file();
        if(!Files.isRegularFile(path))return store;

        try{
            SecureFiles.restrictFile(path);
            Properties p=new Properties();
            try(InputStream in=new BufferedInputStream(Files.newInputStream(path))){
                p.load(in);
            }

            int count=Math.max(0,Math.min(250,
                    Integer.parseInt(p.getProperty("count","0"))));

            for(int i=0;i<count;i++){
                String prefix="user."+i+".";
                String username=normalize(p.getProperty(prefix+"username",""));
                if(username.isBlank())continue;

                UserRole role;
                try{
                    role=UserRole.valueOf(
                            p.getProperty(prefix+"role","DISPLAY")
                    );
                }catch(Exception ex){
                    role=UserRole.DISPLAY;
                }

                EnumSet<Permission> permissions=EnumSet.noneOf(Permission.class);
                for(String token:p.getProperty(prefix+"permissions","").split(",")){
                    try{
                        if(!token.isBlank())
                            permissions.add(Permission.valueOf(token.trim()));
                    }catch(Exception ignored){}
                }

                byte[] salt=Base64.getDecoder().decode(
                        p.getProperty(prefix+"salt",""));
                byte[] hash=Base64.getDecoder().decode(
                        p.getProperty(prefix+"hash",""));
                int iterations=Integer.parseInt(
                        p.getProperty(prefix+"iterations","0"));

                if(salt.length<16||hash.length<32
                        ||iterations<100_000||iterations>2_000_000)
                    continue;

                /*
                 * Role-template migration: existing Management accounts from
                 * releases before Employee Operations should inherit the new
                 * management personnel privileges without requiring every site
                 * to recreate its users. CUSTOM accounts remain untouched.
                 */
                if(role==UserRole.MANAGEMENT
                        &&permissions.contains(Permission.EMPLOYEE_INFORMATION)){
                    permissions.add(Permission.EMPLOYEE_OPERATIONS);
                    permissions.add(Permission.EMPLOYEE_TRAINING);
                    permissions.add(Permission.EMPLOYEE_ATTENDANCE);
                    permissions.add(Permission.EMPLOYEE_PERFORMANCE);
                    permissions.add(Permission.EMPLOYEE_SCHEDULING);
                    permissions.add(Permission.CALL_IN_ADMINISTRATION);
                }

                store.records.put(
                        username,
                        new Record(
                                username,
                                cleanDisplay(p.getProperty(prefix+"displayName","")),
                                role,
                                Boolean.parseBoolean(
                                        p.getProperty(prefix+"enabled","true")),
                                role==UserRole.ADMINISTRATOR
                                        ?EnumSet.allOf(Permission.class)
                                        :permissions,
                                salt,
                                hash,
                                iterations
                        )
                );
            }
        }catch(Exception ex){
            System.err.println("User account data could not be loaded.");
        }

        return store;
    }

    private static void saveStore(Store store){
        Properties p=new Properties();
        var records=store.records.values().stream()
                .sorted(Comparator.comparing(r->r.username))
                .toList();

        p.setProperty("version","1");
        p.setProperty("count",Integer.toString(records.size()));

        for(int i=0;i<records.size();i++){
            Record r=records.get(i);
            String prefix="user."+i+".";
            p.setProperty(prefix+"username",r.username);
            p.setProperty(prefix+"displayName",r.displayName);
            p.setProperty(prefix+"role",r.role.name());
            p.setProperty(prefix+"enabled",Boolean.toString(r.enabled));
            p.setProperty(
                    prefix+"permissions",
                    r.permissions.stream()
                            .map(Enum::name)
                            .sorted()
                            .reduce((a,b)->a+","+b)
                            .orElse("")
            );
            p.setProperty(prefix+"iterations",Integer.toString(r.iterations));
            p.setProperty(prefix+"salt",Base64.getEncoder().encodeToString(r.salt));
            p.setProperty(prefix+"hash",Base64.getEncoder().encodeToString(r.hash));
        }

        try{
            SecureFiles.storePropertiesAtomic(
                    file(),
                    p,
                    "North Star local user accounts"
            );
        }catch(IOException ex){
            throw new IllegalStateException("Unable to save user accounts.",ex);
        }
    }

    private static final class Store{
        final Map<String,Record> records=new LinkedHashMap<>();
    }

    private record Record(
            String username,
            String displayName,
            UserRole role,
            boolean enabled,
            Set<Permission> permissions,
            byte[] salt,
            byte[] hash,
            int iterations
    ){}
}
