package com.onthegomap.planetiler.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.shortbread.Shortbread;
import com.onthegomap.planetiler.shortbread.ShortbreadOptions;
import com.onthegomap.planetiler.shortbread.util.Geo;
import com.onthegomap.planetiler.shortbread.util.Names;
import com.onthegomap.planetiler.shortbread.util.ZOrder;
import com.onthegomap.planetiler.shortbread.util.Zooms;

/**
 * The {@code water_lines} layer (and its {@code water_lines_labels}): rivers, canals, streams, drains and ditches as
 * lines. Ports {@code process_water_lines} from the Tilemaker reference. Rivers and canals use a length-based minimum
 * zoom; smaller waterways have fixed minimum zooms.
 */
public class WaterLines implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "water_lines";
  public static final String LABELS = "water_lines_labels";

  private final ShortbreadOptions options;

  public WaterLines(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchField("waterway"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (Geo.isArea(f) || !f.canBeLine()) {
      return;
    }
    String kind = f.getString("waterway", "");
    int mz;
    int mzLabel;
    // note: process.lua has an unreachable second "canal" branch; canal follows the river branch
    if (kind.equals("river") || kind.equals("canal")) {
      int byLength = Zooms.zminForLength(0.25, Geo.worldLength(f));
      mz = Math.max(9, byLength);
      mzLabel = Math.max(12, byLength); // spec: river/canal labels from z12
    } else if (kind.equals("stream")) {
      mz = 14; // spec: streams from z14
      mzLabel = 14;
    } else if (kind.equals("drain")) {
      mz = options.v11() ? 14 : 13; // Shortbread 1.1 documents drain at z14
      mzLabel = 14;
    } else if (kind.equals("ditch")) {
      mz = 14;
      mzLabel = 14;
    } else {
      return;
    }

    boolean tunnel = ZOrder.isTunnel(f);
    boolean bridge = ZOrder.isBridge(f);
    int sortKey = ZOrder.layer(f);

    if (mz <= 14) {
      features.line(LAYER)
        .setMinZoom(mz)
        .setMaxZoom(14)
        .setMinPixelSize(0)
        .setAttr("kind", kind)
        .setAttr("tunnel", tunnel)
        .setAttr("bridge", bridge)
        .setSortKey(sortKey);
    }

    if (f.hasTag("name") && mzLabel <= 14) {
      var label = features.line(LABELS)
        .setMinZoom(mzLabel)
        .setMaxZoom(14)
        .setMinPixelSize(0)
        .setAttr("kind", kind)
        .setAttr("tunnel", tunnel)
        .setAttr("bridge", bridge)
        .setSortKey(sortKey);
      Names.setNames(label, f, options.languages());
    }
  }
}
