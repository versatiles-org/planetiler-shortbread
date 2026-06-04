package com.onthegomap.planetiler.shortbread;

import com.onthegomap.planetiler.config.Arguments;
import java.util.List;

/**
 * Schema-variant options for the {@link Shortbread} profile, controlling which version of the Shortbread schema is
 * produced and which name translations are emitted.
 *
 * @param v11       whether to produce Shortbread 1.1 (vs the default 1.0)
 * @param languages IETF language codes for the {@code name_<code>} attributes, sourced from {@code name:<code>} tags
 */
public record ShortbreadOptions(boolean v11, List<String> languages) {

  public static ShortbreadOptions from(Arguments args) {
    String version = args.getString("shortbread_version", "Shortbread schema version: 1.0 or 1.1", "1.0");
    boolean v11 = version.startsWith("1.1");
    // 1.0 fixes the set to en/de; 1.1 allows any IETF codes, defaulting to en/de for continuity
    List<String> languages =
      args.getList("name_languages", "IETF language codes to emit as name_<code> attributes", List.of("en", "de"));
    return new ShortbreadOptions(v11, languages);
  }
}
