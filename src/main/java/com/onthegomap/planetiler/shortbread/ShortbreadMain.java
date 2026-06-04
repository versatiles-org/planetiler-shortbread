package com.onthegomap.planetiler.shortbread;

import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import java.nio.file.Path;

/**
 * Entry point that generates Shortbread vector tiles using the {@link Shortbread} profile.
 * <p>
 * Run with {@code java -jar planetiler.jar shortbread --area=monaco}. Two input sources are used:
 * <ul>
 * <li>{@code osm} — an OpenStreetMap {@code .osm.pbf} extract (downloaded from Geofabrik by default)</li>
 * <li>{@code ocean} — the OSM water polygons shapefile from osmdata.openstreetmap.de, used for the {@code ocean}
 * layer</li>
 * </ul>
 */
public class ShortbreadMain {

  private static final String OCEAN_URL =
    "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip";

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws Exception {
    String area = args.getString("area", "geofabrik area to download", "monaco");
    String osmUrl = args.getString("osm_url", "OSM URL to download",
      "planet".equals(area) ? "aws:latest" : ("geofabrik:" + area));

    Planetiler.create(args)
      .setProfile(planetiler -> new Shortbread(planetiler.config()))
      .addOsmSource(Shortbread.OSM_SOURCE, Path.of("data", "sources", area + ".osm.pbf"), osmUrl)
      .addShapefileSource(Shortbread.OCEAN_SOURCE,
        Path.of("data", "sources", "water-polygons-split-3857.zip"), OCEAN_URL)
      .overwriteOutput(Path.of("data", "shortbread.mbtiles"))
      .run();
  }
}
