package com.wtm.util;

import java.awt.Point;

/** Web Mercator conversion helpers shared by the map and overlay renderers. */
public final class GeoUtils {
    private GeoUtils() {}

    public static double lonToWorldX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (256.0 * (1 << zoom));
    }
    public static double latToWorldY(double lat, int zoom) {
        double sin = Math.sin(Math.toRadians(lat));
        return (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * (256.0 * (1 << zoom));
    }
    public static Point screenPoint(double lat, double lon, double centerLat, double centerLon, int zoom, int width, int height) {
        double cx=lonToWorldX(centerLon,zoom), cy=latToWorldY(centerLat,zoom);
        return new Point((int)Math.round(width/2.0 + lonToWorldX(lon,zoom)-cx), (int)Math.round(height/2.0 + latToWorldY(lat,zoom)-cy));
    }
}
