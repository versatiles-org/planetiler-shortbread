package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.SortKey;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Geo;
import org.versatiles.shortbread.util.Names;
import org.versatiles.shortbread.util.Zooms;

/**
 * The {@code water_polygons} layer (and the interior label points of {@code water_polygons_labels}): lakes, reservoirs,
 * rivers, docks, canals and glaciers. The minimum zoom scales with the polygon's on-screen area so small water bodies
 * only appear when large enough.
 */
public class WaterPolygons implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "water_polygons";
  public static final String LABELS = "water_polygons_labels";

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public WaterPolygons(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  @Override
  public Expression filter() {
    // match the values the handler accepts: `matchField("natural")` pulled in every natural=tree node
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchAny("waterway", "riverbank", "dock", "canal"),
        Expression.matchAny("natural", "water", "glacier"),
        Expression.matchAny("landuse", "reservoir", "basin")));
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
    if (worldArea <= 0) {
      // geometry that could not be assembled (e.g. a broken multipolygon): nothing to draw
      return;
    }
    // The polygon below is rendered with setMinPixelSize(1), so it only appears once it covers a square tile pixel.
    // Deriving the minimum zoom from that same threshold keeps water_polygons_labels in step with it, as the spec
    // requires — that layer holds names "for all named water polygons available in the water_polygons layer". A
    // coarser threshold labelled a lake about three zooms before any water was drawn.
    int byArea = Zooms.zminForArea(1, worldArea);
    int mz;
    String kind;
    if (landuse.equals("reservoir") || landuse.equals("basin")) {
      kind = landuse;
      mz = Math.max(4, byArea);
    } else if (isRiver) {
      kind = "river";
      mz = Math.max(4, byArea);
    } else if (natural.equals("water") || natural.equals("glacier")) {
      kind = natural;
      mz = Math.max(4, byArea);
    } else if (waterway.equals("dock") || (waterway.equals("canal") && Geo.areaYesMultiBoundary(f))) {
      // a closed `waterway=canal` way is a line unless it is explicitly tagged as an area; OpenStreetMap Carto's
      // water-areas query likewise takes only dock and riverbank from planet_osm_polygon
      kind = waterway;
      mz = Math.max(10, byArea);
    } else {
      return;
    }

    // a water body that only reaches a full pixel beyond z14 still belongs in the maximum-zoom tile, which is the
    // complete base for overzooming
    mz = Math.min(mz, 14);
    double wayArea = Geo.mercatorAreaSquareMeters(f); // spec: way_area is in m² (Mercator projection)
    int sortKey = SortKey.orderByLog(Math.max(wayArea, 1), 1, 1e14).get();

    features.polygon(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(1)
      .setPixelTolerance(0.25)
      .setAttr("kind", kind)
      .setAttr("way_area", wayArea)
      .setSortKeyDescending(sortKey);

    if (f.hasTag("name")) {
      var label = features.pointOnSurface(LABELS)
        // spec: water_polygons_labels follows its polygon's (area-based) availability, not z14 only
        .setMinZoom(mz)
        .setMaxZoom(14)
        .setAttr("kind", kind)
        .setAttr("way_area", wayArea)
        .setSortKeyDescending(sortKey)
        // thin dense low/mid-zoom water labels: keep the largest (sort key = area) per 64px grid cell (~16/tile) so a
        // continent-sized z4 tile carries the few biggest seas/lakes, not thousands of points. z12+ is left unthinned.
        // buffer must be >= grid size for consistent thinning across tile edges.
        //
        // The buffer stays wide at every zoom, unlike place_labels: this label sits at the polygon's interior point,
        // which for a water body straddling the edge of an extract can fall outside the tiles being written. Narrowing
        // the buffer above the grid zooms measurably lost such a label (a lake on the western edge of a Berlin
        // extract), because no emitted tile reached it any more. A planet build has no such edge, but extracts do.
        .setBufferPixels(64)
        .setPointLabelGridSizeAndLimit(11, 64, 1);
      Names.setNames(label, f, options.nameKeys(), countries);
    }
  }
}
