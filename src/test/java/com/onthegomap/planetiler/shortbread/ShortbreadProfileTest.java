package com.onthegomap.planetiler.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class ShortbreadProfileTest {

  private final Shortbread profile = new Shortbread(PlanetilerConfig.defaults());

  private List<FeatureCollector.Feature> process(Geometry geom, Map<String, Object> tags) {
    SourceFeature sf = SimpleFeature.create(geom, tags, Shortbread.OSM_SOURCE, null, 1);
    return TestUtils.processSourceFeature(sf, profile);
  }

  private FeatureCollector.Feature onlyOne(List<FeatureCollector.Feature> features, String layer) {
    var matches = features.stream().filter(f -> f.getLayer().equals(layer)).toList();
    assertEquals(1, matches.size(), () -> "expected exactly one " + layer + " feature in " +
      features.stream().map(FeatureCollector.Feature::getLayer).toList());
    return matches.get(0);
  }

  private Map<String, Object> attrs(FeatureCollector.Feature f) {
    return f.getAttrsAtZoom(14);
  }

  @Test
  void lakeBecomesWaterPolygonWithLabel() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("natural", "water", "name", "Lake Test"));
    assertEquals("water", attrs(onlyOne(features, "water_polygons")).get("kind"));
    var label = onlyOne(features, "water_polygons_labels");
    assertEquals("water", attrs(label).get("kind"));
    assertEquals("Lake Test", attrs(label).get("name"));
  }

  @Test
  void riverBecomesWaterLine() {
    var features = process(TestUtils.newLineString(0, 0, 0.5, 0.5, 1, 1),
      Map.of("waterway", "river"));
    var line = onlyOne(features, "water_lines");
    assertEquals("river", attrs(line).get("kind"));
    assertEquals(false, attrs(line).get("tunnel"));
  }

  @Test
  void building() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "yes"));
    var b = onlyOne(features, "buildings");
    assertEquals(1, attrs(b).get("dummy"));
    assertEquals(14, b.getMinZoom());
  }

  @Test
  void buildingNoIsSkipped() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "no"));
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("buildings")));
  }

  @Test
  void forestLand() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("natural", "wood"));
    assertEquals("forest", attrs(onlyOne(features, "land")).get("kind"));
  }

  @Test
  void sitesUsesCorrectedSportsCentreSpelling() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("leisure", "sports_centre"));
    assertEquals("sports_centre", attrs(onlyOne(features, "sites")).get("kind"));
  }

  @Test
  void damPolygon() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("waterway", "dam"));
    assertEquals("dam", attrs(onlyOne(features, "dam_polygons")).get("kind"));
  }

  @Test
  void bankPoiHasAtmFlagAndSuppressesAddress() {
    var features = process(TestUtils.newPoint(0, 0),
      Map.of("amenity", "bank", "name", "Bank", "addr:housenumber", "5"));
    var poi = onlyOne(features, "pois");
    assertEquals("bank", attrs(poi).get("amenity"));
    assertEquals(false, attrs(poi).get("atm"));
    // a POI is not also written to addresses
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("addresses")));
  }

  @Test
  void officePoiUsesOfficeWhitelist() {
    // DEVIATION: office validated against office whitelist, not highway
    var features = process(TestUtils.newPoint(0, 0), Map.of("office", "diplomatic"));
    assertEquals("diplomatic", attrs(onlyOne(features, "pois")).get("office"));
  }

  @Test
  void plainAddressNode() {
    var features = process(TestUtils.newPoint(0, 0), Map.of("addr:housenumber", "5"));
    var addr = onlyOne(features, "addresses");
    assertEquals("5", attrs(addr).get("housenumber"));
    assertNull(attrs(addr).get("housename"));
  }

  @Test
  void nameFallbackOnPoi() throws GeometryException {
    var features = process(TestUtils.newPoint(0, 0), Map.of("amenity", "bank", "name:de", "Bankhaus"));
    var poi = onlyOne(features, "pois");
    // no `name`, so name falls back to name:de
    assertEquals("Bankhaus", attrs(poi).get("name"));
    assertEquals("Bankhaus", attrs(poi).get("name_de"));
    assertEquals("Bankhaus", attrs(poi).get("name_en"));
  }
}
