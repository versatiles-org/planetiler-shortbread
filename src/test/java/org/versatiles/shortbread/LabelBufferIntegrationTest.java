package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carrotsearch.hppc.LongArrayList;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.archive.TileArchiveMetadata;
import com.onthegomap.planetiler.archive.TileArchiveWriter;
import com.onthegomap.planetiler.collection.FeatureGroup;
import com.onthegomap.planetiler.collection.LongLongMap;
import com.onthegomap.planetiler.collection.LongLongMultimap;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.TileCoord;
import com.onthegomap.planetiler.geo.TileOrder;
import com.onthegomap.planetiler.mbtiles.Mbtiles;
import com.onthegomap.planetiler.reader.osm.OsmBlockSource;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Gzip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Runs the whole pipeline over synthetic OSM data with label points just outside a tile and checks that the tile still
 * contains them. Renderers that draw one tile at a time (server-side raster rendering with MapLibre Native) can only
 * draw the part of a label that reaches into a tile if the tile contains the label's point; otherwise the label is cut
 * at the tile edge.
 */
class LabelBufferIntegrationTest {

  /** How far west of the tile edge the label points lie, in tile pixels (a 256px tile). */
  private static final double OFFSET_PIXELS = 20;

  // the city lies west of the west edge of this z13 tile (around 11.25°E, 50.5°N)
  private static final TileCoord CITY_TILE = TileCoord.ofXYZ(4352, 2730, 13);
  // the country's label point lies west of the west edge of this z5 tile
  private static final TileCoord COUNTRY_TILE = TileCoord.ofXYZ(17, 10, 5);

  @Test
  void placeLabelJustOutsideATileIsInThatTile() throws Exception {
    try (var db = generate()) {
      assertLabel(db, west(CITY_TILE), "place_labels", "Edge City");
      assertLabel(db, CITY_TILE, "place_labels", "Edge City");
    }
  }

  @Test
  void countryLabelJustOutsideATileIsInThatTile() throws Exception {
    try (var db = generate()) {
      assertLabel(db, west(COUNTRY_TILE), "boundary_labels", "Edgeland");
      assertLabel(db, COUNTRY_TILE, "boundary_labels", "Edgeland");
    }
  }

  private static List<OsmElement> osmElements() {
    List<OsmElement> elements = new ArrayList<>();

    // The city: a place node, which becomes a place_labels point at its own position. It lies OFFSET_PIXELS west of
    // CITY_TILE, in the tile to the west. z13 is above the label grid zooms (up to z12, with a 64px buffer), where
    // place points used to get only Planetiler's default 4px buffer. A city is labelled at z13 in common styles.
    elements.add(new OsmElement.Node(1, Map.of("place", "city", "name", "Edge City"),
      middleLat(CITY_TILE), lonWestOfTile(CITY_TILE, OFFSET_PIXELS)));

    // The country: boundary_labels points are not mapped directly but derived from the interior point of an
    // administrative boundary polygon, so the country needs a geometry. A square makes that point predictable: its
    // interior point is its centre, which is placed OFFSET_PIXELS west of COUNTRY_TILE, like the city above.
    // The square is 4° wide: large enough that the label appears from z4 on (z5 is checked).
    double lon = lonWestOfTile(COUNTRY_TILE, OFFSET_PIXELS);
    double lat = middleLat(COUNTRY_TILE);
    double half = 2;
    double[][] corners = {{lon - half, lat - half}, {lon + half, lat - half}, {lon + half, lat + half},
      {lon - half, lat + half}};
    var ring = new LongArrayList();
    for (int i = 0; i < corners.length; i++) {
      // the square's corners: untagged nodes, only coordinates for the way below
      elements.add(new OsmElement.Node(100 + i, corners[i][1], corners[i][0]));
      ring.add(100 + i);
    }
    ring.add(100);

    // the square's outline: an untagged closed way through the corners. It only becomes a polygon as the outer
    // member of the relation below (it also yields a `boundaries` line, which the test ignores).
    elements.add(new OsmElement.Way(200, Map.of(), ring));

    // the country itself: a boundary relation, as countries are mapped in OSM. Planetiler assembles its outer ways
    // into a polygon, and the profile derives the boundary_labels point from it (admin_level 2 = country).
    elements.add(new OsmElement.Relation(300,
      Map.of("type", "boundary", "boundary", "administrative", "admin_level", "2", "name", "Edgeland"),
      List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 200, "outer"))));

    return elements;
  }

  /** Runs the profile over {@link #osmElements()} into an in-memory archive. */
  private static Mbtiles generate() throws Exception {
    var config = PlanetilerConfig.from(Arguments.of(Map.of()));
    var profile = new Shortbread(config);
    var stats = Stats.inMemory();
    var elements = osmElements();
    OsmBlockSource source = next -> {
      // in the order of an OSM file
      next.accept(OsmBlockSource.Block.of(elements.stream().filter(e -> e instanceof OsmElement.Node).toList()));
      next.accept(OsmBlockSource.Block.of(elements.stream().filter(e -> e instanceof OsmElement.Way).toList()));
      next.accept(OsmBlockSource.Block.of(elements.stream().filter(e -> e instanceof OsmElement.Relation).toList()));
    };
    var featureGroup = FeatureGroup.newInMemoryFeatureGroup(TileOrder.TMS, profile, config, stats);
    try (
      var reader = new OsmReader(Shortbread.OSM_SOURCE, () -> source, LongLongMap.newInMemorySortedTable(),
        LongLongMultimap.newInMemoryReplaceableMultimap(), profile, stats)
    ) {
      reader.pass1(config);
      reader.pass2(featureGroup, config);
    }
    featureGroup.prepare();
    var db = Mbtiles.newInMemoryDatabase(config.arguments());
    TileArchiveWriter.writeOutput(featureGroup, db, () -> 0L, new TileArchiveMetadata(profile, config), null, config,
      stats);
    return db;
  }

  private static void assertLabel(Mbtiles db, TileCoord tile, String layer, String name) throws Exception {
    byte[] bytes = db.getTile(tile);
    var features = bytes == null ? List.<VectorTile.Feature>of() : VectorTile.decode(Gzip.gunzip(bytes));
    assertTrue(features.stream().anyMatch(f -> f.layer().equals(layer) && name.equals(f.tags().get("name"))),
      () -> "no " + layer + " '" + name + "' in tile " + tile);
  }

  private static TileCoord west(TileCoord tile) {
    return TileCoord.ofXYZ(tile.x() - 1, tile.y(), tile.z());
  }

  private static double lonWestOfTile(TileCoord tile, double pixels) {
    return GeoUtils.getWorldLon((tile.x() - pixels / 256) / Math.pow(2, tile.z()));
  }

  private static double middleLat(TileCoord tile) {
    return GeoUtils.getWorldLat((tile.y() + 0.5) / Math.pow(2, tile.z()));
  }
}
