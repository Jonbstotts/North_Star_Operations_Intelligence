package com.wtm.app;
import com.wtm.ui.TruckTrackingModernizer;
import javax.swing.*;
public final class NorthStarMain178 {
  private NorthStarMain178(){}
  public static void main(String[] args){
    AiEnabledMain.main(args);
    SwingUtilities.invokeLater(TruckTrackingModernizer::start);
  }
}
