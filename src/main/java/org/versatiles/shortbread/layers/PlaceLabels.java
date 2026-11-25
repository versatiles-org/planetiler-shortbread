package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import com.onthegomap.planetiler.util.ZoomFunction;
import org.versatiles.shortbread.Experiment;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Geo;
import org.versatiles.shortbread.util.Names;

/**
 * The {@code place_labels} layer (zoom 4+): cities, towns, villages and other named places. Each place type has a fixed
 * minimum zoom and a default population used when the OSM {@code population} tag is missing; {@code capital=yes} /
 * {@code capital=4} promote the kind to {@code capital} / {@code state_capital} at zoom 4. Population drives the label
 * priority (sort key).
 */
public class PlaceLabels implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "place_labels";

  /**
   * Buffer of label points, in tile pixels (a 256px tile), so that a label crossing a tile edge has its point in every
   * tile it reaches. Renderers that draw one tile at a time, such as server-side raster rendering with MapLibre Native,
   * cut the label at the edge of a tile that lacks the point. The buffer covers half of the widest label: MapLibre
   * wraps text at {@code text-max-width}, 10em by default, so half a label is 5em on a 512px style tile, or 2.5em in
   * tile pixels. 40px covers font sizes up to 16px, the largest place and boundary label size in the VersaTiles styles.
   */
  public static final double LABEL_BUFFER_PIXELS = 40;

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
      // EXPERIMENT: islands mapped as polygons are labelled only when the island_labels experiment is on
      if (options.has(Experiment.ISLAND_LABELS)) {
        processIslandArea(f, features);
      }
      return;
    }
    if (!f.isPoint()) {
      return;
    }
    int mz;
    long defaultPopulation;
    switch (place) {
      case "city" -> {
        mz = 6;
        defaultPopulation = 100_000;
      }
      case "town" -> {
        mz = 7;
        defaultPopulation = 5_000;
      }
      case "village" -> {
        mz = 10;
        defaultPopulation = 100;
      }
      case "hamlet" -> {
        mz = 10;
        defaultPopulation = 50;
      }
      case "suburb" -> {
        mz = 10;
        defaultPopulation = 1_000;
      }
      case "quarter" -> {
        mz = 10;
        defaultPopulation = 500;
      }
      case "neighbourhood" -> {
        mz = 10;
        defaultPopulation = 100;
      }
      case "isolated_dwelling", "farm" -> {
        mz = 10;
        defaultPopulation = 5;
      }
      case "locality", "island" -> {
        mz = 10;
        defaultPopulation = 0;
      }
      default -> {
        return;
      }
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
      .setSortKeyDescending(SortKey.orderByLog(Math.max(pop, 1), 1, 1e9).get())
      // thin dense low/mid-zoom place labels: keep the most populous per 64px grid cell (~32/tile), à la OpenMapTiles.
      // z13+ is left unthinned so all places appear when zoomed in. Where the grid runs, the buffer must be at least
      // the grid size, so that adjacent tiles keep the same places; above it, LABEL_BUFFER_PIXELS is enough
      .setBufferPixels(LABEL_BUFFER_PIXELS)
      .setBufferPixelOverrides(ZoomFunction.maxZoom(12, 64))
      .setPointLabelGridSizeAndLimit(12, 64, 2);
    Names.setNames(feature, f, options.nameKeys(), countries);
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
      .setBufferPixels(LABEL_BUFFER_PIXELS)
      .setAttr("kind", "island")
      .setAttr("population", 0L)
      .setSortKeyDescending(SortKey.orderByLog(Math.max(areaM2, 1), 1, 1e14).get());
    Names.setNames(label, f, options.nameKeys(), countries);
  }
}
