package com.wtm.employee;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

/**
 * Private local Employee Operations repository.
 *
 * Personnel data is deliberately separated from the ordinary dashboard
 * configuration. On platforms that support POSIX permissions, the file is
 * restricted to the North Star OS account. Call-in PINs are salted PBKDF2 hashes;
 * plaintext PINs are never persisted.
 */
public final class EmployeeStore {
    private static final String FILE_NAME="employee_operations.properties";
    private static final String PIN_ALGORITHM="PBKDF2WithHmacSHA256";
    private static final int PIN_ITERATIONS=180_000;
    private static final int PIN_BITS=256;
    private static final int PIN_SALT_BYTES=16;

    private EmployeeStore(){}

    private static Path file(){
        return ConfigService.appDataDir().resolve(FILE_NAME);
    }

    public static synchronized Snapshot load(){
        Snapshot snapshot=new Snapshot();
        Path path=file();
        if(!Files.isRegularFile(path))return snapshot;

        try{
            SecureFiles.restrictFile(path);
            Properties p=new Properties();
            try(InputStream in=new BufferedInputStream(Files.newInputStream(path))){
                p.load(in);
            }

            int ec=count(p,"employee.count",0,2000);
            for(int i=0;i<ec;i++){
                String x="employee."+i+".";
                snapshot.employees.add(new EmployeeProfile(
                        p.getProperty(x+"id",""),
                        p.getProperty(x+"number",""),
                        p.getProperty(x+"name",""),
                        p.getProperty(x+"shortName",""),
                        p.getProperty(x+"department",""),
                        p.getProperty(x+"shift",""),
                        date(p.getProperty(x+"hireDate","")),
                        monthDay(p.getProperty(x+"birthday","")),
                        p.getProperty(x+"phone",""),
                        p.getProperty(x+"photoAsset",""),
                        bool(p,x+"active",true),
                        bool(p,x+"celebrationAnnouncements",true),
                        bool(p,x+"showBirthday",true),
                        bool(p,x+"showAnniversary",true),
                        bool(p,x+"employeeOfMonth",false),
                        p.getProperty(x+"pinSalt",""),
                        p.getProperty(x+"pinHash",""),
                        integer(p,x+"pinIterations",0)
                ));
            }

            int tc=count(p,"training.count",0,10000);
            for(int i=0;i<tc;i++){
                String x="training."+i+".";
                snapshot.training.add(new TrainingRecord(
                        p.getProperty(x+"id",""),
                        p.getProperty(x+"employeeId",""),
                        p.getProperty(x+"category",""),
                        p.getProperty(x+"qualification",""),
                        date(p.getProperty(x+"completedDate","")),
                        date(p.getProperty(x+"expirationDate","")),
                        p.getProperty(x+"trainer",""),
                        p.getProperty(x+"status","ACTIVE"),
                        decode(p.getProperty(x+"notes",""))
                ));
            }

            int ac=count(p,"attendance.count",0,50000);
            for(int i=0;i<ac;i++){
                String x="attendance."+i+".";
                snapshot.attendance.add(new AttendanceRecord(
                        p.getProperty(x+"id",""),
                        p.getProperty(x+"employeeId",""),
                        date(p.getProperty(x+"date","")),
                        time(p.getProperty(x+"time","")),
                        p.getProperty(x+"type",""),
                        p.getProperty(x+"source",""),
                        p.getProperty(x+"status","RECORDED"),
                        decode(p.getProperty(x+"notes","")),
                        p.getProperty(x+"callerPhone",""),
                        p.getProperty(x+"externalReference","")
                ));
            }

            int pc=count(p,"performance.count",0,50000);
            for(int i=0;i<pc;i++){
                String x="performance."+i+".";
                snapshot.performance.add(new PerformanceRecord(
                        p.getProperty(x+"id",""),
                        p.getProperty(x+"employeeId",""),
                        date(p.getProperty(x+"date","")),
                        p.getProperty(x+"metric",""),
                        number(p,x+"value",0),
                        number(p,x+"target",Double.NaN),
                        p.getProperty(x+"unit",""),
                        p.getProperty(x+"source",""),
                        decode(p.getProperty(x+"notes",""))
                ));
            }

            int dc=count(p,"duty.count",0,500);
            for(int i=0;i<dc;i++){
                String x="duty."+i+".";
                snapshot.duties.add(new DutyRequirement(
                        p.getProperty(x+"duty",""),
                        p.getProperty(x+"qualification",""),
                        Math.max(1,integer(p,x+"requiredCount",1))
                ));
            }
        }catch(Exception ex){
            throw new IllegalStateException(
                    "Employee Operations data could not be loaded.",ex);
        }

        return snapshot;
    }

    public static synchronized void save(Snapshot snapshot){
        Objects.requireNonNull(snapshot);
        Properties p=new Properties();

        p.setProperty("employee.count",Integer.toString(snapshot.employees.size()));
        for(int i=0;i<snapshot.employees.size();i++){
            EmployeeProfile e=snapshot.employees.get(i);
            String x="employee."+i+".";
            p.setProperty(x+"id",safe(e.id()));
            p.setProperty(x+"number",safe(e.employeeNumber()));
            p.setProperty(x+"name",safe(e.name()));
            p.setProperty(x+"shortName",safe(e.shortName()));
            p.setProperty(x+"department",safe(e.department()));
            p.setProperty(x+"shift",safe(e.shift()));
            p.setProperty(x+"hireDate",text(e.hireDate()));
            p.setProperty(x+"birthday",e.birthday()==null?"":e.birthday().toString());
            p.setProperty(x+"phone",safe(e.phone()));
            p.setProperty(x+"photoAsset",safe(e.photoAsset()));
            p.setProperty(x+"active",Boolean.toString(e.active()));
            p.setProperty(
                    x+"celebrationAnnouncements",
                    Boolean.toString(e.celebrationAnnouncements()));
            p.setProperty(x+"showBirthday",Boolean.toString(e.showBirthday()));
            p.setProperty(x+"showAnniversary",Boolean.toString(e.showAnniversary()));
            p.setProperty(x+"employeeOfMonth",Boolean.toString(e.employeeOfMonth()));
            p.setProperty(x+"pinSalt",safe(e.pinSalt()));
            p.setProperty(x+"pinHash",safe(e.pinHash()));
            p.setProperty(x+"pinIterations",Integer.toString(e.pinIterations()));
        }

        p.setProperty("training.count",Integer.toString(snapshot.training.size()));
        for(int i=0;i<snapshot.training.size();i++){
            TrainingRecord r=snapshot.training.get(i);
            String x="training."+i+".";
            p.setProperty(x+"id",safe(r.id()));
            p.setProperty(x+"employeeId",safe(r.employeeId()));
            p.setProperty(x+"category",safe(r.category()));
            p.setProperty(x+"qualification",safe(r.qualification()));
            p.setProperty(x+"completedDate",text(r.completedDate()));
            p.setProperty(x+"expirationDate",text(r.expirationDate()));
            p.setProperty(x+"trainer",safe(r.trainer()));
            p.setProperty(x+"status",safe(r.status()));
            p.setProperty(x+"notes",encode(r.notes()));
        }

        p.setProperty("attendance.count",Integer.toString(snapshot.attendance.size()));
        for(int i=0;i<snapshot.attendance.size();i++){
            AttendanceRecord r=snapshot.attendance.get(i);
            String x="attendance."+i+".";
            p.setProperty(x+"id",safe(r.id()));
            p.setProperty(x+"employeeId",safe(r.employeeId()));
            p.setProperty(x+"date",text(r.date()));
            p.setProperty(x+"time",text(r.time()));
            p.setProperty(x+"type",safe(r.type()));
            p.setProperty(x+"source",safe(r.source()));
            p.setProperty(x+"status",safe(r.status()));
            p.setProperty(x+"notes",encode(r.notes()));
            p.setProperty(x+"callerPhone",safe(r.callerPhone()));
            p.setProperty(x+"externalReference",safe(r.externalReference()));
        }

        p.setProperty("performance.count",Integer.toString(snapshot.performance.size()));
        for(int i=0;i<snapshot.performance.size();i++){
            PerformanceRecord r=snapshot.performance.get(i);
            String x="performance."+i+".";
            p.setProperty(x+"id",safe(r.id()));
            p.setProperty(x+"employeeId",safe(r.employeeId()));
            p.setProperty(x+"date",text(r.date()));
            p.setProperty(x+"metric",safe(r.metric()));
            p.setProperty(x+"value",Double.toString(r.value()));
            p.setProperty(x+"target",Double.toString(r.target()));
            p.setProperty(x+"unit",safe(r.unit()));
            p.setProperty(x+"source",safe(r.source()));
            p.setProperty(x+"notes",encode(r.notes()));
        }

        p.setProperty("duty.count",Integer.toString(snapshot.duties.size()));
        for(int i=0;i<snapshot.duties.size();i++){
            DutyRequirement r=snapshot.duties.get(i);
            String x="duty."+i+".";
            p.setProperty(x+"duty",safe(r.duty()));
            p.setProperty(x+"qualification",safe(r.qualification()));
            p.setProperty(x+"requiredCount",Integer.toString(r.requiredCount()));
        }

        try{
            SecureFiles.storePropertiesAtomic(
                    file(),
                    p,
                    "North Star Employee Operations - private personnel data"
            );
        }catch(IOException ex){
            throw new IllegalStateException(
                    "Employee Operations data could not be saved.",ex);
        }
    }

    public static synchronized EmployeeProfile setPin(
            EmployeeProfile employee,
            char[] pin
    ){
        Objects.requireNonNull(employee);

        if(pin==null||pin.length<4||pin.length>12)
            throw new IllegalArgumentException(
                    "Call-in PIN must be 4-12 digits.");

        for(char c:pin)
            if(!Character.isDigit(c))
                throw new IllegalArgumentException(
                        "Call-in PIN must contain digits only.");

        byte[] salt=new byte[PIN_SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        try{
            byte[] hash=derive(pin,salt,PIN_ITERATIONS);
            return employee.withPin(
                    Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(hash),
                    PIN_ITERATIONS
            );
        }catch(GeneralSecurityException ex){
            throw new IllegalStateException(
                    "Call-in PIN hashing is unavailable.",ex);
        }
    }

    public static boolean verifyPin(EmployeeProfile employee,char[] pin){
        if(employee==null||pin==null
                ||employee.pinSalt().isBlank()
                ||employee.pinHash().isBlank()
                ||employee.pinIterations()<100_000)
            return false;

        byte[] candidate=null;
        try{
            byte[] salt=Base64.getDecoder().decode(employee.pinSalt());
            byte[] expected=Base64.getDecoder().decode(employee.pinHash());
            candidate=derive(pin,salt,employee.pinIterations());
            return MessageDigest.isEqual(candidate,expected);
        }catch(Exception ex){
            return false;
        }finally{
            if(candidate!=null)Arrays.fill(candidate,(byte)0);
        }
    }

    private static byte[] derive(char[] secret,byte[] salt,int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(secret,salt,iterations,PIN_BITS);
        try{
            return SecretKeyFactory.getInstance(PIN_ALGORITHM)
                    .generateSecret(spec).getEncoded();
        }finally{
            spec.clearPassword();
        }
    }

    public static final class Snapshot {
        public final List<EmployeeProfile> employees=new ArrayList<>();
        public final List<TrainingRecord> training=new ArrayList<>();
        public final List<AttendanceRecord> attendance=new ArrayList<>();
        public final List<PerformanceRecord> performance=new ArrayList<>();
        public final List<DutyRequirement> duties=new ArrayList<>();

        public Snapshot copy(){
            Snapshot copy=new Snapshot();
            copy.employees.addAll(employees);
            copy.training.addAll(training);
            copy.attendance.addAll(attendance);
            copy.performance.addAll(performance);
            copy.duties.addAll(duties);
            return copy;
        }
    }

    private static int count(
            Properties p,
            String key,
            int fallback,
            int max
    ){
        return Math.max(
                0,
                Math.min(max,integer(p,key,fallback))
        );
    }

    private static int integer(Properties p,String key,int fallback){
        try{return Integer.parseInt(p.getProperty(key,Integer.toString(fallback)));}
        catch(Exception ex){return fallback;}
    }

    private static double number(
            Properties p,
            String key,
            double fallback
    ){
        try{return Double.parseDouble(
                p.getProperty(key,Double.toString(fallback)));}
        catch(Exception ex){return fallback;}
    }

    private static boolean bool(
            Properties p,
            String key,
            boolean fallback
    ){
        return Boolean.parseBoolean(
                p.getProperty(key,Boolean.toString(fallback)));
    }

    private static LocalDate date(String value){
        try{return value==null||value.isBlank()?null:LocalDate.parse(value);}
        catch(Exception ex){return null;}
    }

    private static LocalTime time(String value){
        try{return value==null||value.isBlank()?null:LocalTime.parse(value);}
        catch(Exception ex){return null;}
    }

    private static MonthDay monthDay(String value){
        try{return value==null||value.isBlank()?null:MonthDay.parse(value);}
        catch(Exception ex){return null;}
    }

    private static String text(Object value){
        return value==null?"":value.toString();
    }

    private static String safe(String value){
        return value==null?"":value.trim();
    }

    private static String encode(String value){
        if(value==null||value.isBlank())return "";
        return Base64.getEncoder().encodeToString(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value){
        try{
            return new String(
                    Base64.getDecoder().decode(value),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        }catch(Exception ex){
            return "";
        }
    }
}
