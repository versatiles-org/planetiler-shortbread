package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Sets the three Shortbread name attributes from their OSM tags: {@code name} ← {@code name}, {@code name_en} ←
 * {@code name:en}, {@code name_de} ← {@code name:de}. Each is emitted only when the tag is present.
 * <p>
 * DEVIATION: the Tilemaker reference ({@code setNameAttributes}) fills each field with a fallback chain (e.g.
 * {@code name_en} = name:en → name → name:de), so a feature tagged only with {@code name} gets three identical name
 * fields. Following the Shortbread schema's test spec (and the previous Planetiler YAML schema) we instead emit each
 * field from its own tag only, leaving the translated fields unset when no translation exists — this keeps tiles
 * smaller and matches the existing output.
 */
public final class Names {

  private Names() {}

  public static void setNames(FeatureCollector.Feature feature, SourceFeature source) {
    setIfPresent(feature, "name", source.getString("name"));
    setIfPresent(feature, "name_en", source.getString("name:en"));
    setIfPresent(feature, "name_de", source.getString("name:de"));
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }
}
