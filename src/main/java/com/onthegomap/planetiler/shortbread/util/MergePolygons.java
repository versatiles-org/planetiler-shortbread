package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.util.List;

/**
 * A reusable {@link ForwardingProfile.LayerPostProcessor} that merges overlapping/touching polygons sharing identical
 * attributes (e.g. the same {@code kind}) into one multipolygon, and drops merged polygons smaller than
 * {@code minArea} square tile pixels.
 * <p>
 * This generalizes dense area layers (notably {@code land}) at low/mid zoom: contiguous OSM landcover is usually split
 * into many adjacent polygons, which dominate overview-tile size. Coalescing them by {@code kind} cuts feature and
 * shared-boundary vertex counts. Register one instance per area layer to combine.
 *
 * @param name    the output layer to post-process
 * @param minArea minimum area, in square tile pixels, of a merged polygon to keep
 */
public record MergePolygons(String name, double minArea) implements ForwardingProfile.LayerPostProcessor {

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    // mergeOverlappingPolygons groups by attributes internally, so different `kind`s are kept separate
    return FeatureMerge.mergeOverlappingPolygons(items, minArea);
  }
}
