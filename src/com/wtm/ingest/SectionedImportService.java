package com.wtm.ingest;

import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;

/** Imports one NorthStar bundle containing multiple CSV datasets separated by explicit markers. */
public final class SectionedImportService {
 public record Result(int sections,int imported,int review,List<String>details){}
 private SectionedImportService(){}
 public static Result importBundle(Path file,String source)throws Exception{
  if(file==null||!Files.isRegularFile(file))throw new IOException("Bundle file not found.");
  List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);int sections=0,imported=0,review=0;List<String>details=new ArrayList<>();String id=null;List<String>body=new ArrayList<>();
  for(String line:lines){String t=line.trim();if(t.startsWith("__NORTHSTAR_DATASET__")){if(id!=null&&!body.isEmpty()){int[]r=flush(id,body,source,details);sections++;imported+=r[0];review+=r[1];}String[]p=t.split(",",2);id=p.length>1?p[1].trim():"UNKNOWN";body.clear();}else if(t.equals("__END_DATASET__")){if(id!=null){int[]r=flush(id,body,source,details);sections++;imported+=r[0];review+=r[1];id=null;body.clear();}}else if(id!=null)body.add(line);}
  if(id!=null&&!body.isEmpty()){int[]r=flush(id,body,source,details);sections++;imported+=r[0];review+=r[1];}
  if(sections==0)throw new IOException("No NorthStar dataset markers were found. Expected __NORTHSTAR_DATASET__,<ID> and __END_DATASET__.");return new Result(sections,imported,review,List.copyOf(details));
 }
 private static int[]flush(String id,List<String>body,String source,List<String>details)throws Exception{if(body.isEmpty())return new int[]{0,1};Path tmp=Files.createTempFile("northstar-"+safe(id)+"-",".csv");try{Files.write(tmp,body,StandardCharsets.UTF_8);IngestionRecord r=DataIngestionService.get().importFile(tmp,source+" / "+id);details.add(id+" → "+r.status()+" / "+r.detectedType()+" / "+r.records()+" rows");return new int[]{"IMPORTED".equals(r.status())?1:0,"IMPORTED".equals(r.status())?0:1};}finally{Files.deleteIfExists(tmp);}}
 private static String safe(String s){return s==null?"dataset":s.replaceAll("[^A-Za-z0-9_-]","_");}
}
