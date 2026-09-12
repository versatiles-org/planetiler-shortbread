# Shortbread extensions

The profile can emit a few features that are **not part of the Shortbread schema**. They are called *experiments*:
they make richer maps possible, but they may change, be renamed or be removed without a schema version bump, and some
are proposed upstream. This page is the contract for each of them. For building and running the profile, see the
[README](../README.md).

## Enabling experiments

Experiments are chosen when the tiles are generated, with `--shortbread_experiments`: `all`, `none` (the default), or a
comma-separated list of tokens, case-insensitive. An unknown token fails at startup.

```bash
java -jar target/*-with-deps.jar --area=monaco --shortbread_experiments=3d_buildings,locale_names
```

The published VersaTiles tiles are generated with all of them: the `versatiles-planetiler` image defaults to
`EXPERIMENTS=all` and `LANGUAGES=en,fr,es,de,ar,el,it,nl,pl,pt,uk`.

|                  Token                  |            Layers             |                                      Adds                                      |              A strict-spec consumer…               |
|-----------------------------------------|-------------------------------|--------------------------------------------------------------------------------|----------------------------------------------------|
| [`3d_buildings`](#3d_buildings)         | `buildings`                   | `height`, `min_height`, `hide_3d`; `building:part` polygons marked `part=true` | draws the part polygons unless it filters `part`   |
| [`locale_names`](#locale_names)         | all layers with names         | `name_<lang>` filled from `name` inside countries whose language is `<lang>`   | sees spec attributes filled from a different tag   |
| [`island_labels`](#island_labels)       | `place_labels`                | label points for islands mapped as polygons                                    | sees additional points, some before z10            |
| [`address_details`](#address_details)   | `addresses`                   | `unit`, `block`                                                                | can ignore them                                    |
| [`bridge_names`](#bridge_names)         | `bridges`                     | `name`, `name_<code>`                                                          | can ignore them                                    |
| [`early_attributes`](#early_attributes) | `streets`                     | `link` from z5 and `service` from z10; 1.0 access from z13                     | sees spec attributes at lower zooms than specified |
| [`mountain_peaks`](#mountain_peaks)     | `mountain_peaks`, a new layer | peaks, volcanoes and saddles with `kind`, names and `ele`                      | can ignore the layer                               |

## Detecting experiments

The archive metadata does not list the enabled experiments. The `vector_layers` in the metadata reveal some of them,
because they add fields or layers:

|                    In `vector_layers`                    |    Experiment     |
|----------------------------------------------------------|-------------------|
| `height`, `min_height`, `hide_3d`, `part` on `buildings` | `3d_buildings`    |
| `unit`, `block` on `addresses`                           | `address_details` |
| `name` on `bridges`                                      | `bridge_names`    |
| a `mountain_peaks` layer                                 | `mountain_peaks`  |

`locale_names`, `island_labels` and `early_attributes` add no fields and cannot be detected from the metadata. The
Shortbread validator in versatiles-rs reports extension attributes as `unknown_attribute` warnings.

## `3d_buildings`

Heights and [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple3DBuildingsV1) parts for extruded buildings.
Replaces the tokens `building_heights` and `building_parts`, which are still accepted with a deprecation warning and
enable `3d_buildings`.

|  Attribute   |        Type        |                        Present                        |
|--------------|--------------------|-------------------------------------------------------|
| `height`     | integer, meters    | when the feature has a height or levels tag           |
| `min_height` | integer, meters    | when it is greater than 0 and below `height`          |
| `hide_3d`    | boolean, only true | on the `outline` member of a `type=building` relation |
| `part`       | boolean, only true | on `building:part` polygons                           |

- **`height`** comes from `height` or `building:height`, parsed as a length (units such as `ft` are converted), else
  from `building:levels` or `levels` × 3.66 m, rounded up. There is **no default**: a building without such a tag has
  no `height`, so styles need their own fallback.
- **`min_height`** comes from `min_height` or `building:min_height`, else from `building:min_level` or `min_level` ×
  3.66 m, rounded down. It is left out when there is no `height` or when it is not below `height`, so an extrusion is
  never inverted.
- If either value is 3660 m or more, both are left out; such values are almost always tagging errors.
- **Parts:** a feature tagged `building:part` (not `no` or `none`) and not tagged `building` is emitted into
  `buildings` with `part=true`, but only if it has a height or levels tag. A part with only a minimum height or level is
  not emitted.
- **`hide_3d`** marks the footprint of a building whose parts are mapped, so it is not extruded under the parts. Known
  gap, as in OpenMapTiles: parts that only overlap a footprint without a `type=building` relation cannot be detected
  cheaply, so such footprints get no `hide_3d`.
- The spec's `dummy=1` stays on every feature. Like all buildings, parts are only in z14 tiles.

A 2D style draws footprints only:

```json
{
  "type": "fill",
  "source-layer": "buildings",
  "filter": ["!", ["has", "part"]]
}
```

A 3D style extrudes everything except the hidden footprints:

```json
{
  "type": "fill-extrusion",
  "source-layer": "buildings",
  "filter": ["!=", ["get", "hide_3d"], true],
  "paint": {
    "fill-extrusion-height": ["coalesce", ["get", "height"], 5],
    "fill-extrusion-base": ["coalesce", ["get", "min_height"], 0]
  }
}
```

**Cost**, measured on a Berlin extract (2024-11-15): the archive grows from 34,540 KB to 35,228 KB (+2%), the `buildings`
layer by 6% uncompressed, and the largest z14 tile from 191 KB to 232 KB compressed.

**Upstream:** [shortbread-docs #155](https://github.com/shortbread-tiles/shortbread-docs/issues/155) proposes optional
`height` and `min_height` (the maintainer questions making them optional);
[shortbread-docs #77](https://github.com/shortbread-tiles/shortbread-docs/issues/77) discusses `dummy=1`.

## `locale_names`

Makes a "show only language X" label mode possible. Without it, `name_<lang>` comes only from `name:<lang>`, so a town
tagged only with `name` has no `name_de`, even in Germany.

With `locale_names`, a feature that has `name` but no `name:<lang>` also gets `name_<lang>` set to the value of `name`
when all of these hold:

- `<lang>` is in `--name_languages`,
- the centroid of the feature lies in a country polygon of the Natural Earth `ne_10m_admin_0_countries` dataset,
- that country's language is `<lang>` in this table (monolingual countries only; countries with several official
  languages, such as CH, BE, LU and CA, are left out):

| Language |                                                                                                                Countries                                                                                                                 |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `de`     | DE, AT, LI                                                                                                                                                                                                                               |
| `en`     | GB, IE, US, AU, NZ                                                                                                                                                                                                                       |
| `es`     | ES, AR, MX, CO, CL, PE, VE, EC, BO, PY, UY, CR, CU, DO, GT, HN, NI, PA, SV                                                                                                                                                               |
| `fr`     | FR, MC                                                                                                                                                                                                                                   |
| `it`     | IT, SM                                                                                                                                                                                                                                   |
| `pt`     | PT, BR                                                                                                                                                                                                                                   |
| `zh`     | CN, TW                                                                                                                                                                                                                                   |
| others   | NL `nl`, DK `da`, SE `sv`, NO `no`, IS `is`, FI `fi`, PL `pl`, CZ `cs`, SK `sk`, HU `hu`, RO `ro`, BG `bg`, GR `el`, HR `hr`, SI `sl`, RS `sr`, EE `et`, LV `lv`, LT `lt`, RU `ru`, UA `uk`, TR `tr`, JP `ja`, KR `ko`, TH `th`, VN `vi` |

It applies to every layer with names: `boundary_labels`, `place_labels`, `water_polygons_labels`,
`water_lines_labels`, `street_labels`, `street_labels_points`, `streets_polygons_labels`, `pois`, `ferries`,
`public_transport`, and `bridges` or `mountain_peaks` when `bridge_names` or `mountain_peaks` is enabled too.

|    Label mode     |                     Expression                     |
|-------------------|----------------------------------------------------|
| local name        | `["get", "name"]`                                  |
| prefer language X | `["coalesce", ["get", "name_X"], ["get", "name"]]` |
| only language X   | `["get", "name_X"]`                                |

The last mode needs this experiment; versatiles-style uses it for its `text.languageStrict` option.

**Caveats:** this is not additive — it fills the spec attributes `name_<lang>` from a tag the spec does not use for
them. The value is copied as tagged, including multi-value names. Natural Earth 10m is coarse near borders. The
experiment adds the `ne_10m_admin_0_countries` source (a few MB), downloaded like the other sources.

## `island_labels`

Base Shortbread labels islands mapped as nodes only (`place_labels`, `kind=island`, from z10). Most islands are mapped as
polygons and get no label. With `island_labels`, a polygon tagged `place=island` with a `name` gets a label point inside
the polygon:

- `kind=island`, `population=0`, and the usual name attributes,
- minimum zoom by area: z8 from 160 km², z9 from 40 km², otherwise z10,
- sorted by area, largest first, and not thinned by the `place_labels` density limit.

**Caveat:** a strict-spec consumer sees additional `place_labels` points, the larger ones from z8 and z9, before the z10
at which the spec starts islands.

## `address_details`

Adds two string attributes to the `addresses` layer, each only when its tag is present and not empty:

| Attribute |     From     |
|-----------|--------------|
| `unit`    | `addr:unit`  |
| `block`   | `addr:block` |

A feature that is a POI goes to `pois` instead of `addresses` and gets neither attribute.

## `bridge_names`

Adds `name` and `name_<code>` to the `man_made=bridge` polygons of the `bridges` layer (z12+), from their own tags as
on any other layer. The spec defines no name for bridges, so without the experiment a named bridge cannot be labelled.

```json
{
  "type": "symbol",
  "source-layer": "bridges",
  "layout": { "text-field": ["get", "name"] }
}
```

**Upstream:** [shortbread-docs #141](https://github.com/shortbread-tiles/shortbread-docs/issues/141); the maintainer
notes that `bridges` is a polygon layer and not meant for labels.

## `early_attributes`

The spec makes some `streets` attributes available later than the features that need them: link roads look like main
roads from z5 to z10, and service railways (sidings, yards, spurs) look like main lines at z10. With `early_attributes`
these attributes arrive with the feature:

|           Attribute           | Spec |                                   With `early_attributes`                                   |
|-------------------------------|------|---------------------------------------------------------------------------------------------|
| `link`                        | z11  | z5, the proposal's floor: the feature's own minimum zoom, since no link road starts earlier |
| `service`                     | z11  | z10, the proposal's floor, so a motorway with a service tag does not expose it from z5      |
| `bicycle`, `horse` (1.0 only) | z14  | z13, where paths enter the layer                                                            |

The zooms are those of the upstream proposal, not simply each feature's minimum zoom: `service` is floored at z10 even
when the feature itself starts earlier.

`tunnel`, `bridge`, `surface`, `tracktype` and `oneway` keep their spec zooms, because features only merge when their
attributes are identical; the 1.1 access attributes already start at z13.

**Cost**, measured on Estonia and Berlin extracts: about +2% `streets` layer bytes at z10 and no measurable change in
whole tiles. Emitting all mid-tier attributes from the feature's minimum zoom would instead cost +23% to +36% of the z10
`streets` layer.

**Upstream:** [shortbread-docs #184](https://github.com/shortbread-tiles/shortbread-docs/issues/184) proposes these zooms
for the schema. If it is accepted, the behavior becomes the default for the version that includes it.

## `mountain_peaks`

A point layer the schema does not have, for orientation in mountain areas. It follows OpenStreetMap Carto, which draws
peaks and volcanoes from z11 and saddles from z15. Carto's zooms refer to 256 px raster tiles, and the same map scale
is one zoom lower in 512 px vector tiles, so the layer starts at z10 and z14. Only nodes are used, as in Carto.

|   Kind    |       From        | Zoom |
|-----------|-------------------|------|
| `peak`    | `natural=peak`    | 10+  |
| `volcano` | `natural=volcano` | 10+  |
| `saddle`  | `natural=saddle`  | 14   |

|       Attribute       |      Type       |                        Present                        |
|-----------------------|-----------------|-------------------------------------------------------|
| `kind`                | string          | always                                                |
| `name`, `name_<code>` | string          | when tagged, as on the other layers                   |
| `ele`                 | integer, meters | when `ele` is a plain number, rounded to whole meters |

- **`ele`** is read like Carto reads it: up to four digits with optional decimals and an optional minus sign. Values
  with units (`4545 m`), feet, lists or ranges are left out.
- **Order:** features are sorted by `ele`, highest first; features without `ele` come last. There is no density limit.
- **Density**, measured on a Switzerland extract (2026-09-11): 9,227 peaks and 2,676 saddles, 87% of the peaks named
  and 93% with a plain `ele`; at most 348 peaks in a z10 tile, 116 in a z11 tile and 11 in a z14 tile.
- **Cost**, on the same extract: the archive grows by 0.5% (334,184 KiB to 335,944 KiB). z10 tiles grow the most, by
  6.7% in total, but the largest z10 tile only from 125.0 KiB to 126.8 KiB; from z12 on the largest tiles are unchanged.

```json
{
  "type": "symbol",
  "source-layer": "mountain_peaks",
  "filter": ["!=", ["get", "kind"], "saddle"],
  "layout": {
    "text-field": ["concat", ["get", "name"], "\n", ["get", "ele"]],
    "symbol-sort-key": ["-", 0, ["coalesce", ["get", "ele"], 0]]
  }
}
```

**Upstream:** [shortbread-docs #137](https://github.com/shortbread-tiles/shortbread-docs/issues/137) asks for peaks and
volcanoes in a future schema version.

## Stability

Experiments are not a stable contract. Tokens are removed or renamed only with a deprecation period in which the old
token still works and logs a warning, as `building_heights` and `building_parts` do now. Follow the linked upstream
issues for changes to the schema itself.
