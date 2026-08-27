package com.wtm.callin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.Base64;

/**
 * Minimal Twilio x-www-form-urlencoded webhook signature validator.
 *
 * Twilio signs the exact request URL followed by POST parameter names/values
 * sorted by parameter name using HMAC-SHA1 with the account Auth Token.
 */
public final class TwilioWebhookValidator {
    private TwilioWebhookValidator(){}

    public static boolean valid(
            String authToken,
            String exactUrl,
            Map<String,List<String>> parameters,
            String signature
    ){
        if(authToken==null||authToken.isBlank()
                ||exactUrl==null||exactUrl.isBlank()
                ||signature==null||signature.isBlank())
            return false;

        StringBuilder data=new StringBuilder(exactUrl);

        parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry->{
                    List<String> values=entry.getValue();
                    if(values==null||values.isEmpty()){
                        data.append(entry.getKey());
                    }else{
                        for(String value:values)
                            data.append(entry.getKey())
                                    .append(value==null?"":value);
                    }
                });

        try{
            Mac mac=Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    authToken.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA1"
            ));

            byte[] expected=Base64.getEncoder().encode(
                    mac.doFinal(
                            data.toString().getBytes(StandardCharsets.UTF_8)
                    )
            );
            byte[] supplied=signature.trim()
                    .getBytes(StandardCharsets.US_ASCII);

            return MessageDigest.isEqual(expected,supplied);
        }catch(Exception ex){
            return false;
        }
    }
}
