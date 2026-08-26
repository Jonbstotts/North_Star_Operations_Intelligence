package com.wtm.modular.core;

import com.wtm.modular.ui.ModuleUiCoordinator;
import com.wtm.modular.ui.RuntimeStabilizer;
import com.wtm.ui.MapPinStandardizer;
import com.wtm.ui.foundation.UiFoundationRuntime;
import javax.swing.SwingUtilities;

/** Modular bootstrap without post-paint Employee/Showcase/Appearance injectors. */
public final class ModularBootstrap {
    private ModularBootstrap(){}
    public static void start(){
        Runnable boot=()->{
            ModuleRegistry.get();
            ModuleUiCoordinator.start();
            UiFoundationRuntime.start();
            MapPinStandardizer.start();
            RuntimeStabilizer.start();
        };
        if(SwingUtilities.isEventDispatchThread())boot.run(); else SwingUtilities.invokeLater(boot);
    }
}
