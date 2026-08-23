package org.versatiles.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import org.versatiles.shortbread.layers.Addresses;
import org.versatiles.shortbread.layers.Aerialways;
import org.versatiles.shortbread.layers.Boundaries;
import org.versatiles.shortbread.layers.Bridges;
import org.versatiles.shortbread.layers.Buildings;
import org.versatiles.shortbread.layers.Dams;
import org.versatiles.shortbread.layers.Ferries;
import org.versatiles.shortbread.layers.Land;
import org.versatiles.shortbread.layers.Ocean;
import org.versatiles.shortbread.layers.Piers;
import org.versatiles.shortbread.layers.PlaceLabels;
import org.versatiles.shortbread.layers.Pois;
import org.versatiles.shortbread.layers.PublicTransport;
import org.versatiles.shortbread.layers.Sites;
import org.versatiles.shortbread.layers.StreetLabels;
import org.versatiles.shortbread.layers.Streets;
import org.versatiles.shortbread.layers.WaterLines;
import org.versatiles.shortbread.layers.WaterPolygons;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.MergeLines;
import org.versatiles.shortbread.util.MergePolygons;

/**
 * A {@link com.onthegomap.planetiler.Profile} that implements the
 * <a href="https://shortbread-tiles.org/schema/1.0/">Shortbread v1.0</a> vector tile schema as a hand-written Java
 * profile.
 * <p>
 * This is a native re-implementation of the schema that previously shipped as a {@code custommap} YAML config
 * ({@code planetiler-custommap/.../shortbread.yml}). The behavioural reference is the Geofabrik Tilemaker
 * implementation ({@code process.lua} / {@code config.json}); deviations from it are intentional bug-fixes marked with
 * {@code // DEVIATION:} comments.
 * <p>
 * Each output layer is implemented by a handler in the {@code layers} package, registered below and wired together by
 * {@link ForwardingProfile}.
 */
public class Shortbread extends ForwardingProfile {

  /** OSM input source name. */
  public static final String OSM_SOURCE = "osm";
  /** Shapefile input source name for the OSM water (ocean) polygons. */
  public static final String OCEAN_SOURCE = "ocean";

  private final ShortbreadOptions options;

  public Shortbread(PlanetilerConfig config) {
    super(config);
    this.options = ShortbreadOptions.from(config.arguments());

    // country -> default-language spatial index (Natural Earth admin_0, wired before OSM in ShortbreadMain), used by
    // Names to geofence the `name` -> `name_<lang>` fallback. Only built when the locale-names experiment is enabled;
    // otherwise the layers receive null and emit names from tags only (strict spec).
    CountryLanguages countries = null;
    if (options.has(Experiment.LOCALE_NAMES)) {
      countries = new CountryLanguages(options.languages());
      registerHandler(countries);
    }

    // water
    registerHandler(new Ocean());
    registerHandler(new WaterPolygons(options, countries));
    registerHandler(new WaterLines(options, countries));
    registerHandler(new Dams());
    registerHandler(new Piers());
    registerHandler(new Bridges(options, countries));

    // land use / sites / buildings / addresses / pois
    registerHandler(new Land());
    registerHandler(new Sites());
    registerHandler(new Buildings(options));
    registerHandler(new Addresses(options));
    registerHandler(new Pois(options, countries));

    // streets and transport
    registerHandler(new Streets(options, countries));
    registerHandler(new StreetLabels(options, countries));
    registerHandler(new Aerialways());
    registerHandler(new Ferries(options, countries));
    registerHandler(new PublicTransport(options, countries));

    // boundaries and places
    registerHandler(new Boundaries(options, countries));
    registerHandler(new PlaceLabels(options, countries));

    // line layers with `combine_below` in the Tilemaker config
    registerHandler(new MergeLines(WaterLines.LAYER));
    registerHandler(new MergeLines(WaterLines.LABELS));
    registerHandler(new MergeLines(Dams.LINES));
    registerHandler(new MergeLines(Streets.STREETS));
    registerHandler(new MergeLines(StreetLabels.LABELS));
    registerHandler(new MergeLines(Boundaries.LINES));

    // coalesce adjacent same-kind area polygons to shrink dense overview tiles
    registerHandler(new MergePolygons(Land.LAYER, 1));
    // union the split OSM ocean polygons per tile and drop sub-pixel slivers — at low zoom (z0-3) those slivers would
    // otherwise be simplified into degenerate (line) geometry, which fails the schema's "ocean must be polygon" rule
    registerHandler(new MergePolygons(Ocean.LAYER_NAME, 1));
  }

  @Override
  public String name() {
    return "Shortbread";
  }

  @Override
  public String description() {
    return "A basic, lean, general-purpose vector tile schema for OpenStreetMap data. " +
      "See https://shortbread-tiles.org/";
  }

  @Override
  public String attribution() {
    return """
      <a href="https://www.openstreetmap.org/copyright" target="_blank">&copy; OpenStreetMap contributors</a>"""
      .trim();
  }

  @Override
  public String version() {
    return options.v11() ? "1.1" : "1.0";
  }

  // Pre-flight resource estimates so Planetiler can warn about insufficient disk/RAM before a long planet build.
  // The output ratio is from a measured Shortbread planet run; intermediate-disk and RAM have no measured figure yet,
  // so they reuse OpenMapTiles' planet-measured ratios as a conservative upper bound (Shortbread is comparable or
  // leaner: fewer layers, no Wikidata fetch, tiny in-memory relation state). Recalibrate those two once measured.

  @Override
  public long estimateIntermediateDiskBytes(long osmFileSize) {
    // not yet measured for Shortbread; OpenMapTiles: a ~60 GB OSM file used ~200 GB of intermediate feature storage
    return osmFileSize * 200 / 60;
  }

  @Override
  public long estimateOutputBytes(long osmFileSize) {
    // measured: an 88 GB planet.osm.pbf produced a ~66 GB shortbread.pmtiles (~0.75x), far below OpenMapTiles' ~1.67x
    return osmFileSize * 66 / 88;
  }

  @Override
  public long estimateRamRequired(long osmFileSize) {
    // not yet measured for Shortbread; OpenMapTiles: ~20 GB heap is safe for a ~67 GB OSM file
    return osmFileSize * 20 / 67;
  }
}
