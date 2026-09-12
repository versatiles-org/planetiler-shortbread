package org.versatiles.shortbread.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class MergeLinesTest {

  private static final int MAX_ZOOM = 14;
  // Planetiler's own max-zoom tolerance: 1/16 tile pixel, one unit of the 4096-unit tile grid
  private static final double TOLERANCE_AT_MAX_ZOOM = 256d / 4096;
  private static final Map<String, Object> STREET = Map.of("kind", "residential");

  private static VectorTile.Feature feature(long id, Geometry geom) {
    return new VectorTile.Feature("streets", id, VectorTile.encodeGeometry(geom), STREET);
  }

  private static MergeLines merger() {
    return new MergeLines("streets", TOLERANCE_AT_MAX_ZOOM, MAX_ZOOM);
  }

  private static int pointCount(VectorTile.Feature feature) throws Exception {
    return feature.geometry().decode().getNumPoints();
  }

  @Test
  void dropsShortLinesBelowMaxZoom() throws Exception {
    // a third of a tile pixel, below the half-pixel minimum length
    var stub = feature(1, TestUtils.newLineString(0, 0, 0.3, 0));

    var result = merger().postProcess(MAX_ZOOM - 1, List.of(stub));

    assertTrue(result.isEmpty(), () -> "expected the sub-pixel stub to be dropped, got " + result);
  }

  @Test
  void keepsShortLinesAtMaxZoom() throws Exception {
    // the same stub survives at the max zoom, which is the base for overzooming
    var stub = feature(1, TestUtils.newLineString(0, 0, 0.3, 0));

    var result = merger().postProcess(MAX_ZOOM, List.of(stub));

    assertEquals(1, result.size(), () -> "expected the sub-pixel stub to survive, got " + result);
  }

  @Test
  void mergesConnectedLinesAtEveryZoom() throws Exception {
    var west = feature(1, TestUtils.newLineString(0, 0, 10, 0));
    var east = feature(2, TestUtils.newLineString(10, 0, 20, 0));

    // joining connected lines only lowers the feature count, so it is worth doing at the max zoom too
    assertEquals(1, merger().postProcess(MAX_ZOOM - 1, List.of(west, east)).size());
    assertEquals(1, merger().postProcess(MAX_ZOOM, List.of(west, east)).size());
  }

  @Test
  void dropsRedundantPointsAtEveryZoom() throws Exception {
    // the middle point lies exactly on the line between its neighbours: it carries no information at any zoom
    var collinear = feature(1, TestUtils.newLineString(0, 0, 10, 0, 20, 0));

    assertEquals(2, pointCount(merger().postProcess(MAX_ZOOM - 1, List.of(collinear)).getFirst()));
    assertEquals(2, pointCount(merger().postProcess(MAX_ZOOM, List.of(collinear)).getFirst()));
  }

  @Test
  void keepsRealDetailAtMaxZoom() throws Exception {
    // a bend of four grid units is far above the max-zoom tolerance and must survive for overzooming
    var bent = feature(1, TestUtils.newLineString(0, 0, 10, 0.25, 20, 0));

    assertEquals(3, pointCount(merger().postProcess(MAX_ZOOM, List.of(bent)).getFirst()));
  }
}
