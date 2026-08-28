package com.wtm.modular.core;

import com.wtm.ui.foundation.UiFoundationRuntime;
import javax.swing.SwingUtilities;

/**
 * Canonical runtime bootstrap.
 *
 * <p>Only active source-backed runtime services are started here. Workspace
 * structural lifecycle is owned by WorkspaceLifecycleV3; historical module
 * registries, recovery coordinators, and compatibility shells are deliberately
 * excluded from startup.</p>
 */
public final class ModularBootstrap {
    private ModularBootstrap(){}

    public static void start(){
        Runnable boot=UiFoundationRuntime::start;
        if(SwingUtilities.isEventDispatchThread())boot.run(); else SwingUtilities.invokeLater(boot);
    }
}
