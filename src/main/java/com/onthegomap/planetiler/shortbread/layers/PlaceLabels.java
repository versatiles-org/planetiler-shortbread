package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.CountryLanguages;
import com.onthegomap.planetiler.shortbread.util.Names;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;

/**
 * The {@code place_labels} layer (zoom 3+): cities, towns, villages and other named places. Ports
 * {@code process_place_layer}. Each place type has a fixed minimum zoom and a default population used when the OSM
 * {@code population} tag is missing; {@code capital=yes} / {@code capital=4} promote the kind to
 * {@code capital} / {@code state_capital} at zoom 4. Population drives the label priority (sort key).
 */
public class PlaceLabels implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "place_labels";

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public PlaceLabels(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("place",
        "city", "town", "village", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling", "farm",
        "island", "locality"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!f.hasTag("name")) {
      return;
    }
    String place = f.getString("place", "");
    // Islands mapped as polygons (the common case for real islands) get an area-ranked label point; previously only
    // island *nodes* were labelled, so most islands had no label. Bigger islands appear at lower zoom, à la OMT.
    if (place.equals("island") && !f.isPoint() && Geo.isArea(f)) {
      processIslandArea(f, features);
      return;
    }
    if (!f.isPoint()) {
      return;
    }
    int mz;
    long defaultPopulation;
    switch (place) {
      case "city" -> { mz = 6; defaultPopulation = 100_000; }
      case "town" -> { mz = 7; defaultPopulation = 5_000; }
      case "village" -> { mz = 10; defaultPopulation = 100; }
      case "hamlet" -> { mz = 10; defaultPopulation = 50; }
      case "suburb" -> { mz = 10; defaultPopulation = 1_000; }
      case "quarter" -> { mz = 10; defaultPopulation = 500; }
      case "neighbourhood" -> { mz = 10; defaultPopulation = 100; }
      case "isolated_dwelling", "farm" -> { mz = 10; defaultPopulation = 5; }
      case "locality", "island" -> { mz = 10; defaultPopulation = 0; }
      default -> { return; }
    }

    String kind = place;
    if ((place.equals("city") || place.equals("town") || place.equals("village") || place.equals("hamlet"))) {
      if (f.hasTag("capital", "yes")) {
        mz = 4;
        kind = "capital";
      } else if (f.hasTag("capital", "4")) {
        mz = 4;
        kind = "state_capital";
      }
    }

    Long population = Parse.parseLongOrNull(f.getTag("population"));
    long pop = population != null ? population : defaultPopulation;

    var feature = features.point(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setAttr("kind", kind)
      .setAttr("population", pop)
      .setSortKeyDescending(SortKey.orderByLog(Math.max(pop, 1), 1, 1e9).get());
    Names.setNames(feature, f, options.languages(), countries);
  }

  private void processIslandArea(SourceFeature f, FeatureCollector features) {
    if (Geo.worldArea(f) <= 0) {
      return; // broken/degenerate polygon → no usable label point
    }
    double areaM2 = Geo.areaSquareMeters(f);
    // big islands appear earlier, mirroring the OpenMapTiles place/island area ranks
    int mz = areaM2 >= 160_000_000 ? 8 : areaM2 >= 40_000_000 ? 9 : 10;
    var label = features.pointOnSurface(LAYER)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setAttr("kind", "island")
      .setAttr("population", 0L)
      .setSortKeyDescending(SortKey.orderByLog(Math.max(areaM2, 1), 1, 1e14).get());
    Names.setNames(label, f, options.languages(), countries);
  }
}
