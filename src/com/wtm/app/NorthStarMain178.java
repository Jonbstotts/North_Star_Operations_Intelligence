package com.wtm.app;
import com.wtm.ui.TruckTrackingModernizer;
import com.wtm.ui.DataIngestionInjector;
import javax.swing.*;
public final class NorthStarMain178 {
  private NorthStarMain178(){}
  public static void main(String[] args){
    AiEnabledMain.main(args);
    SwingUtilities.invokeLater(TruckTrackingModernizer::start);
    SwingUtilities.invokeLater(DataIngestionInjector::start);
  }
}
