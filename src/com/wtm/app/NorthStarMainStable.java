package com.wtm.app;

import com.wtm.modular.core.ModularBootstrap;
import com.wtm.modular.ui.TrustedEmailUiGuard;
import com.wtm.modular.ui.WorkspaceLifecycleV3;
import com.wtm.security.LocalSecurityHardening;

/**
 * Canonical NorthStar launcher.
 *
 * Every startup service invoked here is now backed by the canonical source
 * tree. Explicit references intentionally replace the former reflective
 * compatibility launcher so missing source becomes a compile-time failure
 * instead of a silent runtime omission.
 */
public final class NorthStarMainStable {
    private NorthStarMainStable(){}

    public static void main(String[] args){
        LocalSecurityHardening.install();
        TrustedEmailUiGuard.install();
        WorkspaceLifecycleV3.install();
        ModularBootstrap.start();
        Main.main(args);
    }
}
