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

The previous YAML schema is still runnable for comparison:

```bash
java -jar planetiler-dist/target/*-with-deps.jar custom \
  --schema=planetiler-custommap/src/main/resources/samples/shortbread.yml --area=monaco
```

## Structure

- `Shortbread` — the `ForwardingProfile` that registers all layer handlers and sets tileset metadata.
- `ShortbreadMain` — the runnable entry point wiring the sources and output.
- `layers/` — one handler per Tilemaker `process_*` function; each may emit to several output layers.
- `util/` — shared helpers ported from the reference: `Names` (the three-field name fallback), `ZOrder`, `Zooms`
  (size-based minimum zoom), `Poi` (POI whitelists), `Surface`, `Geo`, and `MergeLines`.

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
- Empty string values are omitted rather than written as the empty-string NULL sentinel that Tilemaker uses.

`boundary_labels` are derived from administrative boundary polygons (matching the current Planetiler YAML schema) instead
of Tilemaker's externally pre-built admin-points shapefile, so no extra data source is required.
