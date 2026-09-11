package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.config.Arguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShortbreadOptionsTest {

  private static ShortbreadOptions options(Map<String, String> args) {
    return ShortbreadOptions.from(Arguments.of(args));
  }

  @Test
  void defaultsTo10WithEnglishAndGerman() {
    var options = options(Map.of());
    assertFalse(options.v11());
    assertEquals(List.of("en", "de"), options.languages());
  }

  @Test
  void acceptsBothVersions() {
    assertFalse(options(Map.of("shortbread_version", "1.0")).v11());
    assertTrue(options(Map.of("shortbread_version", "1.1")).v11());
  }

  @Test
  void unknownVersionThrows() {
    // 1.10 used to count as 1.1, because only the prefix was checked
    for (String version : List.of("1.2", "1.10", "2", "latest")) {
      assertThrows(IllegalArgumentException.class, () -> options(Map.of("shortbread_version", version)),
        () -> "shortbread_version=" + version);
    }
  }

  @Test
  void version10AcceptsOnlyEnglishAndGerman() {
    assertEquals(List.of("de", "en"), options(Map.of("name_languages", "de,en")).languages());
    for (String languages : List.of("en,de,fr", "fr", "en")) {
      assertThrows(IllegalArgumentException.class, () -> options(Map.of("name_languages", languages)),
        () -> "name_languages=" + languages);
    }
  }

  @Test
  void version11AcceptsAnyLanguages() {
    var options = options(Map.of("shortbread_version", "1.1", "name_languages", "en,fr,ko-Latn"));
    assertEquals(List.of("en", "fr", "ko-Latn"), options.languages());
  }
}
