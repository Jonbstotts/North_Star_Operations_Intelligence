package com.wtm.security;

/** Central permission boundary used by Settings and protected workflows. */
public final class AuthorizationService {
    private AuthorizationService(){}

    public static boolean allowed(Permission permission){
        UserAccount current=SessionManager.currentUser();
        return current!=null && current.has(permission);
    }

    public static void require(Permission permission){
        if(!allowed(permission))
            throw new SecurityException(
                    "The current user does not have permission for "
                    +permission.display()+"."
            );
    }
}
