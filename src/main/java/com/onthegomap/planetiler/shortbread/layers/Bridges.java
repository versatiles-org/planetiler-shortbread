package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Names;

/**
 * The {@code bridges} layer (zoom 12): bridge outlines from {@code man_made=bridge} areas. Ports
 * {@code process_bridges}.
 * <p>
 * EXTENSION (beyond Shortbread 1.0/1.1, which has no bridge name — see shortbread-docs #141): we also emit
 * {@code name} / {@code name_<code>} when present, so named bridges can be labelled. Additive; unnamed bridges are
 * unchanged.
 */
public class Bridges implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "bridges";

  private final ShortbreadOptions options;

  public Bridges(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("man_made", "bridge"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (Geo.isArea(f)) {
      var feature = features.polygon(LAYER)
        .setMinZoom(12)
        .setMaxZoom(14)
        .setMinPixelSize(0)
        .setAttr("kind", "bridge");
      Names.setNames(feature, f, options.languages());
    }
  }
}
