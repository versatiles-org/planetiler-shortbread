package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExperimentTest {

  @Test
  void noneAndEmptyYieldNoExperiments() {
    assertTrue(Experiment.parse(List.of()).isEmpty());
    assertTrue(Experiment.parse(List.of("none")).isEmpty());
    assertTrue(Experiment.parse(null).isEmpty());
  }

  @Test
  void allEnablesEverything() {
    assertEquals(EnumSet.allOf(Experiment.class), Experiment.parse(List.of("all")));
  }

  @Test
  void oldBuildingTokensEnable3dBuildings() {
    assertEquals(Set.of(Experiment.BUILDINGS_3D), Experiment.parse(List.of("building_heights")));
    assertEquals(Set.of(Experiment.BUILDINGS_3D), Experiment.parse(List.of("Building_Parts")));
    assertEquals(Set.of(Experiment.BUILDINGS_3D), Experiment.parse(List.of("3d_buildings", "building_parts")));
  }

  @Test
  void helpListsOnlyCurrentTokens() {
    assertTrue(Experiment.help().contains("3d_buildings"));
    assertFalse(Experiment.help().contains("building_heights"));
    assertFalse(Experiment.help().contains("building_parts"));
  }

  @Test
  void parsesSubsetCaseInsensitively() {
    assertEquals(Set.of(Experiment.LOCALE_NAMES, Experiment.BRIDGE_NAMES),
      Experiment.parse(List.of("Locale_Names", " bridge_names ")));
  }

  @Test
  void unknownTokenThrows() {
    assertThrows(IllegalArgumentException.class, () -> Experiment.parse(List.of("teleporter")));
  }

  @Test
  void everyTokenIsDocumented() throws IOException {
    // compare whole lines: a Windows checkout has CRLF line endings
    List<String> lines = Files.readString(Path.of("docs", "extensions.md")).lines().toList();
    for (Experiment experiment : Experiment.values()) {
      String heading = "## `" + experiment.token() + "`";
      assertTrue(lines.contains(heading), () -> "docs/extensions.md has no section \"" + heading + "\"");
    }
  }
}
