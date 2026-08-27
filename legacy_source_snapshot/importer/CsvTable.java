package com.wtm.importer;
import java.io.*; import java.nio.charset.StandardCharsets; import java.nio.file.*; import java.util.*;
public final class CsvTable {
 private final List<String> headers; private final List<Map<String,String>> rows;
 private CsvTable(List<String> h,List<Map<String,String>> r){headers=List.copyOf(h);rows=List.copyOf(r);}
 public List<String> headers(){return headers;} public List<Map<String,String>> rows(){return rows;}
 public static CsvTable read(Path path)throws IOException{
  if(path==null||!Files.isRegularFile(path))throw new IOException("Select a valid CSV file.");
  String text=Files.readString(path,StandardCharsets.UTF_8); if(text.startsWith("\uFEFF"))text=text.substring(1);
  List<List<String>> rec=parse(text); if(rec.isEmpty())throw new IOException("CSV file is empty.");
  List<String> h=new ArrayList<>(); Set<String> seen=new HashSet<>();
  for(String raw:rec.get(0)){String x=normalize(raw);if(x.isBlank())throw new IOException("CSV contains a blank column header.");if(!seen.add(x))throw new IOException("Duplicate CSV column: "+raw);h.add(x);}
  List<Map<String,String>> rows=new ArrayList<>();
  for(int r=1;r<rec.size();r++){List<String> vals=rec.get(r);if(vals.stream().allMatch(v->v==null||v.isBlank()))continue;Map<String,String> row=new LinkedHashMap<>();for(int c=0;c<h.size();c++)row.put(h.get(c),c<vals.size()?vals.get(c).trim():"");rows.add(Collections.unmodifiableMap(row));}
  return new CsvTable(h,rows);
 }
 public static String normalize(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT).replace(" ","").replace("_","").replace("-","");}
 private static List<List<String>> parse(String text)throws IOException{
  List<List<String>> out=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder f=new StringBuilder();boolean q=false;
  for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(q){if(ch=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){f.append('"');i++;}else q=false;}else f.append(ch);}else{if(ch=='"')q=true;else if(ch==','){row.add(f.toString());f.setLength(0);}else if(ch=='\n'){row.add(f.toString());f.setLength(0);out.add(row);row=new ArrayList<>();}else if(ch!='\r')f.append(ch);}}
  if(q)throw new IOException("CSV contains an unterminated quoted field.");if(f.length()>0||!row.isEmpty()){row.add(f.toString());out.add(row);}return out;
 }
}