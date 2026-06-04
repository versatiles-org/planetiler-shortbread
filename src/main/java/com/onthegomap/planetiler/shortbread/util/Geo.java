package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.Unit;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Geometry helpers, including the area-vs-line decision ported from {@code way_function} in the Tilemaker reference
 * ({@code process.lua}). Planetiler's {@link SourceFeature#canBePolygon()} treats any closed way as a potential polygon
 * regardless of the {@code area} tag, so the OSM area conventions are reproduced here.
 */
public final class Geo {

  private Geo() {}

  /**
   * Tags that force a closed way to be treated as an area: {@code area=yes}, {@code type=multipolygon|boundary}, or
   * {@code area:aeroway=runway|taxiway}. Mirrors {@code area_yes_multi_boundary} in {@code process.lua}.
   */
  public static boolean areaYesMultiBoundary(SourceFeature f) {
    return f.hasTag("area", "yes") ||
      f.hasTag("type", "multipolygon", "boundary") ||
      f.hasTag("area:aeroway", "runway", "taxiway");
  }

  /**
   * Whether a feature should be treated as an area (polygon by default unless tagged {@code area=no}). Mirrors
   * {@code is_area} in {@code process.lua}.
   */
  public static boolean isArea(SourceFeature f) {
    return f.canBePolygon() && (areaYesMultiBoundary(f) || !f.hasTag("area", "no"));
  }

  /** Feature area as a fraction of the planet (Web-Mercator world units), or 0 on geometry errors. */
  public static double worldArea(SourceFeature f) {
    try {
      return f.area();
    } catch (GeometryException e) {
      return 0;
    }
  }

  /** Feature length as a fraction of the planet circumference (Web-Mercator world units), or 0 on geometry errors. */
  public static double worldLength(SourceFeature f) {
    try {
      return f.length();
    } catch (GeometryException e) {
      return 0;
    }
  }

  /** Approximate feature area in square meters. */
  public static double areaSquareMeters(SourceFeature f) {
    return f.area(Unit.Area.SQUARE_METER);
  }

  /** Approximate feature area in hectares. */
  public static double areaHectares(SourceFeature f) {
    return f.area(Unit.Area.HECTARE);
  }
}
