package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Low-zoom glaciers and ice shelves from Natural Earth, emitted into the {@code water_polygons} layer as
 * {@code kind=glacier} at zoom 0-6.
 * <p>
 * EXTENSION (beyond Shortbread 1.0/1.1, which sources glaciers only from OSM and defines no land/ice below zoom 7):
 * mirrors the OpenMapTiles {@code landcover} approach of filling z0-6 from Natural Earth, handing off to OSM glaciers
 * at zoom 7+. Ice shelves map to {@code kind=glacier} (Shortbread has no {@code ice_shelf} kind), so only the data
 * source and zoom range are new — the attribute vocabulary stays in spec.
 * <p>
 * Each Natural Earth layer is wired as its own small per-layer shapefile source (the zips from naciscdn.org; see
 * {@code ShortbreadMain}), avoiding the ~800 MB full Natural Earth download. The source name identifies the layer and
 * selects its zoom range.
 */
public class Glaciers implements ForwardingProfile.FeatureProcessor {

  // source names == the Natural Earth per-layer shapefile names wired in ShortbreadMain
  public static final String GLACIATED_110M = "ne_110m_glaciated_areas";
  public static final String GLACIATED_50M = "ne_50m_glaciated_areas";
  public static final String GLACIATED_10M = "ne_10m_glaciated_areas";
  public static final String ICE_SHELVES_50M = "ne_50m_antarctic_ice_shelves_polys";
  public static final String ICE_SHELVES_10M = "ne_10m_antarctic_ice_shelves_polys";

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchSource(GLACIATED_110M),
      Expression.matchSource(GLACIATED_50M),
      Expression.matchSource(GLACIATED_10M),
      Expression.matchSource(ICE_SHELVES_50M),
      Expression.matchSource(ICE_SHELVES_10M));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    int minZoom;
    int maxZoom;
    switch (f.getSource()) {
      case GLACIATED_110M -> {
        minZoom = 0;
        maxZoom = 1;
      }
      case GLACIATED_50M, ICE_SHELVES_50M -> {
        minZoom = 2;
        maxZoom = 4;
      }
      case GLACIATED_10M, ICE_SHELVES_10M -> {
        minZoom = 5;
        maxZoom = 6;
      }
      default -> {
        return;
      }
    }
    features.polygon(WaterPolygons.LAYER)
      // generalized low-zoom coverage features: keep them whole and seamless across tiles
      .setZoomRange(minZoom, maxZoom)
      .setMinPixelSize(0)
      .setBufferPixels(4)
      .setAttr("kind", "glacier");
  }
}
