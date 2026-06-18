package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.util.Parse;
import java.util.List;
import java.util.Set;

/**
 * The {@code buildings} layer: building footprints (and 3D building parts) at zoom 14.
 * <p>
 * Mirrors Tilemaker {@code process_buildings}: any closed way/relation with a {@code building} tag other than
 * {@code building=no}. The schema carries a constant {@code dummy=1}.
 * <p>
 * EXTENSION (beyond Shortbread 1.0/1.1, which defines only {@code dummy=1} — see shortbread-docs #77): we also emit
 * {@code height} (and {@code min_height} when non-zero) for 3D extrusion. The derivation follows OpenMapTiles
 * ({@code Building.java}): an explicit {@code height}/{@code building:height} tag, else {@code building:levels}
 * (or {@code levels}) × 3.66 m, else a 5 m default; {@code min_height} likewise from {@code min_height} or
 * {@code building:min_level} × 3.66. Absurd values (>= 3660 m, almost always tagging errors) are dropped.
 * <p>
 * EXTENSION (3D / OSM <a href="https://wiki.openstreetmap.org/wiki/Simple3DBuildingsV1">Simple 3D Buildings</a>): we
 * also emit {@code building:part} polygons so multi-part buildings can be extruded correctly, but <em>only</em> those
 * that carry height information (a bare {@code building:part} with no height adds overlapping-footprint noise to flat
 * 2D styles for no 3D benefit). To stop the parent footprint from being double-extruded under its parts, the
 * {@code outline}-role member of a {@code type=building} relation is tagged {@code hide_3d=true} (OpenMapTiles
 * convention): a renderer extrudes every {@code buildings} feature except those. Known gap (as in OpenMapTiles): parts
 * that merely overlap an outline with no {@code type=building} relation cannot be detected cheaply, so such outlines do
 * not get {@code hide_3d}.
 */
public class Buildings
  implements ForwardingProfile.FeatureProcessor, ForwardingProfile.OsmRelationPreprocessor {

  public static final String LAYER_NAME = "buildings";
  private static final double METERS_PER_LEVEL = 3.66;
  private static final double DEFAULT_HEIGHT = 5;
  private static final double MAX_HEIGHT = 3660;

  // tag values of building / building:part that mean "not actually a building"
  private static final Set<String> NOT_A_BUILDING = Set.of("no", "none");
  // tags that make a building:part worth emitting for 3D (else it is just an overlapping 2D footprint)
  private static final List<String> HEIGHT_TAGS = List.of(
    "height", "building:height", "building:levels", "levels",
    "min_height", "building:min_height", "building:min_level", "min_level");

  /** Marks the ways/relations of a {@code type=building} relation, so the {@code outline} member can be found. */
  record BuildingRelation(long id) implements OsmRelationInfo {}

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("type", "building")) {
      return List.of(new BuildingRelation(relation.id()));
    }
    return null;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("building"),
        Expression.matchField("building:part")));
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (!feature.canBePolygon()) {
      return;
    }
    if (isBuildingValue(feature.getString("building"))) {
      var output = emit(features);
      addHeights(feature, output);
      // outline of a type=building relation: keep the 2D footprint but tell the renderer not to extrude it
      if (isOutlineOfBuildingRelation(feature)) {
        output.setAttr("hide_3d", true);
      }
    } else if (isBuildingValue(feature.getString("building:part")) && hasHeightInfo(feature)) {
      // only 3D-relevant parts (those carrying height info) are emitted
      addHeights(feature, emit(features));
    }
  }

  private static FeatureCollector.Feature emit(FeatureCollector features) {
    return features.polygon(LAYER_NAME)
      .setZoomRange(14, 14)
      .setMinPixelSize(0)
      .setAttr("dummy", 1);
  }

  private static boolean isBuildingValue(String value) {
    return value != null && !value.isEmpty() && !NOT_A_BUILDING.contains(value);
  }

  private static boolean hasHeightInfo(SourceFeature f) {
    for (String tag : HEIGHT_TAGS) {
      if (str(f, tag) != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean isOutlineOfBuildingRelation(SourceFeature f) {
    for (var member : f.relationInfo(BuildingRelation.class)) {
      if ("outline".equals(member.role())) {
        return true;
      }
    }
    return false;
  }

  private static void addHeights(SourceFeature f, FeatureCollector.Feature output) {
    Double height = Parse.meters(coalesce(str(f, "height"), str(f, "building:height")));
    Double minHeight = Parse.meters(coalesce(str(f, "min_height"), str(f, "building:min_height")));
    Double levels = coalesce(Parse.parseDoubleOrNull(str(f, "building:levels")), Parse.parseDoubleOrNull(str(f, "levels")));
    Double minLevels =
      coalesce(Parse.parseDoubleOrNull(str(f, "building:min_level")), Parse.parseDoubleOrNull(str(f, "min_level")));

    int renderHeight = (int) Math.ceil(height != null ? height : levels != null ? levels * METERS_PER_LEVEL : DEFAULT_HEIGHT);
    int renderMinHeight = (int) Math.floor(minHeight != null ? minHeight : minLevels != null ? minLevels * METERS_PER_LEVEL : 0);
    if (renderHeight >= MAX_HEIGHT || renderMinHeight >= MAX_HEIGHT) {
      return; // implausible height, likely a tagging error
    }
    output.setAttr("height", renderHeight);
    if (renderMinHeight > 0) {
      output.setAttr("min_height", renderMinHeight);
    }
  }

  private static String str(SourceFeature f, String key) {
    String v = f.getString(key);
    return (v == null || v.isEmpty()) ? null : v;
  }

  private static <T> T coalesce(T a, T b) {
    return a != null ? a : b;
  }
}
