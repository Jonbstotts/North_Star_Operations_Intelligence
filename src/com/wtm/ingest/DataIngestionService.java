package com.wtm.ingest;

import com.wtm.firstparty.NetworkLocationStore;
import com.wtm.firstparty.TrackerTelemetryStore;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.MessageDigest;import java.time.LocalDateTime;import java.util.*;import java.util.prefs.Preferences;

public final class DataIngestionService {
 public enum Type { DEALER_NETWORK, TRACKER_TELEMETRY, LHY_KPI, UNKNOWN }
 private static final DataIngestionService INSTANCE=new DataIngestionService();
 private final Path root=Path.of(System.getProperty("user.home"),".northstar-operations-intelligence","ingestion"), incoming=root.resolve("incoming"),archive=root.resolve("archive"),quarantine=root.resolve("quarantine"),history=root.resolve("import-history.csv");
 private final Preferences prefs=Preferences.userRoot().node("com/wtm/northstar/ingestion");
 private DataIngestionService(){try{Files.createDirectories(incoming);Files.createDirectories(archive);Files.createDirectories(quarantine);}catch(IOException ignored){}}
 public static DataIngestionService get(){return INSTANCE;}
 public Path incoming(){return incoming;} public boolean watchEnabled(){return prefs.getBoolean("watchEnabled",true);} public void setWatchEnabled(boolean v){prefs.putBoolean("watchEnabled",v);} public boolean autoImport(){return prefs.getBoolean("autoImport",true);} public void setAutoImport(boolean v){prefs.putBoolean("autoImport",v);}
 public String enterpriseStatus(){return "Microsoft 365 / Graph — Enterprise Ready · Requires IT Authorization";}
 public synchronized IngestionRecord importFile(Path file,String source)throws Exception{
   if(file==null||!Files.isRegularFile(file))throw new IOException("File not found."); String name=file.getFileName().toString(); if(!name.toLowerCase(Locale.ROOT).endsWith(".csv"))return quarantine(file,source,"Only CSV files are enabled in this prototype.");
   String hash=sha256(file); for(IngestionRecord r:history())if(hash.equals(r.sha256())&&"IMPORTED".equals(r.status()))return append(source,name,hash,r.detectedType(),"DUPLICATE",0,"Exact attachment/file already imported.",r.archivedPath());
   Type type=detect(file); if(type==Type.UNKNOWN)return quarantine(file,source,"CSV schema was not recognized; held for review.");
   int rows=0; String msg="";
   if(type==Type.DEALER_NETWORK){rows=NetworkLocationStore.get().importCsv(file);msg="Dealer/logistics network updated.";}
   else if(type==Type.TRACKER_TELEMETRY){rows=TrackerTelemetryStore.get().importCsv(file);msg="Tracker telemetry imported.";}
   else if(type==Type.LHY_KPI){Path target=Path.of(System.getProperty("user.home"),".northstar-operations-intelligence","kpi-history.csv");Files.createDirectories(target.getParent());mergeCsv(file,target);rows=Math.max(0,Files.readAllLines(file).size()-1);msg="LHY/KPI history merged.";}
   Path dest=unique(archive,name);Files.copy(file,dest,StandardCopyOption.REPLACE_EXISTING);return append(source,name,hash,type.name(),"IMPORTED",rows,msg,dest.toString());
 }
 public Type detect(Path p)throws IOException{String h=Files.lines(p,StandardCharsets.UTF_8).findFirst().orElse("").toLowerCase(Locale.ROOT).replace(" ","");if((h.contains("dealer")||h.contains("customerbk"))&&(h.contains("address")||h.contains("latitude")||h.contains("city")))return Type.DEALER_NETWORK;if(h.contains("deviceid")&&h.contains("timestamp")&&h.contains("latitude")&&h.contains("longitude"))return Type.TRACKER_TELEMETRY;if(h.contains("lhy")||h.contains("laborhouryield")||(h.contains("date")&&h.contains("picks")&&h.contains("moves")))return Type.LHY_KPI;return Type.UNKNOWN;}
 public synchronized List<IngestionRecord> scanIncoming(){List<IngestionRecord> out=new ArrayList<>();if(!watchEnabled())return out;try(var s=Files.list(incoming)){for(Path p:s.filter(Files::isRegularFile).toList())try{if(autoImport()){out.add(importFile(p,"Watched Folder"));Files.deleteIfExists(p);}}catch(Exception e){out.add(append("Watched Folder",p.getFileName().toString(),"",Type.UNKNOWN.name(),"FAILED",0,e.getMessage(),""));}}catch(Exception ignored){}return out;}
 public synchronized List<IngestionRecord> history(){List<IngestionRecord> out=new ArrayList<>();if(!Files.isRegularFile(history))return out;try{List<String> ls=Files.readAllLines(history,StandardCharsets.UTF_8);for(int i=1;i<ls.size();i++){String[] a=ls.get(i).split("\\|",-1);if(a.length>=10)out.add(new IngestionRecord(a[0],a[1],a[2],a[3],a[4],a[5],a[6],parseInt(a[7]),a[8],a[9]));}}catch(Exception ignored){}return out;}
 private IngestionRecord quarantine(Path file,String source,String reason)throws Exception{String hash=sha256(file),name=file.getFileName().toString();Path d=unique(quarantine,name);Files.copy(file,d,StandardCopyOption.REPLACE_EXISTING);return append(source,name,hash,Type.UNKNOWN.name(),"REVIEW",0,reason,d.toString());}
 private IngestionRecord append(String source,String name,String hash,String type,String status,int rows,String msg,String path){IngestionRecord r=new IngestionRecord(UUID.randomUUID().toString(),LocalDateTime.now().toString(),clean(source),clean(name),hash,clean(type),clean(status),rows,clean(msg),clean(path));try{Files.createDirectories(root);boolean fresh=!Files.exists(history);try(BufferedWriter w=Files.newBufferedWriter(history,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){if(fresh)w.write("Id|ReceivedAt|Source|File|SHA256|Type|Status|Records|Message|ArchivedPath\n");w.write(String.join("|",r.id(),r.receivedAt(),r.source(),r.originalName(),r.sha256(),r.detectedType(),r.status(),String.valueOf(r.records()),r.message(),r.archivedPath()));w.newLine();}}catch(Exception ignored){}return r;}
 private static void mergeCsv(Path src,Path target)throws IOException{List<String> in=Files.readAllLines(src,StandardCharsets.UTF_8);if(in.isEmpty())return;if(!Files.exists(target)){Files.copy(src,target);return;}List<String> existing=Files.readAllLines(target,StandardCharsets.UTF_8);Set<String> set=new LinkedHashSet<>(existing);for(int i=1;i<in.size();i++)if(!in.get(i).isBlank())set.add(in.get(i));Files.write(target,set,StandardCharsets.UTF_8);}
 private Path unique(Path dir,String name)throws IOException{Files.createDirectories(dir);String safe=name.replaceAll("[^A-Za-z0-9._-]","_");Path p=dir.resolve(safe);if(!Files.exists(p))return p;int dot=safe.lastIndexOf('.');String b=dot>0?safe.substring(0,dot):safe,e=dot>0?safe.substring(dot):"";return dir.resolve(b+"-"+System.currentTimeMillis()+e);}
 private static String sha256(Path p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>0;)md.update(b,0,n);}return java.util.HexFormat.of().formatHex(md.digest());}
 private static String clean(String s){return s==null?"":s.replace("|","/").replace("\n"," ").replace("\r"," ");}private static int parseInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
}
