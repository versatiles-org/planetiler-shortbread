package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;

/**
 * Sets the Shortbread name attributes from their OSM tags: {@code name} ← {@code name}, and one {@code name_<code>} ←
 * {@code name:<code>} per requested IETF language code. Each is emitted only when the tag is present.
 * <p>
 * Shortbread 1.0 fixes the set to {@code name_en} / {@code name_de}; Shortbread 1.1 generalizes this to an
 * implementation-defined {@code name_<code>} list (see {@link com.onthegomap.planetiler.shortbread.ShortbreadOptions}).
 * <p>
 * DEVIATION: the Tilemaker reference ({@code setNameAttributes}) fills each field with a fallback chain (e.g.
 * {@code name_en} = name:en → name → name:de), so a feature tagged only with {@code name} gets identical name fields.
 * Following the Shortbread schema's test spec (and the previous Planetiler YAML schema) we instead emit each field from
 * its own tag only, leaving the translated fields unset when no translation exists.
 * <p>
 * OSM allows several values in one tag separated by {@code ;} (e.g. {@code name=Mole Lake;Dewe'igan-...}); we keep only
 * the first so a label is a single clean name rather than a concatenated list.
 * <p>
 * EXPERIMENT: as a refinement of that reference behaviour, when a {@link CountryLanguages} index is supplied we copy a
 * feature's unqualified {@code name} into {@code name_<lang>} <em>only</em> when the feature lies in a country whose
 * default language is {@code <lang>} (e.g. {@code name_de} inside Germany). This is the geofenced version of
 * Tilemaker's global copy: it makes a frontend "show only language X" mode show local names inside X-speaking
 * countries, without mislabelling foreign names. See {@link CountryLanguages}.
 */
public final class Names {

  private Names() {}

  public static void setNames(FeatureCollector.Feature feature, SourceFeature source, List<String> languages) {
    setNames(feature, source, languages, null);
  }

  public static void setNames(FeatureCollector.Feature feature, SourceFeature source, List<String> languages,
    CountryLanguages countries) {
    String name = firstName(source.getString("name"));
    setIfPresent(feature, "name", name);
    for (String code : languages) {
      setIfPresent(feature, "name_" + code, firstName(source.getString("name:" + code)));
    }
    if (countries != null && name != null && !name.isEmpty()) {
      String language = countries.languageAt(source);
      // only fill the translated field if it is requested, still empty, and not already set from name:<lang>
      if (language != null && languages.contains(language) &&
        (source.getString("name:" + language) == null || source.getString("name:" + language).isEmpty())) {
        feature.setAttr("name_" + language, name);
      }
    }
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }

  /** Returns the first {@code ;}-separated value of an OSM multi-value name (trimmed), or {@code null} if unset. */
  private static String firstName(String value) {
    if (value == null) {
      return null;
    }
    int i = value.indexOf(';');
    return (i >= 0 ? value.substring(0, i) : value).trim();
  }
}
