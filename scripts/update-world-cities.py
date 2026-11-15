"""Generate the offline world city catalog from GeoNames cities500.zip."""

import argparse
from collections import defaultdict
from io import TextIOWrapper
from pathlib import Path
import re
from zipfile import ZipFile


PRIORITY_SCRIPTS = re.compile("[\u0600-\u06ff\u0900-\u09ff]")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cities_zip", type=Path)
    parser.add_argument("admin_regions", type=Path)
    args = parser.parse_args()
    assets = Path(__file__).resolve().parents[1] / "app/src/main/assets"
    destination = assets / "cities-world"
    destination.mkdir(exist_ok=True)
    existing = {path.stem for path in (assets / "cities").glob("*.tsv")}
    regions = {row[0]: row[1] for line in args.admin_regions.read_text(encoding="utf-8").splitlines()
               if len(row := line.split("\t")) >= 2}
    cities = defaultdict(list)
    zones = {}
    with ZipFile(args.cities_zip) as archive, archive.open("cities500.txt") as source:
        for line in TextIOWrapper(source, encoding="utf-8"):
            row = line.rstrip("\r\n").split("\t")
            country, zone = row[8], row[17]
            if len(country) != 2 or not zone or row[7] in {"PPLH", "PPLQ", "PPLW"}:
                continue
            population = int(row[14] or 0)
            if population > zones.get(zone, (-1, ""))[0]:
                zones[zone] = (population, country)
            if country in existing:
                continue
            # Keep local scripts and the most useful aliases without bloating the APK.
            alternates = [name for name in row[3].split(",") if 1 < len(name) <= 80]
            alternate_scripts = [name for name in alternates if any(ord(ch) > 255 for ch in name)]
            priority_aliases = [name for name in alternates if PRIORITY_SCRIPTS.search(name)]
            terms = "|".join(dict.fromkeys([row[2], *priority_aliases, *alternate_scripts[:12], *alternates[:16]]))
            region = regions.get(f"{country}.{row[10]}", "")
            entry = (row[0], row[1], terms, row[4], row[5], zone, region)
            cities[country].append((row[7] != "PPLC", -population, entry))

    for country, records in cities.items():
        content = "".join("\t".join(entry) + "\n" for _, _, entry in sorted(records))
        (destination / f"{country}.tsv").write_text(content, encoding="utf-8", newline="\n")
    (destination / "zones.tsv").write_text(
        "".join(f"{zone}\t{code}\n" for zone, (_, code) in sorted(zones.items())),
        encoding="utf-8", newline="\n")
    print(f"{sum(map(len, cities.values())):,} cities in {len(cities)} added countries and territories")


if __name__ == "__main__":
    main()
