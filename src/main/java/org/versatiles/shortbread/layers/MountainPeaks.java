package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;
import org.versatiles.shortbread.Shortbread;
import org.versatiles.shortbread.ShortbreadOptions;
import org.versatiles.shortbread.util.CountryLanguages;
import org.versatiles.shortbread.util.Names;

/**
 * EXPERIMENT {@code mountain_peaks}: the {@code mountain_peaks} point layer, which Shortbread 1.0/1.1 does not have
 * (see shortbread-docs #137). Only registered when the experiment is enabled.
 * <p>
 * Follows OpenStreetMap Carto: peaks, volcanoes and saddles mapped as nodes, at the map scale where Carto draws them.
 * Carto's zooms refer to 256 px raster tiles, one level later than the same scale in 512 px vector tiles, so its z11
 * for peaks and volcanoes is z10 here and its z15 for saddles is z14. {@code ele} is only emitted when the tag is a
 * plain number of meters, rounded to whole meters, and higher peaks come first.
 */
public class MountainPeaks implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER = "mountain_peaks";

  // a plain number of meters, as OpenStreetMap Carto accepts it; values with units or ranges are not an elevation
  private static final Pattern ELEVATION = Pattern.compile("-?\\d{1,4}(\\.\\d+)?");

  private final ShortbreadOptions options;
  private final CountryLanguages countries;

  public MountainPeaks(ShortbreadOptions options, CountryLanguages countries) {
    this.options = options;
    this.countries = countries;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchSource(Shortbread.OSM_SOURCE),
      Expression.matchAny("natural", "peak", "volcano", "saddle"));
  }

  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    if (!f.isPoint()) {
      return;
    }
    String kind = f.getString("natural");
    Integer ele = elevation(f.getString("ele"));
    var feature = features.point(LAYER)
      .setMinZoom("saddle".equals(kind) ? 14 : 10)
      .setMaxZoom(14)
      .setAttr("kind", kind)
      // higher first; peaks without a usable elevation last
      .setSortKeyDescending(ele != null ? Math.clamp(ele, -1_000, 9_999) + 1_001 : 0);
    if (ele != null) {
      feature.setAttr("ele", ele);
    }
    Names.setNames(feature, f, options.languages(), countries);
  }

  /** The elevation in whole meters, or {@code null} unless the tag is a plain number. */
  static Integer elevation(String value) {
    if (value == null || !ELEVATION.matcher(value).matches()) {
      return null;
    }
    // half away from zero, like PostgreSQL's ROUND in OpenStreetMap Carto
    return new BigDecimal(value).setScale(0, RoundingMode.HALF_UP).intValue();
  }
}
