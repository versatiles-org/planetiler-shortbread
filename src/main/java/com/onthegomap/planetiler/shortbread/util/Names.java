package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Sets the three Shortbread name attributes ({@code name}, {@code name_en}, {@code name_de}) with the exact fallback
 * chaining used by the Tilemaker reference ({@code setNameAttributes} / {@code fillWithFallback} in {@code process.lua}):
 * <ul>
 * <li>{@code name} = name → name:en → name:de</li>
 * <li>{@code name_en} = name:en → name → name:de</li>
 * <li>{@code name_de} = name:de → name → name:en</li>
 * </ul>
 * <p>
 * Planetiler's {@code LanguageUtils}/{@code OmtLanguageUtils} emit far more {@code name:*} fields than Shortbread wants,
 * so this minimal helper is used instead.
 * <p>
 * DEVIATION: Tilemaker writes empty strings (it cannot emit NULL into a tile). Here we omit a field when its fallback
 * result is empty, which only happens for completely unnamed features — this keeps tiles smaller without changing the
 * value seen by consumers for any named feature.
 */
public final class Names {

  private Names() {}

  public static void setNames(FeatureCollector.Feature feature, SourceFeature source) {
    String name = nonNull(source.getString("name"));
    String nameEn = nonNull(source.getString("name:en"));
    String nameDe = nonNull(source.getString("name:de"));

    setIfPresent(feature, "name", fallback(name, nameEn, nameDe));
    setIfPresent(feature, "name_en", fallback(nameEn, name, nameDe));
    setIfPresent(feature, "name_de", fallback(nameDe, name, nameEn));
  }

  static String fallback(String first, String second, String third) {
    if (!first.isEmpty()) {
      return first;
    }
    if (!second.isEmpty()) {
      return second;
    }
    return third;
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (!value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }

  private static String nonNull(String value) {
    return value == null ? "" : value;
  }
}
