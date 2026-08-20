package com.wtm.ui;

import javax.swing.*;

/** Single product identity for North Star Operations Intelligence. */
public final class BrandIdentity {
    private BrandIdentity(){}

    public static boolean northStar(){ return true; }
    public static String product(){ return NorthStarBrand.PRODUCT_NAME; }
    public static String tagline(){ return NorthStarBrand.TAGLINE; }
    public static ImageIcon symbol(int size){ return NorthStarBrand.symbol(size); }
}
