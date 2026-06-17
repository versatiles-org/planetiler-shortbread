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

/**
 * The {@code public_transport} layer (zoom 11+): stations, stops, terminals, airports and aerialway stations, as points
 * or area centroids. Ports {@code process_public_transport_layer}.
 * <p>
 * DEVIATION (bug fix): Tilemaker computed a per-kind minimum zoom but then hard-coded {@code MinZoom(11)}, discarding it.
 * Here the computed per-kind minimum zoom is used (e.g. stations at z13, aerodromes at z11), matching the apparent
 * intent and the schema's per-kind expectations.
 */
public class PublicTransport implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "public_transport";

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public PublicTransport(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchAny("railway", "station", "halt", "tram_stop"),
        Expression.matchAny("highway", "bus_stop"),
        Expression.matchAny("amenity", "bus_station", "ferry_terminal"),
        Expression.matchAny("aeroway", "aerodrome", "helipad"),
        Expression.matchAny("aerialway", "station")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    String railway = f.getString("railway", "");
    String aeroway = f.getString("aeroway", "");
    String aerialway = f.getString("aerialway", "");
    String highway = f.getString("highway", "");
    String amenity = f.getString("amenity", "");

    String kind;
    int mz;
    if (railway.equals("station") || railway.equals("halt")) {
      kind = railway;
      mz = 13;
    } else if (railway.equals("tram_stop")) {
      kind = railway;
      mz = 14;
    } else if (highway.equals("bus_stop")) {
      kind = highway;
      mz = 14;
    } else if (amenity.equals("bus_station")) {
      kind = amenity;
      mz = 13;
    } else if (amenity.equals("ferry_terminal")) {
      kind = amenity;
      mz = 12;
    } else if (aerialway.equals("station")) {
      kind = "aerialway_station";
      mz = 13;
    } else if (aeroway.equals("aerodrome")) {
      kind = aeroway;
      mz = 11;
    } else if (aeroway.equals("helipad")) {
      kind = aeroway;
      mz = 13;
    } else {
      return;
    }

    FeatureCollector.Feature feature;
    if (f.isPoint()) {
      feature = features.point(LAYER);
    } else if (Geo.isArea(f)) {
      feature = features.pointOnSurface(LAYER);
    } else {
      return;
    }
    feature.setMinZoom(mz).setMaxZoom(14).setAttr("kind", kind);
    String iata = f.getString("iata");
    if (iata != null && !iata.isEmpty()) {
      feature.setAttr("iata", iata);
    }
    Names.setNames(feature, f, options.languages(), countries);
  }
}
