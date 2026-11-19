#!/usr/bin/env python3
"""Builds the bundled map data for Megyejáték.

Two steps, so the slow downloads only happen once:

    python tools/build_data.py fetch --ksh path/to/helysegnevtar.xlsx
    python tools/build_data.py build

`fetch` writes raw sources to tools/cache/ (git-ignored):
  * OpenStreetMap county boundaries (admin_level=6 + Budapest) via Overpass
  * OpenStreetMap boundaries of the settlements that pass the population cut
  * Natural Earth 10m rivers and lakes (from the natural-earth-vector GitHub repo)
  * the KSH settlement table (copied from the --ksh file you downloaded)

`build` turns the cache into app/src/main/assets/map/:
  counties.geojson  20 polygons (19 counties + Budapest), simplified
  outline.geojson   national border (dissolved from the simplified counties)
  water.geojson     rivers (lines) and lakes (polygons) clipped to Hungary
  counties.json     id, name, seat, neighbours, cities, label point, west->east rank
  cities.json       every settlement above the population cut
  meta.json         sources, editions and counts

`build` stops with an error if any consistency check fails (see validate()).
Requires Python 3.10+, `pip install -r tools/requirements.txt`, and Node.js (for npx mapshaper).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / "cache"     # overridable with --cache
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "map"     # overridable with --out
OVERRIDES = ROOT / "tools" / "neighbour_overrides.json"

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
NE_BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"
NE_LAYERS = ["ne_10m_rivers_lake_centerlines", "ne_10m_rivers_europe", "ne_10m_lakes", "ne_10m_lakes_europe"]
MAPSHAPER = "mapshaper@0.8.5"

POPULATION_CUT = 10_000          # strictly more than this
SIMPLIFY = "1.5%"                # share of vertices mapshaper keeps
COORD_PRECISION = 0.0001         # ~10 m
MIN_SHARED_BORDER_KM = 1.0       # shorter contacts (corner touches) are not neighbours
REF_LAT = 47.2                   # centre of Hungary, for the local km projection
WATER_MARGIN_DEG = 0.03          # keep border rivers (Duna, Dráva) that run on the outline


@dataclass(frozen=True)
class Area:
    code: str        # ISO 3166-2, e.g. HU-PE
    name: str
    seat: str
    capital: bool = False


AREAS: list[Area] = [
    Area("HU-BK", "Bács-Kiskun", "Kecskemét"),
    Area("HU-BA", "Baranya", "Pécs"),
    Area("HU-BE", "Békés", "Békéscsaba"),
    Area("HU-BZ", "Borsod-Abaúj-Zemplén", "Miskolc"),
    Area("HU-CS", "Csongrád-Csanád", "Szeged"),
    Area("HU-FE", "Fejér", "Székesfehérvár"),
    Area("HU-GS", "Győr-Moson-Sopron", "Győr"),
    Area("HU-HB", "Hajdú-Bihar", "Debrecen"),
    Area("HU-HE", "Heves", "Eger"),
    Area("HU-JN", "Jász-Nagykun-Szolnok", "Szolnok"),
    Area("HU-KE", "Komárom-Esztergom", "Tatabánya"),
    Area("HU-NO", "Nógrád", "Salgótarján"),
    Area("HU-PE", "Pest", "Budapest"),   # seat is Budapest, but Budapest is not part of Pest
    Area("HU-SO", "Somogy", "Kaposvár"),
    Area("HU-SZ", "Szabolcs-Szatmár-Bereg", "Nyíregyháza"),
    Area("HU-TO", "Tolna", "Szekszárd"),
    Area("HU-VA", "Vas", "Szombathely"),
    Area("HU-VE", "Veszprém", "Veszprém"),
    Area("HU-ZA", "Zala", "Zalaegerszeg"),
    Area("HU-BU", "Budapest", "Budapest", capital=True),
]
AREA_BY_CODE = {a.code: a for a in AREAS}


# ---------------------------------------------------------------- helpers

def log(msg: str) -> None:
    print(msg, file=sys.stderr, flush=True)


def fail(msg: str) -> None:
    log(f"\nHIBA / ERROR: {msg}")
    sys.exit(1)


def fold(s: str) -> str:
    """Lower-case, strip accents and non-letters: 'Vármegye megnevezése' -> 'varmegyemegnevezese'."""
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", s.lower())


def county_key(name: str) -> str:
    """'Bács-Kiskun vármegye' / 'Bács-Kiskun megye' / 'Bács-Kiskun' -> 'bacskiskun'."""
    n = re.sub(r"\s+(vár)?megye$", "", str(name).strip(), flags=re.IGNORECASE)
    if fold(n) in {"fovaros", "budapestfovaros"}:
        n = "Budapest"
    return fold(n)


AREA_BY_KEY = {county_key(a.name): a for a in AREAS}
# Older spellings that may appear in source files.
AREA_BY_KEY[county_key("Csongrád")] = AREA_BY_CODE["HU-CS"]


def slug(name: str) -> str:
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def to_km(lon: float, lat: float) -> tuple[float, float]:
    """Local equirectangular projection in km, good enough for lengths inside Hungary."""
    k = 111.32
    return lon * k * math.cos(math.radians(REF_LAT)), lat * k


def project_km(geom):
    import numpy as np
    import shapely
    k = 111.32
    scale = np.array([k * math.cos(math.radians(REF_LAT)), k])
    return shapely.transform(geom, lambda xy: xy * scale)


def read_json(p: Path):
    return json.loads(p.read_text(encoding="utf-8"))


def write_json(p: Path, data, compact: bool = True) -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":") if compact else None,
                      indent=None if compact else 2)
    p.write_text(text + "\n", encoding="utf-8")


def http_get(url: str, data: dict | None = None, timeout: int = 600) -> bytes:
    import requests
    headers = {"User-Agent": "megyejatek-data-pipeline/1.0 (github.com/valentinaschiavon99)"}
    if data is None:
        r = requests.get(url, timeout=timeout, headers=headers)
    else:
        r = requests.post(url, data=data, timeout=timeout, headers=headers)
    r.raise_for_status()
    return r.content


# ---------------------------------------------------------------- KSH

def read_ksh(path: Path) -> list[dict]:
    """Reads the KSH Helységnévtár table. Column positions differ between editions,
    so the header row and the columns are found by name."""
    import openpyxl

    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))
        header_idx = next((i for i, r in enumerate(rows[:15])
                           if any("helysegmegnevezese" in fold(c or "") for c in r)), None)
        if header_idx is None:
            continue
        # Some editions split the header over two rows; merge them.
        header = [fold(a or "") + fold(b or "") for a, b in
                  zip(rows[header_idx], rows[header_idx + 1] if header_idx + 1 < len(rows) else [None] * 99)]

        def col(*needles: str) -> int:
            for i, h in enumerate(header):
                if all(n in h for n in needles):
                    return i
            fail(f"KSH: no column containing {needles} in sheet '{ws.title}'. Header: {header}")

        c_name = col("helysegmegnevezese")
        c_county = col("megyemegnevezese")
        c_pop = col("lako", "nepesseg")

        out = []
        for r in rows[header_idx + 1:]:
            name, county, pop = r[c_name], r[c_county], r[c_pop]
            if not name or not isinstance(pop, (int, float)):
                continue
            out.append({"name": str(name).strip(), "county": str(county or "").strip(), "population": int(pop)})
        log(f"KSH: {len(out)} settlement rows read from sheet '{ws.title}'")
        return out
    fail(f"KSH: could not find a sheet with a 'Helység megnevezése' header in {path}")


def ksh_big_settlements(rows: list[dict]) -> list[dict]:
    # Budapest may be listed as one row or as 23 district rows ("Budapest 01. ker.").
    if not any(r["name"] == "Budapest" for r in rows):
        districts = [r for r in rows if r["name"].startswith("Budapest ")]
        if districts:
            rows = [r for r in rows if not r["name"].startswith("Budapest ")]
            rows.append({"name": "Budapest", "county": "Budapest",
                         "population": sum(d["population"] for d in districts)})
    big = []
    for r in rows:
        if r["population"] <= POPULATION_CUT:
            continue
        area = AREA_BY_CODE["HU-BU"] if r["name"] == "Budapest" else AREA_BY_KEY.get(county_key(r["county"]))
        if area is None:
            fail(f"KSH: unknown county '{r['county']}' for {r['name']}")
        big.append({**r, "countyId": area.code})
    big.sort(key=lambda r: -r["population"])
    names = [r["name"] for r in big]
    dupes = {n for n in names if names.count(n) > 1}
    if dupes:
        fail(f"KSH: duplicate settlement names above the cut: {dupes}")
    log(f"KSH: {len(big)} settlements with more than {POPULATION_CUT:,} inhabitants")
    return big


# ---------------------------------------------------------------- fetch

_SETTLEMENT_NAME = re.compile(r"^[\w .'\-]+$", re.UNICODE)


def overpass_regex_literal(name: str) -> str:
    """Escapes a settlement name for an Overpass (POSIX ERE) regex inside a double-quoted string.
    Names come from the KSH file, but the query must stay well-formed whatever is in it."""
    if not _SETTLEMENT_NAME.match(name):
        fail(f"unexpected characters in settlement name {name!r}")
    return re.sub(r"([.^$*+?()\[\]{}|\\])", r"\\\\\1", name)


def overpass(query: str):
    import osm2geojson
    log("Overpass: querying (this can take a few minutes)...")
    raw = json.loads(http_get(OVERPASS_URL, data={"data": query}))
    return osm2geojson.json2geojson(raw, filter_used_refs=True)


def cmd_fetch(args) -> None:
    CACHE.mkdir(parents=True, exist_ok=True)

    ksh_src = Path(args.ksh).expanduser()
    if not ksh_src.exists():
        fail(f"KSH file not found: {ksh_src}")
    shutil.copy(ksh_src, CACHE / "ksh_helysegnevtar.xlsx")
    big = ksh_big_settlements(read_ksh(CACHE / "ksh_helysegnevtar.xlsx"))

    hu = 'area["ISO3166-1"="HU"]["admin_level"="2"]->.hu;'
    counties = overpass(f"""
        [out:json][timeout:600];
        {hu}
        (
          rel(area.hu)["boundary"="administrative"]["admin_level"="6"];
          rel(area.hu)["boundary"="administrative"]["ISO3166-2"="HU-BU"];
        );
        out geom;
    """)
    write_json(CACHE / "osm_counties.geojson", counties)
    log(f"OSM: {len(counties['features'])} county-level boundaries saved")

    names = "|".join(overpass_regex_literal(r["name"]) for r in big if r["name"] != "Budapest")
    settlements = overpass(f"""
        [out:json][timeout:600];
        {hu}
        rel(area.hu)["boundary"="administrative"]["admin_level"="8"]["name"~"^({names})$"];
        out geom;
    """)
    write_json(CACHE / "osm_settlements.geojson", settlements)
    log(f"OSM: {len(settlements['features'])} settlement boundaries saved")

    for layer in NE_LAYERS:
        (CACHE / f"{layer}.geojson").write_bytes(http_get(f"{NE_BASE}/{layer}.geojson"))
        log(f"Natural Earth: {layer} saved")

    write_json(CACHE / "fetch_meta.json", {
        "fetchedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "kshFile": ksh_src.name,
    }, compact=False)
    log("fetch: done. Next: python tools/build_data.py build")


# ---------------------------------------------------------------- build

def load_counties() -> dict:
    """Returns {code: shapely geometry} for the 20 areas, unsimplified, lon/lat."""
    from shapely.geometry import shape
    from shapely.validation import make_valid

    fc = read_json(CACHE / "osm_counties.geojson")
    found: dict[str, object] = {}
    for f in fc["features"]:
        tags = f["properties"].get("tags", f["properties"])
        area = AREA_BY_CODE.get(tags.get("ISO3166-2", "")) or AREA_BY_KEY.get(county_key(tags.get("name", "")))
        if area is None:
            continue  # neighbouring countries' units picked up at the border
        geom = make_valid(shape(f["geometry"]))
        if geom.geom_type not in ("Polygon", "MultiPolygon"):
            geom = geom.buffer(0)
        if area.code in found:
            fail(f"OSM: two boundaries match {area.name}")
        found[area.code] = geom
    missing = [AREA_BY_CODE[c].name for c in AREA_BY_CODE if c not in found]
    if missing:
        fail(f"OSM: missing boundaries for {missing}")
    return found


def compute_neighbours(geoms: dict) -> dict[str, list[str]]:
    km = {c: project_km(g) for c, g in geoms.items()}
    eps = 0.05  # km; tolerant to tiny mismatches between neighbouring boundaries
    buffered = {c: g.buffer(eps) for c, g in km.items()}
    nb: dict[str, set[str]] = {c: set() for c in geoms}
    codes = sorted(geoms)
    for i, a in enumerate(codes):
        for b in codes[i + 1:]:
            if not buffered[a].intersects(buffered[b]):
                continue
            shared_km = buffered[a].intersection(buffered[b]).area / (2 * eps)
            if shared_km >= MIN_SHARED_BORDER_KM:
                nb[a].add(b)
                nb[b].add(a)
    if OVERRIDES.exists():
        ov = read_json(OVERRIDES)
        for a, b in ov.get("add", []):
            nb[a].add(b); nb[b].add(a)
        for a, b in ov.get("remove", []):
            nb[a].discard(b); nb[b].discard(a)
    return {c: sorted(v) for c, v in nb.items()}


def run_mapshaper(*args: str) -> None:
    npx = shutil.which("npx")
    if not npx:
        fail("npx not found: install Node.js (https://nodejs.org) to run mapshaper")
    cmd = [npx, "--yes", MAPSHAPER, *args]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        fail(f"mapshaper failed:\n{' '.join(cmd)}\n{res.stderr}")


def round_coords(obj, nd: int = 4):
    if isinstance(obj, float):
        return round(obj, nd)
    if isinstance(obj, list):
        return [round_coords(x, nd) for x in obj]
    return obj


def simplify_counties(geoms: dict, tmp: Path) -> tuple[dict, dict]:
    from shapely.geometry import mapping
    src = {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {"id": c, "name": AREA_BY_CODE[c].name}, "geometry": mapping(g)}
        for c, g in sorted(geoms.items())]}
    write_json(tmp / "counties_full.geojson", src)
    # mapshaper builds a shared-arc topology first, so neighbouring counties stay gap-free.
    run_mapshaper(str(tmp / "counties_full.geojson"),
                  "-simplify", SIMPLIFY, "keep-shapes",
                  "-o", str(tmp / "counties.geojson"), f"precision={COORD_PRECISION}", "format=geojson")
    run_mapshaper(str(tmp / "counties.geojson"),
                  "-dissolve", "copy-fields=",
                  "-o", str(tmp / "outline.geojson"), f"precision={COORD_PRECISION}", "format=geojson",
                  "geojson-type=FeatureCollection")
    counties = read_json(tmp / "counties.geojson")
    outline = read_json(tmp / "outline.geojson")
    for f in outline["features"]:
        f["properties"] = {"id": "HU"}
    return counties, outline


def build_water(outline_full, tmp: Path) -> dict:
    from shapely.geometry import mapping, shape
    clip = outline_full.buffer(WATER_MARGIN_DEG)
    by_kind: dict[str, list] = {"river": [], "lake": []}
    for layer in NE_LAYERS:
        p = CACHE / f"{layer}.geojson"
        if not p.exists():
            fail(f"missing {p.name}: run fetch first")
        kind = "lake" if "lakes" in layer else "river"
        for f in read_json(p)["features"]:
            if not f.get("geometry"):
                continue
            g = shape(f["geometry"])
            if not g.intersects(clip):
                continue
            if kind == "river":
                g = g.intersection(clip)
            elif g.area < 1e-4:
                continue
            if not g.is_empty:
                by_kind[kind].append({"type": "Feature", "properties": {"kind": kind}, "geometry": mapping(g)})

    # Lines and polygons are simplified separately: mapshaper keeps one geometry type per layer.
    features = []
    for kind, feats in by_kind.items():
        if not feats:
            continue
        src, dst = tmp / f"{kind}_full.geojson", tmp / f"{kind}.geojson"
        write_json(src, {"type": "FeatureCollection", "features": feats})
        extra = ["-dissolve", "kind"] if kind == "river" else []
        run_mapshaper(str(src), *extra, "-simplify", "20%", "keep-shapes",
                      "-o", str(dst), f"precision={COORD_PRECISION}", "format=geojson",
                      "geojson-type=FeatureCollection")
        features += read_json(dst)["features"]
    return {"type": "FeatureCollection", "features": features}


def build_cities(big: list[dict], geoms: dict) -> list[dict]:
    from shapely.geometry import Point, shape

    by_name: dict[str, list] = {}
    for f in read_json(CACHE / "osm_settlements.geojson")["features"]:
        tags = f["properties"].get("tags", f["properties"])
        if tags.get("name"):
            by_name.setdefault(tags["name"], []).append(shape(f["geometry"]))

    seats = {a.seat for a in AREAS}
    cities, problems = [], []
    for r in big:
        if r["name"] == "Budapest":
            candidates = [geoms["HU-BU"]]
        else:
            candidates = by_name.get(r["name"], [])
        county_geom = geoms[r["countyId"]]
        # Same-named boundaries elsewhere: pick the one inside the KSH county.
        candidates = [g for g in candidates if county_geom.buffer(0.01).contains(g.representative_point())]
        if not candidates:
            problems.append(f"{r['name']} ({AREA_BY_CODE[r['countyId']].name}): no OSM boundary found")
            continue
        g = max(candidates, key=lambda g: g.area)
        pt = g.centroid if g.geom_type == "Point" or g.contains(g.centroid) else g.representative_point()
        if not county_geom.contains(Point(pt.x, pt.y)):
            pt = g.representative_point()
        cities.append({
            "id": slug(r["name"]),
            "name": r["name"],
            "countyId": r["countyId"],
            "population": r["population"],
            "lon": round(pt.x, 5),
            "lat": round(pt.y, 5),
            "isSeat": r["name"] in seats,
        })
    if problems:
        fail("cities without a usable boundary:\n  " + "\n  ".join(problems))
    return cities


def build_county_table(geoms: dict, neighbours: dict, cities: list[dict]) -> list[dict]:
    from shapely.ops import polylabel

    city_id = {c["name"]: c["id"] for c in cities}
    table = []
    for a in AREAS:
        g = geoms[a.code]
        main = max(g.geoms, key=lambda p: p.area) if g.geom_type == "MultiPolygon" else g
        label = polylabel(main, tolerance=0.001)
        table.append({
            "id": a.code,
            "name": a.name,
            "type": "capital" if a.capital else "county",
            "seatCityId": city_id.get(a.seat),
            "neighbours": neighbours[a.code],
            "cityIds": [c["id"] for c in cities if c["countyId"] == a.code],
            "label": [round(label.x, 4), round(label.y, 4)],
        })
    order = sorted(table, key=lambda t: t["label"][0])
    for rank, t in enumerate(order, start=1):
        t["westToEast"] = rank
    return table


def validate(table: list[dict], cities: list[dict], geoms: dict, counties_fc: dict) -> None:
    from shapely.geometry import Point
    errors = []
    ids = {t["id"] for t in table}
    if len(table) != 20 or len(counties_fc["features"]) != 20:
        errors.append(f"expected 20 areas, got {len(table)} / {len(counties_fc['features'])} polygons")
    by_id = {t["id"]: t for t in table}
    for t in table:
        for n in t["neighbours"]:
            if n not in ids:
                errors.append(f"{t['name']}: unknown neighbour {n}")
            elif t["id"] not in by_id[n]["neighbours"]:
                errors.append(f"asymmetric neighbours: {t['id']} -> {n}")
        if not t["neighbours"]:
            errors.append(f"{t['name']} has no neighbours")
        if t["seatCityId"] is None:
            errors.append(f"{t['name']}: seat {AREA_BY_CODE[t['id']].seat} missing from the city list")
    if "HU-BU" not in by_id["HU-PE"]["neighbours"]:
        errors.append("Pest and Budapest should be neighbours")
    if by_id["HU-PE"]["seatCityId"] != "budapest" or "budapest" in by_id["HU-PE"]["cityIds"]:
        errors.append("Pest: seat must be Budapest, but Budapest must not be one of Pest's cities")
    for c in cities:
        if not geoms[c["countyId"]].buffer(0.005).contains(Point(c["lon"], c["lat"])):
            errors.append(f"city {c['name']} is not inside {AREA_BY_CODE[c['countyId']].name}")
    seen = set()
    for c in cities:
        if c["id"] in seen:
            errors.append(f"duplicate city id {c['id']}")
        seen.add(c["id"])
    if errors:
        fail("validation failed:\n  " + "\n  ".join(errors))


def cmd_build(args) -> None:
    from shapely.ops import unary_union

    for need in ["osm_counties.geojson", "osm_settlements.geojson", "ksh_helysegnevtar.xlsx"]:
        if not (CACHE / need).exists():
            fail(f"missing tools/cache/{need}: run `python tools/build_data.py fetch --ksh ...` first")

    big = ksh_big_settlements(read_ksh(CACHE / "ksh_helysegnevtar.xlsx"))
    geoms = load_counties()
    neighbours = compute_neighbours(geoms)
    cities = build_cities(big, geoms)
    table = build_county_table(geoms, neighbours, cities)

    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        counties_fc, outline_fc = simplify_counties(geoms, tmp)
        water_fc = build_water(unary_union(list(geoms.values())), tmp)

    validate(table, cities, geoms, counties_fc)

    ASSETS.mkdir(parents=True, exist_ok=True)
    write_json(ASSETS / "counties.geojson", round_coords(counties_fc))
    write_json(ASSETS / "outline.geojson", round_coords(outline_fc))
    write_json(ASSETS / "water.geojson", round_coords(water_fc))
    write_json(ASSETS / "counties.json", table)
    write_json(ASSETS / "cities.json", cities)

    fetch_meta = read_json(CACHE / "fetch_meta.json") if (CACHE / "fetch_meta.json").exists() else {}
    write_json(ASSETS / "meta.json", {
        "generatedAt": dt.date.today().isoformat(),
        "fetchedAt": fetch_meta.get("fetchedAt"),
        "kshFile": fetch_meta.get("kshFile"),
        "populationCut": POPULATION_CUT,
        "cityCount": len(cities),
        "simplify": SIMPLIFY,
        "attribution": [
            "Határok és települések: © OpenStreetMap közreműködők (ODbL)",
            "Népesség: KSH Helységnévtár",
            "Folyók és tavak: Natural Earth (közkincs)",
        ],
    }, compact=False)

    sizes = {p.name: p.stat().st_size for p in sorted(ASSETS.iterdir())}
    log("\nbuild: done")
    log(f"  areas: {len(table)}, cities: {len(cities)} (> {POPULATION_CUT:,} inhabitants)")
    log("  neighbour pairs: " + str(sum(len(t['neighbours']) for t in table) // 2))
    for name, size in sizes.items():
        log(f"  {name:<18} {size / 1024:7.1f} KB")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("fetch", help="download raw sources into tools/cache/")
    f.add_argument("--ksh", required=True, help="path to the KSH Helységnévtár .xlsx you downloaded")
    f.set_defaults(func=cmd_fetch)
    b = sub.add_parser("build", help="process tools/cache/ into the app's assets")
    b.set_defaults(func=cmd_build)
    for p in (f, b):
        p.add_argument("--cache", type=Path, default=CACHE, help="raw source folder (default: tools/cache)")
    b.add_argument("--out", type=Path, default=ASSETS, help="output folder (default: app assets/map)")
    b.add_argument("--simplify", default=SIMPLIFY, help="share of vertices to keep (default: 1.5%%)")
    args = ap.parse_args()
    _set_paths(args.cache, getattr(args, "out", ASSETS), getattr(args, "simplify", None))
    args.func(args)


def _set_paths(cache: Path, out: Path, simplify: str | None) -> None:
    global CACHE, ASSETS, SIMPLIFY
    CACHE, ASSETS = cache.resolve(), out.resolve()
    if simplify:
        SIMPLIFY = simplify


if __name__ == "__main__":
    main()
