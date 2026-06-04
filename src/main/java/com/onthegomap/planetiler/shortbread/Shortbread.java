package com.onthegomap.planetiler.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.shortbread.layers.Buildings;
import com.onthegomap.planetiler.shortbread.layers.Ocean;

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

  public Shortbread(PlanetilerConfig config) {
    super(config);

    registerHandler(new Ocean());
    registerHandler(new Buildings());
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
    return "1.0";
  }
}
