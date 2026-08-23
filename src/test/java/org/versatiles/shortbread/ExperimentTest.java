package org.versatiles.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void buildingPartsImpliesBuildingHeights() {
    var set = Experiment.parse(List.of("building_parts"));
    assertTrue(set.contains(Experiment.BUILDING_PARTS));
    assertTrue(set.contains(Experiment.BUILDING_HEIGHTS));
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
}
