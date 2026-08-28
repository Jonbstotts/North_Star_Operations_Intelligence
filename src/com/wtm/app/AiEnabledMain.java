package com.wtm.app;

import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.ui.NorthStarIntelligenceCompactPanel;
import com.wtm.ui.NorthStarIntelligencePanel;
import com.wtm.ui.OperationsWorkspaceFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Event-driven Intelligence integration for the canonical workspace.
 *
 * <p>The compact dashboard surface and full workspace route are mounted through
 * the explicit OperationsWorkspaceFrame extension API. The dashboardBody
 * implementation remains private to the workspace; no private-field reflection,
 * component discovery, or first-paint recovery path is required.</p>
 */
public final class AiEnabledMain {
    private static final String COMPACT_NAME="northstar.ai.compact";

    private AiEnabledMain(){}

    public static void injectSidebar(JFrame frame){
        if(!(frame instanceof OperationsWorkspaceFrame workspace)
                ||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;
        workspace.registerWorkspaceExtensionRoute(
                "NorthStar Intelligence",
                "✦  NorthStar Intelligence",
                "northstar.ai.sidebar",
                "Main Showcase",
                true,
                ()->openFull(workspace)
        );
    }

    public static void openFull(JFrame frame){
        if(!(frame instanceof OperationsWorkspaceFrame workspace)
                ||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;
        if(!workspace.showWorkspaceExtensionRoute(
                "NorthStar Intelligence",new NorthStarIntelligencePanel())){
            JOptionPane.showMessageDialog(
                    frame,
                    "Unable to open NorthStar Intelligence.",
                    "NorthStar Intelligence",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Mounts the compact assistant before the dashboard's trailing vertical
     * glue through the bounded workspace extension API. This works before first
     * paint and deliberately does not require Component.isShowing().
     */
    public static void injectDashboard(JFrame frame){
        if(!(frame instanceof OperationsWorkspaceFrame workspace)
                ||!AuthorizationService.allowed(Permission.AI_ASSISTANT))return;

        NorthStarIntelligenceCompactPanel compact=
                new NorthStarIntelligenceCompactPanel(()->openFull(workspace));
        compact.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred=compact.getPreferredSize();
        int height=Math.max(150,preferred==null?150:preferred.height);
        compact.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));

        JPanel mount=new JPanel(new BorderLayout());
        mount.setName(COMPACT_NAME);
        mount.setOpaque(false);
        mount.setBorder(new EmptyBorder(14,0,0,0));
        mount.setAlignmentX(Component.LEFT_ALIGNMENT);
        mount.setMaximumSize(new Dimension(Integer.MAX_VALUE,height+14));
        mount.add(compact,BorderLayout.CENTER);

        workspace.mountDashboardExtension(COMPACT_NAME,mount,Integer.MAX_VALUE-1);
    }


}
