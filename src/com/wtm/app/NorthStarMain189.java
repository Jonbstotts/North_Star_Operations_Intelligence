package com.wtm.app;
import com.wtm.ui.*;
public final class NorthStarMain189{
 public static void main(String[]a){UiConsistencyInjector.start();NorthStarMain178.main(a);javax.swing.SwingUtilities.invokeLater(()->{LocationsNetworkInjector.start();TruckTrackingPolishInjector.start();MainMapNetworkOverlayInjector.start();});}
}
