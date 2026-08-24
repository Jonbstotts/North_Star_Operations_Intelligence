package com.wtm.ai;

import com.wtm.config.ConfigService;
import com.wtm.util.MiniJson;
import java.io.*;import java.net.URI;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.time.format.*;import java.util.*;

/** Deterministic LHY analytics sourced from Data Collection operational-data/daily_productivity.csv. */
public final class NorthStarLhyAnalytics {
    public record Point(LocalDate date,double value){}
    public record Trend(List<Point> points,double latest,double recentAverage,double weekAverage,double monthAverage,double target){public boolean available(){return points!=null&&!points.isEmpty();}}
    private static final String SOURCE="daily_productivity.csv";
    private NorthStarLhyAnalytics(){}
    public static boolean matches(String q){String s=q==null?"":q.toLowerCase(Locale.ROOT);return s.contains("lhy")||s.contains("labor hour yield")||s.contains("labour hour yield");}

    public static Trend trend(){
        Path file=ConfigService.appDataDir().resolve("operational-data").resolve(SOURCE);
        if(!Files.isRegularFile(file))return new Trend(List.of(),0,0,0,0,17500);
        Map<LocalDate,Point> rows=new TreeMap<>();
        try{
            List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);if(lines.isEmpty())return new Trend(List.of(),0,0,0,0,17500);
            String[] h=csv(lines.get(0));Map<String,Integer> ix=new HashMap<>();for(int i=0;i<h.length;i++)ix.put(normalize(h[i]),i);
            int di=find(ix,"logdate","date","workdate","completeddate"),li=find(ix,"lhy","laborhouryield","labourhouryield");
            if(di<0||li<0)return new Trend(List.of(),0,0,0,0,17500);
            for(int n=1;n<lines.size();n++){
                String[] c=csv(lines.get(n));String rawDate=get(c,di),rawLhy=get(c,li);
                if(rawDate.isBlank()||rawLhy.isBlank())continue; // partial/in-progress day
                try{LocalDate d=parseDate(rawDate);double v=Double.parseDouble(rawLhy.replace(",","").replace("$","").trim());if(v>0&&Double.isFinite(v))rows.put(d,new Point(d,v));}catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        List<Point> p=new ArrayList<>(rows.values());if(p.isEmpty())return new Trend(List.of(),0,0,0,0,17500);
        LocalDate latestDate=p.get(p.size()-1).date();double latest=p.get(p.size()-1).value();double recent=avg(p.subList(Math.max(0,p.size()-5),p.size()));LocalDate ws=latestDate.minusDays(latestDate.getDayOfWeek().getValue()-1L);double week=avg(p.stream().filter(x->!x.date().isBefore(ws)&&!x.date().isAfter(latestDate)).toList());YearMonth ym=YearMonth.from(latestDate);double month=avg(p.stream().filter(x->YearMonth.from(x.date()).equals(ym)).toList());return new Trend(List.copyOf(p),latest,recent,week,month,17500);
    }

    public static NorthStarIntelligenceService.Answer ask(String question)throws Exception{
        Trend t=trend();
        if(!t.available())return new NorthStarIntelligenceService.Answer("NorthStar does not currently have usable completed-day LHY values in Data Collection's Daily LHY & LPH feed. Import or refresh the Daily LHY & LPH CSV, then ask again. Partial rows with blank LHY are intentionally excluded.",List.of(SOURCE));
        NorthStarIntelligenceService svc=NorthStarIntelligenceService.get();NorthStarIntelligenceService.Status st=svc.testConnection();if(!st.online())return new NorthStarIntelligenceService.Answer("LHY operational history is available, but the local AI model is not ready: "+st.detail(),List.of(SOURCE));
        StringBuilder data=new StringBuilder();for(Point p:t.points())data.append(p.date()).append(',').append(String.format(Locale.US,"%.1f",p.value())).append('\n');
        String prompt="You are NorthStar Intelligence. Answer the user's LHY question using ONLY the verified Data Collection metrics below. The dashboard KPI/Main Showcase is presentation-only and is NOT an analytical source. Do not mention truck tracking or unrelated files. Clearly state latest completed LHY, recent 5-day average, current-week average, current-month average, and compare them with the 17,500 target. Describe recent direction only when supported. Do not invent causes. Blank LHY rows are partial/in-progress and are excluded.\n\nQUESTION: "+question+"\n\nVERIFIED LHY METRICS:\nLatest="+fmt(t.latest())+"\n5-day average="+fmt(t.recentAverage())+"\nWeek average="+fmt(t.weekAverage())+"\nMonth average="+fmt(t.monthAverage())+"\nTarget="+fmt(t.target())+"\n\nCOMPLETED-DAY HISTORY:\n"+data+"\nSource: "+SOURCE;
        String body="{\"model\":\""+esc(svc.model())+"\",\"stream\":false,\"prompt\":\""+esc(prompt)+"\"}";
        HttpRequest req=HttpRequest.newBuilder(URI.create(svc.ollamaUrl()+"/api/generate")).timeout(java.time.Duration.ofSeconds(180)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        HttpResponse<String> res=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("Ollama returned HTTP "+res.statusCode()+": "+res.body());Map<String,Object> root=MiniJson.obj(MiniJson.parse(res.body()));String answer=MiniJson.str(root.get("response"));return new NorthStarIntelligenceService.Answer(answer==null||answer.isBlank()?"The model returned an empty response.":answer.trim(),List.of(SOURCE));
    }

    private static LocalDate parseDate(String s){String x=s.trim();for(DateTimeFormatter f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("M/d/uuuu"),DateTimeFormatter.ofPattern("MM/dd/uuuu"),DateTimeFormatter.ofPattern("M-d-uuuu"))){try{return LocalDate.parse(x,f);}catch(Exception ignored){}}if(x.length()>=10){try{return LocalDate.parse(x.substring(0,10));}catch(Exception ignored){}}throw new DateTimeException("Unsupported date: "+s);}
    private static int find(Map<String,Integer> m,String...names){for(String n:names){Integer i=m.get(n);if(i!=null)return i;}return-1;}private static String normalize(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replace(" ","").replace("_","").replace("-","");}
    private static double avg(List<Point> p){return p.isEmpty()?0:p.stream().mapToDouble(Point::value).average().orElse(0);}private static String fmt(double d){return String.format(Locale.US,"%,.1f",d);}private static String get(String[] a,int i){return i>=0&&i<a.length?a[i].trim():"";}
    private static String[] csv(String line){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='\"'){b.append('\"');i++;}else q=!q;}else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out.toArray(String[]::new);}private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
}
