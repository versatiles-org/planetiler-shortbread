package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;

/**
 * Low-zoom glaciers and ice shelves from Natural Earth, emitted into the {@code water_polygons} layer as
 * {@code kind=glacier} at zoom 0-6.
 * <p>
 * EXTENSION (beyond Shortbread 1.0/1.1): the spec sources glaciers only from OSM ({@code natural=glacier} ->
 * {@code water_polygons}) and defines no {@code land}/ice below zoom 7, so worldwide maps have no ice at low zoom. This
 * mirrors the OpenMapTiles {@code landcover} approach of filling z0-6 from Natural Earth's generalized glaciated-area
 * and antarctic ice-shelf tables, handing off to the OSM glaciers at zoom 7+. Ice shelves map to {@code kind=glacier}
 * as well, since Shortbread has no {@code ice_shelf} kind. Stays within the spec's attribute vocabulary; only the data
 * source and zoom range are new.
 */
public class Glaciers implements ForwardingProfile.FeatureProcessor {

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.NATURAL_EARTH_SOURCE),
      Expression.or(
        Expression.matchSourceLayer("ne_110m_glaciated_areas"),
        Expression.matchSourceLayer("ne_50m_glaciated_areas"),
        Expression.matchSourceLayer("ne_10m_glaciated_areas"),
        Expression.matchSourceLayer("ne_50m_antarctic_ice_shelves_polys"),
        Expression.matchSourceLayer("ne_10m_antarctic_ice_shelves_polys")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    int minZoom;
    int maxZoom;
    switch (f.getSourceLayer()) {
      case "ne_110m_glaciated_areas" -> {
        minZoom = 0;
        maxZoom = 1;
      }
      case "ne_50m_glaciated_areas", "ne_50m_antarctic_ice_shelves_polys" -> {
        minZoom = 2;
        maxZoom = 4;
      }
      case "ne_10m_glaciated_areas", "ne_10m_antarctic_ice_shelves_polys" -> {
        minZoom = 5;
        maxZoom = 6;
      }
      default -> {
        return;
      }
    }
    features.polygon(WaterPolygons.LAYER)
      .setZoomRange(minZoom, maxZoom)
      // generalized low-zoom coverage features: keep them whole and seamless across tiles
      .setMinPixelSize(0)
      .setBufferPixels(4)
      .setAttr("kind", "glacier");
  }
}
