#!/usr/bin/env python3
"""Bundle the King Fahd Complex (KFGQPC) Hafs text for the mushaf reader, and recount letters per page.

    python -I scripts/build_kfgqpc_assets.py "KFGQPC Hafs V30.docx" app/src/main/assets/quran

Input: the certified Word document shipped inside hafs-3.0.zip from https://qurancomplex.gov.sa/fonts/hafs
(SHA-256 of the zip: DB6329E457B912E012160EC1EAA5CF15BD2116F47EA0F466A6C8951EA5F95C29).
Outputs, in the assets folder:
  quran-hafs.txt     "sura|aya|text": the Complex's verse text, unchanged except that each verse's end mark
                     (no-break space, U+06DD and its number) is split off into the aya field.
  page_letters.txt   written letters per page (method 1, see core QuranLetters), from that text.
The page starts come from index.txt (Madinah mushaf pages). Every surah's verse count is checked against it.
"""
import hashlib
import html
import os
import re
import sys
import zipfile

METHOD_VERSION = 1
BASMALAH = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
ARABIC_DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")


def is_letter(c):
    o = ord(c)
    return 0x0621 <= o <= 0x063A or 0x0641 <= o <= 0x064A or o in (0x0671, 0x0654)


def bare(text):
    """Letters and spaces only (mark order differs between copies of the basmalah)."""
    return " ".join("".join(c for c in w if is_letter(c)) for w in text.replace(" ", " ").split())


def paragraphs(docx):
    xml = zipfile.ZipFile(docx).read("word/document.xml").decode("utf-8")
    out = []
    for p in re.findall(r"<w:p[ >].*?</w:p>", xml, flags=re.S):
        t = html.unescape("".join(re.findall(r"<w:t[^>]*>(.*?)</w:t>", p, flags=re.S))).strip()
        if t:
            out.append(t)
    return out


def main(docx, out_dir):
    index = [l.split("|") for l in open(os.path.join(out_dir, "index.txt"), encoding="utf-8") if l[:2] in ("S|", "P|")]
    counts = [int(r[3]) for r in index if r[0] == "S"]
    pages = [(int(r[2]), int(r[3].strip())) for r in index if r[0] == "P"]
    if len(counts) != 114 or len(pages) != 604:
        raise SystemExit("index.txt incomplete")

    surahs, current = [], None
    for p in paragraphs(docx):
        if p.startswith("سُورَةُ"):
            current = []
            surahs.append(current)
        elif current is None:
            raise SystemExit("text before the first surah title")
        elif bare(p) == bare(BASMALAH):
            continue  # the basmalah heading (not a verse); Al-Fatiha's first verse carries its own end mark
        else:
            current.append(p)
    if len(surahs) != 114:
        raise SystemExit(f"expected 114 surahs, found {len(surahs)}")

    verses = []
    for s, parts in enumerate(surahs, start=1):
        text = " ".join(parts).replace(" ", " ")
        # Where the document joins the basmalah heading to verse 1 (all surahs but Al-Fatiha and At-Tawbah), it is
        # a heading, not part of the verse; An-Naml 27:30 keeps it, being inside a verse.
        words = text.split(" ")
        if s not in (1, 9) and bare(" ".join(words[:4])) == bare(BASMALAH):
            text = " ".join(words[4:]).lstrip()
        pieces = re.split(r"\s*۝([٠-٩]+)", text)
        found = []
        for i in range(0, len(pieces) - 1, 2):
            body, number = pieces[i].strip(), int(pieces[i + 1].translate(ARABIC_DIGITS))
            found.append((number, body))
        if pieces[-1].strip():
            raise SystemExit(f"surah {s}: text after the last verse mark")
        if [n for n, _ in found] != list(range(1, counts[s - 1] + 1)):
            raise SystemExit(f"surah {s}: verse numbers do not match the Madinah count {counts[s - 1]}")
        verses += [(s, n, body) for n, body in found]
    if len(verses) != 6236:
        raise SystemExit(f"expected 6236 verses, found {len(verses)}")

    src = open(docx, "rb").read()
    with open(os.path.join(out_dir, "quran-hafs.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Quran text of the King Fahd Glorious Quran Printing Complex (KFGQPC), Hafs, from the certified document\n")
        f.write("# in hafs-3.0.zip (https://qurancomplex.gov.sa/fonts/hafs), used with the Complex's permission held by the developer.\n")
        f.write(f"# Text unchanged; each verse's end mark is held in the aya field. Source document SHA-256 {hashlib.sha256(src).hexdigest()}.\n")
        for s, n, body in verses:
            f.write(f"{s}|{n}|{body}\n")

    # Letters per page: verse index of each page start, then sum to the next page.
    starts, idx = [], {}
    for i, (s, n, _) in enumerate(verses):
        idx[(s, n)] = i
    starts = [idx[p] for p in pages] + [len(verses)]
    per_page = [sum(sum(1 for c in verses[v][2] if is_letter(c)) for v in range(starts[k], starts[k + 1])) for k in range(604)]
    with open(os.path.join(out_dir, "page_letters.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# Written letters per page of the Madinah mushaf, method {METHOD_VERSION}, counted from quran-hafs.txt (KFGQPC).\n")
        f.write("\n".join(str(n) for n in per_page) + "\n")
    print(f"6236 verses, 604 pages, {sum(per_page)} letters")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
