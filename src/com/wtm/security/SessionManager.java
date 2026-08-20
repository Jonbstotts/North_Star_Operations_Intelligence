package com.wtm.security;

/** Process-local authenticated session for the standalone desktop deployment. */
public final class SessionManager {
    private static volatile UserAccount current;

    private SessionManager(){}

    public static UserAccount currentUser(){ return current; }

    public static boolean isAuthenticated(){
        return current!=null && current.enabled();
    }

    public static void login(UserAccount account){
        current=account;
    }

    public static void logout(){
        current=null;
    }
}
