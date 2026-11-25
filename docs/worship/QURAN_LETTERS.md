# Quran letter counting — method version 1 (pending qualified review)

Implementation: `QuranLetters` in `core/.../WorshipCatalog.kt`. Tests: `WorshipTest.quranLetterMethod`.

## Rule

The method counts the letters **written** in the Uthmani text.

| Element | Counted | Reason |
| :--- | :--- | :--- |
| Arabic letters U+0621–U+063A, U+0641–U+064A | yes, one each | written letters |
| Hamza forms أ إ آ ؤ ئ ء | yes, one each | each is one written letter |
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

## Current status: the page table is missing (blocked)

Najwa ships no Quran text. Its Quran tracker records pages of the 604-page Madinah mushaf. Letters are therefore counted per page from a table, `app/src/main/assets/quran/page_letters.txt`. The table has 604 positive integers, one per line, with comment lines starting with `#` that name the source text, its checksum and the method version.

- **The table is not included yet.** Building it needs a verified Uthmani text and its page boundaries, for example the Tanzil Uthmani text and `quran-data.js`, both used under Tanzil's terms with attribution. Downloading them needs the owner's permission.
- **Only the counts would ship.** About 3 KB; no Quran text is added to the APK.
- **Until the table exists**, `QuranPageLetters.table()` returns null. The app then shows pages only and computes no letter-based estimate. This is a documented degraded mode, not an error.
- **The loader accepts only a complete table.** 604 positive rows; anything else is ignored.
- **Reading ranges are already recorded.** When a table is added later, past readings gain letter counts without rewriting any record.

## Reading confirmation

- **Only these actions count:** the +1, +2, +5, +10, "whole juz" and "I reached page" actions. Opening the screen never counts.
- **Each confirmation is its own record** with its own id. A second reading of the same pages is intentional and counts again.
- **Undo** takes back the last confirmation of the day. It restores the page, the day's page count and the record.
