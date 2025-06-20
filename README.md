# Planetiler‑Shortbread Docker Image

**Generate Shortbread‑schema vector tiles from any OpenStreetMap extract with a single command.**

[![Docker Hub](https://img.shields.io/docker/pulls/versatiles/versatiles-planetiler?label=Docker%20Hub)](https://hub.docker.com/r/versatiles/versatiles-planetiler)
[![Image Size](https://img.shields.io/docker/image-size/versatiles/versatiles-planetiler?label=Image%20Size)](https://hub.docker.com/r/versatiles/versatiles-planetiler)

---

## What’s inside?

| Component                                                                | Version | Purpose                                        |
| ------------------------------------------------------------------------ | ------- | ---------------------------------------------- |
| **[Planetiler](https://github.com/onthegomap/planetiler)**               | Latest  | OSM → vector tiles compiler                    |
| **[Shortbread configuration](https://shortbread-tiles.org/schema/1.0/)** | 1.0     | Styling / layer schema                         |
| **[VersaTiles](https://github.com/versatiles-org/versatiles)**           | Latest  | Converts `.mbtiles` → `.versatiles` containers |

The final output is a space‑efficient `.versatiles` archive (Brotli‑compressed) that you can serve directly with any VersaTiles‑compatible server. The intermediate `.mbtiles` (Gzip‑compressed) is left in the `/app/data` volume for inspection.

---

## Supported platforms

- `linux/amd64` (Intel / AMD)
- `linux/arm64` (Apple Silicon, Raspberry Pi 4/5, AWS Graviton, …)

---

## Quick start

Generate OSM vector tiles for the whole planet …

```
curl https://get.docker.com | sh
docker run --rm -v "$(pwd)/data:/app/data" versatiles/versatiles-planetiler:latest -a planet
```

The container writes all intermediate / output files into the mounted `data` folder **on your host**, so make sure you have enough free disk space (\~300 GB for planet‑wide tiles).

### Command‑line options

Running the image without parameters shows the built‑in help:

```
Options:
  -h, --help          Show this help message
  -a, --area <name>   Specify the area to process (e.g.: "planet", "geofabrik:ukraine", etc.)
```

`-a planet` – downloads the minute‑updated planet PBF and renders **everything**.

Example: Spain’s capital only (≈ a few minutes on a laptop)

```bash
docker run --rm -v "$(pwd)/data:/app/data" versatiles/versatiles-planetiler:latest -a madrid
```

---

## Output files

After the run you’ll find two files in the mounted `data` directory:

| File             | Description                                                |
| ---------------- | ---------------------------------------------------------- |
| `osm.mbtiles`    | Gzip‑compressed vector‑tile archive produced by Planetiler |
| `osm.versatiles` | Brotli‑compressed archive, auto‑converted by Versatiles    |

Serve the `.versatiles` file with [VersaTiles‑server](https://github.com/versatiles-org/versatiles-rs) or any other compatible tool.

---

## Image tags & versions

- `latest` – always points to the most recent build on `main` using the **latest Planetiler release**.
- `<planetiler‑version>` (e.g. `0.9.1`) – immutable tag matching the exact Planetiler release in the image.

```bash
# Pull a specific Planetiler build
docker pull versatiles/versatiles-planetiler:0.9.1
```
