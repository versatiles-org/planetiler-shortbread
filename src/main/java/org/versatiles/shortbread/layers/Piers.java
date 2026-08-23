package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.util.Geo;

/**
 * The {@code pier_lines} and {@code pier_polygons} layers from {@code man_made=pier|breakwater|groyne}. Ports
 * {@code process_pier_lines} / {@code process_pier_polygons}.
 */
public class Piers implements ForwardingProfile.FeatureProcessor {

  public static final String LINES = "pier_lines";
  public static final String POLYGONS = "pier_polygons";

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("man_made", "pier", "breakwater", "groyne"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    String kind = f.getString("man_made");
    if (Geo.isArea(f)) {
      features.polygon(POLYGONS).setMinZoom(12).setMaxZoom(14).setMinPixelSize(0).setAttr("kind", kind);
    } else if (f.canBeLine()) {
      features.line(LINES).setMinZoom(12).setMaxZoom(14).setMinPixelSize(0).setAttr("kind", kind);
    }
  }
}
