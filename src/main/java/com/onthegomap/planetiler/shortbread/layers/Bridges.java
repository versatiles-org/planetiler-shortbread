package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.util.Geo;

/**
 * The {@code bridges} layer (zoom 12): bridge outlines from {@code man_made=bridge} areas. Ports
 * {@code process_bridges}.
 */
public class Bridges implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "bridges";

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("man_made", "bridge"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (Geo.isArea(f)) {
      features.polygon(LAYER)
        .setMinZoom(12)
        .setMaxZoom(14)
        .setMinPixelSize(0)
        .setAttr("kind", "bridge");
    }
  }
}
