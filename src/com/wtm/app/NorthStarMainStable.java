package com.wtm.app;

import java.lang.reflect.Method;

/**
 * Canonical NorthStar launcher.
 *
 * Only startup services backed by the canonical source tree are invoked here.
 * Optional runtime extensions remain reflective, but stale historical class
 * names are deliberately excluded so a clean source build has one predictable
 * startup path.
 */
public final class NorthStarMainStable {
    private NorthStarMainStable(){}

    public static void main(String[] args){
        invoke("com.wtm.security.LocalSecurityHardening","install");
        invoke("com.wtm.modular.ui.SidebarModulesGlyphGuard","install");
        invoke("com.wtm.modular.ui.TrustedEmailUiGuard","install");
        invoke("com.wtm.modular.ui.DashboardGlyphArtGuard","install");
        invoke("com.wtm.modular.ui.WorkspaceLifecycleV3","install");
        invoke("com.wtm.modular.core.ModularBootstrap","start");
        Main.main(args);
    }

    private static void invoke(String className,String methodName){
        try{
            Class<?> type=Class.forName(className);
            Method method=type.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(null);
        }catch(ClassNotFoundException ignored){
            // Optional module not present in this deployment.
        }catch(ReflectiveOperationException ex){
            System.err.println("NorthStar startup service failed: "+className+"."+methodName+" - "+ex.getMessage());
        }
    }
}
