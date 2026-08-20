package com.wtm.callin;

import com.wtm.config.AppConfig;
import com.wtm.employee.*;
import com.wtm.security.AuditService;
import com.wtm.usage.ApiUsageTracker;
import com.wtm.util.PhoneNumbers;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Base64;

/**
 * Sends immediate management notifications after an attendance call-in.
 *
 * No provider SDK is required: Twilio Messaging and SendGrid both expose HTTPS
 * REST endpoints. Credentials are read from AppConfig only after
 * ApiCredentialService has loaded the protected credential store.
 */
public final class CallInNotifier {
    private static final Duration TIMEOUT=Duration.ofSeconds(12);

    private CallInNotifier(){}

    public static NotificationResult notifyManagement(
            AppConfig config,
            EmployeeProfile employee,
            AttendanceRecord record
    ){
        Objects.requireNonNull(config);
        Objects.requireNonNull(employee);
        Objects.requireNonNull(record);

        String message=buildMessage(employee,record);
        List<String> results=new ArrayList<>();

        if(config.callInSmsNotifications){
            for(String phone:split(config.callInSmsRecipients)){
                try{
                    sendSms(config,phone,message);
                    results.add("SMS sent to "+maskPhone(phone));
                }catch(Exception ex){
                    results.add("SMS failed for "+maskPhone(phone));
                }
            }
        }

        if(config.callInEmailNotifications){
            for(String email:split(config.callInEmailRecipients)){
                try{
                    sendEmail(config,email,message);
                    results.add("Email sent to "+maskEmail(email));
                }catch(Exception ex){
                    results.add("Email failed for "+maskEmail(email));
                }
            }
        }

        if(!results.isEmpty())
            AuditService.record(
                    "Call-in notifications: "+String.join("; ",results));

        return new NotificationResult(results);
    }

    private static void sendSms(
            AppConfig config,
            String to,
            String body
    ) throws Exception {
        require(
                !config.twilioAccountSid.isBlank()
                        &&!config.twilioAuthToken.isBlank(),
                "Twilio credentials are incomplete."
        );
        require(
                !config.callInTwilioFromNumber.isBlank(),
                "Twilio from-number is not configured."
        );

        String normalizedTo=PhoneNumbers.toE164(to);
        String normalizedFrom=PhoneNumbers.toE164(
                config.callInTwilioFromNumber);

        String endpoint=
                "https://api.twilio.com/2010-04-01/Accounts/"
                        +url(config.twilioAccountSid)
                        +"/Messages.json";

        String form="To="+url(normalizedTo)
                +"&From="+url(normalizedFrom)
                +"&Body="+url(body);

        String auth=Base64.getEncoder().encodeToString(
                (config.twilioAccountSid+":"+config.twilioAuthToken)
                        .getBytes(StandardCharsets.UTF_8)
        );

        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Authorization","Basic "+auth)
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        ApiUsageTracker.get().record(endpoint);
        HttpResponse<String> response=client().send(
                request,HttpResponse.BodyHandlers.ofString());

        if(response.statusCode()<200||response.statusCode()>=300)
            throw new IllegalStateException(
                    "Twilio Messaging returned HTTP "
                            +response.statusCode()+".");
    }

    private static void sendEmail(
            AppConfig config,
            String to,
            String body
    ) throws Exception {
        require(
                !config.sendGridApiKey.isBlank(),
                "SendGrid API key is not configured."
        );
        require(
                !config.callInEmailFrom.isBlank(),
                "SendGrid sender address is not configured."
        );

        String json="""
                {
                  "personalizations":[{"to":[{"email":"%s"}]}],
                  "from":{"email":"%s"},
                  "subject":"North Star Employee Call-In",
                  "content":[{"type":"text/plain","value":"%s"}]
                }
                """.formatted(
                json(to),
                json(config.callInEmailFrom),
                json(body)
        );

        HttpRequest request=HttpRequest.newBuilder(
                        URI.create("https://api.sendgrid.com/v3/mail/send"))
                .timeout(TIMEOUT)
                .header("Authorization","Bearer "+config.sendGridApiKey)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        ApiUsageTracker.get().record(
                "https://api.sendgrid.com/v3/mail/send");
        HttpResponse<String> response=client().send(
                request,HttpResponse.BodyHandlers.ofString());

        if(response.statusCode()<200||response.statusCode()>=300)
            throw new IllegalStateException(
                    "SendGrid returned HTTP "+response.statusCode()+".");
    }

    private static HttpClient client(){
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String buildMessage(
            EmployeeProfile employee,
            AttendanceRecord record
    ){
        String type=switch(record.type()){
            case "CALL_OUT"->"CALL OUT";
            case "RUNNING_LATE"->"RUNNING LATE";
            case "LEAVING_EARLY"->"LEAVING EARLY";
            default->"OTHER ATTENDANCE EVENT";
        };

        return "North Star Call-In: "
                +employee.name()+" reported "+type
                +" at "+record.time()
                +" on "+record.date()
                +". Source: "+record.source()
                +(record.notes().isBlank()?"":". Notes: "+record.notes());
    }

    private static List<String> split(String value){
        if(value==null||value.isBlank())return List.of();
        return Arrays.stream(value.split("[,;\\n]"))
                .map(String::trim)
                .filter(s->!s.isBlank())
                .distinct()
                .toList();
    }

    private static String url(String value){
        return URLEncoder.encode(
                value==null?"":value,
                StandardCharsets.UTF_8
        );
    }

    private static String json(String value){
        if(value==null)return "";
        return value
                .replace("\\","\\\\")
                .replace("\"","\\\"")
                .replace("\n","\\n")
                .replace("\r","\\r");
    }

    private static String maskPhone(String phone){
        if(phone==null||phone.length()<4)return "••••";
        return "•••"+phone.substring(phone.length()-4);
    }

    private static String maskEmail(String email){
        if(email==null)return "•••";
        int at=email.indexOf('@');
        if(at<1)return "•••";
        return email.substring(0,1)+"•••"+email.substring(at);
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new IllegalStateException(message);
    }

    public record NotificationResult(List<String> results){
        public boolean attempted(){return results!=null&&!results.isEmpty();}
    }
}
