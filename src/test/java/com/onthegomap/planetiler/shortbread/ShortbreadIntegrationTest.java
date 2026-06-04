package com.onthegomap.planetiler.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.mbtiles.Mbtiles;
import java.nio.file.Path;
import java.util.List;
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

  @Test
  void generatesMonacoTiles(@TempDir Path tmpDir) throws Exception {
    Path dbPath = tmpDir.resolve("shortbread.mbtiles");
    Planetiler.create(Arguments.of("tmpdir", tmpDir.resolve("tmp").toString()))
      .setProfile(p -> new Shortbread(p.config()))
      .addOsmSource(Shortbread.OSM_SOURCE, TestUtils.pathToResource("monaco-latest.osm.pbf"))
      .overwriteOutput(dbPath)
      .run();

    try (var mbtiles = Mbtiles.newReadOnlyDatabase(dbPath)) {
      var metadata = mbtiles.metadataTable().getAll();
      assertEquals("Shortbread", metadata.get("name"));

      var tileMap = TestUtils.getTileMap(mbtiles);
      // Monaco is tiny (~2 km²), so only a handful of tiles per zoom across z0-14
      assertTrue(tileMap.size() >= 10, () -> "expected tiles across zooms, got " + tileMap.size());

      Set<String> layers = tileMap.values().stream()
        .flatMap(List::stream)
        .map(TestUtils.ComparableFeature::layer)
        .collect(Collectors.toSet());

      // layers that Monaco data is guaranteed to populate
      assertTrue(layers.containsAll(Set.of("buildings", "streets", "land", "pois", "place_labels")),
        () -> "missing expected layers, found: " + layers);
    }
  }
}
