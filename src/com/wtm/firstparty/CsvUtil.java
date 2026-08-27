package com.wtm.firstparty;
import java.util.*;
final class CsvUtil {
 static String q(Object o){String s=o==null?"":String.valueOf(o);return '"'+s.replace("\"","\"\"")+'"';}
 static List<String> parse(String line){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out;}
 static double d(String s,double def){try{return Double.parseDouble(s.trim());}catch(Exception e){return def;}}
 static int i(String s,int def){try{return Integer.parseInt(s.trim());}catch(Exception e){return def;}}
 static boolean b(String s,boolean def){if(s==null||s.isBlank())return def;return Boolean.parseBoolean(s.trim())||"1".equals(s.trim())||"yes".equalsIgnoreCase(s.trim());}
 static String clean(String s){return s==null?"":s.trim();}
}
