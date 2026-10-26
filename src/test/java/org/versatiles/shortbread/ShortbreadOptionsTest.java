package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.config.Arguments;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Test
  void extendedListIsValidAndUnique() {
    var languages = ShortbreadOptions.EXTENDED_LANGUAGES;
    assertEquals(81, languages.size());
    assertEquals(languages.size(), Set.copyOf(languages).size(), "duplicates in the extended list");
    assertTrue(languages.containsAll(List.of("en", "de")), "the 1.0 languages belong to the extended list");
    // every entry passes the same validation as a code given on the command line
    assertEquals(languages, ShortbreadOptions.expandLanguages(languages));
  }

  @Test
  void extendedListIsDocumented() throws IOException {
    // the code block right after the README's "### Names" heading lists the languages; compare whole lines, since a
    // Windows checkout has CRLF line endings
    List<String> lines = Files.readString(Path.of("README.md")).lines().toList();
    int start = lines.indexOf("### Names");
    assertTrue(start >= 0, "README.md has no \"### Names\" section");
    int open = lines.subList(start, lines.size()).indexOf("```") + start;
    int close = lines.subList(open + 1, lines.size()).indexOf("```") + open + 1;
    List<String> documented = lines.subList(open + 1, close).stream()
      .flatMap(line -> Arrays.stream(line.trim().split("\\s+")))
      .toList();
    assertEquals(ShortbreadOptions.EXTENDED_LANGUAGES, documented);
  }

  @Test
  void version11ExpandsExtended() {
    var options = options(Map.of("shortbread_version", "1.1", "name_languages", "extended"));
    assertEquals(ShortbreadOptions.EXTENDED_LANGUAGES, options.languages());
    assertEquals(81, options.nameKeys().size());
    assertEquals(new ShortbreadOptions.NameKeys("zh-Hant", "name_zh-Hant", "name:zh-Hant"),
      options.nameKeys().get(ShortbreadOptions.EXTENDED_LANGUAGES.indexOf("zh-Hant")));
  }

  @Test
  void extendedCombinesWithOtherCodesWithoutRepeats() {
    // en keeps its first position, the list does not repeat it, and eo (already in the list) is not added twice
    var languages = options(Map.of("shortbread_version", "1.1", "name_languages", "en,Extended,eo,tok")).languages();
    assertEquals("en", languages.getFirst());
    assertEquals("tok", languages.getLast());
    assertEquals(ShortbreadOptions.EXTENDED_LANGUAGES.size() + 1, languages.size());
    assertEquals(languages.size(), Set.copyOf(languages).size());
  }

  @Test
  void invalidLanguageCodesThrow() {
    for (String code : List.of("EN", "e", "english", "etymology", "name:en", "ja_kana", "zh-", "en de")) {
      assertThrows(IllegalArgumentException.class,
        () -> options(Map.of("shortbread_version", "1.1", "name_languages", "en," + code)),
        () -> "name_languages=en," + code);
    }
  }

  @Test
  void version10RejectsExtended() {
    var e = assertThrows(IllegalArgumentException.class, () -> options(Map.of("name_languages", "extended")));
    assertTrue(e.getMessage().contains("'extended'"), () -> "message should name the input: " + e.getMessage());
  }
}
