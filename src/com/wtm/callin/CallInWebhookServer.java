package com.wtm.callin;

import com.sun.net.httpserver.*;
import com.wtm.config.AppConfig;
import com.wtm.employee.*;
import com.wtm.security.AuditService;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Embedded Twilio Voice webhook receiver.
 *
 * Production deployment still needs a public HTTPS endpoint (reverse proxy,
 * tunnel, Twilio Function/proxy, or future North Star web service) that reaches
 * this listener. Incoming Twilio requests are rejected unless their
 * X-Twilio-Signature validates against the configured public URL/Auth Token.
 */
public final class CallInWebhookServer implements AutoCloseable {
    private final AppConfig config;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ConcurrentMap<String,SessionState> sessions=
            new ConcurrentHashMap<>();

    public CallInWebhookServer(AppConfig config) throws IOException {
        this.config=Objects.requireNonNull(config);

        server=HttpServer.create(
                new InetSocketAddress(config.callInWebhookPort),
                20
        );
        executor=Executors.newFixedThreadPool(
                4,
                r->{
                    Thread t=new Thread(r,"northstar-callin-webhook");
                    t.setDaemon(true);
                    return t;
                }
        );
        server.setExecutor(executor);

        register("/callin/voice",Stage.WELCOME);
        register("/callin/employee",Stage.EMPLOYEE);
        register("/callin/pin",Stage.PIN);
        register("/callin/type",Stage.TYPE);
        server.createContext("/callin/health",exchange->{
            byte[] body="North Star call-in webhook online"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type","text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200,body.length);
            try(OutputStream out=exchange.getResponseBody()){
                out.write(body);
            }
        });
    }

    public void start(){
        server.start();
        AuditService.record(
                "Started employee call-in webhook listener on port "
                        +config.callInWebhookPort);
    }

    private void register(String path,Stage stage){
        server.createContext(path,exchange->handle(exchange,path,stage));
    }

    private void handle(
            HttpExchange exchange,
            String path,
            Stage stage
    ) throws IOException {
        if(!"POST".equalsIgnoreCase(exchange.getRequestMethod())){
            respond(exchange,405,"Method Not Allowed","text/plain");
            return;
        }

        byte[] raw=exchange.getRequestBody().readNBytes(64*1024);
        if(raw.length>=64*1024){
            respond(exchange,413,"Request too large","text/plain");
            return;
        }

        Map<String,List<String>> params=parseForm(
                new String(raw,StandardCharsets.UTF_8));

        String signature=exchange.getRequestHeaders()
                .getFirst("X-Twilio-Signature");

        String exactUrl=publicUrl(path);

        if(!TwilioWebhookValidator.valid(
                config.twilioAuthToken,
                exactUrl,
                params,
                signature
        )){
            AuditService.record(
                    "Rejected invalid Twilio call-in webhook signature");
            respond(exchange,403,"Forbidden","text/plain");
            return;
        }

        String callSid=first(params,"CallSid");
        if(callSid.isBlank()){
            respond(exchange,400,"Missing CallSid","text/plain");
            return;
        }

        SessionState state=sessions.computeIfAbsent(
                callSid,
                ignored->new SessionState()
        );

        String twiml=switch(stage){
            case WELCOME->welcome(state);
            case EMPLOYEE->employee(state,params);
            case PIN->pin(state,params);
            case TYPE->type(state,params,callSid);
        };

        respond(exchange,200,twiml,"text/xml; charset=utf-8");
    }

    private String welcome(SessionState state){
        state.employeeId="";
        state.employeeNumber="";
        state.pinAttempts=0;

        return twiml("""
                <Gather input="dtmf" numDigits="12" timeout="8"
                        action="%s/callin/employee" method="POST">
                  <Say>Welcome to the North Star employee call in system.
                  Enter your employee number, then press pound.</Say>
                </Gather>
                <Say>No employee number was received. Goodbye.</Say>
                """.formatted(base()));
    }

    private String employee(
            SessionState state,
            Map<String,List<String>> params
    ){
        String digits=cleanDigits(first(params,"Digits"));
        EmployeeStore.Snapshot snapshot=EmployeeStore.load();
        EmployeeProfile employee=EmployeeService.findByEmployeeNumber(
                snapshot,digits);

        if(employee==null){
            return twiml("""
                    <Say>That employee number was not recognized.</Say>
                    <Redirect method="POST">%s/callin/voice</Redirect>
                    """.formatted(base()));
        }

        state.employeeId=employee.id();
        state.employeeNumber=employee.employeeNumber();

        if(employee.pinHash().isBlank()){
            return twiml("""
                    <Say>Your call in PIN has not been configured.
                    Please contact management. Goodbye.</Say>
                    """);
        }

        return twiml("""
                <Gather input="dtmf" numDigits="12" timeout="8"
                        action="%s/callin/pin" method="POST">
                  <Say>Enter your call in PIN, then press pound.</Say>
                </Gather>
                <Say>No PIN was received. Goodbye.</Say>
                """.formatted(base()));
    }

    private String pin(
            SessionState state,
            Map<String,List<String>> params
    ){
        EmployeeStore.Snapshot snapshot=EmployeeStore.load();
        EmployeeProfile employee=EmployeeService.findById(
                snapshot,state.employeeId);

        if(employee==null)
            return twiml("<Say>Your session expired. Goodbye.</Say>");

        char[] pin=cleanDigits(first(params,"Digits")).toCharArray();
        boolean ok;
        try{
            ok=EmployeeStore.verifyPin(employee,pin);
        }finally{
            Arrays.fill(pin,'\0');
        }

        if(!ok){
            state.pinAttempts++;
            if(state.pinAttempts>=3){
                return twiml("""
                        <Say>Authentication failed. Management has not been
                        notified. Goodbye.</Say>
                        """);
            }

            return twiml("""
                    <Gather input="dtmf" numDigits="12" timeout="8"
                            action="%s/callin/pin" method="POST">
                      <Say>That PIN was incorrect. Please try again.</Say>
                    </Gather>
                    <Say>No PIN was received. Goodbye.</Say>
                    """.formatted(base()));
        }

        return twiml("""
                <Gather input="dtmf" numDigits="1" timeout="10"
                        action="%s/callin/type" method="POST">
                  <Say>Press 1 if you are calling out.
                  Press 2 if you are running late.
                  Press 3 if you are leaving early.
                  Press 4 for another attendance issue.</Say>
                </Gather>
                <Say>No selection was received. Goodbye.</Say>
                """.formatted(base()));
    }

    private String type(
            SessionState state,
            Map<String,List<String>> params,
            String callSid
    ){
        String selection=cleanDigits(first(params,"Digits"));
        if(!Set.of("1","2","3","4").contains(selection)){
            return twiml("""
                    <Gather input="dtmf" numDigits="1" timeout="10"
                            action="%s/callin/type" method="POST">
                      <Say>That selection was not valid.
                      Press 1 if you are calling out.
                      Press 2 if you are running late.
                      Press 3 if you are leaving early.
                      Press 4 for another attendance issue.</Say>
                    </Gather>
                    <Say>No selection was received. Goodbye.</Say>
                    """.formatted(base()));
        }

        EmployeeStore.Snapshot snapshot=EmployeeStore.load();
        EmployeeProfile employee=EmployeeService.findById(
                snapshot,state.employeeId);

        if(employee==null)
            return twiml("<Say>Your session expired. Goodbye.</Say>");

        AttendanceRecord record=EmployeeService.recordCallIn(
                snapshot,
                employee,
                selection,
                "TWILIO_VOICE",
                "",
                first(params,"From"),
                callSid
        );

        CallInNotifier.notifyManagement(config,employee,record);
        sessions.remove(callSid);

        String spoken=switch(record.type()){
            case "CALL_OUT"->"calling out";
            case "RUNNING_LATE"->"running late";
            case "LEAVING_EARLY"->"leaving early";
            default->"another attendance issue";
        };

        return twiml("""
                <Say>Your report of %s has been recorded.
                Thank you. Goodbye.</Say>
                """.formatted(spoken));
    }

    private String publicUrl(String path){
        String base=config.callInPublicBaseUrl==null
                ?""
                :config.callInPublicBaseUrl.trim();
        while(base.endsWith("/"))
            base=base.substring(0,base.length()-1);
        return base+path;
    }

    private String base(){
        return xml(config.callInPublicBaseUrl==null
                ?""
                :config.callInPublicBaseUrl.replaceAll("/+$",""));
    }

    private static String twiml(String body){
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                +"<Response>"+body+"</Response>";
    }

    private static String xml(String value){
        return value==null?"":value
                .replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;")
                .replace("'","&apos;");
    }

    private static Map<String,List<String>> parseForm(String form){
        Map<String,List<String>> out=new LinkedHashMap<>();
        if(form==null||form.isBlank())return out;

        for(String pair:form.split("&")){
            int equals=pair.indexOf('=');
            String key=decode(equals<0?pair:pair.substring(0,equals));
            String value=decode(equals<0?"":pair.substring(equals+1));
            out.computeIfAbsent(key,k->new ArrayList<>()).add(value);
        }
        return out;
    }

    private static String decode(String value){
        return URLDecoder.decode(
                value==null?"":value,
                StandardCharsets.UTF_8
        );
    }

    private static String first(
            Map<String,List<String>> params,
            String key
    ){
        List<String> values=params.get(key);
        return values==null||values.isEmpty()?"":values.get(0);
    }

    private static String cleanDigits(String value){
        return value==null?"":value.replaceAll("[^0-9]","");
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body,
            String contentType
    ) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",contentType);
        exchange.getResponseHeaders().set(
                "Cache-Control","no-store, max-age=0");
        exchange.sendResponseHeaders(status,bytes.length);
        try(OutputStream out=exchange.getResponseBody()){
            out.write(bytes);
        }
    }

    @Override public void close(){
        server.stop(1);
        executor.shutdownNow();
        sessions.clear();
        AuditService.record("Stopped employee call-in webhook listener");
    }

    private enum Stage { WELCOME,EMPLOYEE,PIN,TYPE }

    private static final class SessionState {
        private String employeeId="";
        private String employeeNumber="";
        private int pinAttempts=0;
    }
}
