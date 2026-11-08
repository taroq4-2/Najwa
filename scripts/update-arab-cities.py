"""Refresh the offline Arab League place catalog from the GeoNames country dumps."""

from concurrent.futures import ThreadPoolExecutor
import io
from pathlib import Path
import re
from urllib.request import urlopen
from zipfile import ZipFile


COUNTRIES = "SA AE BH KW OM QA JO PS LB SY IQ YE EG SD LY TN DZ MA MR SO DJ KM".split()
BASE = "https://download.geonames.org/export/dump"
DESTINATION = Path(__file__).resolve().parents[1] / "app/src/main/assets/cities"
ARABIC = re.compile(r"[\u0621-\u064a]")


def arabic_names(code):
    with urlopen(f"{BASE}/alternatenames/{code}.zip", timeout=60) as response:
        archive = ZipFile(io.BytesIO(response.read()))
    names = {}
    with archive.open(f"{code}.txt") as source:
        for line in io.TextIOWrapper(source, encoding="utf-8"):
            row = line.rstrip("\n").split("\t")
            if row[2] == "ar" and row[6] != "1" and row[7] != "1":
                names.setdefault(row[1], []).append((row[4] != "1", row[5] == "1", row[3]))
    return {ident: [row[2] for row in sorted(rows)] for ident, rows in names.items()}


def fetch(code):
    localized = arabic_names(code)
    with urlopen(f"{BASE}/{code}.zip", timeout=60) as response:
        archive = ZipFile(io.BytesIO(response.read()))
    places = []
    regions = {}
    with archive.open(f"{code}.txt") as source:
        for line in io.TextIOWrapper(source, encoding="utf-8"):
            row = line.rstrip("\n").split("\t")
            names = localized.get(row[0], [])
            name = names[0] if names else row[1]
            if row[7] == "ADM1":
                regions[row[10]] = name
            if row[6] != "P" or row[7] in {"PPLH", "PPLQ", "PPLW"} or not row[17]:
                continue
            aliases = [name for name in row[3].split(",") if ARABIC.search(name)]
            terms = "|".join(dict.fromkeys([row[1], row[2], *names, *aliases]))
            # This Saudi record's source time zone is erroneously Budapest.
            zone = "Asia/Riyadh" if row[0] == "12547268" else row[17]
            value = (row[0], name, terms, row[4], row[5], zone, row[10])
            priority = 0 if row[7] == "PPLC" else 1
            places.append((priority, -int(row[14] or 0), value))
    places.sort()
    if not places:
        raise ValueError(f"No populated places found for {code}")
    return code, [(*value[:-1], regions.get(value[-1], "")) for _, _, value in places]


def main():
    with ThreadPoolExecutor(max_workers=6) as pool:
        countries = list(pool.map(fetch, COUNTRIES))
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for code, places in countries:
        content = "".join("\t".join(place) + "\n" for place in places)
        (DESTINATION / f"{code}.tsv").write_text(content, encoding="utf-8", newline="\n")
        print(code, len(places))
    print("Total:", sum(len(places) for _, places in countries))


if __name__ == "__main__":
    main()
