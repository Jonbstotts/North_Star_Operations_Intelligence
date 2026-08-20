package com.wtm.security;

import java.util.EnumSet;
import java.util.Set;

/** Immutable authenticated-user view; password material never leaves UserService. */
public record UserAccount(
        String username,
        String displayName,
        UserRole role,
        boolean enabled,
        Set<Permission> permissions
) {
    public UserAccount{
        permissions=permissions==null
                ?Set.of()
                :Set.copyOf(permissions);
    }

    public boolean has(Permission permission){
        return enabled && (
                role==UserRole.ADMINISTRATOR
                ||permissions.contains(permission)
        );
    }

    public String friendlyName(){
        return displayName==null||displayName.isBlank()?username:displayName;
    }
}
