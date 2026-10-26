package org.versatiles.shortbread.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.versatiles.shortbread.Shortbread;

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
 * into every {@code name_<lang>}, which would mislabel a French town's name as German). Countries with several
 * languages (CH, BE, LU, CA, ...) are intentionally omitted to avoid guessing; see {@link #COUNTRIES_BY_LANGUAGE}.
 * <p>
 * The index holds {@link PreparedGeometry prepared} polygon parts: every named feature in the build queries it, and a
 * prepared geometry indexes a country's edges once instead of walking its full ring on each test — Natural Earth
 * coastlines run to tens of thousands of points. The index uses (Web-Mercator) {@link SourceFeature#worldGeometry()
 * world geometry}, matching the query point. Natural Earth 10m is coarse near borders; that is acceptable for a
 * name-language heuristic.
 */
public class CountryLanguages implements ForwardingProfile.FeatureProcessor, ForwardingProfile.FinishHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CountryLanguages.class);

  /** Natural Earth source/layer name, wired in {@code ShortbreadMain}. */
  public static final String SOURCE = "ne_10m_admin_0_countries";

  // Language -> the countries (ISO 3166-1 alpha-2) whose unqualified `name` is taken to be in that language. A country
  // is listed when Nominatim's settings/country_settings.yaml gives it exactly this one language and, for a non-Latin
  // script, its place and street names are mostly written in that script: Bangladesh, Nepal and Myanmar are left out,
  // because many of their names are in Latin script. Countries with several languages, such as CH, BE, LU, CA, MA or IQ,
  // are left out to avoid guessing. Exceptions, where Nominatim also lists regional or minority languages, so names in
  // those regions are taken to be in the main language: BO, ES, FI, IE, IT, NO, NZ, PY, and TW (Nominatim: zh-Hant).
  private static final Map<String, String> COUNTRIES_BY_LANGUAGE = Map.ofEntries(
    Map.entry("ar", "AE BH EG JO KW LY OM QA SA SY YE"),
    Map.entry("az", "AZ"),
    Map.entry("bg", "BG"),
    Map.entry("ca", "AD"),
    Map.entry("cs", "CZ"),
    Map.entry("da", "DK"),
    Map.entry("de", "AT DE LI"),
    Map.entry("el", "GR"),
    Map.entry("en", "AG AI AU BB BM BS BZ DM FJ FK FM GB GD GG GH GI GM GS GY IE IM IO JE JM KI KN KY LC LR MS NG NZ " +
      "SB SH SL SS TC TO TT TV US VC VG ZM"),
    Map.entry("es", "AR BO CL CO CR CU DO EC ES GT HN MX NI PA PE PY SV UY VE"),
    Map.entry("et", "EE"),
    Map.entry("fa", "IR"),
    Map.entry("fi", "FI"),
    Map.entry("fr", "BF BI BJ CD CG CI FR GA GN MC ML NE SN TG"),
    Map.entry("he", "IL"),
    Map.entry("hr", "HR"),
    Map.entry("hu", "HU"),
    Map.entry("hy", "AM"),
    Map.entry("id", "ID"),
    Map.entry("is", "IS"),
    Map.entry("it", "IT SM VA"),
    Map.entry("ja", "JP"),
    Map.entry("ka", "GE"),
    Map.entry("ko", "KP KR"),
    Map.entry("lt", "LT"),
    Map.entry("lv", "LV"),
    Map.entry("mk", "MK"),
    Map.entry("ms", "BN MY"),
    Map.entry("nl", "NL SR"),
    Map.entry("no", "NO"),
    Map.entry("pl", "PL"),
    Map.entry("pt", "AO BR CV GW MZ PT ST"),
    Map.entry("ro", "RO"),
    Map.entry("ru", "RU"),
    Map.entry("sk", "SK"),
    Map.entry("sl", "SI"),
    Map.entry("sq", "AL"),
    Map.entry("sr", "RS"),
    Map.entry("sv", "SE"),
    Map.entry("th", "TH"),
    Map.entry("tr", "TR"),
    Map.entry("uk", "UA"),
    Map.entry("vi", "VN"),
    Map.entry("zh", "CN TW"));

  /** ISO 3166-1 alpha-2 &rarr; language, from {@link #COUNTRIES_BY_LANGUAGE}; a country listed twice fails here. */
  static final Map<String, String> ISO_TO_LANGUAGE = COUNTRIES_BY_LANGUAGE.entrySet().stream()
    .flatMap(e -> Arrays.stream(e.getValue().split(" ")).map(iso -> Map.entry(iso, e.getKey())))
    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

  // Natural Earth admin_0 ISO field, by preference. *_EH variants fill the "-99" gaps in the plain field; the
  // shapefile reader preserves the DBF (upper-case) field names, but we also try lower-case for robustness.
  private static final List<String> ISO_FIELDS = List.of("ISO_A2_EH", "ISO_A2", "iso_a2_eh", "iso_a2");

  /** One prepared polygon part of a country, with the language to return when a point falls inside it. */
  private record PreparedCountry(PreparedGeometry geometry, String language) {}

  private final STRtree index = new STRtree();
  private final Set<String> requestedLanguages;
  private volatile boolean built = false;
  private volatile boolean sourceRead = false;

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
    sourceRead = true;
    String iso = isoCode(f);
    if (iso == null) {
      return;
    }
    String language = ISO_TO_LANGUAGE.get(iso);
    if (language == null || !requestedLanguages.contains(language)) {
      return; // unknown/multilingual country, or a language this run does not emit
    }
    try {
      put(f.worldGeometry(), language);
    } catch (GeometryException e) {
      LOGGER.warn("Skipping country {} with invalid geometry: {}", iso, e.getMessage());
    }
  }

  /**
   * Logs an error if the OSM source finished before any feature of the Natural Earth source was read.
   * <p>
   * This instance only exists when {@code locale_names} is on, and {@link org.versatiles.shortbread.ShortbreadMain}
   * declares the Natural Earth source before OSM so the index is complete first. Every Planetiler task for this profile
   * goes through {@code ShortbreadMain}, but a {@link Shortbread} built by other code gets its options from the config
   * and not necessarily that source, and would then emit tiles with every geofenced name silently missing.
   * <p>
   * The check asks whether the source was read, not whether a country was indexed: an extract can lie entirely in
   * countries that are unmapped or whose language this run does not emit (Israel, or Estonia without {@code et}), and
   * the Natural Earth reader only returns countries inside the extract's bounds. It logs instead of throwing because
   * Planetiler catches and logs every exception from {@code finish} without failing the run.
   */
  @Override
  public void finish(String sourceName, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (Shortbread.OSM_SOURCE.equals(sourceName) && !sourceRead) {
      LOGGER.error("The locale_names experiment is enabled but the '{}' source was not read, so no name_<lang> is " +
        "filled from name. Declare it before the OSM source, as ShortbreadMain does, or disable locale_names.", SOURCE);
    }
  }

  /** Whether any feature of the Natural Earth source reached this handler, indexed or not. */
  boolean sourceWasRead() {
    return sourceRead;
  }

  /** Indexes every polygon part of {@code geom}, prepared for repeated point-in-polygon tests. */
  private void put(Geometry geom, String language) {
    if (geom instanceof Polygon poly) {
      var entry = new PreparedCountry(PreparedGeometryFactory.prepare(poly), language);
      // STRtree inserts are not thread-safe and the source is read in parallel
      synchronized (this) {
        index.insert(poly.getEnvelopeInternal(), entry);
      }
    } else if (geom instanceof GeometryCollection geoms) {
      for (int i = 0; i < geoms.getNumGeometries(); i++) {
        put(geoms.getGeometryN(i), language);
      }
    }
  }

  private void build() {
    if (!built) {
      synchronized (this) {
        if (!built) {
          index.build();
          built = true;
        }
      }
    }
  }

  /**
   * Returns the default language of the country containing this feature's representative point, or {@code null} if the
   * feature is outside every indexed country (or its geometry is unusable).
   */
  public String languageAt(SourceFeature f) {
    try {
      // centroid() is cached on the feature and derived from its assembled polygon/line, so a feature that appears in
      // several layers computes it once; a geometry that cannot be assembled throws and is treated as "no country"
      Geometry point = f.centroid();
      if (point.isEmpty() || !(point instanceof Point p)) {
        return null;
      }
      build();
      for (Object item : index.query(p.getEnvelopeInternal())) {
        if (item instanceof PreparedCountry country && country.geometry().contains(p)) {
          return country.language();
        }
      }
      return null;
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
