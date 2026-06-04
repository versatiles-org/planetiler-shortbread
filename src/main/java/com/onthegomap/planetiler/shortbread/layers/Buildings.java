package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;

/**
 * The {@code buildings} layer: building footprints at zoom 14.
 * <p>
 * Mirrors Tilemaker {@code process_buildings}: any closed way/relation with a {@code building} tag other than
 * {@code building=no}. The schema carries no real attributes, only a constant {@code dummy=1}.
 */
public class Buildings implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER_NAME = "buildings";

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchField("building"));
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon() && !feature.hasTag("building", "no")) {
      features.polygon(LAYER_NAME)
        .setZoomRange(14, 14)
        .setMinPixelSize(0)
        .setAttr("dummy", 1);
    }
  }
}
