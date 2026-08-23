package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.util.List;

/**
 * A reusable {@link ForwardingProfile.LayerPostProcessor} that merges connected line segments sharing identical
 * attributes, approximating the {@code combine_below} setting of the Tilemaker reference ({@code config.json}).
 * Register one instance per line layer that should be combined.
 */
public record MergeLines(String name) implements ForwardingProfile.LayerPostProcessor {

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    // drop sub-pixel fragments, simplify slightly, keep a small buffer for cross-tile continuity
    return FeatureMerge.mergeLineStrings(items, 0.5, 0.1, 4);
  }
}
