package com.onthegomap.planetiler.shortbread.util;

/**
 * Size-based minimum-zoom helpers ported from the Tilemaker reference ({@code zmin_for_area} / {@code zmin_for_length}
 * in {@code process.lua}). They return the lowest zoom level at which a feature of the given Web-Mercator size reaches
 * the requested on-screen size in tile pixels, so small features are dropped at low zooms.
 * <p>
 * Areas are expected in square meters and lengths in meters, both in the Web-Mercator projection (matching Tilemaker's
 * {@code Area()} / {@code Length()}).
 */
public final class Zooms {

  private Zooms() {}

  /** Earth circumference at the equator in Web Mercator, as used by the Tilemaker formulas. */
  private static final double CIRCUMFERENCE = 40052725.78;
  private static final double LOG2 = Math.log(2);

  public static int zminForArea(double minSquarePixels, double areaSquareMeters) {
    double zmin = Math.log((minSquarePixels * CIRCUMFERENCE * CIRCUMFERENCE) / (Math.pow(2, 16) * areaSquareMeters)) /
      (2 * LOG2);
    return (int) Math.floor(zmin);
  }

  public static int zminForLength(double minLengthPixels, double lengthMeters) {
    double zmin = Math.log((CIRCUMFERENCE * minLengthPixels) / (Math.pow(2, 8) * lengthMeters)) / LOG2;
    return (int) Math.floor(zmin);
  }
}
