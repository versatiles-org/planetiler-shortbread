package com.onthegomap.planetiler.shortbread.util;

import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Parse;
import java.util.Set;

/**
 * Ports the boolean tag helpers and {@code setZOrder} draw-order computation from the Tilemaker reference
 * ({@code process.lua}). The computed z-order is used as a Planetiler sort key (higher value = drawn on top / kept when
 * features are dropped).
 */
public final class ZOrder {

  private ZOrder() {}

  private static final int Z_STEP = 14;

  private static final Set<String> TUNNEL_VALUES = Set.of("yes", "culvert", "building_passage");
  private static final Set<String> BRIDGE_VALUES =
    Set.of("yes", "viaduct", "boardwalk", "cantilever", "covered", "low_water_crossing", "movable", "trestle");

  /** {@code tunnel=yes|culvert|building_passage} or {@code covered=yes}. */
  public static boolean isTunnel(SourceFeature f) {
    return TUNNEL_VALUES.contains(str(f, "tunnel")) || "yes".equals(str(f, "covered"));
  }

  public static boolean isBridge(SourceFeature f) {
    return BRIDGE_VALUES.contains(str(f, "bridge"));
  }

  public static boolean isOneway(SourceFeature f) {
    String oneway = str(f, "oneway");
    return "yes".equals(oneway) || "1".equals(oneway) || "true".equals(oneway) || "-1".equals(oneway);
  }

  public static boolean isReverseOneway(SourceFeature f) {
    return "-1".equals(str(f, "oneway"));
  }

  /** Numeric {@code layer} tag clamped to [-7, 7], defaulting to 0. */
  public static int layer(SourceFeature f) {
    Integer layer = Parse.parseIntOrNull(f.getTag("layer"));
    if (layer == null) {
      return 0;
    }
    return Math.max(-7, Math.min(7, layer));
  }

  /**
   * Computes the integer draw order, mirroring {@code setZOrder(is_rail, ignore_bridge)} in {@code process.lua}: a
   * bridge raises it, a tunnel lowers it, the {@code layer} tag scales it, and a per-class rank is added on top.
   */
  public static int zOrder(SourceFeature f, boolean isRail, boolean ignoreBridge) {
    int z = 0;
    if (!ignoreBridge && isBridge(f)) {
      z += Z_STEP;
    } else if (isTunnel(f)) {
      z -= Z_STEP;
    }
    z += layer(f) * Z_STEP;
    z += classRank(str(f, "highway"), str(f, "railway"), isRail, f);
    return z;
  }

  private static int classRank(String highway, String railway, boolean isRail, SourceFeature f) {
    if (isRail && "rail".equals(railway) && !f.hasTag("service")) {
      return 13;
    } else if (isRail && "rail".equals(railway)) {
      return 12;
    } else if (isRail && (railway.equals("subway") || railway.equals("light_rail") || railway.equals("tram") ||
      railway.equals("funicular") || railway.equals("monorail"))) {
      return 11;
    }
    return switch (highway) {
      case "motorway" -> 10;
      case "trunk" -> 9;
      case "primary" -> 8;
      case "secondary" -> 7;
      case "tertiary" -> 6;
      case "unclassified", "residential", "road", "motorway_link", "trunk_link", "primary_link", "secondary_link",
        "tertiary_link", "busway", "bus_guideway" -> 5;
      case "living_street", "pedestrian" -> 4;
      case "service" -> 3;
      case "footway", "bridleway", "cycleway", "path", "track" -> 2;
      case "steps", "platform" -> 1;
      default -> 0;
    };
  }

  private static String str(SourceFeature f, String key) {
    return f.getString(key, "");
  }
}
