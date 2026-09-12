package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.GeometryType;
import java.util.List;

/**
 * A reusable {@link ForwardingProfile.LayerPostProcessor} that merges overlapping/touching polygons sharing identical
 * attributes (e.g. the same {@code kind}) into one multipolygon, and drops merged polygons smaller than {@code minArea}
 * square tile pixels.
 * <p>
 * This generalizes dense area layers (notably {@code land}) at low/mid zoom: contiguous OSM landcover is usually split
 * into many adjacent polygons, which dominate overview-tile size. Coalescing them by {@code kind} cuts feature and
 * shared-boundary vertex counts. Register one instance per area layer to combine.
 * <p>
 * Merging still runs at {@code maxzoom} — it only joins polygons that already touch, so nothing is lost — but there the
 * area threshold drops to {@code minAreaAtMaxZoom}, because that tile is the base for overzooming. Planetiler applies
 * the same rule to its own rendering thresholds, keeping features down to {@code min_feature_size_at_max_zoom} (1/16
 * tile pixel, so 1/256 square tile pixel of area) at the maximum zoom instead of a whole pixel.
 * <p>
 * It also <em>drops any non-polygonal feature</em> first, at every zoom: a thin polygon can snap to a degenerate ring
 * that Planetiler types as a (multi)linestring. Such a feature would break a polygon-only layer (e.g. {@code ocean},
 * which a Shortbread validator rejects as "wrong geometry") and cannot be merged anyway. The valid polygons are kept.
 *
 * @param name             the output layer to post-process
 * @param minArea          minimum area, in square tile pixels, of a merged polygon to keep below {@code maxzoom}
 * @param minAreaAtMaxZoom the same threshold at {@code maxzoom}, where clients overzoom into the tile
 * @param maxzoom          the maximum rendering zoom
 */
public record MergePolygons(String name, double minArea, double minAreaAtMaxZoom, int maxzoom)
  implements ForwardingProfile.LayerPostProcessor {

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    // keep only polygon-typed features (drop degenerate slivers that snapped to lines), then merge — mergeOverlapping
    // groups by attributes internally, so different `kind`s are kept separate
    List<VectorTile.Feature> polygons = items.stream()
      .filter(f -> f.geometry().geomType() == GeometryType.POLYGON)
      .toList();
    return FeatureMerge.mergeOverlappingPolygons(polygons, zoom >= maxzoom ? minAreaAtMaxZoom : minArea);
  }
}
