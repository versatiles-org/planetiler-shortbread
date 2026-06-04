package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Names;
import com.onthegomap.planetiler.shortbread.util.Zooms;
import com.onthegomap.planetiler.util.SortKey;

/**
 * The {@code water_polygons} layer (and its {@code water_polygons_labels} centroids): lakes, reservoirs, rivers, docks,
 * canals and glaciers. Ports {@code process_water_polygons} from the Tilemaker reference: the minimum zoom scales with
 * the polygon's on-screen area so small water bodies only appear when large enough.
 */
public class WaterPolygons implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "water_polygons";
  public static final String LABELS = "water_polygons_labels";

  private final ShortbreadOptions options;

  public WaterPolygons(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("waterway"),
        Expression.matchField("natural"),
        Expression.matchField("landuse")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!Geo.isArea(f)) {
      return;
    }
    String waterway = f.getString("waterway", "");
    String natural = f.getString("natural", "");
    String water = f.getString("water", "");
    String landuse = f.getString("landuse", "");
    boolean isRiver = (natural.equals("water") && water.equals("river")) || waterway.equals("riverbank");

    double worldArea = Geo.worldArea(f);
    int mz = Integer.MAX_VALUE;
    String kind = null;
    if (landuse.equals("reservoir") || landuse.equals("basin") || (natural.equals("water") && !isRiver) ||
      natural.equals("glacier")) {
      mz = Math.max(4, Zooms.zminForArea(0.01, worldArea));
      if (mz >= 10) {
        mz = Math.max(10, Zooms.zminForArea(0.1, worldArea));
      }
      if (landuse.equals("reservoir") || landuse.equals("basin")) {
        kind = landuse;
      } else if (natural.equals("water") || natural.equals("glacier")) {
        kind = natural;
      }
    } else if (isRiver || waterway.equals("dock") || waterway.equals("canal")) {
      mz = Math.max(4, Zooms.zminForArea(0.1, worldArea));
      kind = isRiver ? "river" : waterway;
    }

    if (kind == null || mz > 14) {
      return;
    }
    double wayArea = Geo.areaSquareMeters(f);
    int sortKey = SortKey.orderByLog(Math.max(wayArea, 1), 1, 1e14).get();

    features.polygon(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setAttr("kind", kind)
      .setAttr("way_area", wayArea)
      .setSortKeyDescending(sortKey);

    if (f.hasTag("name")) {
      var label = features.centroid(LABELS)
        .setZoomRange(14, 14)
        .setAttr("kind", kind)
        .setAttr("way_area", wayArea)
        .setSortKeyDescending(sortKey);
      Names.setNames(label, f, options.languages());
    }
  }
}
