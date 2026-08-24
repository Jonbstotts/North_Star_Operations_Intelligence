package com.wtm.ingest;

import com.sun.net.httpserver.HttpServer;
import com.wtm.util.MiniJson;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

/** Development Gmail connector for NorthStar DataPath. OAuth client secrets and refresh tokens stay in local app data only. */
public final class GmailDataPathService {
    public record Status(boolean configured, boolean connected, String detail, String mailbox) {}
    public record SyncResult(int messages, int csvAttachments, int imported, int duplicateOrReview, List<String> notes) {}
    private record OAuthClient(String clientId,String clientSecret,String authUri,String tokenUri) {}

    private static final GmailDataPathService INSTANCE=new GmailDataPathService();
    private static final String SCOPE="https://www.googleapis.com/auth/gmail.readonly";
    private final Path appRoot=Path.of(System.getProperty("user.home"),".northstar-operations-intelligence");
    private final Path credentialsDir=appRoot.resolve("credentials");
    private final Path clientFile=credentialsDir.resolve("gmail-oauth-client.json");
    private final Path tokenFile=credentialsDir.resolve("gmail-token.properties");
    private final Path processedFile=credentialsDir.resolve("gmail-processed.properties");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Preferences prefs=Preferences.userRoot().node("com/wtm/northstar/gmail");

    private GmailDataPathService(){try{Files.createDirectories(credentialsDir);}catch(IOException ignored){}}
    public static GmailDataPathService get(){return INSTANCE;}
    public Path clientConfigPath(){return clientFile;}
    public boolean autoCheck(){return prefs.getBoolean("autoCheck",false);} public void setAutoCheck(boolean v){prefs.putBoolean("autoCheck",v);}
    public int intervalMinutes(){return Math.max(5,prefs.getInt("intervalMinutes",15));} public void setIntervalMinutes(int v){prefs.putInt("intervalMinutes",Math.max(5,v));}
    public String approvedSenders(){return prefs.get("approvedSenders","");} public void setApprovedSenders(String v){prefs.put("approvedSenders",v==null?"":v.trim());}
    public long lastSyncEpoch(){return prefs.getLong("lastSync",0);} private void setLastSync(){prefs.putLong("lastSync",System.currentTimeMillis());}

    public void installClientJson(Path source)throws IOException{
        if(source==null||!Files.isRegularFile(source))throw new IOException("Select the Google OAuth desktop-client JSON file.");
        String text=Files.readString(source,StandardCharsets.UTF_8);
        try{parseClient(text);}catch(Exception ex){throw new IOException("This does not look like a valid Google Desktop OAuth client JSON.",ex);}
        Files.createDirectories(credentialsDir);Files.copy(source,clientFile,StandardCopyOption.REPLACE_EXISTING);
    }

    public Status status(){
        if(!Files.isRegularFile(clientFile))return new Status(false,false,"OAuth client JSON not installed.","");
        if(!Files.isRegularFile(tokenFile))return new Status(true,false,"OAuth client ready • Gmail authorization required.","");
        try{String token=accessToken();Map<String,Object> profile=getJson("https://gmail.googleapis.com/gmail/v1/users/me/profile",token);String email=MiniJson.str(profile.get("emailAddress"));return new Status(true,true,"Connected • read-only Gmail access",email);}catch(Exception ex){return new Status(true,false,"Authorization needs attention • "+safe(ex.getMessage()),"");}
    }

    public String connectInteractive()throws Exception{
        OAuthClient client=loadClient();
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        int port=server.getAddress().getPort();String redirect="http://localhost:"+port+"/oauth2callback";String state=UUID.randomUUID().toString();
        CompletableFuture<String> codeFuture=new CompletableFuture<>();
        server.createContext("/oauth2callback",exchange->{try{Map<String,String> q=query(exchange.getRequestURI().getRawQuery());String html;if(!state.equals(q.get("state"))){html="<h2>NorthStar authorization failed</h2><p>State verification failed.</p>";codeFuture.completeExceptionally(new IOException("OAuth state verification failed."));}else if(q.containsKey("error")){html="<h2>NorthStar Gmail authorization was not completed</h2><p>"+escapeHtml(q.get("error"))+"</p>";codeFuture.completeExceptionally(new IOException("Google authorization returned: "+q.get("error")));}else{String code=q.get("code");html="<h2>NorthStar DataPath connected</h2><p>You may close this browser window and return to NorthStar.</p>";codeFuture.complete(code);}byte[] bytes=html.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");exchange.sendResponseHeaders(200,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}}finally{}});
        server.start();
        String auth=client.authUri()+"?client_id="+enc(client.clientId())+"&redirect_uri="+enc(redirect)+"&response_type=code&scope="+enc(SCOPE)+"&access_type=offline&prompt=consent&state="+enc(state);
        if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(auth));
        else throw new IOException("Open this URL in a browser: "+auth);
        String code;
        try{code=codeFuture.get(180,TimeUnit.SECONDS);}finally{server.stop(0);}if(code==null||code.isBlank())throw new IOException("Google did not return an authorization code.");
        Map<String,Object> token=postForm(client.tokenUri(),Map.of("code",code,"client_id",client.clientId(),"client_secret",client.clientSecret(),"redirect_uri",redirect,"grant_type","authorization_code"));
        saveTokenResponse(token,true);Status status=status();if(!status.connected())throw new IOException(status.detail());return status.mailbox();
    }

    public synchronized SyncResult syncNow()throws Exception{
        String token=accessToken();StringBuilder query=new StringBuilder("has:attachment filename:csv newer_than:60d");String senders=approvedSenders();if(!senders.isBlank()){List<String> parts=new ArrayList<>();for(String s:senders.split("[,;]"))if(!s.isBlank())parts.add("from:"+s.trim());if(!parts.isEmpty())query.append(" (").append(String.join(" OR ",parts)).append(')');}
        String url="https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=100&q="+enc(query.toString());Map<String,Object> root=getJson(url,token);List<Object> msgs=MiniJson.arr(root.get("messages"));int attachments=0,imported=0,other=0;List<String> notes=new ArrayList<>();Properties processed=loadProps(processedFile);
        for(Object obj:msgs){Map<String,Object> m=MiniJson.obj(obj);String id=MiniJson.str(m.get("id"));if(id.isBlank())continue;Map<String,Object> full=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+enc(id)+"?format=full",token);Map<String,Object> payload=MiniJson.obj(full.get("payload"));List<AttachmentRef> refs=new ArrayList<>();collectCsvParts(payload,refs);for(AttachmentRef ref:refs){String key=id+":"+(ref.attachmentId().isBlank()?ref.filename():ref.attachmentId());if("1".equals(processed.getProperty(key)))continue;attachments++;byte[] bytes;if(!ref.data().isBlank())bytes=decode(ref.data());else{Map<String,Object> a=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+enc(id)+"/attachments/"+enc(ref.attachmentId()),token);bytes=decode(MiniJson.str(a.get("data")));}
            Path temp=Files.createTempFile(DataIngestionService.get().incoming(),"gmail-",sanitize(ref.filename()));Files.write(temp,bytes);try{IngestionRecord r=DataIngestionService.get().importFile(temp,"Gmail DataPath");if("IMPORTED".equals(r.status()))imported++;else other++;notes.add(ref.filename()+" → "+r.status()+" / "+r.detectedType());processed.setProperty(key,"1");}finally{Files.deleteIfExists(temp);}
        }}storeProps(processedFile,processed,"NorthStar Gmail processed attachments");setLastSync();return new SyncResult(msgs.size(),attachments,imported,other,notes);
    }

    private record AttachmentRef(String filename,String attachmentId,String data){}
    private static void collectCsvParts(Map<String,Object> part,List<AttachmentRef> out){String name=MiniJson.str(part.get("filename"));Map<String,Object> body=MiniJson.obj(part.get("body"));if(name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".csv"))out.add(new AttachmentRef(name,MiniJson.str(body.get("attachmentId")),MiniJson.str(body.get("data"))));for(Object child:MiniJson.arr(part.get("parts")))collectCsvParts(MiniJson.obj(child),out);}

    private String accessToken()throws Exception{Properties p=loadProps(tokenFile);String refresh=p.getProperty("refresh_token","");long expiry=parseLong(p.getProperty("expires_at","0"));String access=p.getProperty("access_token","");if(!access.isBlank()&&System.currentTimeMillis()+60_000<expiry)return access;if(refresh.isBlank())throw new IOException("No Gmail refresh token is stored. Reconnect Gmail.");OAuthClient c=loadClient();Map<String,Object> res=postForm(c.tokenUri(),Map.of("client_id",c.clientId(),"client_secret",c.clientSecret(),"refresh_token",refresh,"grant_type","refresh_token"));saveTokenResponse(res,false);return loadProps(tokenFile).getProperty("access_token","");}
    private void saveTokenResponse(Map<String,Object> m,boolean initial)throws IOException{Properties p=loadProps(tokenFile);String access=MiniJson.str(m.get("access_token"));String refresh=MiniJson.str(m.get("refresh_token"));long expires=(long)(MiniJson.num(m.get("expires_in"),3600)*1000);if(!access.isBlank())p.setProperty("access_token",access);if(!refresh.isBlank())p.setProperty("refresh_token",refresh);if(initial&&refresh.isBlank()&&p.getProperty("refresh_token","").isBlank())throw new IOException("Google did not return a refresh token. Revoke the app grant and connect again with consent.");p.setProperty("expires_at",Long.toString(System.currentTimeMillis()+expires));storeProps(tokenFile,p,"NorthStar Gmail OAuth tokens • local only");}
    private OAuthClient loadClient()throws Exception{return parseClient(Files.readString(clientFile,StandardCharsets.UTF_8));}
    private static OAuthClient parseClient(String json){Map<String,Object> root=MiniJson.obj(MiniJson.parse(json));Map<String,Object> c=MiniJson.obj(root.get("installed"));if(c.isEmpty())throw new IllegalArgumentException("Desktop 'installed' OAuth client not found.");return new OAuthClient(MiniJson.str(c.get("client_id")),MiniJson.str(c.get("client_secret")),MiniJson.str(c.get("auth_uri")),MiniJson.str(c.get("token_uri")));}
    private Map<String,Object> getJson(String url,String token)throws Exception{HttpRequest req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).GET().build();HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("Gmail API HTTP "+res.statusCode()+" • "+res.body());return MiniJson.obj(MiniJson.parse(res.body()));}
    private Map<String,Object> postForm(String url,Map<String,String> form)throws Exception{StringJoiner j=new StringJoiner("&");for(var e:form.entrySet())j.add(enc(e.getKey())+"="+enc(e.getValue()));HttpRequest req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(j.toString())).build();HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new IOException("OAuth token request HTTP "+res.statusCode()+" • "+res.body());return MiniJson.obj(MiniJson.parse(res.body()));}
    private static Properties loadProps(Path p){Properties x=new Properties();try{if(Files.isRegularFile(p))try(InputStream in=Files.newInputStream(p)){x.load(in);}}catch(Exception ignored){}return x;}
    private static void storeProps(Path p,Properties x,String comment)throws IOException{Files.createDirectories(p.getParent());try(OutputStream out=Files.newOutputStream(p,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){x.store(out,comment);}}
    private static Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw==null)return m;for(String pair:raw.split("&")){int i=pair.indexOf('=');String k=i<0?pair:pair.substring(0,i),v=i<0?"":pair.substring(i+1);m.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));}return m;}
    private static String enc(String s){return URLEncoder.encode(s==null?"":s,StandardCharsets.UTF_8).replace("+","%20");}
    private static byte[] decode(String s){return Base64.getUrlDecoder().decode(s==null?"":s);}
    private static String sanitize(String s){String x=s==null?"attachment.csv":s.replaceAll("[^A-Za-z0-9._-]","_");return x.toLowerCase(Locale.ROOT).endsWith(".csv")?x:"attachment.csv";}
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String safe(String s){if(s==null)return"Unknown error";return s.length()>180?s.substring(0,180)+"…":s;}
    private static String escapeHtml(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
