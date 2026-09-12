package org.versatiles.shortbread.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergePolygonsTest {

  private static final int MAX_ZOOM = 14;
  // Planetiler's own max-zoom threshold: a 1/16 tile pixel length, squared into an area
  private static final double MIN_AREA_AT_MAX_ZOOM = (256d / 4096) * (256d / 4096);

  private static VectorTile.Feature feature(long id, org.locationtech.jts.geom.Geometry geom) {
    return new VectorTile.Feature("ocean", id, VectorTile.encodeGeometry(geom), Map.of());
  }

  private static MergePolygons merger() {
    return new MergePolygons("ocean", 1, MIN_AREA_AT_MAX_ZOOM, MAX_ZOOM);
  }

  @Test
  void dropsNonPolygonalFeatures() throws Exception {
    // a real ocean polygon, plus a degenerate sliver that snapped to a linestring at low zoom
    var polygon = feature(1, TestUtils.newPolygon(0, 0, 100, 0, 100, 100, 0, 100, 0, 0));
    var line = feature(2, TestUtils.newLineString(0, 0, 100, 100));

    var result = merger().postProcess(0, List.of(polygon, line));

    // the linestring is dropped; the layer keeps only polygon-typed features (so it never encodes as MultiLineString)
    assertTrue(result.stream().allMatch(f -> f.geometry().geomType() == GeometryType.POLYGON));
    assertEquals(1, result.size());
  }

  @Test
  void dropsSubPixelPolygonsBelowMaxZoom() throws Exception {
    // a quarter of a square tile pixel, below the one-pixel minimum area
    var tiny = feature(1, TestUtils.newPolygon(0, 0, 0.5, 0, 0.5, 0.5, 0, 0.5, 0, 0));

    var result = merger().postProcess(MAX_ZOOM - 1, List.of(tiny));

    assertTrue(result.isEmpty(), () -> "expected the sub-pixel polygon to be dropped, got " + result);
  }

  @Test
  void keepsSubPixelPolygonsAtMaxZoom() throws Exception {
    // the same polygon survives at the max zoom, which is the base for overzooming
    var tiny = feature(1, TestUtils.newPolygon(0, 0, 0.5, 0, 0.5, 0.5, 0, 0.5, 0, 0));

    var result = merger().postProcess(MAX_ZOOM, List.of(tiny));

    assertEquals(1, result.size(), () -> "expected the sub-pixel polygon to survive, got " + result);
  }

  @Test
  void dropsNonPolygonalFeaturesAtMaxZoom() throws Exception {
    // the geometry-type filter is not relaxed at the max zoom: `ocean` must stay polygon-only at every zoom
    var polygon = feature(1, TestUtils.newPolygon(0, 0, 100, 0, 100, 100, 0, 100, 0, 0));
    var line = feature(2, TestUtils.newLineString(0, 0, 100, 100));

    var result = merger().postProcess(MAX_ZOOM, List.of(polygon, line));

    assertTrue(result.stream().allMatch(f -> f.geometry().geomType() == GeometryType.POLYGON));
    assertEquals(1, result.size());
  }
}
