package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.TileCoord;
import com.onthegomap.planetiler.mbtiles.Mbtiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test that runs the whole Planetiler pipeline with the {@link Shortbread} profile over the bundled Monaco
 * extract and checks the resulting tiles. Only the {@code osm} source is wired (the {@code ocean} shapefile is skipped)
 * so the test needs no network access.
 */
class ShortbreadIntegrationTest {

  /** Runs the profile over Monaco and returns the archive for inspection. */
  private static Mbtiles generate(Path tmpDir, Map<String, String> extraArgs) throws Exception {
    Path dbPath = tmpDir.resolve("shortbread-" + extraArgs.hashCode() + ".mbtiles");
    var args = new java.util.HashMap<>(extraArgs);
    args.put("tmpdir", tmpDir.resolve("tmp-" + extraArgs.hashCode()).toString());
    Planetiler.create(Arguments.of(args))
      .setProfile(p -> new Shortbread(p.config()))
      .addOsmSource(Shortbread.OSM_SOURCE, TestUtils.extractPathToResource(tmpDir, "monaco-latest.osm.pbf"))
      .overwriteOutput(dbPath)
      .run();
    return Mbtiles.newReadOnlyDatabase(dbPath);
  }

  /** All features of one layer at one zoom, across every tile. */
  private static List<Map<String, Object>> featuresAt(Mbtiles mbtiles, int zoom, String layer) throws Exception {
    return TestUtils.getTileMap(mbtiles).entrySet().stream()
      .filter(e -> e.getKey().z() == zoom)
      .flatMap(e -> e.getValue().stream())
      .filter(f -> f.layer().equals(layer))
      .map(TestUtils.ComparableFeature::attrs)
      .toList();
  }

  @Test
  void generatesMonacoTiles(@TempDir Path tmpDir) throws Exception {
    try (var mbtiles = generate(tmpDir, Map.of())) {
      var metadata = mbtiles.metadataTable().getAll();
      assertEquals("Shortbread", metadata.get("name"));
      assertEquals("1.0", metadata.get("version"), "the default build declares Shortbread 1.0");

      var tileMap = TestUtils.getTileMap(mbtiles);
      // Monaco is tiny (~2 km²), so only a handful of tiles per zoom across z0-14
      assertTrue(tileMap.size() >= 10, () -> "expected tiles across zooms, got " + tileMap.size());

      Set<String> layers = tileMap.values().stream()
        .flatMap(List::stream)
        .map(TestUtils.ComparableFeature::layer)
        .collect(Collectors.toSet());

      // layers that Monaco data is guaranteed to populate; boundaries come from the Monaco–France border relation
      assertTrue(layers.containsAll(Set.of("buildings", "streets", "land", "pois", "place_labels", "boundaries")),
        () -> "missing expected layers, found: " + layers);
    }
  }

  @Test
  void metadataListsEveryLayerItEmits(@TempDir Path tmpDir) throws Exception {
    try (var mbtiles = generate(tmpDir, Map.of())) {
      // vector_layers lives inside the `json` metadata value, not as a top-level key
      String json = mbtiles.metadataTable().getAll().get("json");
      assertNotNull(json, "mbtiles metadata must carry a `json` value");
      var vectorLayers = new ObjectMapper().readTree(json).get("vector_layers");
      assertNotNull(vectorLayers, "the `json` metadata must list vector_layers");

      Set<String> declared = new java.util.HashSet<>();
      vectorLayers.forEach(layer -> declared.add(layer.get("id").asText()));
      assertTrue(declared.containsAll(Set.of("buildings", "streets", "place_labels", "water_polygons", "addresses")),
        () -> "vector_layers is missing expected layers, found: " + declared);

      // the beyond-spec mountain_peaks layer is only declared when its experiment is on
      assertFalse(declared.contains("mountain_peaks"), "mountain_peaks must not appear without its experiment");

      // every declared layer must also carry its zoom range, which is what a client uses to decide when to request it
      vectorLayers.forEach(layer -> {
        assertTrue(layer.has("minzoom") && layer.has("maxzoom"),
          () -> "vector_layers entry without a zoom range: " + layer);
      });
    }
  }

  @Test
  void monacoPlaceLabelCarriesItsAttributes(@TempDir Path tmpDir) throws Exception {
    try (var mbtiles = generate(tmpDir, Map.of())) {
      // Monaco's place=city node is also tagged capital=yes, so the profile promotes it to kind=capital and moves it
      // from the city zoom (6) down to 4 — this asserts that promotion against real data
      var atZoom4 = featuresAt(mbtiles, 4, "place_labels");
      var monaco = atZoom4.stream().filter(a -> "Monaco".equals(a.get("name"))).findFirst();
      assertTrue(monaco.isPresent(), () -> "no place_labels feature named Monaco at z4, got " + atZoom4);
      assertEquals("capital", monaco.get().get("kind"), "capital=yes promotes the kind");
      assertEquals(36371L, monaco.get().get("population"), "population comes from the OSM tag, not the default");

      // suburbs enter at z10, so they must not be in the z4 tile
      assertTrue(atZoom4.stream().noneMatch(a -> "Monte-Carlo".equals(a.get("name"))),
        "a suburb must not appear at z4");
      assertTrue(featuresAt(mbtiles, 14, "place_labels").stream()
        .anyMatch(a -> "Monte-Carlo".equals(a.get("name"))), "the Monte-Carlo suburb should be labelled at z14");
    }
  }

  @Test
  void elevenAndExperimentsChangeTheOutput(@TempDir Path tmpDir) throws Exception {
    try (var mbtiles = generate(tmpDir, Map.of("shortbread_version", "1.1"))) {
      assertEquals("1.1", mbtiles.metadataTable().getAll().get("version"));
    }
    try (var mbtiles = generate(tmpDir, Map.of("shortbread_experiments", "3d_buildings"))) {
      // 3d_buildings adds height fields to buildings, which a client discovers through vector_layers.
      //
      // Note what this cannot test: `vector_layers` is built from the layers that actually emitted features
      // (LayerAttrStats), not from the registered handlers, so an experiment is only visible in the metadata when the
      // data exercises it. Monaco contains no natural=peak/volcano/saddle node, so enabling mountain_peaks here adds
      // no layer at all — see the caveat in docs/extensions.md.
      var buildings = new ObjectMapper().readTree(mbtiles.metadataTable().getAll().get("json")).get("vector_layers");
      var fields = java.util.stream.StreamSupport.stream(buildings.spliterator(), false)
        .filter(layer -> "buildings".equals(layer.get("id").asText()))
        .findFirst().orElseThrow(() -> new AssertionError("buildings layer must be declared"))
        .get("fields");
      assertTrue(fields.has("height"), () -> "3d_buildings must add a height field, got " + fields);
    }
  }

  @Test
  void zoomRangesInMetadataMatchTheArchive(@TempDir Path tmpDir) throws Exception {
    try (var mbtiles = generate(tmpDir, Map.of())) {
      var metadata = mbtiles.metadataTable().getAll();
      assertEquals("0", metadata.get("minzoom"));
      assertEquals("14", metadata.get("maxzoom"));

      // Planetiler writes the configured range into the metadata but only emits tiles that contain data, so a tiny
      // extract has no z0/z1 tile at all: the archive's zooms must lie inside the declared range and reach the max
      var zooms = TestUtils.getTileMap(mbtiles).keySet().stream().map(TileCoord::z).collect(Collectors.toSet());
      assertTrue(zooms.stream().allMatch(z -> z >= 0 && z <= 14),
        () -> "tiles outside the declared zoom range: " + zooms);
      assertTrue(zooms.contains(14), () -> "the archive should reach the maximum zoom, found " + zooms);
    }
  }
}
