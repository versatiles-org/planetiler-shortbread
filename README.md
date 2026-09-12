# planetiler-shortbread

A native Java [Planetiler](https://github.com/onthegomap/planetiler) profile that generates vector tiles in the
[Shortbread](https://shortbread-tiles.org/) schema, versions [1.0](https://shortbread-tiles.org/schema/1.0/) and
[1.1](https://shortbread-tiles.org/schema/1.1/).

This replaces the previous YAML/`custommap` implementation (`planetiler-custommap/.../shortbread.yml`). The declarative
YAML engine could not express several things the schema needs — size-based minimum zoom, sort keys / draw order,
relation membership for administrative boundaries, and per-zoom attribute tiers — so the schema is implemented here as a
hand-written profile instead.

## Building and running

This repo builds standalone against a released `planetiler-core` from Maven Central:

```bash
./mvnw clean package
```

That produces an executable `target/*-with-deps.jar`:

```bash
# small extract
java -jar target/*-with-deps.jar --area=monaco

# whole planet
java -jar target/*-with-deps.jar --area=planet
```

These input sources are used and downloaded automatically if missing:

- `osm` — an OpenStreetMap `.osm.pbf` extract (Geofabrik by default)
- `ocean` — the OSM water polygons shapefile
  ([water-polygons-split-3857](https://osmdata.openstreetmap.de/data/water-polygons.html)) used for the `ocean` layer
- `ne_10m_admin_0_countries` — the [Natural Earth](https://www.naturalearthdata.com/) country polygons, a few MB, only
  when the `locale_names` [experiment](#experimental-features-beyond-the-spec) is enabled

Output is written to `data/shortbread.mbtiles` by default.

## Command-line reference

|                               Argument                               |                                 Default                                 |                                                                                                              Description                                                                                                              |
|----------------------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--area`                                                             | `monaco`                                                                | Geofabrik extract to download as OSM input; `planet` downloads the latest planet file (`aws:latest`)                                                                                                                                  |
| `--output`                                                           | `data/shortbread.mbtiles`                                               | output archive                                                                                                                                                                                                                        |
| `--shortbread_version`                                               | `1.0`                                                                   | schema version, `1.0` or `1.1`; any other value fails at startup                                                                                                                                                                      |
| `--name_languages`                                                   | `en,de`                                                                 | IETF language codes emitted as `name_<code>`; with 1.0 only `en,de` is accepted                                                                                                                                                       |
| `--shortbread_experiments`                                           | `none`                                                                  | `all`, `none`, or a comma-separated list of [experiment tokens](docs/extensions.md), case-insensitive; an unknown token fails at startup, the deprecated `building_heights` and `building_parts` enable `3d_buildings` with a warning |
| `--osm_path` / `--osm_url`                                           | `data/sources/<area>.osm.pbf` / `geofabrik:<area>`                      | OSM input file and its download URL                                                                                                                                                                                                   |
| `--ocean_path` / `--ocean_url`                                       | `data/sources/water-polygons-split-3857.zip` / osmdata.openstreetmap.de | ocean shapefile and its download URL                                                                                                                                                                                                  |
| `--ne_10m_admin_0_countries_path` / `--ne_10m_admin_0_countries_url` | `data/sources/ne_10m_admin_0_countries.zip` / Natural Earth             | country polygons for `locale_names`                                                                                                                                                                                                   |

All other [Planetiler arguments](https://github.com/onthegomap/planetiler) work as usual, for example `--download`,
`--force`, `--tmpdir` or `--output_layerstats`.

Inside a Planetiler checkout (see [below](#use-inside-planetiler)) the profile is reachable as the tasks `shortbread` /
`generate-shortbread` (1.0 unless `--shortbread_version` says otherwise) and `shortbread-1.1` /
`generate-shortbread-1.1`.

## Schema version (1.0 / 1.1)

The profile produces Shortbread **1.0** by default. Shortbread **1.1** (released 2026-08-17) is produced with a flag or
the dedicated task; VersaTiles builds use 1.1:

```bash
java -jar target/*-with-deps.jar --area=monaco --shortbread_version=1.1
```

The archive metadata's `version` is `1.0` or `1.1` accordingly. Differences applied for 1.1:

- `streets`: instead of the raw `bicycle` / `horse` tag values from z14, the access attributes `motorcar`, `bicycle`,
  `foot` and `horse`, normalized to `yes` / `limited` / `no`, from z13, on highways only. Each takes the first
  recognized value along a chain of tags: `motorcar` ← `motorcar`, `motor_vehicle`, `vehicle`, `access`; `bicycle` ←
  `bicycle`, `vehicle`, `access`; `foot` ← `foot`, `access`; `horse` ← `horse`, `access`. `yes`, `designated` and
  `permissive` become `yes`; `customers`, `destination`, `agricultural`, `forestry`, `delivery`, `discouraged` and
  `permit` become `limited`; `dismount`, `military`, `private` and `no` become `no`; other values are skipped.
- `water_lines` / `water_lines_labels`: `waterway=drain` is added, at zoom 14 (1.0 defines no `drain` kind).
- `pois`: adds `amenity=fuel` (`kind=fuel`) and `leisure=park` (`kind=park`).
- `pois`: `dog_park` and `playground` move from `amenity` to `leisure`, so they are read from — and emitted under —
  whichever key the selected version uses. A feature that qualifies for `pois` is not also emitted in `addresses`, so
  the address layer follows the same version.
- `boundaries`: a disputed boundary relation marks its lines `disputed` when its `admin_level` is unset or `2` / `4`;
  1.0 also accepts `3`.
- Names: instead of the fixed `name_en` / `name_de`, any IETF-coded `name_<code>` is emitted from `name:<code>` for the
  configured language list. Set it with `--name_languages=en,de,fr,...` (default `en,de`). 1.0 defines only `name_en`
  and `name_de`, so with 1.0 the flag accepts only `en,de`.

The previous YAML schema is still runnable for comparison, from a Planetiler distribution jar:

```bash
java -jar planetiler-dist/target/*-with-deps.jar custom \
  --schema=planetiler-custommap/src/main/resources/samples/shortbread.yml --area=monaco
```

## Experimental features (beyond the spec)

This profile can emit a few features that are **not part of the Shortbread schema**. They are *experiments* — useful for
richer maps, possibly changing or proposed upstream, and explicitly opt-in.

They are **off by default**. Enable them with `--shortbread_experiments`, a comma-separated list of `all`, `none` (the
default), or specific tokens:

```bash
# everything on
java -jar target/*-with-deps.jar --area=monaco --shortbread_experiments=all

# just 3D buildings + localized names
java -jar target/*-with-deps.jar --area=monaco \
  --shortbread_experiments=3d_buildings,locale_names

# explicit default (same as omitting the flag)
java -jar target/*-with-deps.jar --area=monaco --shortbread_experiments=none
```

|       Token        |            Layers            |                                             Adds                                              |
|--------------------|------------------------------|-----------------------------------------------------------------------------------------------|
| `3d_buildings`     | `buildings`                  | `height`, `min_height`, `hide_3d`; `building:part` polygons marked `part=true`                |
| `locale_names`     | all name layers              | `name_<lang>` from `name` inside countries whose language is `<lang>`                         |
| `island_labels`    | `place_labels`               | label points for islands mapped as polygons                                                   |
| `address_details`  | `addresses`                  | `unit`, `block`                                                                               |
| `bridge_names`     | `bridges`                    | `name`, `name_<code>`                                                                         |
| `early_attributes` | `streets`                    | `link` and `service` from each feature's minimum zoom; with 1.0, `bicycle` / `horse` from z13 |
| `mountain_peaks`   | `mountain_peaks` (new layer) | peaks and volcanoes from z10, saddles at z14, with `kind`, names and `ele`                    |

The full contract of each experiment — attributes, when they are present, how they are derived, style snippets,
caveats, how to detect it in a tileset, and its upstream status — is in [docs/extensions.md](docs/extensions.md).

Most experiments only add attributes that a strict-spec consumer can ignore. Three change what such a consumer sees:
`3d_buildings` adds `building:part` polygons to `buildings`, `island_labels` adds `place_labels` points from z8, and
`locale_names` fills the spec's `name_<lang>` attributes from `name`. `early_attributes` makes spec attributes available
at lower zooms than the spec defines.

The registry of tokens lives in `Experiment.java`. A new token must also get a section in `docs/extensions.md`; a test
checks that.

## Use inside Planetiler

This repo doubles as a Planetiler submodule, the same way
[planetiler-openmaptiles](https://github.com/openmaptiles/planetiler-openmaptiles) does. When it is checked out inside a
Planetiler working copy, `submodule.pom.xml` is used instead of `pom.xml` so the module builds against the sibling
`planetiler-core` rather than a released one, and the profile is reachable from the distribution jar:

```bash
java -jar planetiler-dist/target/*-with-deps.jar shortbread --area=monaco
```

## Structure

- `Shortbread` — the `ForwardingProfile` that registers all layer handlers and sets tileset metadata.
- `ShortbreadMain` — the runnable entry point wiring the sources and output.
- `ShortbreadOptions` — the schema version, name languages and enabled experiments, validated at startup.
- `layers/` — one handler per group of related output layers.
- `Experiment` — the registry of beyond-spec [experimental features](#experimental-features-beyond-the-spec) and the
  `--shortbread_experiments` parser.
- `util/` — shared helpers: `Names` (name attributes + the optional geofenced fallback), `CountryLanguages` (the
  country→language index backing `locale_names`), `Access` (the 1.1 access attributes), `ZOrder`, `Zooms` (size-based
  minimum zoom), `Poi` (POI whitelists), `Geo`, `MergeLines`, and `MergePolygons`.
- `docs/extensions.md` — the contract of the experiments.

## Notable output details

These apply to every build, with or without experiments.

### Deviations from the spec

- **Density limits.** Three layers cap the number of points per grid cell to keep dense tiles small, so they drop spec
  features where the data is densest:
  - `addresses`: at most 8 per 8 px cell at z14. Addresses have no ranking, so which ones survive a full cell is not
    defined. Measured on Noord-Holland: the worst central Amsterdam z14 tile went from 32,502 to 8,021 housenumbers and
    from 452 KB to 322 KB compressed.
  - `place_labels`: at most 2 per 64 px cell up to z12, most populous first.
  - `water_polygons_labels`: at most 1 per 64 px cell up to z11, largest first.
- **Sub-pixel geometry.** `land` polygons of the same `kind` are merged per tile and merged pieces smaller than 1 px²
  dropped; `water_polygons` enter the map at the zoom where they cover a square tile pixel, and any that would only
  reach that size beyond z14 appear at z14; `ocean` polygons are merged per tile and slivers that
  collapse to lines at low zoom are dropped. None of these size limits apply at the maximum zoom, which is the base for
  overzooming: there a merged polygon only has to clear Planetiler's own `min_feature_size_at_max_zoom` (1/16 px, so
  1/256 px² of area), and only geometry that collapses to a line or a zero-area ring is dropped.

### Interpretation choices

- `boundaries` lines come only from the member ways of `type=boundary` relations, as OpenStreetMap Carto draws them; a
  way tagged `boundary=administrative` without a relation is not a boundary line. `admin_level` is read as a plain
  integer, so a value like `2;4` matches neither 2 nor 4.
- `boundary_labels` are derived from administrative boundary polygons rather than a pre-built admin-points shapefile, so
  no extra data source is required. They are sorted by `way_area`, largest first.
- `way_area` is a full-precision number in Web-Mercator units: m² on `water_polygons` and `water_polygons_labels`,
  hectares on `boundary_labels`.
- `surface` is the raw value of the OSM tag, as the schema defines it — not collapsed to `paved`/`unpaved`.
- `tunnel` follows the schema exactly: `tunnel=yes`, `tunnel=building_passage` or `covered=yes`. A `tunnel=culvert`
  waterway is **not** a tunnel, which differs from OpenStreetMap Carto — culverts make up most waterway tunnels there.
- A closed waterway is a line unless it is tagged as an area (`area=yes`, or a multipolygon/boundary relation): a
  ring-shaped ditch or moat appears in `water_lines`, and `water_polygons` takes a `waterway=canal` ring only when it is
  tagged as an area, as Carto's water-areas query does.
- `water_polygons` and their labels share one minimum zoom, the zoom at which the polygon first covers a square tile
  pixel, so a lake is never labelled before any water is drawn.
- `name` and the `name_<code>` attributes each come from their own tag, with no fallback: a feature tagged only with
  `name` gets no translated fields. (The opt-in `locale_names`
  [experiment](#experimental-features-beyond-the-spec) adds a *geofenced* fallback.)
- Name values are emitted as tagged. A multi-value tag such as `name=Mole Lake;Dewe’igan-madwewe-agaaming-zaaga’igan`
  stays one string; a style that wants a single name can split it at `;`.
- Attributes whose value equals the schema's default are omitted: empty values rather than empty strings, and the
  boolean attributes that default to `false` — `tunnel` and `bridge` on `water_lines`, `water_lines_labels` and
  `street_polygons`, `link`, `rail`, `tunnel`, `bridge`, `oneway` and `oneway_reverse` on `streets`, and `atm` and
  `recycling:*` on `pois` — are only written when `true`. Attributes without a default in the schema, such as
  `maritime` and `disputed` on `boundaries`, are always written.
- In `streets`, `street_labels`, `water_lines`, `water_lines_labels`, `dam_lines` and `boundaries`, connected lines with
  identical attributes are merged per tile. Below the maximum zoom, pieces shorter than half a pixel are dropped and the
  geometry is simplified with a 0.1 px tolerance; at the maximum zoom, which is the base for overzooming, every piece is
  kept and the merged line is only simplified at the tile's own resolution (Planetiler's
  `simplify_tolerance_at_max_zoom`, 1/16 px — one unit of the 4096-unit tile grid).
- Merging groups features by their output attributes alone. Two lines that differ only in a tag the schema does not
  emit — the OSM `layer`, say — therefore become one feature at one position in the draw order, and a merged line can
  run opposite to one of the ways it came from.

## Tests

- `ShortbreadSpecTest` runs the example-based specification in `src/test/resources/shortbread.spec.yml` against the
  profile via `BaseSchemaValidator`. Each example lists an input feature and the vector-tile features it should produce.
- `ShortbreadProfileTest` adds focused per-layer unit tests, mostly with all experiments enabled.
- `ShortbreadV11Test` covers the 1.0/1.1 differences.
- `ShortbreadOptionsTest` covers the validation of the version and language options.
- `ExperimentTest` covers the experiment tokens and checks that each one is documented in `docs/extensions.md`.
- `ShortbreadIntegrationTest` runs the whole pipeline over the bundled Monaco extract.
- `util/CountryLanguagesTest`, `util/MergePolygonsTest` and `util/ShortbreadUtilTest` cover the helpers.

Tile sizes are guarded separately by the `Tile size budget` workflow (`.github/workflows/tile-size.yml`). Weekly and on
demand, it generates Estonia and Noord-Holland without and with all experiments, and fails when a compressed tile is
larger than 500 KiB, naming the tile and its largest layer. The check itself is `.github/scripts/check_tile_size.py`,
which also runs locally on any `--output_layerstats` file.

