package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.util.Parse;

/**
 * The {@code buildings} layer: building footprints at zoom 14.
 * <p>
 * Mirrors Tilemaker {@code process_buildings}: any closed way/relation with a {@code building} tag other than
 * {@code building=no}. The schema carries a constant {@code dummy=1}.
 * <p>
 * EXTENSION (beyond Shortbread 1.0/1.1, which defines only {@code dummy=1} — see shortbread-docs #77): we also emit
 * {@code height} (and {@code min_height} when non-zero) for 3D extrusion. The derivation follows OpenMapTiles
 * ({@code Building.java}): an explicit {@code height}/{@code building:height} tag, else {@code building:levels}
 * (or {@code levels}) × 3.66 m, else a 5 m default; {@code min_height} likewise from {@code min_height} or
 * {@code building:min_level} × 3.66. Absurd values (>= 3660 m, almost always tagging errors) are dropped.
 */
public class Buildings implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER_NAME = "buildings";
  private static final double METERS_PER_LEVEL = 3.66;
  private static final double DEFAULT_HEIGHT = 5;
  private static final double MAX_HEIGHT = 3660;

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchField("building"));
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon() && !feature.hasTag("building", "no")) {
      var output = features.polygon(LAYER_NAME)
        .setZoomRange(14, 14)
        .setMinPixelSize(0)
        .setAttr("dummy", 1);
      addHeights(feature, output);
    }
  }

  private static void addHeights(SourceFeature f, FeatureCollector.Feature output) {
    Double height = Parse.meters(coalesce(str(f, "height"), str(f, "building:height")));
    Double minHeight = Parse.meters(coalesce(str(f, "min_height"), str(f, "building:min_height")));
    Double levels = coalesce(Parse.parseDoubleOrNull(str(f, "building:levels")), Parse.parseDoubleOrNull(str(f, "levels")));
    Double minLevels =
      coalesce(Parse.parseDoubleOrNull(str(f, "building:min_level")), Parse.parseDoubleOrNull(str(f, "min_level")));

    int renderHeight = (int) Math.ceil(height != null ? height : levels != null ? levels * METERS_PER_LEVEL : DEFAULT_HEIGHT);
    int renderMinHeight = (int) Math.floor(minHeight != null ? minHeight : minLevels != null ? minLevels * METERS_PER_LEVEL : 0);
    if (renderHeight >= MAX_HEIGHT || renderMinHeight >= MAX_HEIGHT) {
      return; // implausible height, likely a tagging error
    }
    output.setAttr("height", renderHeight);
    if (renderMinHeight > 0) {
      output.setAttr("min_height", renderMinHeight);
    }
  }

  private static String str(SourceFeature f, String key) {
    String v = f.getString(key);
    return (v == null || v.isEmpty()) ? null : v;
  }

  private static <T> T coalesce(T a, T b) {
    return a != null ? a : b;
  }
}
