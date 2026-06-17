package com.onthegomap.planetiler.shortbread;

import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.shortbread.layers.Glaciers;
import com.onthegomap.planetiler.shortbread.util.CountryLanguages;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point that generates Shortbread vector tiles using the {@link Shortbread} profile.
 * <p>
 * Run with {@code java -jar planetiler.jar shortbread --area=monaco}. Input sources:
 * <ul>
 * <li>{@code osm} — an OpenStreetMap {@code .osm.pbf} extract (downloaded from Geofabrik by default)</li>
 * <li>{@code ocean} — the OSM water polygons shapefile from osmdata.openstreetmap.de, for the {@code ocean} layer</li>
 * <li>5 small Natural Earth per-layer shapefiles (glaciated areas + antarctic ice shelves) for low-zoom glaciers</li>
 * <li>{@code ne_10m_admin_0_countries} — Natural Earth country polygons, declared <em>before</em> the OSM source so
 * {@link CountryLanguages} builds its country&rarr;language index before any OSM feature is processed</li>
 * </ul>
 */
public class ShortbreadMain {

  private static final String OCEAN_URL =
    "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip";
  // Natural Earth's official CDN. We pull only the 5 small per-layer glacier/ice shapefiles (a few MB total) used by
  // {@link Glaciers} for low-zoom ice, rather than the full ~800 MB Natural Earth vector dataset. Each layer's zip is
  // at {base}/{scale}/physical/{layer}.zip, where {scale} (110m|50m|10m) is the second underscore-segment of the name.
  private static final String NATURAL_EARTH_BASE_URL = "https://naciscdn.org/naturalearth/";

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws Exception {
    String area = args.getString("area", "geofabrik area to download", "monaco");
    String osmUrl = args.getString("osm_url", "OSM URL to download",
      "planet".equals(area) ? "aws:latest" : ("geofabrik:" + area));

    var planetiler = Planetiler.create(args)
      .setProfile(p -> new Shortbread(p.config()))
      // country polygons FIRST: stages run in declared order, so CountryLanguages' index is fully built before the
      // OSM source is processed (the same Natural-Earth-before-OSM ordering OpenMapTiles relies on)
      .addShapefileSource(CountryLanguages.SOURCE, Path.of("data", "sources", CountryLanguages.SOURCE + ".zip"),
        NATURAL_EARTH_BASE_URL + "10m/cultural/" + CountryLanguages.SOURCE + ".zip")
      .addOsmSource(Shortbread.OSM_SOURCE, Path.of("data", "sources", area + ".osm.pbf"), osmUrl)
      .addShapefileSource(Shortbread.OCEAN_SOURCE,
        Path.of("data", "sources", "water-polygons-split-3857.zip"), OCEAN_URL);

    // low-zoom (z0-6) glaciers/ice (Glaciers): each Natural Earth layer as its own small per-layer shapefile zip
    for (String layer : List.of(Glaciers.GLACIATED_110M, Glaciers.GLACIATED_50M, Glaciers.GLACIATED_10M,
      Glaciers.ICE_SHELVES_50M, Glaciers.ICE_SHELVES_10M)) {
      String scale = layer.split("_")[1]; // 110m | 50m | 10m
      planetiler.addShapefileSource(layer, Path.of("data", "sources", layer + ".zip"),
        NATURAL_EARTH_BASE_URL + scale + "/physical/" + layer + ".zip");
    }

    planetiler.overwriteOutput(Path.of("data", "shortbread.mbtiles")).run();
  }
}
