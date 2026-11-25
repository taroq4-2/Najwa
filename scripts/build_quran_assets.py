#!/usr/bin/env python3
"""Bundle the mushaf reader's data in app/src/main/assets/quran/.

    python -I scripts/build_quran_assets.py quran-uthmani.txt quran-data.js app/src/main/assets/quran

quran-uthmani.txt is copied byte for byte, copyright block included (Tanzil's terms forbid changing the text).
index.txt is derived from quran-data.js: surahs (S), the 604 page starts (P) and the 30 juz starts (J).
"""
import ast
import hashlib
import os
import re
import shutil
import sys


def array(js, name):
    block = js[js.index(f"QuranData.{name} = ["):]
    block = block[block.index("[") + 1:block.index("];")]
    rows = []
    for line in block.splitlines():
        line = line.split("//")[0].strip().rstrip(",").strip()
        for item in re.findall(r"\[[^\[\]]*\]", line):
            value = ast.literal_eval(item)
            if value:
                rows.append(value)
    return rows


def main(text_path, data_path, out_dir):
    raw = open(text_path, "rb").read()
    text = raw.decode("utf-8")
    if "Tanzil Quran Text" not in text or "PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK" not in text:
        raise SystemExit("Tanzil copyright block missing")
    verses = [l for l in text.splitlines() if l and not l.startswith("#")]
    if len(verses) != 6236:
        raise SystemExit("Expected 6236 verses")
    os.makedirs(out_dir, exist_ok=True)
    shutil.copyfile(text_path, os.path.join(out_dir, "quran-uthmani.txt"))

    js = open(data_path, encoding="utf-8").read()
    suras = array(js, "Sura")
    pages = array(js, "Page")
    juz = array(js, "Juz")
    if len(suras) != 115 or len(pages) != 605 or len(juz) != 31:
        raise SystemExit(f"Unexpected metadata sizes {len(suras)} {len(pages)} {len(juz)}")
    lines = [
        "# Mushaf index for Najwa's reader: S|number|first verse index|verses|name|transliteration|type,",
        "# P|page|sura|aya (604 page starts) and J|juz|sura|aya (30 juz starts).",
        "# Derived from Tanzil Quran Metadata, Copyright (C) 2008-2009 Tanzil.info, Creative Commons Attribution 3.0.",
        f"# Text: quran-uthmani.txt (unchanged), SHA-256 {hashlib.sha256(raw).hexdigest()}.",
    ]
    for n, s in enumerate(suras[:114], start=1):
        start, ayas, _order, _rukus, name, tname, _ename, kind = s
        lines.append(f"S|{n}|{start}|{ayas}|{name}|{tname}|{kind}")
    for n, (sura, aya) in enumerate(pages[:604], start=1):
        lines.append(f"P|{n}|{sura}|{aya}")
    for n, (sura, aya) in enumerate(juz[:30], start=1):
        lines.append(f"J|{n}|{sura}|{aya}")
    with open(os.path.join(out_dir, "index.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("114 surahs, 604 pages, 30 juz; text", len(raw), "bytes")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
