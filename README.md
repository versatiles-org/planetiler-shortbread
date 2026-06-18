# planetiler-shortbread

A native Java [Planetiler](https://github.com/onthegomap/planetiler) profile that generates vector tiles in the
[Shortbread v1.0](https://shortbread-tiles.org/schema/1.0/) schema.

This replaces the previous YAML/`custommap` implementation (`planetiler-custommap/.../shortbread.yml`). The declarative
YAML engine could not express several things the schema needs — size-based minimum zoom, sort keys / draw order,
relation membership for administrative boundaries, and per-zoom attribute tiers — so the schema is implemented here as a
hand-written profile instead.

## Running

From a packaged Planetiler distribution jar:

```bash
# small extract
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco

# whole planet
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=planet
```

Two input sources are used and downloaded automatically if missing:

- `osm` — an OpenStreetMap `.osm.pbf` extract (Geofabrik by default; override with `--osm_url` / `--osm_path`)
- `ocean` — the OSM water polygons shapefile
  ([water-polygons-split-3857](https://osmdata.openstreetmap.de/data/water-polygons.html)) used for the `ocean` layer

Output is written to `data/shortbread.mbtiles` by default (override with `--output`).

## Schema version (1.0 / 1.1)

The profile produces Shortbread **1.0** by default. The **1.1** draft is available via a flag or a dedicated task:

```bash
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco --shortbread_version=1.1
# or the shorthand task:
java -jar planetiler-dist/target/*-with-deps.jar shortbread-1.1 --area=monaco
```

Differences applied for 1.1:

- `water_lines` / `water_lines_labels`: `waterway=drain` moves to zoom 14.
- `pois`: adds `amenity=fuel` (`kind=fuel`) and `leisure=park` (`kind=park`).
- Names: instead of the fixed `name_en` / `name_de`, any IETF-coded `name_<code>` is emitted from `name:<code>` for the
  configured language list. Set it with `--name_languages=en,de,fr,...` (default `en,de`). This flag also works for 1.0.

(The `dog_park` / `playground` POI tagging "fix" in 1.1 — moving them from `amenity` to `leisure` — already matches this
implementation, which follows Tilemaker's `leisure` classification.)

The previous YAML schema is still runnable for comparison:

```bash
java -jar planetiler-dist/target/*-with-deps.jar custom \
  --schema=planetiler-custommap/src/main/resources/samples/shortbread.yml --area=monaco
```

## Experimental features (beyond the spec)

This profile can emit a few features that are **not part of the Shortbread schema**. They are *experiments* — useful for
richer maps, possibly changing or proposed upstream, and explicitly opt-in so the default output stays conformant.

They are **off by default** (a bare run produces strict-spec tiles). Enable them with `--shortbread_experiments`, a
comma-separated list of `all`, `none` (the default), or specific tokens:

```bash
# everything on
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco --shortbread_experiments=all

# just 3D buildings + localized names
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco \
  --shortbread_experiments=building_heights,building_parts,locale_names

# explicit strict spec (same as omitting the flag)
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco --shortbread_experiments=none
```

| Token              | Adds                                                                                                                                       | Notes                                                                                                                                                                                                                                                                                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `building_heights` | `height` and `min_height` on the `buildings` layer                                                                                         | For 3D extrusion. OpenMapTiles derivation: `height`/`building:height` → `building:levels` × 3.66 m → 5 m default; `min_height` only when > 0; implausible (≥ 3660 m) values dropped. ([shortbread-docs #77](https://github.com/shortbread-tiles/shortbread-docs/issues/77))                                                                              |
| `building_parts`   | OSM [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple3DBuildingsV1) `building:part` polygons + a `hide_3d` flag on outlines | Only parts that carry height info are emitted (a bare part adds 2D noise). The `outline`-role member of a `type=building` relation is tagged `hide_3d=true` so a renderer extrudes the parts, not the parent footprint. **Implies `building_heights`.**                                                                                                  |
| `locale_names`     | geofenced `name_<lang>` fallback                                                                                                           | A feature tagged only with `name`, inside a country whose default language is `<lang>`, also gets `name_<lang>` (e.g. `name_de` in Germany). Makes a "show only language X" style usable. **Adds a data source**: the Natural Earth `ne_10m_admin_0_countries` shapefile (a few MB, downloaded automatically when enabled). Respects `--name_languages`. |
| `island_labels`    | `place_labels` for islands mapped as **polygons**                                                                                          | The base profile only labels island *nodes*; this area-ranks polygon islands so larger ones appear earlier.                                                                                                                                                                                                                                              |
| `address_details`  | `addr:unit` and `addr:block` on the `addresses` layer                                                                                      | Beyond the spec's `housename`/`housenumber`.                                                                                                                                                                                                                                                                                                             |
| `bridge_names`     | `name` (and `name_<lang>`) on `man_made=bridge` polygons                                                                                   | The spec defines no name for bridges. ([shortbread-docs #141](https://github.com/shortbread-tiles/shortbread-docs/issues/141))                                                                                                                                                                                                                           |

All experiments are **additive**: they only add attributes/features to existing layers (the `buildings` layer still
carries the spec's `dummy=1`, geometry and zoom ranges are unchanged), so a strict-spec consumer can ignore the extras.
The registry of tokens lives in `Experiment.java`; new beyond-spec features (e.g. a future `mountain_peaks` layer)
register there and stay off by default.

## Structure

- `Shortbread` — the `ForwardingProfile` that registers all layer handlers and sets tileset metadata.
- `ShortbreadMain` — the runnable entry point wiring the sources and output.
- `layers/` — one handler per Tilemaker `process_*` function; each may emit to several output layers.
- `Experiment` — the registry of beyond-spec [experimental features](#experimental-features-beyond-the-spec) and the
  `--shortbread_experiments` parser.
- `util/` — shared helpers ported from the reference: `Names` (name attributes + the optional geofenced fallback),
  `CountryLanguages` (the country→language index backing `locale_names`), `ZOrder`, `Zooms` (size-based minimum zoom),
  `Poi` (POI whitelists), `Surface`, `Geo`, `MergeLines`, and `MergePolygons`.

## Reference and deviations

The behavioural reference is the Geofabrik
[Tilemaker implementation](https://github.com/shortbread-tiles/shortbread-tilemaker) (`process.lua` / `config.json`).
The goal is to reproduce the Shortbread v1.0 output while fixing a handful of clear bugs in the reference. Every such
deviation is marked with a `// DEVIATION:` comment in the code; the notable ones are:

- `surface`: `paved` surfaces map to `paved` (the reference's `paved` branch wrongly returned `unpaved`).
- `pois`: the `man_made` attribute comes from `man_made` (not `historic`), `office` is validated against the office
  whitelist (not the highway one), and `man_made` participates in the "is this a POI" decision.
- `sites`: matches the correct `leisure=sports_centre` spelling.
- `street_labels`: `tunnel` is computed from the tags (the reference always emitted `false`).
- `public_transport`: uses the intended per-kind minimum zoom (the reference computed it, then hard-coded zoom 11).
- `name` / `name_en` / `name_de` are taken from their own tags with no fallback (matching the schema's test spec and
  the previous YAML schema), rather than Tilemaker's fallback chaining. (The opt-in `locale_names`
  [experiment](#experimental-features-beyond-the-spec) adds a *geofenced* fallback instead of Tilemaker's global one.)
- `surface` is canonicalized to `paved` / `unpaved`; `way_area` is a full-precision number.
- Empty string values are omitted rather than written as the empty-string NULL sentinel that Tilemaker uses.

## Tests

`ShortbreadSpecTest` runs the example-based specification in
`src/test/resources/shortbread.spec.yml` (ported from the YAML schema's test spec) against the Java profile via
`BaseSchemaValidator`. Each example lists an input feature and the vector-tile features it should produce. Where the
Java output intentionally differs from the original YAML expectation, the spec entry is annotated with a
`# DEVIATION` comment. `ShortbreadProfileTest` adds focused per-layer unit tests and `ShortbreadIntegrationTest` runs
the whole pipeline over the bundled Monaco extract.

`boundary_labels` are derived from administrative boundary polygons (matching the current Planetiler YAML schema) instead
of Tilemaker's externally pre-built admin-points shapefile, so no extra data source is required.
