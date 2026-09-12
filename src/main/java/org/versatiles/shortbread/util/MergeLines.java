package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.util.List;

/**
 * A reusable {@link ForwardingProfile.LayerPostProcessor} that merges connected line segments sharing identical
 * attributes, so that short fragments do not survive as separate features at low zoom. Register one instance per line
 * layer that should be combined.
 * <p>
 * Connected lines are joined at every zoom — that only reduces the feature count, it never removes geometry — but
 * <em>dropping</em> geometry stops at {@code maxzoom}. Those tiles are the base for overzooming, so nothing is dropped
 * there for being short. Merged lines are still simplified, with {@code toleranceAtMaxZoom}: Planetiler already
 * simplifies every feature at the maximum zoom with its {@code simplify_tolerance_at_max_zoom} (1/16 tile pixel, one
 * unit of the 4096-unit tile grid), but it does so per feature, and Douglas-Peucker always keeps a feature's endpoints.
 * Joining two lines turns those endpoints into interior vertices, so simplifying the merged line again with the same
 * tolerance drops the ones that are now redundant, while no vertex can move further than one grid unit.
 * <p>
 * Below {@code maxzoom}, merged lines, stubs and loops under half a tile pixel are dropped and the result is simplified
 * with a coarser 0.1 px tolerance.
 *
 * @param name               the output layer to post-process
 * @param toleranceAtMaxZoom simplification tolerance, in tile pixels, at {@code maxzoom}
 * @param maxzoom            the maximum rendering zoom, at which nothing is dropped for being short
 */
public record MergeLines(String name, double toleranceAtMaxZoom, int maxzoom)
  implements ForwardingProfile.LayerPostProcessor {

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    if (zoom >= maxzoom) {
      // join connected lines and keep every piece (minLength 0), simplifying only at the tile's own resolution
      return FeatureMerge.mergeLineStrings(items, 0, toleranceAtMaxZoom, 4);
    }
    // drop sub-pixel fragments, simplify slightly, keep a small buffer for cross-tile continuity
    return FeatureMerge.mergeLineStrings(items, 0.5, 0.1, 4);
  }
}
