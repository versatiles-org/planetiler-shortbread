package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Set;

/**
 * Whitelists of OSM key/value pairs accepted into the {@code pois} layer, ported from the {@code Set{...}} tables and
 * {@code valueAcceptedOrNil} in the Tilemaker reference ({@code process.lua}).
 * <p>
 * Shared by the {@code Pois} and {@code Addresses} layers: a feature that qualifies as a POI is not also emitted to
 * {@code addresses}.
 * <p>
 * DEVIATIONS (bug fixes) relative to {@code process.lua}:
 * <ul>
 * <li>{@code office} is validated against {@link #OFFICE} (Tilemaker mistakenly validated it against the highway
 * whitelist).</li>
 * <li>{@code man_made} participates in the "is this a POI" decision so that standalone man_made features (tower,
 * lighthouse, …) can appear, as the Shortbread spec lists {@code man_made} as a POI key (Tilemaker omitted it from the
 * gate, making the attribute unreachable). The {@code man_made} output attribute is set from the {@code man_made} value,
 * not from {@code historic}.</li>
 * </ul>
 */
public final class Poi {

  private Poi() {}

  public static final Set<String> AMENITY = Set.of(
    "police", "fire_station", "post_box", "post_office", "telephone", "library", "townhall", "courthouse", "prison",
    "embassy", "community_centre", "nursing_home", "arts_centre", "grave_yard", "marketplace", "recycling",
    "university", "school", "college", "public_building", "pharmacy", "hospital", "clinic", "doctors", "dentist",
    "veterinary", "theatre", "nightclub", "cinema", "restaurant", "fast_food", "cafe", "pub", "bar", "foot_court",
    "biergarten", "shelter", "car_rental", "car_wash", "car_sharing", "bicycle_rental", "vending_machine", "bank",
    "atm", "toilets", "bench", "drinking_water", "fountain", "hunting_stand", "waste_basket", "place_of_worship");

  public static final Set<String> SHOP = Set.of(
    "supermarket", "bakery", "kiosk", "mall", "department_store", "general", "convenience", "clothes", "florist",
    "chemist", "books", "butcher", "shoes", "alcohol", "beverages", "optician", "jewelry", "gift", "sports",
    "stationery", "outdoor", "mobile_phone", "toys", "newsagent", "greengrocer", "beauty", "video", "car", "bicycle",
    "doityourself", "hardware", "furniture", "computer", "garden_centre", "hairdresser", "travel_agency", "laundry",
    "dry_cleaning");

  public static final Set<String> TOURISM = Set.of(
    "hotel", "motel", "artwork", "bed_and_breakfast", "guest_house", "hostel", "chalet", "camp_site", "alpine_hut",
    "caravan_site", "information", "picnic_site", "viewpoint", "zoo", "theme_park");

  public static final Set<String> MAN_MADE = Set.of(
    "surveillance", "tower", "windmill", "lighthouse", "wastewater_plant", "water_well", "watermill", "water_works");

  public static final Set<String> HISTORIC = Set.of(
    "monument", "memorial", "castle", "ruins", "archaeological_site", "wayside_cross", "wayside_shrine", "battlefield",
    "fort");

  public static final Set<String> LEISURE = Set.of(
    "playground", "dog_park", "sports_centre", "pitch", "swimming_pool", "water_park", "golf_course", "stadium",
    "ice_rink");

  public static final Set<String> EMERGENCY = Set.of("phone", "fire_hydrant", "defibrillator");

  public static final Set<String> HIGHWAY = Set.of("emergency_access_point");

  public static final Set<String> OFFICE = Set.of("diplomatic");

  /** Amenity values that also carry a {@code cuisine} attribute. */
  public static final Set<String> CATERING = Set.of("restaurant", "fast_food", "pub", "bar", "cafe");
  /** Leisure values that also carry a {@code sport} attribute. */
  public static final Set<String> SPORT = Set.of("pitch", "sports_centre");

  /** Returns {@code value} if it is in {@code set}, otherwise {@code null}. */
  public static String accepted(Set<String> set, String value) {
    return value != null && set.contains(value) ? value : null;
  }

  /** Returns true if the feature carries at least one whitelisted POI key/value pair. */
  public static boolean matches(SourceFeature f) {
    return matches(f, false);
  }

  /**
   * Returns true if the feature is a POI. When {@code v11} is set, the Shortbread 1.1 additions ({@code amenity=fuel}
   * and {@code leisure=park}) are also accepted.
   */
  public static boolean matches(SourceFeature f, boolean v11) {
    return accepted(AMENITY, f.getString("amenity")) != null ||
      accepted(SHOP, f.getString("shop")) != null ||
      accepted(TOURISM, f.getString("tourism")) != null ||
      accepted(MAN_MADE, f.getString("man_made")) != null ||
      accepted(HISTORIC, f.getString("historic")) != null ||
      accepted(LEISURE, f.getString("leisure")) != null ||
      accepted(EMERGENCY, f.getString("emergency")) != null ||
      accepted(HIGHWAY, f.getString("highway")) != null ||
      accepted(OFFICE, f.getString("office")) != null ||
      (v11 && (f.hasTag("amenity", "fuel") || f.hasTag("leisure", "park")));
  }
}
