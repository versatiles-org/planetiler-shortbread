package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.versatiles.shortbread.Shortbread;

/**
 * The {@code aerialways} layer (zoom 12): cable cars, gondolas, lifts and tows. Ports {@code process_aerialways}.
 */
public class Aerialways implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "aerialways";

  private static final String[] KINDS =
    {"cable_car", "gondola", "goods", "chair_lift", "drag_lift", "t-bar", "j-bar", "platter", "rope_tow"};

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
    // OSM tags rope tows as `rope_tow`, but the schema spells the kind with a hyphen, like `t-bar` and `j-bar`
    if ("rope_tow".equals(kind)) {
      kind = "rope-tow";
    }
    features.line(LAYER)
      .setMinZoom(12)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setAttr("kind", kind);
  }
}
