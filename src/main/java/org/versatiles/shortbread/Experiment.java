package org.versatiles.shortbread;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of the optional, <em>beyond-spec</em> features the {@link Shortbread} profile can emit. Each value is one
 * deviation from Shortbread 1.0/1.1; none is produced unless explicitly enabled via the
 * {@code --shortbread_experiments} argument (default: strict spec, no experiments). This is the single place that
 * documents every deviation and its CLI token.
 *
 * @see ShortbreadOptions
 */
public enum Experiment {
  BUILDINGS_3D("3d_buildings",
    "3D buildings: height/min_height on buildings, building:part polygons marked part=true, hide_3d on outlines"),
  LOCALE_NAMES("locale_names",
    "geofenced name_<lang> fallback inside matching countries (adds the Natural Earth admin_0 source)"),
  ISLAND_LABELS("island_labels", "place_labels for islands mapped as polygons (not just nodes)"),
  ADDRESS_DETAILS("address_details", "addr:unit and addr:block attributes on the addresses layer"),
  BRIDGE_NAMES("bridge_names", "name attribute on bridge polygons");

  private static final Logger LOGGER = LoggerFactory.getLogger(Experiment.class);

  private final String token;
  private final String description;

  Experiment(String token, String description) {
    this.token = token;
    this.description = description;
  }

  public String token() {
    return token;
  }

  public String description() {
    return description;
  }

  private static final Map<String, Experiment> BY_TOKEN =
    EnumSet.allOf(Experiment.class).stream().collect(Collectors.toMap(Experiment::token, Function.identity()));

  /** Tokens of experiments that were merged into another one, still accepted with a warning. */
  private static final Map<String, Experiment> DEPRECATED_TOKENS =
    Map.of("building_heights", BUILDINGS_3D, "building_parts", BUILDINGS_3D);

  private static String tokenList() {
    return BY_TOKEN.keySet().stream().sorted().collect(Collectors.joining(", "));
  }

  /** Help text for the {@code --shortbread_experiments} argument. */
  public static String help() {
    return "comma-separated beyond-spec experiments to enable: 'all', 'none' (default = strict spec), or any of: " +
      tokenList();
  }

  /**
   * Parses argument tokens (each {@code all}, {@code none}, or an experiment {@link #token()}) into a set. Blanks and
   * {@code none} are ignored, {@code all} expands to every experiment, and the deprecated {@code building_heights} /
   * {@code building_parts} tokens enable {@link #BUILDINGS_3D} with a warning. Throws {@link IllegalArgumentException}
   * on an unknown token.
   */
  public static Set<Experiment> parse(List<String> tokens) {
    Set<Experiment> result = EnumSet.noneOf(Experiment.class);
    if (tokens != null) {
      for (String raw : tokens) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("none")) {
          continue;
        }
        if (t.equals("all")) {
          result.addAll(EnumSet.allOf(Experiment.class));
          continue;
        }
        Experiment ext = BY_TOKEN.get(t);
        if (ext == null && DEPRECATED_TOKENS.containsKey(t)) {
          ext = DEPRECATED_TOKENS.get(t);
          LOGGER.warn("Shortbread experiment '{}' is deprecated, use '{}' instead; it enables heights, building " +
            "parts and hide_3d together", t, ext.token());
        }
        if (ext == null) {
          throw new IllegalArgumentException(
            "Unknown shortbread experiment '" + raw + "'. Valid values: all, none, " + tokenList());
        }
        result.add(ext);
      }
    }
    return result;
  }
}
