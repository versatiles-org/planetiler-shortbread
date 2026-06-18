package com.onthegomap.planetiler.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.util.CountryLanguages;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class ShortbreadProfileTest {

  // most tests exercise the profile with all beyond-spec experiments enabled; strict-spec defaults are covered by
  // experimentsAreOffByDefault below
  private final Shortbread profile =
    new Shortbread(PlanetilerConfig.from(Arguments.of(Map.of("shortbread_experiments", "all"))));

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
  void waterPolygonLabelFollowsPolygonZoom() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("natural", "water", "name", "Lake Test"));
    var poly = onlyOne(features, "water_polygons");
    var label = onlyOne(features, "water_polygons_labels");
    // spec: water label appears with its polygon (area-based), not only at z14
    assertEquals(poly.getMinZoom(), label.getMinZoom());
    assertTrue(label.getMinZoom() < 14);
  }

  @Test
  void waterLabelPointFallsInsideConcavePolygon() {
    // U-shaped lake (base + two prongs) whose area-weighted centroid lands in the notch between the
    // prongs, i.e. OUTSIDE the polygon. A centroid-based label would be placed in the water gap;
    // pointOnSurface must place it on the polygon itself.
    var polygon = TestUtils.newPolygon(0, 0, 3, 0, 3, 3, 2, 3, 2, 1, 1, 1, 1, 3, 0, 3, 0, 0);
    // precondition: prove the centroid really is outside, so this test would fail with centroid()
    assertFalse(polygon.covers(polygon.getCentroid()),
      () -> "test precondition broken: centroid " + polygon.getCentroid() + " should be outside the U-shape");

    var features = process(polygon, Map.of("natural", "water", "name", "U Lake"));
    var label = onlyOne(features, "water_polygons_labels");
    // getGeometry() is in world web-mercator coords; convert back to lat/lon to compare with the input
    var labelLatLon = GeoUtils.worldToLatLonCoords(label.getGeometry());
    assertTrue(polygon.covers(labelLatLon),
      () -> "water_polygons_labels point must lie inside the polygon, got " + labelLatLon);
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
    // EXPERIMENT: untagged building gets the 5m default height, no min_height
    assertEquals(5, attrs(b).get("height"));
    assertNull(attrs(b).get("min_height"));
  }

  @Test
  void buildingHeightFromLevels() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("building", "yes", "building:levels", "4"));
    var b = onlyOne(features, "buildings");
    assertEquals(15, attrs(b).get("height")); // ceil(4 * 3.66) = ceil(14.64)
    assertNull(attrs(b).get("min_height"));
  }

  @Test
  void buildingExplicitHeightAndMinHeight() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("building", "yes", "height", "12.5", "min_height", "3"));
    var b = onlyOne(features, "buildings");
    assertEquals(13, attrs(b).get("height")); // ceil(12.5)
    assertEquals(3, attrs(b).get("min_height")); // floor(3)
  }

  @Test
  void pierPolygonMinZoom() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("man_made", "pier"));
    var pier = onlyOne(features, "pier_polygons");
    assertEquals("pier", attrs(pier).get("kind"));
    assertEquals(12, pier.getMinZoom()); // spec-aligned, not z13
  }

  @Test
  void trunkStreetMinZoom() {
    var street = onlyOne(process(TestUtils.newLineString(0, 0, 1, 1), Map.of("highway", "trunk")), "streets");
    assertEquals(6, street.getMinZoom());
  }

  @Test
  void serviceStreetMinZoom() {
    var street = onlyOne(process(TestUtils.newLineString(0, 0, 1, 1), Map.of("highway", "service")), "streets");
    assertEquals(13, street.getMinZoom()); // spec: service from z13 (was z14)
  }

  @Test
  void buildingNoIsSkipped() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "no"));
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("buildings")));
  }

  @Test
  void buildingPartWithHeightEmitted() {
    // EXPERIMENT (Simple 3D Buildings): a building:part carrying height info is emitted into the buildings layer
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("building:part", "yes", "building:levels", "10"));
    var b = onlyOne(features, "buildings");
    assertEquals(1, attrs(b).get("dummy"));
    assertEquals(37, attrs(b).get("height")); // ceil(10 * 3.66) = ceil(36.6)
  }

  @Test
  void bareBuildingPartWithoutHeightIsSkipped() {
    // a building:part with no height info is not 3D-relevant → not emitted (keeps flat 2D styles clean)
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building:part", "yes"));
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("buildings")));
  }

  @Test
  void buildingPartNoIsSkipped() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("building:part", "no", "height", "10"));
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("buildings")));
  }

  @Test
  void buildingOutlineOfRelationIsHidden3d() {
    // the outline member of a type=building relation keeps its 2D footprint but is flagged hide_3d so its parts,
    // not the outline, get extruded
    var relation = new OsmElement.Relation(1);
    relation.setTag("type", "building");
    OsmRelationInfo info = profile.preprocessOsmRelation(relation).get(0);
    var member = new OsmReader.RelationMember<>("outline", info);
    SourceFeature way = SimpleFeature.createFakeOsmFeature(
      TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "yes"), Shortbread.OSM_SOURCE, null, 2,
      List.of(member));
    var features = TestUtils.processSourceFeature(way, profile);
    var b = onlyOne(features, "buildings");
    assertEquals(true, attrs(b).get("hide_3d"));
  }

  @Test
  void standaloneBuildingHasNoHide3d() {
    var b = onlyOne(process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "yes")),
      "buildings");
    assertNull(attrs(b).get("hide_3d"));
  }

  @Test
  void resourceEstimatesScaleWithInput() {
    long planet = 80_000_000_000L; // ~80 GB planet.osm.pbf
    long output = profile.estimateOutputBytes(planet);
    long intermediate = profile.estimateIntermediateDiskBytes(planet);
    long ram = profile.estimateRamRequired(planet);
    // all non-zero (the default Profile returns 0, which would silently disable the disk/RAM pre-check)
    assertTrue(output > 0 && intermediate > 0 && ram > 0);
    // intermediate feature storage is the dominant figure and exceeds both the input and the output
    assertTrue(intermediate > planet);
    assertTrue(intermediate > output);
    // measured: Shortbread output (~0.8x) is smaller than the input pbf — much leaner than OpenMapTiles
    assertTrue(output < planet);
    // RAM estimate stays well under the input size (memory-mapped node cache keeps heap modest)
    assertTrue(ram < planet / 2);
  }

  @Test
  void experimentsAreOffByDefault() {
    // default profile (no --shortbread_experiments) is strict spec: none of the beyond-spec extras are emitted
    Shortbread strict = new Shortbread(PlanetilerConfig.defaults());

    var building = onlyOne(TestUtils.processSourceFeature(SimpleFeature.create(
      TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building", "yes", "building:levels", "5"),
      Shortbread.OSM_SOURCE, null, 1), strict), "buildings");
    assertEquals(1, attrs(building).get("dummy"));
    assertNull(attrs(building).get("height")); // BUILDING_HEIGHTS off

    // a height-bearing building:part is not emitted at all (BUILDING_PARTS off)
    assertTrue(TestUtils.processSourceFeature(SimpleFeature.create(
      TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("building:part", "yes", "height", "10"),
      Shortbread.OSM_SOURCE, null, 1), strict).stream().noneMatch(f -> f.getLayer().equals("buildings")));

    // bridge gets no name (BRIDGE_NAMES off)
    var bridge = onlyOne(TestUtils.processSourceFeature(SimpleFeature.create(
      TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("man_made", "bridge", "name", "Tower Bridge"),
      Shortbread.OSM_SOURCE, null, 1), strict), "bridges");
    assertNull(attrs(bridge).get("name"));

    // address gets no unit/block (ADDRESS_DETAILS off)
    var addr = onlyOne(TestUtils.processSourceFeature(SimpleFeature.create(TestUtils.newPoint(0, 0),
      Map.of("addr:housenumber", "5", "addr:unit", "B"), Shortbread.OSM_SOURCE, null, 1), strict), "addresses");
    assertEquals("5", attrs(addr).get("housenumber"));
    assertNull(attrs(addr).get("unit"));
  }

  @Test
  void forestLand() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("natural", "wood"));
    assertEquals("forest", attrs(onlyOne(features, "land")).get("kind"));
  }

  @Test
  void landGaragesMinZoom() {
    var land = onlyOne(process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0), Map.of("landuse", "garages")), "land");
    assertEquals("garages", attrs(land).get("kind"));
    assertEquals(10, land.getMinZoom()); // spec: garages from z10 (was z12)
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
    assertNull(attrs(addr).get("unit"));
  }

  @Test
  void addressUnitAndBlock() {
    var features = process(TestUtils.newPoint(0, 0),
      Map.of("addr:housenumber", "5", "addr:unit", "A", "addr:block", "12"));
    var addr = onlyOne(features, "addresses");
    assertEquals("5", attrs(addr).get("housenumber"));
    assertEquals("A", attrs(addr).get("unit"));
    assertEquals("12", attrs(addr).get("block"));
  }

  @Test
  void bridgeWithName() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("man_made", "bridge", "name", "Tower Bridge"));
    var bridge = onlyOne(features, "bridges");
    assertEquals("bridge", attrs(bridge).get("kind"));
    assertEquals("Tower Bridge", attrs(bridge).get("name"));
  }

  @Test
  void motorwayStreetWithAttributeTiers() {
    var features = process(TestUtils.newLineString(0, 0, 0.5, 0.5, 1, 1),
      Map.of("highway", "motorway", "surface", "asphalt", "oneway", "yes"));
    var street = onlyOne(features, "streets");
    assertEquals(5, street.getMinZoom());
    // low tier (z5-10): only kind + rail
    var z5 = street.getAttrsAtZoom(5);
    assertEquals("motorway", z5.get("kind"));
    assertNull(z5.get("surface"));
    assertNull(z5.get("oneway"));
    // mid tier (z11): surface/tunnel/bridge appear
    var z11 = street.getAttrsAtZoom(11);
    assertEquals("paved", z11.get("surface"));
    assertEquals(false, z11.get("tunnel"));
    assertNull(z11.get("oneway"));
    // full tier (z14): oneway appears
    assertEquals(true, street.getAttrsAtZoom(14).get("oneway"));
  }

  @Test
  void railwayStreet() {
    var features = process(TestUtils.newLineString(0, 0, 1, 1), Map.of("railway", "rail"));
    var street = onlyOne(features, "streets");
    assertEquals("rail", attrs(street).get("kind"));
    assertEquals(true, attrs(street).get("rail"));
  }

  @Test
  void streetLabelWithRefGrid() {
    var features = process(TestUtils.newLineString(0, 0, 1, 1),
      Map.of("highway", "motorway", "ref", "A1;A2;A3"));
    var label = onlyOne(features, "street_labels");
    assertEquals("motorway", attrs(label).get("kind"));
    assertEquals("A1\nA2\nA3", attrs(label).get("ref"));
    assertEquals(3, attrs(label).get("ref_rows"));
    assertEquals(2, attrs(label).get("ref_cols"));
  }

  @Test
  void streetLabelRefSkipsEmptySegments() {
    // an empty segment from "A1;;B22" must be dropped, not rendered as a blank shield row
    var features = process(TestUtils.newLineString(0, 0, 1, 1),
      Map.of("highway", "motorway", "ref", "A1;;B22"));
    var label = onlyOne(features, "street_labels");
    assertEquals("A1\nB22", attrs(label).get("ref"));
    assertEquals(2, attrs(label).get("ref_rows"));
    assertEquals(3, attrs(label).get("ref_cols")); // widest of "A1"(2) and "B22"(3)
  }

  @Test
  void railwayLineGetsLabel() {
    // spec: railways are labelled in street_labels from z10
    var features = process(TestUtils.newLineString(0, 0, 1, 1), Map.of("railway", "rail", "name", "Main Line"));
    var label = onlyOne(features, "street_labels");
    assertEquals("rail", attrs(label).get("kind"));
    assertEquals("Main Line", attrs(label).get("name"));
    assertEquals(10, label.getMinZoom());
  }

  @Test
  void motorwayJunctionPoint() {
    var features = process(TestUtils.newPoint(0, 0),
      Map.of("highway", "motorway_junction", "ref", "12", "name", "Exit"));
    var p = onlyOne(features, "street_labels_points");
    assertEquals("motorway_junction", attrs(p).get("kind"));
    assertEquals("12", attrs(p).get("ref"));
  }

  @Test
  void aerialwayLine() {
    var features = process(TestUtils.newLineString(0, 0, 1, 1), Map.of("aerialway", "gondola"));
    assertEquals("gondola", attrs(onlyOne(features, "aerialways")).get("kind"));
  }

  @Test
  void ferryLine() {
    var features = process(TestUtils.newLineString(0, 0, 1, 1), Map.of("route", "ferry", "name", "Ferry"));
    var ferry = onlyOne(features, "ferries");
    assertEquals("ferry", attrs(ferry).get("kind"));
    assertEquals(10, ferry.getMinZoom());
  }

  @Test
  void publicTransportStationUsesPerKindZoom() {
    var features = process(TestUtils.newPoint(0, 0), Map.of("railway", "station", "name", "Hbf"));
    var pt = onlyOne(features, "public_transport");
    assertEquals("station", attrs(pt).get("kind"));
    // DEVIATION: per-kind zoom (13) instead of the hard-coded 11
    assertEquals(13, pt.getMinZoom());
  }

  @Test
  void boundaryRelationIsCaptured() {
    var relation = new OsmElement.Relation(1);
    relation.setTag("type", "boundary");
    relation.setTag("boundary", "administrative");
    relation.setTag("admin_level", "2");
    var infos = profile.preprocessOsmRelation(relation);
    assertEquals(1, infos.size());
  }

  @Test
  void boundaryLineFromMemberWay() {
    var relation = new OsmElement.Relation(1);
    relation.setTag("type", "boundary");
    relation.setTag("boundary", "administrative");
    relation.setTag("admin_level", "2");
    OsmRelationInfo info = profile.preprocessOsmRelation(relation).get(0);
    var member = new OsmReader.RelationMember<>("outer", info);
    SourceFeature way = SimpleFeature.createFakeOsmFeature(
      TestUtils.newLineString(0, 0, 1, 1), Map.of(), Shortbread.OSM_SOURCE, null, 2, List.of(member));

    var features = TestUtils.processSourceFeature(way, profile);
    var line = onlyOne(features, "boundaries");
    assertEquals(2, attrs(line).get("admin_level"));
    assertEquals(0, line.getMinZoom());
    assertEquals(false, attrs(line).get("disputed"));
  }

  @Test
  void adminLevel3BoundaryIsDropped() {
    // Shortbread boundaries only has admin_level 2 and 4; level-3 relations must not be captured...
    var relation = new OsmElement.Relation(1);
    relation.setTag("type", "boundary");
    relation.setTag("boundary", "administrative");
    relation.setTag("admin_level", "3");
    assertNull(profile.preprocessOsmRelation(relation));

    // ...and a directly-tagged admin_level=3 way produces no boundaries line
    var features = process(TestUtils.newLineString(0, 0, 1, 1),
      Map.of("boundary", "administrative", "admin_level", "3"));
    assertTrue(features.stream().noneMatch(f -> f.getLayer().equals("boundaries")),
      () -> "expected no boundaries feature, got " +
        features.stream().map(FeatureCollector.Feature::getLayer).toList());
  }

  @Test
  void boundaryLabelFromAdminPolygon() {
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("boundary", "administrative", "admin_level", "2", "name", "Country"));
    var label = onlyOne(features, "boundary_labels");
    assertEquals(2, attrs(label).get("admin_level"));
    assertEquals("Country", attrs(label).get("name"));
  }

  @Test
  void boundaryLabelUsesMercatorArea() {
    // a ~71,000 km² (geodesic) country at latitude 55: geodesic < 1e5 km² → would be z5, but its Web-Mercator area
    // is ~2.2e5 km² (>= 1e5) so the spec puts it at z4. Confirms gating + way_area use Mercator, not geodesic.
    var features = process(TestUtils.newPolygon(0, 55, 10, 55, 10, 56, 0, 56, 0, 55),
      Map.of("boundary", "administrative", "admin_level", "2", "name", "Highland"));
    var label = onlyOne(features, "boundary_labels");
    assertEquals(4, label.getMinZoom());
  }

  @Test
  void placeLabelCityWithDefaultPopulation() {
    var features = process(TestUtils.newPoint(0, 0), Map.of("place", "city", "name", "Metropolis"));
    var place = onlyOne(features, "place_labels");
    assertEquals("city", attrs(place).get("kind"));
    assertEquals(6, place.getMinZoom());
    assertEquals(100_000L, attrs(place).get("population"));
  }

  @Test
  void placeLabelCapital() {
    var features = process(TestUtils.newPoint(0, 0),
      Map.of("place", "city", "capital", "yes", "name", "Capital City", "population", "2000000"));
    var place = onlyOne(features, "place_labels");
    assertEquals("capital", attrs(place).get("kind"));
    assertEquals(4, place.getMinZoom());
    assertEquals(2_000_000L, attrs(place).get("population"));
  }

  @Test
  void islandPolygonGetsAreaRankedLabel() {
    // ~1°×1° polygon near the equator ≈ 1.2e10 m² (>= 160M) → big-island min zoom 8
    var features = process(TestUtils.newPolygon(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      Map.of("place", "island", "name", "Big Isle"));
    var label = onlyOne(features, "place_labels");
    assertEquals("island", attrs(label).get("kind"));
    assertEquals("Big Isle", attrs(label).get("name"));
    assertEquals(8, label.getMinZoom());
  }

  @Test
  void islandPointStillLabeled() {
    var features = process(TestUtils.newPoint(0, 0), Map.of("place", "island", "name", "Isle"));
    var label = onlyOne(features, "place_labels");
    assertEquals("island", attrs(label).get("kind"));
    assertEquals(10, label.getMinZoom());
  }

  @Test
  void placeLabelsRankByPopulation() {
    var big = onlyOne(process(TestUtils.newPoint(0, 0),
      Map.of("place", "city", "name", "Big", "population", "5000000")), "place_labels");
    var small = onlyOne(process(TestUtils.newPoint(0, 0),
      Map.of("place", "city", "name", "Small", "population", "20000")), "place_labels");
    // for label layers a lower sort key shows at lower zooms (higher priority), so the bigger city must win
    assertTrue(big.getSortKey() < small.getSortKey(),
      () -> "big city sortKey " + big.getSortKey() + " should be < small city " + small.getSortKey());
  }

  @Test
  void nameTranslationsWithoutFallback() {
    var features = process(TestUtils.newPoint(0, 0),
      Map.of("amenity", "bank", "name", "Bank", "name:de", "Bankhaus"));
    var poi = onlyOne(features, "pois");
    assertEquals("Bank", attrs(poi).get("name"));
    assertEquals("Bankhaus", attrs(poi).get("name_de"));
    // no name:en tag and no fallback, so name_en is unset
    assertNull(attrs(poi).get("name_en"));
  }

  // a generous lon/lat box around Germany, fed to the profile's CountryLanguages handler to populate its index
  private static final Geometry GERMANY_BOX = TestUtils.newPolygon(6, 47, 15, 47, 15, 55, 6, 55, 6, 47);

  /** Populate the profile's country->language index by running an admin_0 polygon through it (stages run in order). */
  private void indexCountry(String iso, Geometry latLonPolygon) {
    SourceFeature country = SimpleFeature.create(latLonPolygon, Map.of("ISO_A2", iso),
      CountryLanguages.SOURCE, null, 1);
    TestUtils.processSourceFeature(country, profile);
  }

  @Test
  void nameDeFallbackInsideGermany() {
    indexCountry("DE", GERMANY_BOX);
    // a feature tagged only with `name`, inside Germany, gets name_de = name
    var poi = onlyOne(process(TestUtils.newPoint(10, 51),
      Map.of("amenity", "bank", "name", "Sparkasse")), "pois");
    assertEquals("Sparkasse", attrs(poi).get("name"));
    assertEquals("Sparkasse", attrs(poi).get("name_de"));
  }

  @Test
  void noNameDeFallbackOutsideIndexedCountries() {
    indexCountry("DE", GERMANY_BOX);
    // outside the indexed German polygon (and no French polygon indexed): no fallback
    var poi = onlyOne(process(TestUtils.newPoint(2, 48),
      Map.of("amenity", "bank", "name", "Banque")), "pois");
    assertEquals("Banque", attrs(poi).get("name"));
    assertNull(attrs(poi).get("name_de"));
  }

  @Test
  void explicitNameDeNotOverwrittenByFallback() {
    indexCountry("DE", GERMANY_BOX);
    var poi = onlyOne(process(TestUtils.newPoint(10, 51),
      Map.of("amenity", "bank", "name", "Bank am Dom", "name:de", "Dombank")), "pois");
    // an explicit name:de tag wins over the geofenced fallback
    assertEquals("Dombank", attrs(poi).get("name_de"));
  }
}
