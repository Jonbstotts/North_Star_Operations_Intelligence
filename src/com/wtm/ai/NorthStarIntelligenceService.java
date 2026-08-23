package com.wtm.ai;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.util.MiniJson;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local-only NorthStar Intelligence engine. */
public final class NorthStarIntelligenceService {
    public record Answer(String text, List<String> sources) {}
    public record Status(boolean online, String detail) {}
    private record Chunk(Path source, String text, int score) {}

    private static final NorthStarIntelligenceService INSTANCE=new NorthStarIntelligenceService();
    private static final Pattern WORD=Pattern.compile("[^a-z0-9]+",Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP=Set.of("the","a","an","and","or","of","to","in","is","are","was","were","what","when","where","who","how","why","for","on","with","this","that","our","we","i","me","my","it","do","does","did","can","could","would");

    private final Path appRoot=Path.of(System.getProperty("user.home"),".northstar-operations-intelligence");
    private final Path aiRoot=appRoot.resolve("ai");
    private final Path knowledgeRoot=aiRoot.resolve("knowledge");
    private final Path settingsFile=aiRoot.resolve("ai.properties");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final List<Runnable> libraryListeners=new CopyOnWriteArrayList<>();

    private volatile String ollamaUrl="http://127.0.0.1:11434";
    private volatile String model="llama3.2:3b";
    private volatile boolean useOperationalData=true;

    private NorthStarIntelligenceService(){loadSettings();ensureDirs();}
    public static NorthStarIntelligenceService get(){return INSTANCE;}
    public String ollamaUrl(){return ollamaUrl;}
    public String model(){return model;}
    public boolean useOperationalData(){return useOperationalData;}

    public synchronized void saveSettings(String url,String modelName,boolean operational){
        ollamaUrl=normalizeUrl(url);
        model=modelName==null||modelName.isBlank()?"llama3.2:3b":modelName.trim();
        useOperationalData=operational;
        ensureDirs();
        Properties p=new Properties();
        p.setProperty("ollama.url",ollamaUrl);p.setProperty("ollama.model",model);p.setProperty("operational.data",Boolean.toString(useOperationalData));
        try(OutputStream out=Files.newOutputStream(settingsFile,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){p.store(out,"NorthStar Intelligence local settings");}catch(IOException ignored){}
    }

    private void loadSettings(){
        try{if(!Files.exists(settingsFile))return;Properties p=new Properties();try(InputStream in=Files.newInputStream(settingsFile)){p.load(in);}ollamaUrl=normalizeUrl(p.getProperty("ollama.url",ollamaUrl));model=p.getProperty("ollama.model",model).trim();useOperationalData=Boolean.parseBoolean(p.getProperty("operational.data","true"));}catch(Exception ignored){}
    }

    public Status testConnection(){
        try{HttpRequest req=HttpRequest.newBuilder(URI.create(ollamaUrl+"/api/tags")).timeout(Duration.ofSeconds(4)).GET().build();HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()>=200&&res.statusCode()<300)return new Status(true,"Ollama online • "+model);return new Status(false,"Ollama returned HTTP "+res.statusCode());}catch(Exception ex){return new Status(false,"Ollama offline • start Ollama and load "+model);}
    }

    public Answer ask(String question)throws Exception{
        String q=question==null?"":question.trim();if(q.isBlank())return new Answer("Ask a question first.",List.of());
        List<Chunk> context=retrieve(q);
        List<String> sources=context.stream().map(c->displaySource(c.source())).distinct().limit(10).toList();
        StringBuilder evidence=new StringBuilder();for(Chunk c:context)evidence.append("\n--- SOURCE: ").append(displaySource(c.source())).append(" ---\n").append(c.text()).append('\n');
        if(context.isEmpty())evidence.append("\nNo matching NorthStar records or uploaded documents were found.\n");
        String employeeRule=AuthorizationService.allowed(Permission.AI_EMPLOYEE_METRICS)?"The signed-in user is authorized for employee metrics when those records appear in context.":"Do not provide employee-specific performance, attendance, training, or personnel metrics. The signed-in user is not authorized for them.";
        String prompt="You are NorthStar Intelligence, the local operations assistant inside North Star Operations Intelligence.\nAnswer using ONLY the supplied NORTHSTAR CONTEXT. Do not use outside facts to fill gaps.\nIf the context is insufficient, say what information is missing rather than guessing.\nPreserve dates, units, locations, statuses, and numerical values exactly when possible.\nFor calculations or trends, explain the basis briefly and do not invent missing rows.\nFor possible causes/correlations, explicitly distinguish correlation from proven causation.\nCite factual claims using [Source: filename] using the exact source labels in the context.\nFor policy questions, summarize the policy and encourage the user to open the cited source for authoritative wording.\n"+employeeRule+"\n\nUSER QUESTION:\n"+q+"\n\nNORTHSTAR CONTEXT:\n"+evidence;
        Status status=testConnection();if(!status.online())return new Answer(localFallback(context)+"\n\nLocal AI engine status: "+status.detail(),sources);
        String json="{\"model\":\""+escape(model)+"\",\"stream\":false,\"prompt\":\""+escape(prompt)+"\"}";
        HttpRequest req=HttpRequest.newBuilder(URI.create(ollamaUrl+"/api/generate")).timeout(Duration.ofSeconds(120)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json,StandardCharsets.UTF_8)).build();
        HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("Ollama returned HTTP "+res.statusCode()+". "+res.body());
        Map<String,Object> root=MiniJson.obj(MiniJson.parse(res.body()));String response=MiniJson.str(root.get("response"));if(response.isBlank())response="The local model returned an empty response.";return new Answer(response.trim(),sources);
    }

    private String localFallback(List<Chunk> context){
        if(context.isEmpty())return "I could not find matching information in NorthStar's local data or knowledge library.";
        StringBuilder b=new StringBuilder("I found relevant local records, but Ollama is not currently available to synthesize them.\n\n");for(int i=0;i<Math.min(3,context.size());i++){Chunk c=context.get(i);String text=c.text().replaceAll("\\s+"," ").trim();if(text.length()>300)text=text.substring(0,300)+"…";b.append("• ").append(displaySource(c.source())).append(": ").append(text).append('\n');}return b.toString().trim();
    }

    public synchronized Path importDocument(Path source)throws IOException{
        Objects.requireNonNull(source);ensureDirs();if(!Files.isRegularFile(source))throw new IOException("Select a document file.");String ext=extension(source);if(!Set.of("txt","md","csv","json","log","docx","pdf").contains(ext))throw new IOException("Supported documents: TXT, MD, CSV, JSON, LOG, DOCX, and text-based PDF.");String safe=source.getFileName().toString().replaceAll("[^A-Za-z0-9._ -]","_");Path target=knowledgeRoot.resolve(safe);if(Files.exists(target)){String base=safe,suffix="";int dot=safe.lastIndexOf('.');if(dot>0){base=safe.substring(0,dot);suffix=safe.substring(dot);}int n=2;while(Files.exists(target))target=knowledgeRoot.resolve(base+" ("+(n++)+")"+suffix);}Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES);String text=extractText(target);if(text.isBlank()){Files.deleteIfExists(target);throw new IOException(ext.equals("pdf")?"No readable PDF text was found. This first local build uses the optional 'pdftotext' system utility for PDFs. TXT/MD/CSV/DOCX work without extra software.":"The document did not contain readable text.");}fireLibraryChanged();return target;
    }

    public synchronized void removeDocument(Path file)throws IOException{if(file==null)return;Path normalized=file.toAbsolutePath().normalize();if(!normalized.startsWith(knowledgeRoot.toAbsolutePath().normalize()))throw new IOException("Only AI knowledge-library documents can be removed here.");Files.deleteIfExists(normalized);fireLibraryChanged();}
    public List<Path> knowledgeFiles(){ensureDirs();try(var stream=Files.list(knowledgeRoot)){return stream.filter(Files::isRegularFile).sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase())).toList();}catch(IOException ex){return List.of();}}
    public Path knowledgeRoot(){ensureDirs();return knowledgeRoot;}
    public void addLibraryListener(Runnable r){if(r!=null)libraryListeners.add(r);}public void removeLibraryListener(Runnable r){libraryListeners.remove(r);}private void fireLibraryChanged(){for(Runnable r:libraryListeners)r.run();}

    private List<Chunk> retrieve(String question){
        List<String> terms=terms(question);List<Path> files=new ArrayList<>(knowledgeFiles());if(useOperationalData)files.addAll(operationalFiles());List<Chunk> chunks=new ArrayList<>();Set<Path> seen=new HashSet<>();for(Path file:files){Path norm=file.toAbsolutePath().normalize();if(!seen.add(norm))continue;try{String text=extractText(file);if(text.isBlank())continue;for(String chunk:chunk(text,1400,180)){int score=score(file,chunk,terms);if(score>0)chunks.add(new Chunk(file,chunk,score));}}catch(Exception ignored){}}chunks.sort(Comparator.comparingInt(Chunk::score).reversed());return chunks.stream().limit(10).toList();
    }

    private List<Path> operationalFiles(){
        if(!Files.isDirectory(appRoot))return List.of();boolean employee=AuthorizationService.allowed(Permission.AI_EMPLOYEE_METRICS);List<Path> out=new ArrayList<>();try(var stream=Files.walk(appRoot,3)){stream.filter(Files::isRegularFile).forEach(p->{if(p.startsWith(aiRoot))return;String name=p.getFileName().toString().toLowerCase(Locale.ROOT);String ext=extension(p);if(!Set.of("csv","json","txt","md","log").contains(ext))return;if(name.contains("credential")||name.contains("secret")||name.contains("token")||name.contains("config")||name.contains("user")||name.contains("audit"))return;boolean employeeFile=name.contains("employee")||name.contains("performance")||name.contains("attendance")||name.contains("training")||name.contains("callin")||name.contains("call-in");if(employeeFile&&!employee)return;boolean operations=name.contains("kpi")||name.contains("lhy")||name.contains("shipment")||name.contains("truck")||name.contains("weather")||name.contains("traffic")||name.contains("calendar")||name.contains("event")||name.contains("holiday")||employeeFile;if(operations)out.add(p);});}catch(IOException ignored){}return out;
    }

    private String extractText(Path file)throws IOException{if(Files.size(file)>8_000_000)throw new IOException("Document is too large for the first local AI index (8 MB text limit).");return switch(extension(file)){case "docx"->extractDocx(file);case "pdf"->extractPdf(file);default->Files.readString(file,StandardCharsets.UTF_8);};}
    private String extractDocx(Path file)throws IOException{try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(file))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if("word/document.xml".equals(entry.getName())){String xml=new String(zip.readAllBytes(),StandardCharsets.UTF_8);return xml.replace("</w:p>","\n").replace("</w:tr>","\n").replaceAll("<[^>]+>","").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'");}}}return "";}
    private String extractPdf(Path file)throws IOException{try{Process p=new ProcessBuilder("pdftotext",file.toAbsolutePath().toString(),"-").redirectErrorStream(true).start();byte[] bytes=p.getInputStream().readAllBytes();int code=p.waitFor();if(code==0)return new String(bytes,StandardCharsets.UTF_8);}catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException ignored){}return "";}
    private static List<String> chunk(String text,int size,int overlap){String clean=text.replace("\u0000","").replace("\r","");List<String> out=new ArrayList<>();for(int start=0;start<clean.length();){int end=Math.min(clean.length(),start+size);if(end<clean.length()){int nl=clean.lastIndexOf('\n',end);if(nl>start+size/2)end=nl;}String part=clean.substring(start,end).trim();if(!part.isBlank())out.add(part);if(end>=clean.length())break;start=Math.max(start+1,end-overlap);}return out;}
    private static int score(Path file,String text,List<String> terms){if(terms.isEmpty())return 1;String hay=(file.getFileName()+" "+text).toLowerCase(Locale.ROOT);int score=0;for(String term:terms){int i=0,count=0;while((i=hay.indexOf(term,i))>=0&&count<12){count++;i+=term.length();}score+=count*3;if(file.getFileName().toString().toLowerCase(Locale.ROOT).contains(term))score+=8;}return score;}
    private static List<String> terms(String q){LinkedHashSet<String> out=new LinkedHashSet<>();for(String token:WORD.split(q.toLowerCase(Locale.ROOT)))if(token.length()>2&&!STOP.contains(token))out.add(token);return new ArrayList<>(out);}
    private static String extension(Path p){String name=p.getFileName().toString();int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}private static String displaySource(Path p){return p.getFileName().toString();}
    private static String normalizeUrl(String url){String u=url==null?"":url.trim();if(u.isBlank())u="http://127.0.0.1:11434";while(u.endsWith("/"))u=u.substring(0,u.length()-1);return u;}
    private void ensureDirs(){try{Files.createDirectories(knowledgeRoot);}catch(IOException ignored){}}
    private static String escape(String s){StringBuilder b=new StringBuilder(s.length()+32);for(char c:s.toCharArray())switch(c){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.toString();}
}
