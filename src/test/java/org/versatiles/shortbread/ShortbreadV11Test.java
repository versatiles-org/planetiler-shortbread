package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

/** Verifies the Shortbread 1.1 deltas, and that the 1.0 default is unaffected by them. */
class ShortbreadV11Test {

  private static Shortbread profile(Map<String, String> args) {
    return new Shortbread(PlanetilerConfig.from(Arguments.of(args)));
  }

  private final Shortbread v10 = new Shortbread(PlanetilerConfig.defaults());
  private final Shortbread v11 = profile(Map.of("shortbread_version", "1.1"));

  private List<FeatureCollector.Feature> process(Shortbread profile, Geometry geom, Map<String, Object> tags) {
    SourceFeature sf = SimpleFeature.create(geom, tags, Shortbread.OSM_SOURCE, null, 1);
    return TestUtils.processSourceFeature(sf, profile);
  }

  private Map<String, Object> attrs(FeatureCollector.Feature f) {
    return f.getAttrsAtZoom(14);
  }

  private FeatureCollector.Feature layer(List<FeatureCollector.Feature> features, String layer) {
    return features.stream().filter(f -> f.getLayer().equals(layer)).findFirst().orElse(null);
  }

  @Test
  void fuelIsAPoiOnlyIn11() {
    var tags = Map.<String, Object>of("amenity", "fuel", "name", "Gas");
    assertNull(layer(process(v10, TestUtils.newPoint(0, 0), tags), "pois"));
    assertEquals("fuel", attrs(layer(process(v11, TestUtils.newPoint(0, 0), tags), "pois")).get("amenity"));
  }

  @Test
  void parkIsAPoiOnlyIn11() {
    var tags = Map.<String, Object>of("leisure", "park", "name", "Central Park");
    assertNull(layer(process(v10, TestUtils.newPoint(0, 0), tags), "pois"));
    assertEquals("park", attrs(layer(process(v11, TestUtils.newPoint(0, 0), tags), "pois")).get("leisure"));
  }

  @Test
  void drainIsOnlyInTheOutputIn11() {
    var tags = Map.<String, Object>of("waterway", "drain");
    // 1.0 defines no drain kind at all
    assertNull(layer(process(v10, TestUtils.newLineString(0, 0, 1, 1), tags), "water_lines"));
    assertEquals(14, layer(process(v11, TestUtils.newLineString(0, 0, 1, 1), tags), "water_lines").getMinZoom());
  }

  @Test
  void configurableNameLanguages() {
    var v11fr = profile(Map.of("shortbread_version", "1.1", "name_languages", "en,de,fr"));
    var tags = Map.<String, Object>of("place", "city", "name", "City", "name:fr", "Ville");
    // default 1.0 emits only en/de, so name:fr is ignored
    assertNull(attrs(layer(process(v10, TestUtils.newPoint(0, 0), tags), "place_labels")).get("name_fr"));
    // with fr requested, name_fr is emitted from name:fr
    var place = layer(process(v11fr, TestUtils.newPoint(0, 0), tags), "place_labels");
    assertEquals("Ville", attrs(place).get("name_fr"));
    assertEquals("City", attrs(place).get("name"));
    assertNull(attrs(place).get("name_en"));
  }

  private Map<String, Object> street(Shortbread profile, Map<String, Object> tags, int zoom) {
    return layer(process(profile, TestUtils.newLineString(0, 0, 1, 1), tags), "streets").getAttrsAtZoom(zoom);
  }

  @Test
  void accessAttributesAreMappedAndAvailableFromZoom13In11() {
    var tags = Map.<String, Object>of("highway", "residential", "motorcar", "destination",
      "bicycle", "designated", "foot", "yes", "horse", "private");

    // 1.0: the raw bicycle/horse values, from z14, and no motorcar/foot at all
    assertNull(street(v10, tags, 13).get("bicycle"));
    var v10z14 = street(v10, tags, 14);
    assertEquals("designated", v10z14.get("bicycle"));
    assertEquals("private", v10z14.get("horse"));
    assertNull(v10z14.get("motorcar"));
    assertNull(v10z14.get("foot"));

    // 1.1: all four, normalized, from z13
    var v11z13 = street(v11, tags, 13);
    assertEquals("limited", v11z13.get("motorcar"));
    assertEquals("yes", v11z13.get("bicycle"));
    assertEquals("yes", v11z13.get("foot"));
    assertEquals("no", v11z13.get("horse"));
    assertNull(street(v11, tags, 12).get("bicycle"));
  }

  @Test
  void accessFallsBackAlongTheTagChainIn11() {
    // no specific tags: everything comes from access
    var fromAccess = street(v11, Map.of("highway", "track", "access", "permissive"), 13);
    assertEquals("yes", fromAccess.get("motorcar"));
    assertEquals("yes", fromAccess.get("bicycle"));
    assertEquals("yes", fromAccess.get("foot"));
    assertEquals("yes", fromAccess.get("horse"));

    // motor_vehicle covers motorcar, vehicle covers bicycle, and the more specific tag wins
    var mixed = street(v11, Map.of("highway", "track", "access", "yes", "vehicle", "no", "motor_vehicle", "forestry"),
      13);
    assertEquals("limited", mixed.get("motorcar"));
    assertEquals("no", mixed.get("bicycle"));
    assertEquals("yes", mixed.get("foot"));

    // unrecognized values are skipped, falling through to the next tag in the chain
    var exotic = street(v11, Map.of("highway", "track", "bicycle", "unknown_value", "access", "private"), 13);
    assertEquals("no", exotic.get("bicycle"));
  }

  @Test
  void accessIsAbsentWithoutTagsAndOnRailwaysIn11() {
    var plain = street(v11, Map.of("highway", "residential"), 13);
    for (String attribute : List.of("motorcar", "bicycle", "foot", "horse")) {
      assertNull(plain.get(attribute), attribute + " should be absent when no access tag is present");
    }
    // railways carry no access attributes even when tagged
    var rail = street(v11, Map.of("railway", "rail", "access", "private"), 13);
    for (String attribute : List.of("motorcar", "bicycle", "foot", "horse")) {
      assertNull(rail.get(attribute), attribute + " should be absent on railways");
    }
  }

  @Test
  void metadataReportsVersion() {
    assertEquals("1.0", v10.version());
    assertEquals("1.1", v11.version());
    assertTrue(v10.name().equals("Shortbread"));
  }
}
