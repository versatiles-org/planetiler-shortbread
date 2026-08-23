package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.Geo;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Names;
import org.versatiles.shortbread.util.Surface;
import org.versatiles.shortbread.util.ZOrder;
import java.util.Set;

/**
 * The {@code streets} line layer plus the {@code street_polygons} / {@code streets_polygons_labels} area layers. Ports
 * {@code process_streets} and {@code process_street_polygons} from the Tilemaker reference.
 * <p>
 * Tilemaker emits three line layers (streets_low z5-10, streets_med z11-13, streets z14) merged into {@code streets} via
 * {@code write_to}, each carrying progressively more attributes. Here a single {@code streets} feature reproduces those
 * tiers using {@link FeatureCollector.Feature#setAttrWithMinzoom}: {@code kind}/{@code rail} from the feature's minimum
 * zoom, the mid-tier attributes from z11, and {@code bicycle}/{@code horse}/{@code oneway(_reverse)} from z14.
 */
public class Streets implements ForwardingProfile.FeatureProcessor {

  public static final String STREETS = "streets";
  public static final String POLYGONS = "street_polygons";
  public static final String POLYGON_LABELS = "streets_polygons_labels";

  private static final int MED_TIER_MINZOOM = 11;
  private static final int FULL_TIER_MINZOOM = 14;

  private static final Set<String> LINK_HIGHWAYS =
    Set.of("motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link");

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public Streets(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("highway"),
        Expression.matchField("railway"),
        Expression.matchField("aeroway"),
        Expression.matchField("area:aeroway")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    boolean areaLinear = Geo.areaYesMultiBoundary(f);
    if (!areaLinear && f.canBeLine() &&
      (f.hasTag("highway") || f.hasTag("railway") || f.hasTag("aeroway"))) {
      processStreet(f, features);
    }
    if (areaLinear && Geo.isArea(f) && (f.hasTag("highway") || f.hasTag("area:aeroway"))) {
      processStreetPolygon(f, features);
    }
  }

  private void processStreet(SourceFeature f, FeatureCollector features) {
    String highway = f.getString("highway", "");
    String railway = f.getString("railway", "");
    String aeroway = f.getString("aeroway", "");
    String service = f.getString("service", "");

    String kind = null;
    int mz = Integer.MAX_VALUE;
    boolean rail = false;
    if (!highway.isEmpty()) {
      switch (highway) {
        case "motorway", "motorway_link" -> { kind = "motorway"; mz = 5; }
        case "trunk", "trunk_link" -> { kind = "trunk"; mz = 6; }
        case "primary", "primary_link" -> { kind = "primary"; mz = 8; }
        case "secondary", "secondary_link" -> { kind = "secondary"; mz = 9; }
        case "tertiary", "tertiary_link" -> { kind = "tertiary"; mz = 10; }
        case "unclassified", "residential", "bus_guideway", "busway" -> { kind = highway; mz = 12; }
        case "living_street", "pedestrian", "track" -> { kind = highway; mz = 13; }
        case "service" -> { kind = highway; mz = 13; }
        case "footway", "steps", "path", "cycleway" -> { kind = highway; mz = 13; }
        default -> { /* not a street */ }
      }
    } else if ((railway.equals("rail") || railway.equals("narrow_gauge")) && service.isEmpty()) {
      kind = railway;
      rail = true;
      mz = 8;
    } else if ((railway.equals("rail") || railway.equals("narrow_gauge")) ||
      railway.equals("light_rail") || railway.equals("tram") || railway.equals("subway") ||
      railway.equals("funicular") || railway.equals("monorail")) {
      kind = railway;
      rail = true;
      mz = 10;
    } else if (aeroway.equals("runway")) {
      kind = aeroway;
      mz = 11;
    } else if (aeroway.equals("taxiway")) {
      kind = aeroway;
      mz = 13;
    }

    if (kind == null || mz > 14) {
      return;
    }
    boolean link = LINK_HIGHWAYS.contains(highway);
    String surface = Surface.canonicalize(f.getString("surface"));
    String tracktype = f.getString("tracktype");
    boolean tunnel = ZOrder.isTunnel(f);
    boolean bridge = ZOrder.isBridge(f);
    boolean oneway = !rail && ZOrder.isOneway(f);
    boolean onewayReverse = !rail && ZOrder.isReverseOneway(f);

    var feature = features.line(STREETS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setSortKey(ZOrder.zOrder(f, rail, false))
      .setAttr("kind", kind)
      .setAttr("rail", rail);

    // mid tier (z11+)
    feature.setAttrWithMinzoom("link", link, MED_TIER_MINZOOM);
    feature.setAttrWithMinzoom("tunnel", tunnel, MED_TIER_MINZOOM);
    feature.setAttrWithMinzoom("bridge", bridge, MED_TIER_MINZOOM);
    if (surface != null) {
      feature.setAttrWithMinzoom("surface", surface, MED_TIER_MINZOOM);
    }
    if (tracktype != null && !tracktype.isEmpty()) {
      feature.setAttrWithMinzoom("tracktype", tracktype, MED_TIER_MINZOOM);
    }
    if (!service.isEmpty()) {
      feature.setAttrWithMinzoom("service", service, MED_TIER_MINZOOM);
    }

    // full tier (z14)
    feature.setAttrWithMinzoom("oneway", oneway, FULL_TIER_MINZOOM);
    feature.setAttrWithMinzoom("oneway_reverse", onewayReverse, FULL_TIER_MINZOOM);
    setIfPresent(feature, "bicycle", f.getString("bicycle"), FULL_TIER_MINZOOM);
    setIfPresent(feature, "horse", f.getString("horse"), FULL_TIER_MINZOOM);
  }

  private void processStreetPolygon(SourceFeature f, FeatureCollector features) {
    String highway = f.getString("highway", "");
    String areaAeroway = f.getString("area:aeroway", "");
    String kind;
    int mz;
    if (highway.equals("pedestrian") || highway.equals("service")) {
      kind = highway;
      mz = 14;
    } else if (areaAeroway.equals("runway")) {
      kind = areaAeroway;
      mz = 11;
    } else if (areaAeroway.equals("taxiway")) {
      kind = areaAeroway;
      mz = 13;
    } else {
      return;
    }

    var feature = features.polygon(POLYGONS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setSortKey(ZOrder.zOrder(f, false, false))
      .setAttr("kind", kind)
      .setAttr("rail", false)
      .setAttr("tunnel", ZOrder.isTunnel(f))
      .setAttr("bridge", ZOrder.isBridge(f));
    String surface = f.getString("surface");
    if (surface != null && !surface.isEmpty()) {
      feature.setAttr("surface", surface); // raw, as in process.lua (not canonicalized)
    }
    String service = f.getString("service");
    if (service != null && !service.isEmpty()) {
      feature.setAttr("service", service);
    }

    // DEVIATION: emit the label only for named polygons (process.lua emitted it unconditionally)
    if (f.hasTag("name")) {
      var label = features.pointOnSurface(POLYGON_LABELS)
        .setZoomRange(14, 14)
        .setAttr("kind", kind);
      Names.setNames(label, f, options.languages(), countries);
    }
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value, int minzoom) {
    if (value != null && !value.isEmpty()) {
      feature.setAttrWithMinzoom(key, value, minzoom);
    }
  }
}
