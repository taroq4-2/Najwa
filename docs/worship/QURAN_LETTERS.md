# Quran letter counting — method version 1 (pending qualified review)

Implementation: `QuranLetters` in `core/.../WorshipCatalog.kt` and `scripts/build_quran_page_letters.py` (same rule). Tests: `WorshipTest.quranLetterMethod`, `WorshipPlatformTest.pageLetterTable…`.

## Rule

The method counts the letters **written** in the Uthmani text.

| Element | Counted | Reason |
| :--- | :--- | :--- |
| Arabic letters U+0621–U+063A, U+0641–U+064A | yes, one each | written letters |
| Hamza forms أ إ آ ؤ ئ ء, and a hamza written above a tatweel (U+0654, as in يَسْـَٔلُونَ) | yes, one each | each is one written letter |
| Alif wasla ٱ (U+0671) | yes, as alif | written letter |
| Shadda | no extra letter | the doubled letter is written once |
| Harakat, tanwin, sukun, madda sign | no | marks, not letters |
| Dagger alif ٰ (U+0670) and small waw/yaa (U+06E5/6) | no | superscript, not written as letters |
| Tatweel, pause and annotation marks (U+06D6–U+06ED), verse ornaments ﴿ ﴾, verse numbers, spaces | no | not letters |
| Disjoined letters (الٓمٓ) | as written (3) | an alternative counts the pronounced names; **reviewer decision** |
| Basmalah | only where it is a verse (Al-Fatiha) | the page table must not add it to other surahs; **reviewer decision** for At-Tawbah and An-Naml 30 (counted as text of that verse) |

These fixtures from text already in the app agree with commonly cited counts:

- Basmalah: 19 letters.
- Al-Ikhlas: 47 letters.

## Page table (built)

`app/src/main/assets/quran/page_letters.txt` holds 604 counts, one per page of the Madinah mushaf. It is about 3 KB and contains no Quran text.

Build:

```sh
python -I scripts/build_quran_page_letters.py quran-uthmani.txt quran-data.js app/src/main/assets/quran/page_letters.txt
```

Sources:

- **Text:** Tanzil Quran Text, Uthmani, version 1.1 (CC BY 3.0).
  - Used in `txt-2` format, with pause marks, sajdah and rub-el-hizb signs.
  - SHA-256 `4b91f9e6e8ac645d039e4ed85b3be492e795232a31cd22d668ac58238722e26f`, 6,236 verses.
- **Pages:** the `QuranData.Page` array of Tanzil's `quran-data.js`, which lists 604 page starts and an end marker.

Basmalah handling:

- Tanzil writes the basmalah before verse 1 of 112 surahs. The builder checks that each of those verses starts with it, removes it there, and counts it only where it is a verse (1:1) or part of a verse (27:30).

Cross-checks (pinned in `WorshipPlatformTest`):

- **Page 1 (Al-Fatiha with its basmalah verse): 139 letters**, the commonly cited count.
- **Page 604:** Al-Ikhlas 47, Al-Falaq 71 and An-Nas 80, a total of 198.
  - Al-Ikhlas and An-Nas match the app's own adhkar texts letter for letter.
  - Al-Falaq differs by two. The adhkar text writes «النَّفَّاثَاتِ» in standard spelling, while the Uthmani text writes «ٱلنَّفَّٰثَٰتِ» with two superscript alifs, which are not written letters. The table follows the Uthmani spelling, as the method requires.
- **Whole mushaf: 326,159 letters.** Published totals differ by method (roughly 320,000 to 330,000), so this figure cannot confirm the method on its own.

The loader accepts only a complete table: 604 positive rows. Reading ranges recorded earlier gain letter counts automatically.

### Reviewer decisions still open

1. **Disjoined letters.** Count them as written (current) or by their pronounced names.
2. **Hamza written above a tatweel.** Currently counted as one letter.
3. **Superscript alif.** The dagger alif is currently not counted.
4. **Which text and edition is authoritative** for the count.

## Reading confirmation

- **Only these actions count:** the +1, +2, +5, +10, "whole juz" and "I reached page" actions. Opening the screen never counts.
- **Each confirmation is its own record** with its own id. A second reading of the same pages is intentional and counts again.
- **Undo** takes back the last confirmation of the day. It restores the page, the day's page count and the record.
