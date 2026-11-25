#!/usr/bin/env python3
"""Build app/src/main/assets/quran/page_letters.txt: written letters per page of the 604-page Madinah mushaf.

    python -I scripts/build_quran_page_letters.py quran-uthmani.txt quran-data.js app/src/main/assets/quran/page_letters.txt

Inputs (not stored in the repository; only the counts ship):
  quran-uthmani.txt  Tanzil Quran Text (Uthmani), "sura|aya|text" format, with its copyright block.
  quran-data.js      Tanzil Quran metadata; only the QuranData.Page array (page start positions) is read.

Method version 1 is the same as QuranLetters in core/src/main/kotlin/org/najwa/core/WorshipCatalog.kt
(docs/worship/QURAN_LETTERS.md). Tanzil writes the basmalah at the start of verse 1 of every surah except
Al-Fatiha and At-Tawbah; it is removed there, so it counts only where it is a verse (1:1) or inside a verse (27:30).
"""
import hashlib
import re
import sys

METHOD_VERSION = 1


def is_letter(c):
    o = ord(c)
    return 0x0621 <= o <= 0x063A or 0x0641 <= o <= 0x064A or o in (0x0671, 0x0654)


def count(text):
    return sum(1 for c in text if is_letter(c))


def letters_only(word):
    return "".join(c for c in word if is_letter(c))


BASMALAH = ["بسم", "ٱلله", "ٱلرحمن", "ٱلرحيم"]


def main(text_path, data_path, out_path):
    raw = open(text_path, encoding="utf-8").read()
    header = [line for line in raw.splitlines() if line.startswith("#")]
    version = next((h.strip("# ").strip() for h in header if "Tanzil Quran Text" in h), None)
    if not version or not any("Creative Commons Attribution" in h for h in header):
        raise SystemExit("Unexpected source: Tanzil copyright block not found")
    verses = {}
    for line in raw.splitlines():
        if not line or line.startswith("#"):
            continue
        sura, aya, text = line.split("|", 2)
        verses[(int(sura), int(aya))] = text
    if len(verses) != 6236:
        raise SystemExit(f"Expected 6236 verses, found {len(verses)}")

    stripped = 0
    for (sura, aya), text in list(verses.items()):
        if aya == 1 and sura not in (1, 9):
            words = text.split(" ")
            if [letters_only(w) for w in words[:4]] != BASMALAH:
                raise SystemExit(f"{sura}:1 does not start with the basmalah")
            verses[(sura, aya)] = " ".join(words[4:])
            stripped += 1

    js = open(data_path, encoding="utf-8").read()
    block = js[js.index("QuranData.Page = ["):]
    block = block[:block.index("];")]
    starts = [(int(s), int(a)) for s, a in re.findall(r"\[(\d+),\s*(\d+)\]", block)]
    if len(starts) != 605 or starts[0] != (1, 1) or starts[-1] != (115, 1):
        raise SystemExit("Unexpected QuranData.Page array")

    order = sorted(verses)
    index = {key: i for i, key in enumerate(order)}
    pages = []
    for p in range(604):
        first = index[starts[p]]
        last = index[starts[p + 1]] if starts[p + 1] != (115, 1) else len(order)
        pages.append(sum(count(verses[k]) for k in order[first:last]))
    if any(n <= 0 for n in pages):
        raise SystemExit("A page has no letters")

    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Written letters per page of the 604-page Madinah mushaf, one line per page.\n")
        f.write(f"# Method version {METHOD_VERSION} (docs/worship/QURAN_LETTERS.md), pending qualified review.\n")
        f.write(f"# Derived from {version}, Copyright (C) Tanzil Project, https://tanzil.net,\n")
        f.write("# License: Creative Commons Attribution 3.0. No Quran text is included here, only counts.\n")
        f.write(f"# Source SHA-256: {digest}; basmalah removed from {stripped} surah openings; total {sum(pages)} letters.\n")
        for n in pages:
            f.write(f"{n}\n")
    print(f"{len(pages)} pages, {sum(pages)} letters, basmalah removed from {stripped} openings, source {digest[:16]}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
