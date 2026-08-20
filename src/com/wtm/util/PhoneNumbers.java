package com.wtm.util;

/**
 * Central telephone-number normalization for UI data and provider integrations.
 *
 * Employee-facing entry remains human friendly while outbound telephony APIs
 * receive canonical E.164 values. U.S. 10-digit numbers automatically receive
 * the +1 country code. Already-valid international E.164 values are preserved.
 */
public final class PhoneNumbers {
    private PhoneNumbers(){}

    /**
     * Converts a supported phone entry into E.164.
     * Blank input remains blank.
     *
     * Accepted U.S. examples:
     * 123-456-7890
     * (205) 799-9890
     * 205 799 9890
     * 2057999890
     * 1-123-456-7890
     * +12057999890
     */
    public static String toE164(String value){
        String input=value==null?"":value.trim();
        if(input.isBlank())return "";

        boolean explicitPlus=input.startsWith("+");
        String digits=input.replaceAll("\\D","");

        if(digits.length()==10){
            return "+1"+digits;
        }

        if(digits.length()==11&&digits.startsWith("1")){
            return "+"+digits;
        }

        // Generic international E.164: up to 15 digits, no leading zero.
        if(explicitPlus
                &&digits.length()>=8
                &&digits.length()<=15
                &&!digits.startsWith("0")){
            return "+"+digits;
        }

        throw new IllegalArgumentException(
                "Enter a valid phone number. U.S. examples: "
                        +"123-456-7890 or +12057999890."
        );
    }

    /** Friendly display while retaining E.164 internally. */
    public static String formatForDisplay(String value){
        if(value==null||value.isBlank())return "";
        String trimmed=value.trim();
        String digits=trimmed.replaceAll("\\D","");

        if(digits.length()==11&&digits.startsWith("1")){
            String ten=digits.substring(1);
            return "("+ten.substring(0,3)+") "
                    +ten.substring(3,6)+"-"+ten.substring(6);
        }
        if(digits.length()==10){
            return "("+digits.substring(0,3)+") "
                    +digits.substring(3,6)+"-"+digits.substring(6);
        }
        return trimmed;
    }

    public static boolean valid(String value){
        try{
            toE164(value);
            return true;
        }catch(IllegalArgumentException ex){
            return false;
        }
    }
}
