package com.onthegomap.planetiler.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.shortbread.layers.Addresses;
import com.onthegomap.planetiler.shortbread.layers.Aerialways;
import com.onthegomap.planetiler.shortbread.layers.Boundaries;
import com.onthegomap.planetiler.shortbread.layers.Bridges;
import com.onthegomap.planetiler.shortbread.layers.Buildings;
import com.onthegomap.planetiler.shortbread.layers.Dams;
import com.onthegomap.planetiler.shortbread.layers.Ferries;
import com.onthegomap.planetiler.shortbread.layers.Glaciers;
import com.onthegomap.planetiler.shortbread.layers.Land;
import com.onthegomap.planetiler.shortbread.layers.Ocean;
import com.onthegomap.planetiler.shortbread.layers.Piers;
import com.onthegomap.planetiler.shortbread.layers.PlaceLabels;
import com.onthegomap.planetiler.shortbread.layers.Pois;
import com.onthegomap.planetiler.shortbread.layers.PublicTransport;
import com.onthegomap.planetiler.shortbread.layers.Sites;
import com.onthegomap.planetiler.shortbread.layers.StreetLabels;
import com.onthegomap.planetiler.shortbread.layers.Streets;
import com.onthegomap.planetiler.shortbread.layers.WaterLines;
import com.onthegomap.planetiler.shortbread.layers.WaterPolygons;
import com.onthegomap.planetiler.shortbread.util.MergeLines;
import com.onthegomap.planetiler.shortbread.util.MergePolygons;

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
  /** Natural Earth input source name, used for low-zoom glaciers/ice ({@link Glaciers}). */
  public static final String NATURAL_EARTH_SOURCE = "natural_earth";

  private final ShortbreadOptions options;

  public Shortbread(PlanetilerConfig config) {
    super(config);
    this.options = ShortbreadOptions.from(config.arguments());

    // water
    registerHandler(new Ocean());
    registerHandler(new Glaciers()); // low-zoom (z0-6) glaciers/ice from Natural Earth into water_polygons
    registerHandler(new WaterPolygons(options));
    registerHandler(new WaterLines(options));
    registerHandler(new Dams());
    registerHandler(new Piers());
    registerHandler(new Bridges(options));

    // land use / sites / buildings / addresses / pois
    registerHandler(new Land());
    registerHandler(new Sites());
    registerHandler(new Buildings());
    registerHandler(new Addresses(options));
    registerHandler(new Pois(options));

    // streets and transport
    registerHandler(new Streets(options));
    registerHandler(new StreetLabels(options));
    registerHandler(new Aerialways());
    registerHandler(new Ferries(options));
    registerHandler(new PublicTransport(options));

    // boundaries and places
    registerHandler(new Boundaries(options));
    registerHandler(new PlaceLabels(options));

    // line layers with `combine_below` in the Tilemaker config
    registerHandler(new MergeLines(WaterLines.LAYER));
    registerHandler(new MergeLines(WaterLines.LABELS));
    registerHandler(new MergeLines(Dams.LINES));
    registerHandler(new MergeLines(Streets.STREETS));
    registerHandler(new MergeLines(StreetLabels.LABELS));
    registerHandler(new MergeLines(Boundaries.LINES));

    // coalesce adjacent same-kind area polygons to shrink dense overview tiles
    registerHandler(new MergePolygons(Land.LAYER, 1));
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
}
