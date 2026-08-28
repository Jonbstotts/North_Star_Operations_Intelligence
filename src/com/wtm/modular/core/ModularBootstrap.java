package com.wtm.modular.core;

import com.wtm.modular.ui.ModuleUiCoordinatorLifecycle;
import com.wtm.ui.MapPinStandardizer;
import com.wtm.ui.foundation.UiFoundationRuntime;
import javax.swing.SwingUtilities;

/** Modular bootstrap with event-driven, first-paint sidebar initialization. */
public final class ModularBootstrap {
    private ModularBootstrap(){}
    public static void start(){
        Runnable boot=()->{
            ModuleRegistry.get();
            ModuleUiCoordinatorLifecycle.start();
            UiFoundationRuntime.start();
            MapPinStandardizer.start();
        };
        if(SwingUtilities.isEventDispatchThread())boot.run(); else SwingUtilities.invokeLater(boot);
    }
}
