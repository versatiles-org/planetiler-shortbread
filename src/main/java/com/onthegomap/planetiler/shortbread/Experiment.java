package com.onthegomap.planetiler.shortbread;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the optional, <em>beyond-spec</em> features the {@link Shortbread} profile can emit. Each value is one
 * deviation from Shortbread 1.0/1.1; none is produced unless explicitly enabled via the {@code --shortbread_experiments}
 * argument (default: strict spec, no experiments). This is the single place that documents every deviation and its CLI
 * token.
 *
 * @see ShortbreadOptions
 */
public enum Experiment {
  BUILDING_HEIGHTS("building_heights", "3D building height/min_height attributes on the buildings layer"),
  BUILDING_PARTS("building_parts",
    "Simple-3D-Buildings building:part polygons + hide_3d outline flag (implies building_heights)"),
  LOCALE_NAMES("locale_names",
    "geofenced name_<lang> fallback inside matching countries (adds the Natural Earth admin_0 source)"),
  ISLAND_LABELS("island_labels", "place_labels for islands mapped as polygons (not just nodes)"),
  ADDRESS_DETAILS("address_details", "addr:unit and addr:block attributes on the addresses layer"),
  BRIDGE_NAMES("bridge_names", "name attribute on bridge polygons");

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
   * {@code none} are ignored, {@code all} expands to every experiment, and {@link #BUILDING_PARTS} implies
   * {@link #BUILDING_HEIGHTS}. Throws {@link IllegalArgumentException} on an unknown token.
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
        if (ext == null) {
          throw new IllegalArgumentException(
            "Unknown shortbread experiment '" + raw + "'. Valid values: all, none, " + tokenList());
        }
        result.add(ext);
      }
    }
    if (result.contains(BUILDING_PARTS)) {
      result.add(BUILDING_HEIGHTS); // 3D parts are meaningless without heights
    }
    return result;
  }
}
