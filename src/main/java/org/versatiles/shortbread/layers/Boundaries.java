package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.SortKey;
import java.util.List;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Geo;
import org.versatiles.shortbread.util.Names;

/**
 * The {@code boundaries} line layer and the {@code boundary_labels} layer.
 * <p>
 * Boundary lines come only from the member ways of boundary relations: administrative relations with
 * {@code admin_level} 2 or 4, and disputed relations. The relation's admin level / disputed flag is captured in
 * {@link #preprocessOsmRelation} and read back on each member way; a way tagged {@code boundary=administrative} without
 * a parent relation is not a boundary line. {@code boundary_labels} are derived directly from administrative boundary
 * polygons (admin_level 2 or 4) as an interior label point, rather than a pre-built admin-points shapefile, so no
 * external data source is needed.
 */
public class Boundaries implements ForwardingProfile.FeatureProcessor, ForwardingProfile.OsmRelationPreprocessor {

  public static final String LINES = "boundaries";
  public static final String LABELS = "boundary_labels";

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public Boundaries(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  /** Relation info captured during pass 1: admin level (99 if disputed-only) and whether it is a disputed boundary. */
  record BoundaryRelation(long id, int adminLevel, boolean disputed) implements OsmRelationInfo {}

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "boundary")) {
      return null;
    }
    String boundary = relation.getString("boundary");
    String adminLevelTag = relation.getString("admin_level");
    Integer adminLevel = parseAdminLevel(adminLevelTag);
    if ("administrative".equals(boundary)) {
      // Shortbread boundaries are only country (2) and state (4) lines; admin_level=3 (and 5+) are not in the schema
      if (adminLevel != null && (adminLevel == 2 || adminLevel == 4)) {
        return List.of(new BoundaryRelation(relation.id(), adminLevel, false));
      }
    } else if ("disputed".equals(boundary) && (adminLevelTag == null || isDisputedLevel(adminLevel))) {
      return List.of(new BoundaryRelation(relation.id(), 99, true));
    }
    return null;
  }

  /**
   * Whether a disputed relation's {@code admin_level} makes its member ways disputed: 1.0 accepts 2 to 4, 1.1 only 2
   * and 4. A value that is set but not an integer (e.g. {@code 2;4}) matches neither.
   */
  private boolean isDisputedLevel(Integer adminLevel) {
    if (adminLevel == null) {
      return false;
    }
    return options.v11() ? adminLevel == 2 || adminLevel == 4 : adminLevel >= 2 && adminLevel <= 4;
  }

  /**
   * Parses {@code admin_level} as a plain integer, or returns {@code null}. Stricter than {@code Parse.parseIntOrNull},
   * which reads the leading number of a value, so a multi-value {@code 2;4} is not taken as 2.
   */
  private static Integer parseAdminLevel(Object tag) {
    if (tag == null) {
      return null;
    }
    try {
      return Integer.parseInt(tag.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Expression filter() {
    // member ways are usually untagged, so we cannot filter on tags here
    return Expression.matchSource(Shortbread.OSM_SOURCE);
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    boundaryLabel(f, features);

    // boundary lines come from member ways; skip the relation feature itself (it carries a `type` tag)
    if (f.hasTag("type") || !f.canBeLine()) {
      return;
    }
    // the way's own boundary tags don't count: admin level and the disputed flag come from its parent relations
    int minAdminLevel = 99;
    boolean disputed = false;
    for (var member : f.relationInfo(BoundaryRelation.class)) {
      BoundaryRelation info = member.relation();
      if (info.disputed()) {
        disputed = true;
      } else {
        minAdminLevel = Math.min(minAdminLevel, info.adminLevel());
      }
    }
    int mz;
    if (minAdminLevel == 2) {
      mz = 0;
    } else if (minAdminLevel == 4) {
      mz = 7;
    } else {
      return; // ways without an administrative parent relation, including disputed-only ones, are not drawn
    }
    if (f.hasTag("disputed", "yes")) {
      disputed = true;
    }
    boolean maritime = f.hasTag("maritime", "yes") || f.hasTag("natural", "coastline");

    features.line(LINES)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setAttr("admin_level", minAdminLevel)
      .setAttr("maritime", maritime)
      .setAttr("disputed", disputed);
  }

  private void boundaryLabel(SourceFeature f, FeatureCollector features) {
    if (!f.canBePolygon() || !f.hasTag("boundary", "administrative")) {
      return;
    }
    // a label point carries a name: without one it is an unlabelled dot, which is what an unnamed closed member way
    // of a boundary relation would otherwise add
    if (!f.hasTag("name")) {
      return;
    }
    Integer adminLevel = parseAdminLevel(f.getTag("admin_level"));
    if (adminLevel == null || (adminLevel != 2 && adminLevel != 4)) {
      return;
    }
    // spec: the min-area thresholds and way_area are Web-Mercator-projected (not geodesic) — geodesic area is
    // ~cos²(lat) smaller, which would push mid/high-latitude countries below the thresholds. mercatorArea also
    // returns 0 on a broken/degenerate multipolygon, so this guards unbuildable geometries too.
    double mercatorM2 = Geo.mercatorAreaSquareMeters(f);
    if (mercatorM2 <= 0) {
      return;
    }
    double areaKm2 = mercatorM2 / 1e6;
    int mz;
    if (adminLevel == 2 && areaKm2 >= 2e6) {
      mz = 2;
    } else if (areaKm2 >= 7e5) {
      mz = 3;
    } else if (areaKm2 >= 1e5) {
      mz = 4;
    } else {
      mz = 5;
    }
    var label = features.pointOnSurface(LABELS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setAttr("admin_level", adminLevel)
      .setAttr("way_area", mercatorM2 / 1e4) // hectares (Mercator projection), per the spec
      // spec: labels are sorted by way_area in descending order
      .setSortKeyDescending(SortKey.orderByLog(mercatorM2, 1, 1e15).get());
    Names.setNames(label, f, options.nameKeys(), countries);
  }
}
