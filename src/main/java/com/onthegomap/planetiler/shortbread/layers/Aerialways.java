package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;

/**
 * The {@code aerialways} layer (zoom 12): cable cars, gondolas, lifts and tows. Ports {@code process_aerialways}.
 */
public class Aerialways implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "aerialways";

  private static final String[] KINDS =
    {"cable_car", "gondola", "chair_lift", "drag_lift", "t-bar", "j-bar", "platter", "rope_tow"};

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("aerialway", (Object[]) KINDS));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!f.canBeLine()) {
      return;
    }
    features.line(LAYER)
      .setMinZoom(12)
      .setMaxZoom(14)
      .setAttr("kind", f.getString("aerialway"));
  }
}
