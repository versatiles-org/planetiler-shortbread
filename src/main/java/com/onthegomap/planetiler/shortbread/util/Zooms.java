package com.onthegomap.planetiler.shortbread.util;

/**
 * Size-based minimum-zoom helpers ported from the Tilemaker reference ({@code zmin_for_area} / {@code zmin_for_length}
 * in {@code process.lua}). They return the lowest zoom level at which a feature reaches the requested on-screen size in
 * tile pixels, so small features are dropped at low zooms.
 * <p>
 * Inputs are taken directly from Planetiler's {@link com.onthegomap.planetiler.geo.WithGeometry#area()} /
 * {@link com.onthegomap.planetiler.geo.WithGeometry#length()}, i.e. fractions of the whole planet in Web-Mercator world
 * coordinates ({@code 1} = the entire planet area / circumference). Tilemaker works in Web-Mercator square meters, but
 * its earth-circumference constant cancels out exactly against the projected area, so these formulas reduce to the
 * world-fraction forms below and produce identical zoom levels.
 */
public final class Zooms {

  private Zooms() {}

  private static final double LOG2 = Math.log(2);

  /**
   * @param minSquarePixels minimum on-screen area in tile pixels² (a 256px tile)
   * @param worldArea       feature area as a fraction of the planet ({@link WithGeometry#area()})
   */
  public static int zminForArea(double minSquarePixels, double worldArea) {
    if (worldArea <= 0) {
      return Integer.MAX_VALUE;
    }
    double zmin = Math.log(minSquarePixels / (0x1p16 * worldArea)) / (2 * LOG2);
    return (int) Math.floor(zmin);
  }

  /**
   * @param minLengthPixels minimum on-screen length in tile pixels (a 256px tile)
   * @param worldLength     feature length as a fraction of the planet circumference ({@link WithGeometry#length()})
   */
  public static int zminForLength(double minLengthPixels, double worldLength) {
    if (worldLength <= 0) {
      return Integer.MAX_VALUE;
    }
    double zmin = Math.log(minLengthPixels / (0x1p8 * worldLength)) / LOG2;
    return (int) Math.floor(zmin);
  }
}
