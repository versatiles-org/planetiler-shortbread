package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Set;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.util.Geo;

/**
 * The {@code sites} layer (zoom 14): university/hospital/prison/parking grounds, sports centres, construction sites and
 * military danger areas. Ports {@code process_sites}.
 * <p>
 * DEVIATION (bug fix): Tilemaker matched {@code leisure=sports_center} (a misspelling that never matches real OSM
 * data); here we match the correct {@code leisure=sports_centre}.
 */
public class Sites implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "sites";

  private static final Set<String> AMENITY = Set.of(
    "university", "hospital", "prison", "parking", "bicycle_parking", "school", "college");

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("amenity"),
        Expression.matchField("leisure"),
        Expression.matchField("military"),
        Expression.matchField("landuse")));
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
