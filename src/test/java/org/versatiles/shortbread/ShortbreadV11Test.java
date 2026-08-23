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
import org.locationtech.jts.geom.Geometry;
import org.junit.jupiter.api.Test;

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
  void drainMovesToZoom14In11() {
    var tags = Map.<String, Object>of("waterway", "drain");
    assertEquals(13, layer(process(v10, TestUtils.newLineString(0, 0, 1, 1), tags), "water_lines").getMinZoom());
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

  @Test
  void metadataReportsVersion() {
    assertEquals("1.0", v10.version());
    assertEquals("1.1", v11.version());
    assertTrue(v10.name().equals("Shortbread"));
  }
}
