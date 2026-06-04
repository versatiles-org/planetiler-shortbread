package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Names;
import com.onthegomap.planetiler.shortbread.util.Poi;

/**
 * The {@code pois} layer (zoom 14): points of interest. Ports {@code process_pois} from the Tilemaker reference. Each
 * accepted OSM key (amenity, shop, tourism, man_made, historic, leisure, emergency, highway, office) keeps its own
 * attribute holding the whitelisted value, plus a number of conditional extras (cuisine, sport, recycling flags, …),
 * the name fields and the address.
 * <p>
 * DEVIATIONS (bug fixes) vs {@code process.lua}: the {@code man_made} attribute is filled from the {@code man_made}
 * value (Tilemaker used {@code historic}); {@code office} is validated against the office whitelist; and empty values
 * are omitted instead of being written as the empty-string NULL sentinel (see {@link Poi}).
 */
public class Pois implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "pois";

  private final ShortbreadOptions options;

  public Pois(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("amenity"),
        Expression.matchField("shop"),
        Expression.matchField("tourism"),
        Expression.matchField("man_made"),
        Expression.matchField("historic"),
        Expression.matchField("leisure"),
        Expression.matchField("emergency"),
        Expression.matchField("highway"),
        Expression.matchField("office")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    String amenity = Poi.accepted(Poi.AMENITY, f.getString("amenity"));
    String shop = Poi.accepted(Poi.SHOP, f.getString("shop"));
    String tourism = Poi.accepted(Poi.TOURISM, f.getString("tourism"));
    String manMade = Poi.accepted(Poi.MAN_MADE, f.getString("man_made"));
    String historic = Poi.accepted(Poi.HISTORIC, f.getString("historic"));
    String leisure = Poi.accepted(Poi.LEISURE, f.getString("leisure"));
    // Shortbread 1.1 additions
    if (options.v11()) {
      if (amenity == null && f.hasTag("amenity", "fuel")) {
        amenity = "fuel";
      }
      if (leisure == null && f.hasTag("leisure", "park")) {
        leisure = "park";
      }
    }
    String emergency = Poi.accepted(Poi.EMERGENCY, f.getString("emergency"));
    String highway = Poi.accepted(Poi.HIGHWAY, f.getString("highway"));
    String office = Poi.accepted(Poi.OFFICE, f.getString("office"));

    if (amenity == null && shop == null && tourism == null && manMade == null && historic == null && leisure == null &&
      emergency == null && highway == null && office == null) {
      return;
    }

    FeatureCollector.Feature feature;
    if (f.isPoint()) {
      feature = features.point(LAYER);
    } else if (Geo.isArea(f)) {
      feature = features.centroid(LAYER);
    } else {
      return;
    }
    feature.setZoomRange(14, 14);

    setIfPresent(feature, "amenity", amenity);
    setIfPresent(feature, "shop", shop);
    setIfPresent(feature, "tourism", tourism);
    setIfPresent(feature, "man_made", manMade); // DEVIATION: from man_made, not historic
    setIfPresent(feature, "historic", historic);
    setIfPresent(feature, "leisure", leisure);
    setIfPresent(feature, "emergency", emergency);
    setIfPresent(feature, "highway", highway);
    setIfPresent(feature, "office", office);

    if (amenity != null && Poi.CATERING.contains(amenity)) {
      setIfPresent(feature, "cuisine", f.getString("cuisine"));
    }
    if (leisure != null && Poi.SPORT.contains(leisure)) {
      setIfPresent(feature, "sport", f.getString("sport"));
    }
    if ("vending_machine".equals(amenity)) {
      setIfPresent(feature, "vending", f.getString("vending"));
    }
    if ("information".equals(tourism)) {
      setIfPresent(feature, "information", f.getString("information"));
    }
    if ("tower".equals(manMade)) {
      setIfPresent(feature, "tower:type", f.getString("tower:type"));
    }
    if ("recycling".equals(amenity)) {
      feature.setAttr("recycling:glass_bottles", isYes(f, "recycling:glass_bottles"));
      feature.setAttr("recycling:paper", isYes(f, "recycling:paper"));
      feature.setAttr("recycling:clothes", isYes(f, "recycling:clothes"));
      feature.setAttr("recycling:scrap_metal", isYes(f, "recycling:scrap_metal"));
    }
    if ("bank".equals(amenity)) {
      feature.setAttr("atm", isYes(f, "atm"));
    }
    if ("place_of_worship".equals(amenity)) {
      setIfPresent(feature, "religion", f.getString("religion"));
      setIfPresent(feature, "denomination", f.getString("denomination"));
    }

    Names.setNames(feature, f, options.languages());
    setIfPresent(feature, "housename", f.getString("addr:housename"));
    setIfPresent(feature, "housenumber", f.getString("addr:housenumber"));
  }

  private static boolean isYes(SourceFeature f, String key) {
    return "yes".equals(f.getString(key, ""));
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }
}
