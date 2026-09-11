package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.Parse;
import java.util.List;
import java.util.Set;
import org.versatiles.shortbread.Experiment;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;

/**
 * The {@code buildings} layer: building footprints (and 3D building parts) at zoom 14.
 * <p>
 * Any closed way/relation with a {@code building} tag other than {@code building=no}. The schema carries a constant
 * {@code dummy=1}.
 * <p>
 * EXPERIMENT {@code 3d_buildings} (beyond Shortbread 1.0/1.1, which defines only {@code dummy=1} — see shortbread-docs
 * #77):
 * <ul>
 * <li>{@code height} for 3D extrusion, only when it is tagged: an explicit {@code height}/{@code building:height} tag,
 * else {@code building:levels} (or {@code levels}) × 3.66 m, as OpenMapTiles ({@code Building.java}) derives it. There
 * is no default height; a style supplies its own for buildings without one. {@code min_height} likewise comes from
 * {@code min_height}/{@code building:min_height} or {@code building:min_level} × 3.66, and is only emitted with a
 * {@code height} it stays below, so an extrusion is never inverted. Absurd values (>= 3660 m, almost always tagging
 * errors) are dropped.</li>
 * <li>OSM <a href="https://wiki.openstreetmap.org/wiki/Simple3DBuildingsV1">Simple 3D Buildings</a>
 * {@code building:part} polygons, so multi-part buildings can be extruded correctly, but <em>only</em> those with a
 * height. They are marked {@code part=true}, so a 2D style can leave them out and draw only the footprints.</li>
 * <li>{@code hide_3d=true} on the {@code outline}-role member of a {@code type=building} relation, so the parent
 * footprint is not extruded under its parts (OpenMapTiles convention): a renderer extrudes every {@code buildings}
 * feature except those. Known gap (as in OpenMapTiles): parts that merely overlap an outline with no
 * {@code type=building} relation cannot be detected cheaply, so such outlines do not get {@code hide_3d}.</li>
 * </ul>
 */
public class Buildings
  implements ForwardingProfile.FeatureProcessor, ForwardingProfile.OsmRelationPreprocessor {

  public static final String LAYER_NAME = "buildings";
  private static final double METERS_PER_LEVEL = 3.66;
  private static final double MAX_HEIGHT = 3660;

  // tag values of building / building:part that mean "not actually a building"
  private static final Set<String> NOT_A_BUILDING = Set.of("no", "none");

  /** Marks the ways/relations of a {@code type=building} relation, so the {@code outline} member can be found. */
  record BuildingRelation(long id) implements OsmRelationInfo {}

  private final ShortbreadOptions options;

  public Buildings(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("type", "building")) {
      return List.of(new BuildingRelation(relation.id()));
    }
    return null;
  }

  @Override
  public Expression filter() {
    Expression building = Expression.matchField("building");
    // only look at building:part features when the 3D buildings experiment is on
    Expression keys = options.has(Experiment.BUILDINGS_3D) ?
      Expression.or(building, Expression.matchField("building:part")) : building;
    return Expression.and(Expression.matchSource(Shortbread.OSM_SOURCE), keys);
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (!feature.canBePolygon()) {
      return;
    }
    boolean buildings3d = options.has(Experiment.BUILDINGS_3D);
    if (isBuildingValue(feature.getString("building"))) {
      var output = emit(features);
      if (buildings3d) {
        addHeights(feature, output);
        // outline of a type=building relation: keep the 2D footprint but tell the renderer not to extrude it
        if (isOutlineOfBuildingRelation(feature)) {
          output.setAttr("hide_3d", true);
        }
      }
    } else if (buildings3d && isBuildingValue(feature.getString("building:part")) && height(feature) != null) {
      // only parts with a height are 3D-relevant; the marker lets a 2D style skip them
      addHeights(feature, emit(features).setAttr("part", true));
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

  private static boolean isOutlineOfBuildingRelation(SourceFeature f) {
    for (var member : f.relationInfo(BuildingRelation.class)) {
      if ("outline".equals(member.role())) {
        return true;
      }
    }
    return false;
  }

  /** The tagged height in meters, from a height tag or else the number of levels, or {@code null} if neither parses. */
  private static Double height(SourceFeature f) {
    Double height = Parse.meters(coalesce(str(f, "height"), str(f, "building:height")));
    if (height != null) {
      return height;
    }
    Double levels =
      coalesce(Parse.parseDoubleOrNull(str(f, "building:levels")), Parse.parseDoubleOrNull(str(f, "levels")));
    return levels != null ? levels * METERS_PER_LEVEL : null;
  }

  private static void addHeights(SourceFeature f, FeatureCollector.Feature output) {
    Double height = height(f);
    if (height == null) {
      return; // untagged: a style applies its own default height
    }
    Double minHeight = Parse.meters(coalesce(str(f, "min_height"), str(f, "building:min_height")));
    Double minLevels =
      coalesce(Parse.parseDoubleOrNull(str(f, "building:min_level")), Parse.parseDoubleOrNull(str(f, "min_level")));

    int renderHeight = (int) Math.ceil(height);
    int renderMinHeight =
      (int) Math.floor(minHeight != null ? minHeight : minLevels != null ? minLevels * METERS_PER_LEVEL : 0);
    if (renderHeight >= MAX_HEIGHT || renderMinHeight >= MAX_HEIGHT) {
      return; // implausible height, likely a tagging error
    }
    output.setAttr("height", renderHeight);
    if (renderMinHeight > 0 && renderMinHeight < renderHeight) {
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
