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

/**
 * The {@code street_labels} line layer and the {@code street_labels_points} layer ({@code highway=motorway_junction}
 * nodes). Ports {@code process_street_labels} and the motorway-junction branch of {@code node_function}.
 * <p>
 * The {@code ref} tag is split on {@code ;} into a multi-line string, with {@code ref_rows}/{@code ref_cols} giving the
 * shield grid dimensions.
 * <p>
 * DEVIATION (bug fix): Tilemaker called {@code toTunnelBool()} with no arguments here, so {@code tunnel} was always
 * false; we compute it from the actual tags.
 */
public class StreetLabels implements ForwardingProfile.FeatureProcessor {

  public static final String LABELS = "street_labels";
  public static final String POINTS = "street_labels_points";

  private final ShortbreadOptions options;

  public StreetLabels(ShortbreadOptions options) {
    this.options = options;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchField("highway"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    String highway = f.getString("highway", "");

    if (f.isPoint()) {
      if (highway.equals("motorway_junction")) {
        var feature = features.point(POINTS)
          .setZoomRange(12, 14)
          .setAttr("kind", highway);
        setIfPresent(feature, "ref", f.getString("ref"));
        Names.setNames(feature, f, options.languages());
      }
      return;
    }

    if (!f.canBeLine() || Geo.areaYesMultiBoundary(f)) {
      return;
    }
    int mz = labelMinZoom(highway);
    if (mz > 14) {
      return;
    }

    String ref = f.getString("ref", "");
    String[] refs = ref.isEmpty() ? new String[0] : ref.split(";");
    int rows = 0;
    int cols = 0;
    StringBuilder joined = new StringBuilder();
    for (String word : refs) {
      if (word.isEmpty()) {
        continue;
      }
      if (rows > 0) {
        joined.append('\n');
      }
      joined.append(word);
      rows++;
      cols = Math.max(cols, word.length());
    }

    boolean hasName = f.hasTag("name");
    if (!hasName && rows == 0) {
      return;
    }

    var feature = features.line(LABELS)
      .setMinZoom(mz)
      .setMaxZoom(14)
      .setMinPixelSize(0)
      .setSortKey(ZOrder.zOrder(f, false, true))
      .setAttr("kind", highway)
      .setAttr("tunnel", ZOrder.isTunnel(f)); // DEVIATION: actually computed (Tilemaker always emitted false)
    if (rows > 0) {
      feature.setAttr("ref", joined.toString());
      feature.setAttr("ref_rows", rows);
      feature.setAttr("ref_cols", cols);
    }
    Names.setNames(feature, f, options.languages());
  }

  private static int labelMinZoom(String highway) {
    return switch (highway) {
      case "motorway" -> 10;
      case "trunk", "primary" -> 12;
      case "secondary", "tertiary" -> 13;
      case "motorway_link", "trunk_link", "primary_link", "secondary_link" -> 13;
      case "tertiary_link", "unclassified", "residential", "busway", "bus_guideway", "living_street", "pedestrian",
        "track", "service", "footway", "steps", "path", "cycleway" -> 14;
      default -> Integer.MAX_VALUE;
    };
  }

  private static void setIfPresent(FeatureCollector.Feature feature, String key, String value) {
    if (value != null && !value.isEmpty()) {
      feature.setAttr(key, value);
    }
  }
}
