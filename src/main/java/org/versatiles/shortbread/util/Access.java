package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Map;

/**
 * Derives the Shortbread 1.1 access attributes ({@code motorcar}, {@code bicycle}, {@code foot}, {@code horse}) of the
 * {@code streets} layer from OSM access tags.
 * <p>
 * Each attribute takes the value of the first tag in its priority chain whose value is one of the recognized access
 * values, normalized to {@code yes} / {@code limited} / {@code no}. Unrecognized values are ignored, so a more specific
 * tag with an exotic value falls through to the next tag in the chain. When no tag in a chain yields a value, the
 * attribute is omitted.
 * <p>
 * Access is only defined for {@code highway} features; railways and aeroways carry none of these attributes.
 * <p>
 * Shortbread 1.0 has no such mapping: it emits the raw {@code bicycle} and {@code horse} tag values instead, from z14.
 *
 * @see <a href="https://shortbread-tiles.org/schema/1.1/">Shortbread 1.1, layer "streets"</a>
 */
public final class Access {

  private Access() {}

  /** Zoom from which Shortbread 1.1 makes the access attributes available. */
  public static final int MINZOOM = 13;

  private static final Map<String, String> VALUES = Map.ofEntries(
    Map.entry("yes", "yes"),
    Map.entry("designated", "yes"),
    Map.entry("permissive", "yes"),
    Map.entry("customers", "limited"),
    Map.entry("destination", "limited"),
    Map.entry("agricultural", "limited"),
    Map.entry("forestry", "limited"),
    Map.entry("delivery", "limited"),
    Map.entry("discouraged", "limited"),
    Map.entry("permit", "limited"),
    Map.entry("dismount", "no"),
    Map.entry("military", "no"),
    Map.entry("private", "no"),
    Map.entry("no", "no"));

  /** Output attribute name to the OSM tags consulted for it, most specific first. */
  private static final Map<String, List<String>> CHAINS = Map.of(
    "motorcar", List.of("motorcar", "motor_vehicle", "vehicle", "access"),
    "bicycle", List.of("bicycle", "vehicle", "access"),
    "foot", List.of("foot", "access"),
    "horse", List.of("horse", "access"));

  /** The attribute names, in the order the schema lists them. */
  public static final List<String> ATTRIBUTES = List.of("motorcar", "bicycle", "foot", "horse");

  /**
   * Returns the normalized access value for {@code attribute}, or {@code null} if no tag in its chain carries a
   * recognized value.
   */
  public static String of(SourceFeature feature, String attribute) {
    for (String tag : CHAINS.get(attribute)) {
      String value = feature.getString(tag);
      // Map.ofEntries rejects a null key, and an absent tag reads as null
      String mapped = value == null ? null : VALUES.get(value);
      if (mapped != null) {
        return mapped;
      }
    }
    return null;
  }
}
