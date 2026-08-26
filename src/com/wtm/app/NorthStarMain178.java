package com.wtm.app;

import com.wtm.ui.DataIngestionInjector;
import javax.swing.SwingUtilities;

/** Launch path without delayed TruckTrackingModernizer polling. */
public final class NorthStarMain178 {
    private NorthStarMain178(){}
    public static void main(String[] args){
        AiEnabledMain.main(args);
        SwingUtilities.invokeLater(DataIngestionInjector::start);
    }
}
