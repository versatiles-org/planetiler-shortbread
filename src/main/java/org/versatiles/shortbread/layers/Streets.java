package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Set;
import org.versatiles.shortbread.Experiment;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.Access;
import org.versatiles.shortbread.util.Attrs;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Geo;
import org.versatiles.shortbread.util.Names;
import org.versatiles.shortbread.util.ZOrder;

/**
 * The {@code streets} line layer plus the {@code street_polygons} / {@code streets_polygons_labels} area layers.
 * <p>
 * The schema makes a street's attributes available in tiers, each from its own zoom. A single {@code streets} feature
 * carries all of them using {@link FeatureCollector.Feature#setAttrWithMinzoom}: {@code kind}/{@code rail} from the
 * feature's minimum zoom, the mid-tier attributes from z11, and {@code oneway(_reverse)} from z14. Access attributes
 * follow the schema version: raw {@code bicycle}/{@code horse} from z14 in 1.0, normalized
 * {@code motorcar}/{@code bicycle}/{@code foot}/{@code horse} from z13 in 1.1 (see {@link Access}).
 * <p>
 * The boolean attributes default to {@code false} in the schema, so they are only written when true.
 * <p>
 * EXPERIMENT {@code early_attributes} (shortbread-docs #184): {@code link} and {@code service} identify a feature, so
 * they are emitted from the feature's own minimum zoom instead of z11, and in 1.0 {@code bicycle}/{@code horse} from
 * z13, where paths enter the layer. The other mid-tier attributes stay at z11, because features only merge at low zoom
 * when their attributes are identical.
 */
public class Streets implements ForwardingProfile.FeatureProcessor {

  public static final String STREETS = "streets";
  public static final String POLYGONS = "street_polygons";
  public static final String POLYGON_LABELS = "streets_polygons_labels";

  private static final int MED_TIER_MINZOOM = 11;
  private static final int FULL_TIER_MINZOOM = 14;
  // with early_attributes: 1.0 bicycle/horse from the zoom where paths enter the layer
  private static final int EARLY_ACCESS_MINZOOM = 13;
  // with early_attributes: the earliest zoom the proposal allows for `service`, where service railways enter the layer
  private static final int EARLY_SERVICE_MINZOOM = 10;

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

  /** A {@code kind} value together with the minimum zoom the schema gives it. */
  private record Kind(String value, int minzoom) {}

  private void processStreet(SourceFeature f, FeatureCollector features) {
    String service = f.getString("service", "");

    // A way can carry several of these tags at once — a tram embedded in a service road is both a street and a
    // railway — but the schema gives each feature a single `kind`. OpenStreetMap Carto draws such a way twice (its
    // roads and railway selects both pick it up), so emit one feature per identity rather than letting one win.
    emitStreet(f, features, highwayKind(f.getString("highway", "")), true, false);
    emitStreet(f, features, railwayKind(f.getString("railway", ""), service), false, true);
    emitStreet(f, features, aerowayKind(f.getString("aeroway", "")), false, false);
  }

  private static Kind highwayKind(String highway) {
    return switch (highway) {
      case "motorway", "motorway_link" -> new Kind("motorway", 5);
      case "trunk", "trunk_link" -> new Kind("trunk", 6);
      case "primary", "primary_link" -> new Kind("primary", 8);
      case "secondary", "secondary_link" -> new Kind("secondary", 9);
      case "tertiary", "tertiary_link" -> new Kind("tertiary", 10);
      case "unclassified", "residential", "bus_guideway", "busway" -> new Kind(highway, 12);
      case "living_street", "pedestrian", "track", "service", "footway", "steps", "path", "cycleway" ->
        new Kind(highway, 13);
      default -> null;
    };
  }

  private static Kind railwayKind(String railway, String service) {
    return switch (railway) {
      // spec: rail and narrow_gauge from z8, or z10 when the way carries a service tag (siding, spur, yard)
      case "rail", "narrow_gauge" -> new Kind(railway, service.isEmpty() ? 8 : 10);
      case "light_rail", "tram", "subway", "funicular", "monorail" -> new Kind(railway, 10);
      default -> null;
    };
  }

  private static Kind aerowayKind(String aeroway) {
    return switch (aeroway) {
      case "runway" -> new Kind("runway", 11);
      case "taxiway" -> new Kind("taxiway", 13);
      default -> null;
    };
  }

  /**
   * Emits one {@code streets} feature for a single identity of the way.
   *
   * @param isHighway whether this feature is the way's road identity, which alone carries {@code link} and the access
   *                  attributes — those describe the road, not a railway sharing its geometry
   * @param rail      whether this feature is the way's railway identity
   */
  private void emitStreet(SourceFeature f, FeatureCollector features, Kind kind, boolean isHighway, boolean rail) {
    if (kind == null) {
      return;
    }
    int mz = kind.minzoom();
    String service = f.getString("service", "");
    String surface = f.getString("surface");
    String tracktype = f.getString("tracktype");
    boolean link = isHighway && LINK_HIGHWAYS.contains(f.getString("highway", ""));
    boolean oneway = !rail && ZOrder.isOneway(f);
    boolean onewayReverse = !rail && ZOrder.isReverseOneway(f);
    boolean early = options.has(Experiment.EARLY_ATTRIBUTES);
    // link and service identify the feature: with early_attributes they arrive with it rather than at z11
    int identifyingMinzoom = early ? Math.min(mz, MED_TIER_MINZOOM) : MED_TIER_MINZOOM;
    // `service` is floored at z10, the zoom the proposal specifies: a motorway or trunk carrying a service tag would
    // otherwise expose it from z5, earlier than any consumer of the proposed schema expects
    int serviceMinzoom = early ? Math.clamp(mz, EARLY_SERVICE_MINZOOM, MED_TIER_MINZOOM) : MED_TIER_MINZOOM;

    var feature = features.line(STREETS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setSortKey(ZOrder.zOrder(f, rail, false))
      .setAttr("kind", kind.value());
    Attrs.setIfTrue(feature, "rail", rail, mz);

    // mid tier (z11+)
    Attrs.setIfTrue(feature, "link", link, identifyingMinzoom);
    Attrs.setIfTrue(feature, "tunnel", ZOrder.isTunnel(f), MED_TIER_MINZOOM);
    Attrs.setIfTrue(feature, "bridge", ZOrder.isBridge(f), MED_TIER_MINZOOM);
    if (surface != null && !surface.isEmpty()) {
      // the schema defines surface as the raw value of the OSM tag, like street_polygons below
      feature.setAttrWithMinzoom("surface", surface, MED_TIER_MINZOOM);
    }
    if (tracktype != null && !tracktype.isEmpty()) {
      feature.setAttrWithMinzoom("tracktype", tracktype, MED_TIER_MINZOOM);
    }
    if (!service.isEmpty()) {
      feature.setAttrWithMinzoom("service", service, serviceMinzoom);
    }

    // full tier (z14)
    Attrs.setIfTrue(feature, "oneway", oneway, FULL_TIER_MINZOOM);
    Attrs.setIfTrue(feature, "oneway_reverse", onewayReverse, FULL_TIER_MINZOOM);

    if (!isHighway) {
      return; // the access attributes describe the road, so they belong to the highway feature only
    }
    if (options.v11()) {
      // 1.1: motorcar/bicycle/foot/horse, normalized to yes/limited/no, from z13
      for (String attribute : Access.ATTRIBUTES) {
        Attrs.setIfPresent(feature, attribute, Access.of(f, attribute), Access.MINZOOM);
      }
    } else {
      // 1.0: the raw bicycle/horse tag values, from z14 (z13 with early_attributes)
      int accessMinzoom = early ? EARLY_ACCESS_MINZOOM : FULL_TIER_MINZOOM;
      Attrs.setIfPresent(feature, "bicycle", f.getString("bicycle"), accessMinzoom);
      Attrs.setIfPresent(feature, "horse", f.getString("horse"), accessMinzoom);
    }
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

    // rail is always false here, the schema default, so it is not written
    var feature = features.polygon(POLYGONS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setSortKey(ZOrder.zOrder(f, false, false))
      .setAttr("kind", kind);
    Attrs.setIfTrue(feature, "tunnel", ZOrder.isTunnel(f), mz);
    Attrs.setIfTrue(feature, "bridge", ZOrder.isBridge(f), mz);
    String surface = f.getString("surface");
    if (surface != null && !surface.isEmpty()) {
      feature.setAttr("surface", surface);
    }
    String service = f.getString("service");
    if (service != null && !service.isEmpty()) {
      feature.setAttr("service", service);
    }

    // only named polygons get a label
    if (f.hasTag("name")) {
      var label = features.pointOnSurface(POLYGON_LABELS)
        .setZoomRange(14, 14)
        .setAttr("kind", kind);
      Names.setNames(label, f, options.nameKeys(), countries);
    }
  }


}
