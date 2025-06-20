# Docker Image: Planetiler + Shortbread + VersaTiles

**Generate Shortbread‑schema vector tiles from any OpenStreetMap extract with a single command.**

[![Docker Image Size](https://img.shields.io/docker/image-size/versatiles/versatiles-planetiler?label=Docker%20Image%20Size)](https://hub.docker.com/r/versatiles/versatiles-planetiler) [![Docker Image Pulls](https://img.shields.io/docker/pulls/versatiles/versatiles-planetiler?label=Docker%20Image%20Pulls)](https://hub.docker.com/r/versatiles/versatiles-planetiler)  
![Planetiler Version](https://img.shields.io/docker/v/versatiles/versatiles-planetiler/latest?label=Planetiler%20Version)

---

## 🫵 We need your help!

Preview the latest tiles at **<https://planetiler.versatiles.org>** and let us know what you think.

- **Found a problem?** Open an issue with a URL and screenshot.
- **Want to fix something?** Tweak [`resources/config/shortbread.yml`](./resources/config/shortbread.yml) and send a pull request.

We refresh the demo regularly. Once the config is stable we’ll upstream it to [Planetiler](https://github.com/onthegomap/planetiler/blob/main/planetiler-custommap/src/main/resources/samples/shortbread.yml).

---

## 📦 What’s inside?

| Component                                                                | Version | Purpose                                        |
| ------------------------------------------------------------------------ | ------- | ---------------------------------------------- |
| **[Planetiler](https://github.com/onthegomap/planetiler)**               | Latest  | OSM → vector tiles compiler                    |
| **[Shortbread configuration](https://shortbread-tiles.org/schema/1.0/)** | 1.0     | Styling / layer schema                         |
| **[VersaTiles](https://github.com/versatiles-org/versatiles)**           | Latest  | Converts `.mbtiles` → `.versatiles` containers |

The container first writes a **Gzip‑compressed `.mbtiles`** file, then automatically converts it to a **Brotli‑compressed `.versatiles`** archive.

---

## 💪 Supported platforms

`linux/amd64` (Intel / AMD) • `linux/arm64` (Apple Silicon, Raspberry Pi 4/5, AWS Graviton, …)  
Multi‑arch manifests ensure Docker pulls the correct architecture for you.

---

## 🚀 Quick start

```bash
# Generate planet‑wide tiles
docker run --rm \
  -v "$(pwd)/data:/app/data" \
  versatiles/versatiles-planetiler:latest -a planet
```

All output is written to the mounted `data` folder. A full‑planet run needs roughly **300 GB** of free disk space.

### Command‑line options

```
Options:
  -h, --help          Show this help message
  -a, --area <name>   Area to process (e.g. "planet", "geofabrik:ukraine", "madrid")
```

Examples:

```bash
# Madrid only (fast)
docker run --rm -v "$(pwd)/data:/app/data" versatiles/versatiles-planetiler:latest -a madrid
```

---

## 📂 Output files

| File                 | Description                                      |
| -------------------- | ------------------------------------------------ |
| `osm(-…).mbtiles`    | Gzip‑compressed archive produced by Planetiler   |
| `osm(-…).versatiles` | Brotli‑compressed archive produced by VersaTiles |

Serve either file with [VersaTiles‑server](https://github.com/versatiles-org/versatiles-rs) or any compatible server.

---

## 🏷️ Image tags

- `latest` – current build on `main`, always using the **latest Planetiler release**
- `<planetiler‑version>` (e.g. `0.9.1`) – pinned to that exact Planetiler release

```bash
# Pull a specific version
docker pull versatiles/versatiles-planetiler:0.9.1
```
