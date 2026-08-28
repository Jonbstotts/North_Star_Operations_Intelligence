package com.wtm.modular.core;

import com.wtm.ui.foundation.UiFoundationRuntime;
import javax.swing.SwingUtilities;

/**
 * Canonical modular bootstrap.
 *
 * <p>Only active source-backed services are started here. Workspace structural
 * lifecycle is owned by WorkspaceLifecycleV3; historical recovery/coordinator
 * hooks and empty compatibility shells are deliberately excluded.</p>
 */
public final class ModularBootstrap {
    private ModularBootstrap(){}

    public static void start(){
        Runnable boot=()->{
            ModuleRegistry.get();
            UiFoundationRuntime.start();
        };
        if(SwingUtilities.isEventDispatchThread())boot.run(); else SwingUtilities.invokeLater(boot);
    }
}
