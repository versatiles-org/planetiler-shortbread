package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Names;
import com.onthegomap.planetiler.util.Parse;
import java.util.List;

/**
 * The {@code boundaries} line layer and the {@code boundary_labels} layer. Ports {@code process_boundary_lines} /
 * {@code relation_scan_function} from the Tilemaker reference.
 * <p>
 * Boundary lines come from the member ways of administrative ({@code admin_level} 2-4) or disputed boundary relations:
 * the relation's admin level / disputed flag is captured in {@link #preprocessOsmRelation} and read back on each member
 * way. {@code boundary_labels} are derived directly from administrative boundary polygons (admin_level 2 or 4) as an
 * interior label point, following the current Planetiler YAML schema rather than Tilemaker's pre-built admin-points
 * shapefile, so no external data source is needed.
 */
public class Boundaries implements ForwardingProfile.FeatureProcessor, ForwardingProfile.OsmRelationPreprocessor {

  public static final String LINES = "boundaries";
  public static final String LABELS = "boundary_labels";

  private final ShortbreadOptions options;

  public Boundaries(ShortbreadOptions options) {
    this.options = options;
  }

  /** Relation info captured during pass 1: admin level (99 if disputed-only) and whether it is a disputed boundary. */
  record BoundaryRelation(long id, int adminLevel, boolean disputed) implements OsmRelationInfo {}

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "boundary")) {
      return null;
    }
    String boundary = relation.getString("boundary");
    Integer adminLevel = Parse.parseIntOrNull(relation.getTag("admin_level"));
    if ("administrative".equals(boundary)) {
      if (adminLevel != null && adminLevel >= 2 && adminLevel <= 4) {
        return List.of(new BoundaryRelation(relation.id(), adminLevel, false));
      }
    } else if ("disputed".equals(boundary) && (adminLevel == null || (adminLevel >= 2 && adminLevel <= 4))) {
      return List.of(new BoundaryRelation(relation.id(), 99, true));
    }
    return null;
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
    int minAdminLevel = 99;
    boolean disputed = false;
    // the way's own boundary tags (covers directly tagged ways, as the previous YAML schema relied on)
    if (f.hasTag("boundary", "administrative")) {
      Integer ownLevel = Parse.parseIntOrNull(f.getTag("admin_level"));
      if (ownLevel != null && ownLevel >= 2 && ownLevel <= 4) {
        minAdminLevel = Math.min(minAdminLevel, ownLevel);
      }
    }
    if (f.hasTag("boundary", "disputed")) {
      disputed = true;
    }
    // plus any parent boundary relations (covers untagged member ways)
    for (var member : f.relationInfo(BoundaryRelation.class)) {
      BoundaryRelation info = member.relation();
      if (!info.disputed() && info.adminLevel() >= 2) {
        minAdminLevel = Math.min(minAdminLevel, info.adminLevel());
      }
      if (info.disputed()) {
        disputed = true;
      }
    }
    int mz;
    if (minAdminLevel == 2) {
      mz = 0;
    } else if (minAdminLevel <= 4) {
      mz = 7;
    } else {
      return; // disputed-only ways with no administrative parent are not drawn
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
    Integer adminLevel = Parse.parseIntOrNull(f.getTag("admin_level"));
    if (adminLevel == null || (adminLevel != 2 && adminLevel != 4)) {
      return;
    }
    double areaKm2 = Geo.areaSquareMeters(f) / 1e6;
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
      .setAttr("way_area", Geo.areaHectares(f)); // hectares, as in the Planetiler YAML schema
    Names.setNames(label, f, options.languages());
  }
}
