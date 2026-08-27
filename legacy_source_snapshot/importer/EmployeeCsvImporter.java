package com.wtm.importer;
import com.wtm.employee.*; import com.wtm.util.PhoneNumbers; import java.nio.file.Path; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.*;
/** Bulk employee upsert. EmployeeNumber is the stable match key. Existing IDs, PIN hashes and linked records are preserved. Blank cells preserve existing values. */
public final class EmployeeCsvImporter implements CsvImportHandler<EmployeeStore.Snapshot>{
 public static final String TEMPLATE_HEADER="EmployeeNumber,Name,ShortName,Department,Shift,HireDate,Birthday,Phone,PhotoAsset,Active,CelebrationAnnouncements,ShowBirthday,ShowAnniversary,EmployeeOfMonth";
 public ImportResult importFile(Path file,EmployeeStore.Snapshot snapshot)throws Exception{
  Objects.requireNonNull(snapshot);CsvTable t=CsvTable.read(file);if(!t.headers().contains("employeenumber"))throw new IllegalArgumentException("CSV must include an EmployeeNumber column.");
  Map<String,Integer> existing=new HashMap<>();for(int i=0;i<snapshot.employees.size();i++){String k=key(snapshot.employees.get(i).employeeNumber());if(!k.isBlank())existing.put(k,i);}
  int a=0,u=0,n=0,s=0,rowNo=1;List<String>w=new ArrayList<>();
  for(Map<String,String> row:t.rows()){rowNo++;String num=get(row,"employeenumber");if(num.isBlank()){s++;w.add("Row "+rowNo+": missing EmployeeNumber.");continue;}Integer idx=existing.get(key(num));EmployeeProfile old=idx==null?null:snapshot.employees.get(idx);
   try{EmployeeProfile next=merge(old,row,num);if(old==null){snapshot.employees.add(next);existing.put(key(num),snapshot.employees.size()-1);a++;}else if(old.equals(next))n++;else{snapshot.employees.set(idx,next);u++;}}catch(Exception ex){s++;w.add("Row "+rowNo+" ("+num+"): "+ex.getMessage());}}
  return new ImportResult(t.rows().size(),a,u,n,s,w);
 }
 private static EmployeeProfile merge(EmployeeProfile o,Map<String,String>r,String num){boolean f=o==null;return new EmployeeProfile(
  f?UUID.randomUUID().toString():o.id(),num,text(r,"name",f?"":o.name()),text(r,"shortname",f?"":o.shortName()),text(r,"department",f?"":o.department()),text(r,"shift",f?"":o.shift()),
  date(r,"hiredate",f?null:o.hireDate()),birthday(r,"birthday",f?null:o.birthday()),phone(r,"phone",f?"":o.phone()),text(r,"photoasset",f?"":o.photoAsset()),
  bool(r,"active",f||o.active()),bool(r,"celebrationannouncements",f||o.celebrationAnnouncements()),bool(r,"showbirthday",f||o.showBirthday()),bool(r,"showanniversary",f||o.showAnniversary()),bool(r,"employeeofmonth",!f&&o.employeeOfMonth()),
  f?"":o.pinSalt(),f?"":o.pinHash(),f?0:o.pinIterations());}
 private static String get(Map<String,String>r,String k){return r.getOrDefault(k,"").trim();} private static String text(Map<String,String>r,String k,String old){String v=get(r,k);return v.isBlank()?old:v;}
 private static boolean bool(Map<String,String>r,String k,boolean old){String v=get(r,k);if(v.isBlank())return old;return switch(v.toLowerCase(Locale.ROOT)){case"true","yes","y","1","active"->true;case"false","no","n","0","inactive"->false;default->throw new IllegalArgumentException("invalid "+k+" value '"+v+"'");};}
 private static LocalDate date(Map<String,String>r,String k,LocalDate old){String v=get(r,k);if(v.isBlank())return old;for(DateTimeFormatter f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("M/d/uuuu"),DateTimeFormatter.ofPattern("M-d-uuuu"))){try{return LocalDate.parse(v,f);}catch(Exception ignored){}}throw new IllegalArgumentException("invalid HireDate '"+v+"' (use YYYY-MM-DD or M/D/YYYY)");}
 private static MonthDay birthday(Map<String,String>r,String k,MonthDay old){String v=get(r,k);if(v.isBlank())return old;try{if(v.matches("\\d{4}-\\d{2}-\\d{2}"))return MonthDay.from(LocalDate.parse(v));if(v.matches("\\d{1,2}/\\d{1,2}")){String[]p=v.split("/");return MonthDay.of(Integer.parseInt(p[0]),Integer.parseInt(p[1]));}if(v.matches("--\\d{2}-\\d{2}"))return MonthDay.parse(v);}catch(Exception ignored){}throw new IllegalArgumentException("invalid Birthday '"+v+"' (use M/D or --MM-DD)");}
 private static String phone(Map<String,String>r,String k,String old){String v=get(r,k);return v.isBlank()?old:PhoneNumbers.toE164(v);} private static String key(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);}
}