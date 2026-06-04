package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.util.Geo;

/**
 * The {@code dam_lines} and {@code dam_polygons} layers from {@code waterway=dam}. Ports {@code process_dam}.
 */
public class Dams implements ForwardingProfile.FeatureProcessor {

  public static final String LINES = "dam_lines";
  public static final String POLYGONS = "dam_polygons";

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("waterway", "dam"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (Geo.isArea(f)) {
      features.polygon(POLYGONS).setMinZoom(12).setMaxZoom(14).setAttr("kind", "dam");
    } else if (f.canBeLine()) {
      features.line(LINES).setMinZoom(12).setMaxZoom(14).setAttr("kind", "dam");
    }
  }
}
