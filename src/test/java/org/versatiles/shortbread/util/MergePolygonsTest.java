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

  private static VectorTile.Feature feature(long id, org.locationtech.jts.geom.Geometry geom) {
    return new VectorTile.Feature("ocean", id, VectorTile.encodeGeometry(geom), Map.of());
  }

  @Test
  void dropsNonPolygonalFeatures() throws Exception {
    // a real ocean polygon, plus a degenerate sliver that snapped to a linestring at low zoom
    var polygon = feature(1, TestUtils.newPolygon(0, 0, 100, 0, 100, 100, 0, 100, 0, 0));
    var line = feature(2, TestUtils.newLineString(0, 0, 100, 100));

    var result = new MergePolygons("ocean", 1).postProcess(0, List.of(polygon, line));

    // the linestring is dropped; the layer keeps only polygon-typed features (so it never encodes as MultiLineString)
    assertTrue(result.stream().allMatch(f -> f.geometry().geomType() == GeometryType.POLYGON));
    assertEquals(1, result.size());
  }
}
