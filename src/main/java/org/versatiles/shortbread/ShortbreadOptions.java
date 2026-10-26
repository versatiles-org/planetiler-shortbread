package org.versatiles.shortbread;

import com.onthegomap.planetiler.config.Arguments;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

  /** The {@code --name_languages} entry that stands for {@link #EXTENDED_LANGUAGES}. */
  public static final String EXTENDED = "extended";

  /**
   * The languages behind {@code --name_languages=extended}: the OpenMapTiles language list without its non-IETF codes
   * ({@code ja_kana}, {@code ja_rm}), {@code tok} and the romanisations ({@code ja-Latn}, {@code ja-Hira},
   * {@code ko-Latn}), plus the languages with more than about 25,000 {@code name:<code>} tags on taginfo that
   * OpenMapTiles lacks ({@code gl}, {@code mr}, {@code ms}, {@code my}, {@code ne}, {@code sw}, {@code uz}).
   */
  public static final List<String> EXTENDED_LANGUAGES = List.of("af", "am", "ar", "az", "be", "bg", "bn", "br", "bs",
    "ca", "co", "cs", "cy", "da", "de", "el", "en", "eo", "es", "et", "eu", "fa", "fi", "fr", "fy", "ga", "gd", "gl",
    "he", "hi", "hr", "hu", "hy", "id", "is", "it", "ja", "ka", "kk", "kn", "ko", "ku", "la", "lb", "lt", "lv", "mk",
    "ml", "mr", "ms", "mt", "my", "ne", "nl", "no", "oc", "pa", "pl", "pnb", "pt", "rm", "ro", "ru", "sk", "sl", "sq",
    "sr", "sr-Latn", "sv", "sw", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "zh", "zh-Hans", "zh-Hant");

  // an IETF language tag as OSM uses it in name:<code> keys: a lower-case primary language, then optional subtags
  private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*");

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
    List<String> requested = args.getList("name_languages", "IETF language codes to emit as name_<code> attributes, " +
      "or '" + EXTENDED + "' for " + EXTENDED_LANGUAGES.size() + " languages", List.of("en", "de"));
    if (!v11 && !Set.copyOf(requested).equals(V10_LANGUAGES)) {
      throw new IllegalArgumentException("Shortbread 1.0 defines only name_en and name_de, so name_languages must be " +
        "en,de, got '" + String.join(",", requested) + "'. Use --shortbread_version=1.1 for other languages.");
    }
    List<String> languages = expandLanguages(requested);
    // beyond-spec features are opt-in; default is strict spec (no experiments)
    Set<Experiment> experiments =
      Experiment.parse(args.getList("shortbread_experiments", Experiment.help(), List.of()));
    return new ShortbreadOptions(v11, languages, experiments);
  }

  /**
   * Replaces {@value #EXTENDED} with {@link #EXTENDED_LANGUAGES} and drops repeated codes, keeping the first position.
   * Throws {@link IllegalArgumentException} on an entry that is not a language code, so a typo or a {@code name:*}
   * suffix that is not a language (such as {@code etymology}) fails at startup instead of emitting an unused attribute.
   */
  static List<String> expandLanguages(List<String> requested) {
    Set<String> languages = new LinkedHashSet<>();
    for (String code : requested) {
      if (code.toLowerCase(Locale.ROOT).equals(EXTENDED)) {
        languages.addAll(EXTENDED_LANGUAGES);
      } else if (LANGUAGE_CODE.matcher(code).matches()) {
        languages.add(code);
      } else {
        throw new IllegalArgumentException("Invalid name_languages entry '" + code + "': expected an IETF language " +
          "code as used in name:<code> tags (for example en, zh-Hant), or '" + EXTENDED + "'.");
      }
    }
    return List.copyOf(languages);
  }

  /** Whether the given beyond-spec experiment is enabled. */
  public boolean has(Experiment experiment) {
    return experiments.contains(experiment);
  }
}
