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
    String kind = f.getString("aerialway");
    // DEVIATION: OSM (and the Tilemaker/YAML reference) use `rope_tow`, but the Shortbread `aerialways` kind enum omits
    // it; map it to the generic surface-lift kind `drag_lift` (a rope tow is a type of drag lift) to stay in-schema.
    if ("rope_tow".equals(kind)) {
      kind = "drag_lift";
    }
    features.line(LAYER)
      .setMinZoom(12)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setAttr("kind", kind);
  }
}
