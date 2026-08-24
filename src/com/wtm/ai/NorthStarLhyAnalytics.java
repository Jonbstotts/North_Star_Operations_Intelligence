package com.wtm.ai;

import com.wtm.config.ConfigService;
import com.wtm.util.MiniJson;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Deterministic LHY analytics and model synthesis built on NorthStar's persisted KPI history. */
public final class NorthStarLhyAnalytics {
    public record Point(LocalDate date,double value){}
    public record Trend(List<Point> points,double latest,double recentAverage,double weekAverage,double monthAverage,double target){public boolean available(){return points!=null&&!points.isEmpty();}}
    private NorthStarLhyAnalytics(){}
    public static boolean matches(String q){String s=q==null?"":q.toLowerCase(Locale.ROOT);return s.contains("lhy")||s.contains("labor hour yield")||s.contains("labour hour yield");}
    public static Trend trend(){
        Path file=ConfigService.appDataDir().resolve("kpi-history.csv");
        if(!Files.isRegularFile(file))return new Trend(List.of(),0,0,0,0,17500);
        Map<LocalDate,Point> rows=new TreeMap<>();
        try{
            List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8); if(lines.isEmpty())return new Trend(List.of(),0,0,0,0,17500);
            String[] h=csv(lines.get(0));Map<String,Integer> ix=new HashMap<>();for(int i=0;i<h.length;i++)ix.put(h[i].trim().toLowerCase(Locale.ROOT),i);
            int di=ix.getOrDefault("date",0),mi=ix.getOrDefault("metricid",1),li=ix.getOrDefault("label",2),vi=ix.getOrDefault("value",3);
            for(int n=1;n<lines.size();n++){String[] c=csv(lines.get(n));String metric=get(c,mi).toLowerCase(Locale.ROOT),label=get(c,li).toLowerCase(Locale.ROOT);if(!(metric.contains("lhy")||label.contains("lhy")))continue;try{LocalDate d=LocalDate.parse(get(c,di));double v=Double.parseDouble(get(c,vi).replace(",",""));if(v>0&&Double.isFinite(v))rows.put(d,new Point(d,v));}catch(Exception ignored){}}
        }catch(Exception ignored){}
        List<Point> p=new ArrayList<>(rows.values());if(p.isEmpty())return new Trend(List.of(),0,0,0,0,17500);
        LocalDate latestDate=p.get(p.size()-1).date();double latest=p.get(p.size()-1).value();double recent=avg(p.subList(Math.max(0,p.size()-5),p.size()));LocalDate ws=latestDate.minusDays(latestDate.getDayOfWeek().getValue()-1L);double week=avg(p.stream().filter(x->!x.date().isBefore(ws)&&!x.date().isAfter(latestDate)).toList());YearMonth ym=YearMonth.from(latestDate);double month=avg(p.stream().filter(x->YearMonth.from(x.date()).equals(ym)).toList());return new Trend(List.copyOf(p),latest,recent,week,month,17500);
    }
    public static NorthStarIntelligenceService.Answer ask(String question)throws Exception{
        Trend t=trend();if(!t.available())return new NorthStarIntelligenceService.Answer("NorthStar does not currently have usable LHY history in kpi-history.csv. Import LHY KPI history first, then ask again.",List.of("kpi-history.csv"));
        NorthStarIntelligenceService svc=NorthStarIntelligenceService.get();NorthStarIntelligenceService.Status st=svc.testConnection();if(!st.online())return new NorthStarIntelligenceService.Answer("LHY history is available, but the local AI model is not ready: "+st.detail(),List.of("kpi-history.csv"));
        StringBuilder data=new StringBuilder();for(Point p:t.points())data.append(p.date()).append(',').append(String.format(Locale.US,"%.1f",p.value())).append('\n');
        String prompt="You are NorthStar Intelligence. Answer the user's LHY question using ONLY the verified metrics below. Do not mention truck tracking or unrelated files. Be concise but useful. Clearly state latest LHY, recent 5-day average, current-week average, current-month average, and compare them with the 17,500 target. Describe the direction of the recent trend if the data supports it. Do not invent causes.\n\nQUESTION: "+question+"\n\nVERIFIED LHY METRICS:\nLatest="+fmt(t.latest())+"\n5-day average="+fmt(t.recentAverage())+"\nWeek average="+fmt(t.weekAverage())+"\nMonth average="+fmt(t.monthAverage())+"\nTarget="+fmt(t.target())+"\n\nHISTORY:\n"+data+"\nSource: kpi-history.csv";
        String body="{\"model\":\""+esc(svc.model())+"\",\"stream\":false,\"prompt\":\""+esc(prompt)+"\"}";
        HttpRequest req=HttpRequest.newBuilder(URI.create(svc.ollamaUrl()+"/api/generate")).timeout(java.time.Duration.ofSeconds(180)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        HttpResponse<String> res=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("Ollama returned HTTP "+res.statusCode()+": "+res.body());Map<String,Object> root=MiniJson.obj(MiniJson.parse(res.body()));String answer=MiniJson.str(root.get("response"));return new NorthStarIntelligenceService.Answer(answer==null||answer.isBlank()?"The model returned an empty response.":answer.trim(),List.of("kpi-history.csv"));
    }
    private static double avg(List<Point> p){return p.isEmpty()?0:p.stream().mapToDouble(Point::value).average().orElse(0);}private static String fmt(double d){return String.format(Locale.US,"%,.1f",d);}private static String get(String[] a,int i){return i>=0&&i<a.length?a[i].trim():"";}
    private static String[] csv(String line){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='\"'){b.append('\"');i++;}else q=!q;}else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out.toArray(String[]::new);}private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
}
