#!/usr/bin/env python3
"""Keep only search names that Najwa's interface languages can type.

Najwa supports Arabic, English, Indonesian, Urdu, Bengali, Hindi and Turkish, so
alternate city names in Latin, Arabic-script, Devanagari and Bengali scripts are
useful for search; names in other scripts (Cyrillic, Greek, CJK, …) only add size.

Used by update-arab-cities.py and update-world-cities.py, and runnable on its own
to trim the bundled catalogue in place:

    python scripts/city_terms.py app/src/main/assets
"""
import re
import sys
from pathlib import Path

_SUPPORTED = re.compile(
    "^["
    "\u0000-ɏḀ-ỿ"  # Latin (English, Indonesian, Turkish)
    "؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿"  # Arabic script (Arabic, Urdu)
    "ऀ-ॿ"  # Devanagari (Hindi)
    "ঀ-৿"  # Bengali
    "‌‍‎‏’ʻʼʾʿ"
    "]+$"
)


def supported_terms(name: str, terms: str) -> str:
    """Searchable aliases for [name], without duplicates, one-letter codes or other scripts."""
    kept = [t for t in terms.split("|") if len(t) > 1 and t != name and _SUPPORTED.match(t)]
    return "|".join(dict.fromkeys([name, *kept]))


def trim_file(path: Path) -> int:
    lines = path.read_text(encoding="utf-8").split("\n")
    out = []
    for line in lines:
        fields = line.split("\t")
        if len(fields) == 7:
            fields[2] = supported_terms(fields[1], fields[2])
            line = "\t".join(fields)
        out.append(line)
    text = "\n".join(out)
    before = path.stat().st_size
    path.write_text(text, encoding="utf-8", newline="\n")
    return before - len(text.encode("utf-8"))


if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets")
    saved = sum(trim_file(p) for p in sorted(root.glob("cities*/*.tsv")) if p.name != "zones.tsv")
    print(f"removed {saved / 1e6:.1f} MB of unsupported-script aliases")
