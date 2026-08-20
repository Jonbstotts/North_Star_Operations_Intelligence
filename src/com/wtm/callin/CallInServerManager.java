package com.wtm.callin;

import com.wtm.config.AppConfig;

import java.util.Objects;

/** Process-wide lifecycle for the optional embedded call-in webhook listener. */
public final class CallInServerManager {
    private static CallInWebhookServer server;
    private static String status="Stopped";

    private CallInServerManager(){}

    public static synchronized void apply(AppConfig config){
        stop();

        if(config==null
                ||!config.callInEnabled
                ||!"TWILIO_WEBHOOK".equalsIgnoreCase(config.callInMode)){
            status="Disabled / local test mode";
            return;
        }

        if(config.callInPublicBaseUrl==null
                ||!config.callInPublicBaseUrl.startsWith("https://")){
            status="Not started: public HTTPS base URL is required";
            return;
        }

        if(config.twilioAuthToken==null
                ||config.twilioAuthToken.isBlank()){
            status="Not started: Twilio Auth Token is required";
            return;
        }

        try{
            server=new CallInWebhookServer(config);
            server.start();
            status="Listening on port "+config.callInWebhookPort;
        }catch(Exception ex){
            server=null;
            status="Start failed: "+ex.getMessage();
        }
    }

    public static synchronized void stop(){
        if(server!=null){
            server.close();
            server=null;
        }
    }

    public static synchronized boolean running(){
        return server!=null;
    }

    public static synchronized String status(){
        return status;
    }
}
