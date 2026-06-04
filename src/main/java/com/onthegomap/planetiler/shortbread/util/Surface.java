package com.onthegomap.planetiler.shortbread.util;

import java.util.Set;

/**
 * Canonicalizes the OSM {@code surface} tag to the Shortbread values {@code paved} / {@code unpaved}, mirroring the
 * {@code process_streets} surface handling in the Tilemaker reference.
 * <p>
 * DEVIATION (bug fix): in {@code process.lua} the "paved" branch erroneously also assigns {@code "unpaved"}; here paved
 * surfaces correctly map to {@code "paved"}.
 */
public final class Surface {

  private Surface() {}

  private static final Set<String> UNPAVED = Set.of(
    "unpaved", "compacted", "dirt", "earth", "fine_gravel", "grass", "grass_paver", "gravel", "ground", "mud",
    "pebblestone", "salt", "woodchips", "clay");

  private static final Set<String> PAVED = Set.of(
    "paved", "asphalt", "cobblestone", "cobblestone:flattened", "sett", "concrete", "concrete:lanes",
    "concrete:plates", "paving_stones");

  /** Returns {@code "paved"}, {@code "unpaved"}, or {@code null} if the surface is empty/unrecognized. */
  public static String canonicalize(String surface) {
    if (surface == null || surface.isEmpty()) {
      return null;
    }
    if (UNPAVED.contains(surface)) {
      return "unpaved";
    }
    if (PAVED.contains(surface)) {
      return "paved";
    }
    return null;
  }
}
