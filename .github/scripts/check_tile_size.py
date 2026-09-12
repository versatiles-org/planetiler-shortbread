#!/usr/bin/env python3
"""Checks Planetiler layer stats against a tile size budget.

Usage: check_tile_size.py --budget-kib 500 RUN.mbtiles.layerstats.tsv.gz [...]

Each file is written by Planetiler's --output_layerstats. For each one the script reports the largest compressed tile
per zoom together with that tile's largest layer, and it fails when any tile is larger than the budget, naming the
tile and its largest layer. The report also goes to the GitHub step summary when $GITHUB_STEP_SUMMARY is set.
"""

import argparse
import csv
import gzip
import os
import sys
from pathlib import Path

# how many over-budget tiles to name before summarizing the rest as a count
MAX_REPORTED_FAILURES = 20


def read_stats(path):
    """Returns {(z, x, y): (compressed tile bytes, [(uncompressed layer bytes, layer name), ...])}."""
    tiles = {}
    with gzip.open(path, "rt", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            key = (int(row["z"]), int(row["x"]), int(row["y"]))
            _, layers = tiles.setdefault(key, (int(row["archived_tile_bytes"]), []))
            layers.append((int(row["layer_bytes"]), row["layer"]))
    return tiles


def kib(size):
    return f"{size / 1024:,.1f} KiB"


def tile_name(key):
    return f"{key[0]}/{key[1]}/{key[2]}"


def main():
    parser = argparse.ArgumentParser(description="Checks Planetiler layer stats against a tile size budget.")
    parser.add_argument("--budget-kib", type=float, required=True, help="largest allowed compressed tile, in KiB")
    parser.add_argument("stats", nargs="+", type=Path, help="layer stats files written by --output_layerstats")
    args = parser.parse_args()
    budget = args.budget_kib * 1024

    report = []
    failures = []  # (size, message)
    for path in args.stats:
        run = path.name.removesuffix(".layerstats.tsv.gz")
        if not path.exists():
            print(f"::error title=Missing layer stats::{path} does not exist; the run that writes it probably failed")
            return 2
        tiles = read_stats(path)
        if not tiles:
            print(f"::error title=Empty layer stats::{path} lists no tiles, so nothing was checked against the budget")
            return 2

        largest = {}  # zoom -> key of the largest tile
        for key, (size, _) in tiles.items():
            if key[0] not in largest or size > tiles[largest[key[0]]][0]:
                largest[key[0]] = key
        report += [
            f"### {run}",
            "",
            "| zoom | largest tile | compressed | its largest layer (uncompressed) |",
            "|---:|---|---:|---|",
        ]
        for zoom in sorted(largest):
            size, layers = tiles[largest[zoom]]
            layer_bytes, layer = max(layers)
            report.append(f"| {zoom} | {tile_name(largest[zoom])} | {kib(size)} | {layer}, {kib(layer_bytes)} |")
        report.append("")

        for key, (size, layers) in tiles.items():
            if size > budget:
                layer_bytes, layer = max(layers)
                failures.append((size, f"{run}: tile {tile_name(key)} is {kib(size)} compressed, over the budget of "
                                       f"{kib(budget)}; its largest layer is {layer} ({kib(layer_bytes)} uncompressed)"))

    text = "\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")

    if not failures:
        print(f"All tiles are within the budget of {kib(budget)}.")
        return 0
    failures.sort(reverse=True)
    for _, message in failures[:MAX_REPORTED_FAILURES]:
        print(f"::error title=Tile over budget::{message}")
    if len(failures) > MAX_REPORTED_FAILURES:
        print(f"::error title=Tile over budget::{len(failures) - MAX_REPORTED_FAILURES} more tiles are over the budget")
    return 1


if __name__ == "__main__":
    sys.exit(main())
