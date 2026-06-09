package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Poi;

/**
 * The {@code addresses} layer (zoom 14): house numbers/names from {@code addr:housenumber} / {@code addr:housename}.
 * Ports {@code process_addresses}.
 * <p>
 * As in Tilemaker, a feature that already qualifies for the {@code pois} layer is not also emitted here (see
 * {@link Poi#matches}).
 */
public class Addresses implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "addresses";

  private final ShortbreadOptions options;

  public Addresses(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.or(
        Expression.matchField("addr:housename"),
        Expression.matchField("addr:housenumber")));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (Poi.matches(f, options.v11())) {
      return;
    }
    FeatureCollector.Feature feature;
    if (f.isPoint()) {
      feature = features.point(LAYER);
    } else if (Geo.isArea(f)) {
      feature = features.pointOnSurface(LAYER);
    } else {
      return;
    }
    feature.setZoomRange(14, 14);
    setIfPresent(feature, "housename", f.getString("addr:housename"));
    setIfPresent(feature, "housenumber", f.getString("addr:housenumber"));
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }
}
