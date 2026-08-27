package com.wtm.app;

import java.lang.reflect.Method;

/**
 * Canonical NorthStar launcher.
 *
 * Runtime services are started once before the workspace is created. Optional
 * services are invoked reflectively so source builds stay tolerant of modules
 * intentionally packaged separately.
 */
public final class NorthStarMainStable {
    private NorthStarMainStable(){}

    public static void main(String[] args){
        invoke("com.wtm.modular.ui.AnnouncementBorderGuard","install");
        invoke("com.wtm.modular.ui.StartupUiGuard","install");
        invoke("com.wtm.modular.ui.WorkspaceUiRecoveryGuard","install");
        invoke("com.wtm.modular.ui.SidebarModulesGlyphGuard","install");
        invoke("com.wtm.modular.core.ModularBootstrap","start");
        invoke("com.wtm.firstparty.NetworkGeocodingService","start");
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
