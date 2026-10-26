package org.versatiles.shortbread.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class CountryLanguagesTest {

  // a generous lon/lat box around Germany
  private static final Geometry GERMANY_BOX = TestUtils.newPolygon(6, 47, 15, 47, 15, 55, 6, 55, 6, 47);

  private static SourceFeature country(String isoField, String iso, Geometry polygon) {
    return SimpleFeature.create(polygon, Map.of(isoField, iso), CountryLanguages.SOURCE, null, 1);
  }

  private static SourceFeature point(double lon, double lat) {
    return SimpleFeature.create(TestUtils.newPoint(lon, lat), Map.of(), "osm", null, 2);
  }

  @Test
  void mapsCountryToLanguageAndQueriesPoints() {
    var countries = new CountryLanguages(List.of("en", "de"));
    countries.processFeature(country("ISO_A2", "DE", GERMANY_BOX), null);

    assertEquals("de", countries.languageAt(point(10, 51)), "point inside Germany");
    assertNull(countries.languageAt(point(2, 48)), "point outside any indexed country");
  }

  @Test
  void ignoresLanguagesNotRequested() {
    // de is not in the requested set, so Germany is never indexed
    var countries = new CountryLanguages(List.of("en"));
    countries.processFeature(country("ISO_A2", "DE", GERMANY_BOX), null);

    assertNull(countries.languageAt(point(10, 51)));
  }

  @Test
  void ignoresUnknownOrMissingIso() {
    var countries = new CountryLanguages(List.of("en", "de"));
    countries.processFeature(country("ISO_A2", "-99", GERMANY_BOX), null); // NE "no data" sentinel
    countries.processFeature(country("ISO_A2", "CH", GERMANY_BOX), null); // multilingual, intentionally unmapped

    assertNull(countries.languageAt(point(10, 51)));
  }

  @Test
  void notesWhenTheCountrySourceWasNeverRead() {
    // locale_names is on (this instance only exists then) but the Natural Earth source was not wired, so every
    // geofenced name would be silently missing; finish only logs, because Planetiler swallows exceptions from it
    var countries = new CountryLanguages(List.of("de"));
    assertFalse(countries.sourceWasRead());
    assertDoesNotThrow(() -> countries.finish("osm", null, f -> {
    }));
  }

  @Test
  void countsTheSourceAsReadWithoutAnIndexedCountry() {
    // an extract can lie entirely in unmapped countries (Israel) or in countries whose language this run does not emit
    // (Estonia without et): the source was read, nothing is wrong
    var countries = new CountryLanguages(List.of("de"));
    countries.processFeature(country("ISO_A2", "IL", GERMANY_BOX), null);
    countries.processFeature(country("ISO_A2", "EE", GERMANY_BOX), null);

    assertTrue(countries.sourceWasRead());
    assertNull(countries.languageAt(point(10, 51)));
  }

  @Test
  void countsTheSourceAsReadWhenCountriesWereIndexed() {
    var countries = new CountryLanguages(List.of("de"));
    countries.processFeature(country("ISO_A2", "DE", GERMANY_BOX), null);

    assertTrue(countries.sourceWasRead());
  }

  @Test
  void readsLowerCaseAndEhIsoFields() {
    // shapefile DBF fields are upper-case, but lower-case and *_EH variants are accepted as fallbacks
    var countries = new CountryLanguages(List.of("de"));
    countries.processFeature(country("iso_a2_eh", "de", GERMANY_BOX), null);

    assertEquals("de", countries.languageAt(point(10, 51)));
  }
}
