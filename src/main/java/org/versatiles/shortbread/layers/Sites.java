package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Set;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.util.Geo;

/**
 * The {@code sites} layer (zoom 14): university/hospital/prison/parking grounds, sports centres, construction sites and
 * military danger areas.
 */
public class Sites implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "sites";

  private static final Set<String> AMENITY = Set.of(
    "university", "hospital", "prison", "parking", "bicycle_parking", "school", "college");

  @Override
  public Expression filter() {
    // match the values the handler accepts, not the bare keys, so the whole planet's amenities and landuse polygons
    // are not routed through this handler only to be rejected
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchAny("amenity", List.copyOf(AMENITY)),
        Expression.matchAny("leisure", "sports_centre"),
        Expression.matchAny("military", "danger_area"),
        Expression.matchAny("landuse", "construction")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!Geo.isArea(f)) {
      return;
    }
    String amenity = f.getString("amenity", "");
    String kind;
    if (AMENITY.contains(amenity)) {
      kind = amenity;
    } else if (f.hasTag("leisure", "sports_centre")) {
      kind = "sports_centre";
    } else if (f.hasTag("landuse", "construction")) {
      kind = "construction";
    } else if (f.hasTag("military", "danger_area")) {
      kind = "danger_area";
    } else {
      return;
    }

    features.polygon(LAYER)
      .setZoomRange(14, 14)
      .setMinPixelSize(0)
      .setAttr("kind", kind);
  }
}
