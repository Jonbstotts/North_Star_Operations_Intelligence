package com.wtm.modular.core;

import com.wtm.ui.MapPinStandardizer;
import com.wtm.ui.foundation.UiFoundationRuntime;
import javax.swing.SwingUtilities;

/**
 * Canonical modular bootstrap.
 *
 * <p>Only source-backed services are started here. The former
 * ModuleUiCoordinatorLifecycle depended on runtime-only reflective classes and
 * is intentionally excluded; workspace structural lifecycle is owned by
 * WorkspaceLifecycleV3.</p>
 */
public final class ModularBootstrap {
    private ModularBootstrap(){}

    public static void start(){
        Runnable boot=()->{
            ModuleRegistry.get();
            UiFoundationRuntime.start();
            MapPinStandardizer.start();
        };
        if(SwingUtilities.isEventDispatchThread())boot.run(); else SwingUtilities.invokeLater(boot);
    }
}
