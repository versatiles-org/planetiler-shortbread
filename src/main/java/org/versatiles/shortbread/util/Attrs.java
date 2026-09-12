package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Helpers for writing an output attribute only when it carries information.
 * <p>
 * Shortbread gives most attributes a default — an empty string, or {@code false} for the booleans — and a value equal
 * to the default is left out rather than written, so tiles do not carry it on every feature. These helpers express that
 * rule once instead of once per layer.
 */
public final class Attrs {

  private Attrs() {}

  /** Writes {@code value} unless it is null or empty. */
  public static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }

  /** Writes {@code value} from {@code minzoom} upwards, unless it is null or empty. */
  public static void setIfPresent(FeatureCollector.Feature feature, String key, String value, int minzoom) {
    if (value != null && !value.isEmpty()) {
      feature.setAttrWithMinzoom(key, value, minzoom);
    }
  }

  /** Writes a boolean attribute only when it is true, since the schema's default for these is false. */
  public static void setIfTrue(FeatureCollector.Feature feature, String key, boolean value, int minzoom) {
    if (value) {
      feature.setAttrWithMinzoom(key, true, minzoom);
    }
  }

  /** Writes {@code key} as {@code true} when the source feature carries it as {@code yes}. */
  public static void setIfYes(FeatureCollector.Feature feature, SourceFeature f, String key) {
    if (f.hasTag(key, "yes")) {
      feature.setAttr(key, true);
    }
  }
}
