package org.versatiles.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.versatiles.shortbread.Shortbread;

/**
 * The {@code ocean} layer: ocean/sea polygons covering the whole globe at every zoom level.
 * <p>
 * Unlike most layers this is not derived from OSM tags but from the pre-assembled OSM water polygons shapefile
 * (water-polygons-split-3857) published at osmdata.openstreetmap.de, wired in as the {@code ocean} source. The schema
 * defines no attributes on this layer.
 */
public class Ocean implements ForwardingProfile.FeatureProcessor {

  public static final String LAYER_NAME = "ocean";

  @Override
  public Expression filter() {
    return Expression.matchSource(Shortbread.OCEAN_SOURCE);
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    features.polygon(LAYER_NAME)
      .setZoomRange(0, 14)
      .setBufferPixels(4)
      // keep the full ocean coverage at every zoom, do not drop small slivers between tiles
      .setMinPixelSize(0);
  }
}
