package org.versatiles.shortbread;

import com.onthegomap.planetiler.config.Arguments;
import java.util.List;
import java.util.Set;

/**
 * Schema-variant options for the {@link Shortbread} profile, controlling which version of the Shortbread schema is
 * produced, which name translations are emitted, and which beyond-spec {@link Experiment}s are enabled.
 *
 * @param v11        whether to produce Shortbread 1.1 (vs the default 1.0)
 * @param languages  IETF language codes for the {@code name_<code>} attributes, sourced from {@code name:<code>} tags
 * @param experiments the enabled beyond-spec experiments (empty = strict spec)
 */
public record ShortbreadOptions(boolean v11, List<String> languages, Set<Experiment> experiments) {

  public static ShortbreadOptions from(Arguments args) {
    String version = args.getString("shortbread_version", "Shortbread schema version: 1.0 or 1.1", "1.0");
    boolean v11 = version.startsWith("1.1");
    // 1.0 fixes the set to en/de; 1.1 allows any IETF codes, defaulting to en/de for continuity
    List<String> languages =
      args.getList("name_languages", "IETF language codes to emit as name_<code> attributes", List.of("en", "de"));
    // beyond-spec features are opt-in; default is strict spec (no experiments)
    Set<Experiment> experiments = Experiment.parse(args.getList("shortbread_experiments", Experiment.help(), List.of()));
    return new ShortbreadOptions(v11, languages, experiments);
  }

  /** Whether the given beyond-spec experiment is enabled. */
  public boolean has(Experiment experiment) {
    return experiments.contains(experiment);
  }
}
