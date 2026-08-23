package org.versatiles.shortbread.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShortbreadUtilTest {

  private static SourceFeature point(Map<String, Object> tags) {
    return SimpleFeature.create(TestUtils.newPoint(0, 0), tags);
  }

  @Test
  void surfaceCanonicalization() {
    assertEquals("unpaved", Surface.canonicalize("gravel"));
    assertEquals("unpaved", Surface.canonicalize("unpaved"));
    // DEVIATION from Tilemaker bug: paved surfaces map to "paved", not "unpaved"
    assertEquals("paved", Surface.canonicalize("asphalt"));
    assertEquals("paved", Surface.canonicalize("paved"));
    assertNull(Surface.canonicalize("something_else"));
    assertNull(Surface.canonicalize(""));
    assertNull(Surface.canonicalize(null));
  }

  @Test
  void zminDecreasesWithSize() {
    // inputs are fractions of the planet; a bigger feature becomes visible at a lower zoom
    assertTrue(Zooms.zminForArea(0.1, 1e-3) < Zooms.zminForArea(0.1, 1e-9));
    assertTrue(Zooms.zminForLength(0.25, 1e-2) < Zooms.zminForLength(0.25, 1e-7));
  }

  @Test
  void zOrderBooleans() {
    assertTrue(ZOrder.isTunnel(point(Map.of("tunnel", "culvert"))));
    assertTrue(ZOrder.isTunnel(point(Map.of("covered", "yes"))));
    assertFalse(ZOrder.isTunnel(point(Map.of("tunnel", "no"))));
    assertTrue(ZOrder.isBridge(point(Map.of("bridge", "viaduct"))));
    assertFalse(ZOrder.isBridge(point(Map.of("bridge", "no"))));
    assertTrue(ZOrder.isOneway(point(Map.of("oneway", "-1"))));
    assertTrue(ZOrder.isReverseOneway(point(Map.of("oneway", "-1"))));
    assertFalse(ZOrder.isReverseOneway(point(Map.of("oneway", "yes"))));
    assertEquals(7, ZOrder.layer(point(Map.of("layer", "9")))); // clamped
    assertEquals(0, ZOrder.layer(point(Map.of())));
  }

  @Test
  void zOrderRanksBridgesAboveTunnels() {
    int bridge = ZOrder.zOrder(point(Map.of("highway", "motorway", "bridge", "yes")), false, false);
    int plain = ZOrder.zOrder(point(Map.of("highway", "motorway")), false, false);
    int tunnel = ZOrder.zOrder(point(Map.of("highway", "motorway", "tunnel", "yes")), false, false);
    assertTrue(bridge > plain);
    assertTrue(plain > tunnel);
  }

  @Test
  void poiMatching() {
    assertTrue(Poi.matches(point(Map.of("amenity", "bank"))));
    assertFalse(Poi.matches(point(Map.of("amenity", "not_a_real_amenity"))));
    // DEVIATION: office validated against the office whitelist (not highway)
    assertTrue(Poi.matches(point(Map.of("office", "diplomatic"))));
    assertFalse(Poi.matches(point(Map.of("office", "company"))));
    // DEVIATION: man_made participates in the POI gate
    assertTrue(Poi.matches(point(Map.of("man_made", "lighthouse"))));
    assertFalse(Poi.matches(point(Map.of("building", "yes"))));
  }
}
