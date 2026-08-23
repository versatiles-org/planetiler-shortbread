package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.PolygonIndex;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A country &rarr; default-language spatial lookup, used to infer the language of an unqualified OSM {@code name} tag
 * from where the feature sits. Built from the Natural Earth {@code ne_10m_admin_0_countries} polygons (wired as a
 * source <em>before</em> the OSM source in {@code ShortbreadMain}, so the index is fully populated before any OSM
 * feature is processed — the same ordering OpenMapTiles relies on for its Natural Earth lookups).
 * <p>
 * EXPERIMENT (beyond Shortbread 1.0/1.1): lets {@link Names} fill {@code name_<lang>} for a feature tagged only with
 * {@code name} when it lies in a country whose default language is {@code <lang>} (e.g. a German town tagged just
 * {@code name=Köln} gets {@code name_de=Köln}). This makes a frontend "show only language X" mode usable — the
 * unqualified local name is treated as language X inside X-speaking countries — without globally copying {@code name}
 * into every {@code name_<lang>} (which the Tilemaker reference does, and which would mislabel a French town's name as
 * German). Multilingual countries (CH, BE, LU, CA, ...) are intentionally omitted to avoid guessing.
 * <p>
 * The index uses (Web-Mercator) {@link SourceFeature#worldGeometry() world geometry}, matching the query point.
 * Natural Earth 10m is coarse near borders; that is acceptable for a name-language heuristic.
 */
public class CountryLanguages implements ForwardingProfile.FeatureProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(CountryLanguages.class);

  /** Natural Earth source/layer name, wired in {@code ShortbreadMain}. */
  public static final String SOURCE = "ne_10m_admin_0_countries";

  // ISO 3166-1 alpha-2 -> default (single official/dominant) language, for clearly monolingual countries only.
  // Multi-official-language countries are deliberately absent (see class doc).
  private static final Map<String, String> ISO_TO_LANGUAGE = Map.ofEntries(
    Map.entry("DE", "de"), Map.entry("AT", "de"), Map.entry("LI", "de"),
    Map.entry("FR", "fr"), Map.entry("MC", "fr"),
    Map.entry("NL", "nl"),
    Map.entry("GB", "en"), Map.entry("IE", "en"), Map.entry("US", "en"), Map.entry("AU", "en"),
    Map.entry("NZ", "en"),
    Map.entry("IT", "it"), Map.entry("SM", "it"),
    Map.entry("ES", "es"), Map.entry("AR", "es"), Map.entry("MX", "es"), Map.entry("CO", "es"),
    Map.entry("CL", "es"), Map.entry("PE", "es"), Map.entry("VE", "es"), Map.entry("EC", "es"),
    Map.entry("BO", "es"), Map.entry("PY", "es"), Map.entry("UY", "es"), Map.entry("CR", "es"),
    Map.entry("CU", "es"), Map.entry("DO", "es"), Map.entry("GT", "es"), Map.entry("HN", "es"),
    Map.entry("NI", "es"), Map.entry("PA", "es"), Map.entry("SV", "es"),
    Map.entry("PT", "pt"), Map.entry("BR", "pt"),
    Map.entry("DK", "da"), Map.entry("SE", "sv"), Map.entry("NO", "no"), Map.entry("IS", "is"),
    Map.entry("FI", "fi"),
    Map.entry("PL", "pl"), Map.entry("CZ", "cs"), Map.entry("SK", "sk"), Map.entry("HU", "hu"),
    Map.entry("RO", "ro"), Map.entry("BG", "bg"), Map.entry("GR", "el"), Map.entry("HR", "hr"),
    Map.entry("SI", "sl"), Map.entry("RS", "sr"), Map.entry("EE", "et"), Map.entry("LV", "lv"),
    Map.entry("LT", "lt"), Map.entry("RU", "ru"), Map.entry("UA", "uk"), Map.entry("TR", "tr"),
    Map.entry("JP", "ja"), Map.entry("KR", "ko"), Map.entry("CN", "zh"), Map.entry("TW", "zh"),
    Map.entry("TH", "th"), Map.entry("VN", "vi"));

  // Natural Earth admin_0 ISO field, by preference. *_EH variants fill the "-99" gaps in the plain field; the
  // shapefile reader preserves the DBF (upper-case) field names, but we also try lower-case for robustness.
  private static final List<String> ISO_FIELDS = List.of("ISO_A2_EH", "ISO_A2", "iso_a2_eh", "iso_a2");

  private final PolygonIndex<String> index = PolygonIndex.create();
  private final Set<String> requestedLanguages;

  /** @param requestedLanguages the {@code name_<code>} languages this run emits; others are never indexed */
  public CountryLanguages(List<String> requestedLanguages) {
    this.requestedLanguages = Set.copyOf(requestedLanguages);
  }

  @Override
  public Expression filter() {
    return Expression.matchSource(SOURCE);
  }

  /** Builds the country index; emits no tile features. */
  @Override
  public void processFeature(SourceFeature f, FeatureCollector features) {
    String iso = isoCode(f);
    if (iso == null) {
      return;
    }
    String language = ISO_TO_LANGUAGE.get(iso);
    if (language == null || !requestedLanguages.contains(language)) {
      return; // unknown/multilingual country, or a language this run does not emit
    }
    try {
      index.put(f.worldGeometry(), language);
    } catch (GeometryException e) {
      LOGGER.warn("Skipping country {} with invalid geometry: {}", iso, e.getMessage());
    }
  }

  /**
   * Returns the default language of the country containing this feature's representative point, or {@code null} if the
   * feature is outside every indexed country (or its geometry is unusable).
   */
  public String languageAt(SourceFeature f) {
    try {
      Geometry point = f.worldGeometry().getCentroid();
      if (point.isEmpty()) {
        return null;
      }
      return index.getOnlyContaining((org.locationtech.jts.geom.Point) point);
    } catch (GeometryException e) {
      return null;
    }
  }

  private static String isoCode(SourceFeature f) {
    for (String field : ISO_FIELDS) {
      String value = f.getString(field);
      if (value != null && !value.isEmpty() && !value.equals("-99")) {
        return value.toUpperCase(Locale.ROOT);
      }
    }
    return null;
  }
}
