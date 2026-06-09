package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.util.Geo;
import java.util.Set;

/**
 * The {@code land} layer: land use / land cover polygons (forest, residential, farmland, parks, wetlands, …). Ports
 * {@code process_land}; the kind and minimum zoom come from a fixed per-value table. As in Tilemaker, both
 * {@code landuse=forest} and {@code natural=wood} map to {@code kind=forest}.
 */
public class Land implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "land";

  private static final Set<String> LANDUSE_Z10 = Set.of(
    "residential", "industrial", "commercial", "retail", "railway", "landfill", "brownfield", "greenfield",
    "farmyard", "farmland");
  private static final Set<String> LANDUSE_Z11 = Set.of(
    "grass", "meadow", "orchard", "vineyard", "allotments", "village_green", "recreation_ground",
    "greenhouse_horticulture", "plant_nursery", "quarry");
  private static final Set<String> NATURAL_Z11 = Set.of(
    "heath", "scrub", "grassland", "bare_rock", "scree", "shingle");
  private static final Set<String> WETLAND_Z11 = Set.of("swamp", "bog", "string_bog", "wet_meadow", "marsh");
  private static final Set<String> LEISURE_Z11 = Set.of("golf_course", "park", "garden", "playground", "miniature_golf");

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("landuse"),
        Expression.matchField("natural"),
        Expression.matchField("wetland"),
        Expression.matchField("leisure"),
        Expression.matchAny("amenity", "grave_yard")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!Geo.isArea(f)) {
      return;
    }
    String landuse = f.getString("landuse", "");
    String natural = f.getString("natural", "");
    String wetland = f.getString("wetland", "");
    String leisure = f.getString("leisure", "");

    String kind;
    int mz;
    if (landuse.equals("forest") || natural.equals("wood")) {
      kind = "forest";
      mz = 7;
    } else if (LANDUSE_Z10.contains(landuse)) {
      kind = landuse;
      mz = 10;
    } else if (LANDUSE_Z11.contains(landuse)) {
      kind = landuse;
      mz = 11;
    } else if (natural.equals("sand") || natural.equals("beach")) {
      kind = natural;
      mz = 10;
    } else if (NATURAL_Z11.contains(natural)) {
      kind = natural;
      mz = 11;
    } else if (WETLAND_Z11.contains(wetland)) {
      kind = wetland;
      mz = 11;
    } else if (f.hasTag("amenity", "grave_yard")) {
      kind = "grave_yard";
      mz = 13;
    } else if (landuse.equals("garages")) {
      kind = "garages";
      mz = 12;
    } else if (LEISURE_Z11.contains(leisure)) {
      kind = leisure;
      mz = 11;
    } else if (landuse.equals("cemetery")) {
      kind = "cemetery";
      mz = 13;
    } else {
      return;
    }

    features.polygon(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(1)
      .setPixelTolerance(0.25)
      .setAttr("kind", kind);
  }
}
