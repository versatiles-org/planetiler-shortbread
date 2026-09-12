package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import org.versatiles.shortbread.ShortbreadOptions.NameKeys;

/**
 * Sets the Shortbread name attributes from their OSM tags: {@code name} ← {@code name}, and one {@code name_<code>} ←
 * {@code name:<code>} per requested IETF language code. Each is emitted only when the tag is present.
 * <p>
 * Shortbread 1.0 fixes the set to {@code name_en} / {@code name_de}; Shortbread 1.1 generalizes this to an
 * implementation-defined {@code name_<code>} list (see {@link org.versatiles.shortbread.ShortbreadOptions}).
 * <p>
 * Each field comes from its own tag only: a feature tagged with {@code name} alone gets no {@code name_<code>}
 * attributes, rather than copies of {@code name}.
 * <p>
 * Values are emitted as tagged. OSM allows several values in one tag separated by {@code ;} (e.g.
 * {@code name=Mole Lake;Dewe'igan-...}); such a value stays one string, and a style that wants a single name can split
 * it.
 * <p>
 * EXPERIMENT: when a {@link CountryLanguages} index is supplied, a feature's unqualified {@code name} is copied into
 * {@code name_<lang>} <em>only</em> when the feature lies in a country whose default language is {@code <lang>} (e.g.
 * {@code name_de} inside Germany). This makes a frontend "show only language X" mode show local names inside X-speaking
 * countries, without mislabelling foreign names. See {@link CountryLanguages}.
 */
public final class Names {

  private Names() {}

  public static void setNames(FeatureCollector.Feature feature, SourceFeature source, List<NameKeys> languages,
    CountryLanguages countries) {
    String name = source.getString("name");
    Attrs.setIfPresent(feature, "name", name);
    // the `name_<code>` / `name:<code>` strings are built once in ShortbreadOptions, not per feature
    for (NameKeys keys : languages) {
      Attrs.setIfPresent(feature, keys.attribute(), source.getString(keys.tag()));
    }
    if (countries != null && name != null && !name.isEmpty()) {
      String language = countries.languageAt(source);
      if (language == null) {
        return;
      }
      // only fill the translated field if it is requested, and still empty — not already set from name:<lang>
      for (NameKeys keys : languages) {
        if (keys.language().equals(language)) {
          String tagged = source.getString(keys.tag());
          if (tagged == null || tagged.isEmpty()) {
            feature.setAttr(keys.attribute(), name);
          }
          return;
        }
      }
    }
  }

}
