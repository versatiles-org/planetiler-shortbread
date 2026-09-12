package org.versatiles.shortbread;

import com.onthegomap.planetiler.config.Arguments;
import java.util.List;
import java.util.Set;

/**
 * Schema-variant options for the {@link Shortbread} profile, controlling which version of the Shortbread schema is
 * produced, which name translations are emitted, and which beyond-spec {@link Experiment}s are enabled.
 *
 * @param v11         whether to produce Shortbread 1.1 (vs the default 1.0)
 * @param languages   IETF language codes for the {@code name_<code>} attributes, sourced from {@code name:<code>} tags
 * @param experiments the enabled beyond-spec experiments (empty = strict spec)
 */
public record ShortbreadOptions(boolean v11, List<String> languages, Set<Experiment> experiments,
  List<NameKeys> nameKeys) {

  /** The output attribute and the OSM tag for one requested language, built once instead of per feature. */
  public record NameKeys(String language, String attribute, String tag) {}

  /** The name languages Shortbread 1.0 defines: {@code name_en} and {@code name_de}. */
  private static final Set<String> V10_LANGUAGES = Set.of("en", "de");

  /** Derives the per-language key strings from {@code languages}, so callers never build the 4th component. */
  public ShortbreadOptions(boolean v11, List<String> languages, Set<Experiment> experiments) {
    this(v11, languages, experiments, languages.stream()
      .map(code -> new NameKeys(code, "name_" + code, "name:" + code))
      .toList());
  }

  /** Reads the options, throwing {@link IllegalArgumentException} on a value the selected schema version rejects. */
  public static ShortbreadOptions from(Arguments args) {
    String version = args.getString("shortbread_version", "Shortbread schema version: 1.0 or 1.1", "1.0");
    boolean v11 = switch (version) {
      case "1.0" -> false;
      case "1.1" -> true;
      default -> throw new IllegalArgumentException(
        "Unknown shortbread_version '" + version + "'. Valid values: 1.0, 1.1");
    };
    // 1.1 allows any IETF codes, defaulting to en/de for continuity; 1.0 fixes the set to en/de
    List<String> languages =
      args.getList("name_languages", "IETF language codes to emit as name_<code> attributes", List.of("en", "de"));
    if (!v11 && !Set.copyOf(languages).equals(V10_LANGUAGES)) {
      throw new IllegalArgumentException("Shortbread 1.0 defines only name_en and name_de, so name_languages must be " +
        "en,de, got '" + String.join(",", languages) + "'. Use --shortbread_version=1.1 for other languages.");
    }
    // beyond-spec features are opt-in; default is strict spec (no experiments)
    Set<Experiment> experiments =
      Experiment.parse(args.getList("shortbread_experiments", Experiment.help(), List.of()));
    return new ShortbreadOptions(v11, languages, experiments);
  }

  /** Whether the given beyond-spec experiment is enabled. */
  public boolean has(Experiment experiment) {
    return experiments.contains(experiment);
  }
}
