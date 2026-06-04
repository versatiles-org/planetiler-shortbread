package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.util.Names;

/**
 * The {@code ferries} layer (zoom 8): ferry routes. Ports {@code process_ferries}. Car ferries appear from zoom 10,
 * passenger-only ferries ({@code motor_vehicle=no}) from zoom 12.
 */
public class Ferries implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "ferries";

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("route", "ferry"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!f.canBeLine()) {
      return;
    }
    int mz = f.hasTag("motor_vehicle", "no") ? 12 : 10;
    var feature = features.line(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setAttr("kind", "ferry");
    Names.setNames(feature, f);
  }
}
