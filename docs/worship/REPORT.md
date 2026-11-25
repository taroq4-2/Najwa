# Najwa 1.7.0 (118) — worship companion: delivery report

Branch `worship-engine`, based on `92abace` (1.6.0). Companion documents:

- [Baseline](BASELINE.md)
- [Architecture](ARCHITECTURE.md)
- [Evidence catalog](EVIDENCE.md)
- [Quran letter method](QURAN_LETTERS.md)

## How the review was done

The requested structure was 100 development experts, 50 critics and 10 auditors. There was **one AI assistant** working in this environment, with no independent agents or human reviewers. Those roles were used as checklists: architecture, religious content, widgets, data, performance, privacy, accessibility and regression were each reviewed in turn against the real code and the emulator. No review results, scholarly approvals or benchmarks have been invented.

## What was delivered

| Requirement | Status |
| :--- | :--- |
| Evidence model and engine (statuses, AND/OR outcomes, thresholds, windows, idempotency, overflow) | **Implemented and verified**: 15 core tests |
| Religious numeric rewards in release builds | **Pending qualified religious review**. Every rule is unreviewed, so release builds show no number (gate respected). |
| Prayer integration (read from the existing log, post-prayer adhkar link, widget refresh on marking) | **Implemented and verified**: tests + emulator |
| Luminous dhikr path, undo, restart confirmation, focus mode, category status | **Implemented and verified on emulator** (Arabic RTL) |
| Exact counting under long-press, multi-touch, scrolling and rotation | **Implemented but not verified by instrumented test**. Relies on Compose `clickable`: one click per release, cancelled by scroll, single pointer. Counts persist on every tap. |
| Reduced motion | **Implemented but not verified**: path animation snaps when system animations are off |
| Protection and expiation from the same records (no duplicates, morning/evening separate) | **Implemented and verified** (tests); shown only in preview until approved |
| Personal daily goal (pages, adhkar, or hasanat when approved) | **Implemented and verified** (tests + settings on emulator) |
| Daily summary on home | **Implemented and verified on emulator** |
| Optional "My day" widget preserving the large prayer block | **Implemented and verified on emulator** (preview and release states) |
| Quran letter counting method | **Implemented and verified on fixtures** (basmalah 19, Al-Ikhlas 47, hamza on tatweel); **pending qualified review** of the method choices |
| Quran per-page letter table | **Implemented and verified**: built from Tanzil Uthmani 1.1 with the owner's permission. Page 1 = 139, page 604 = 198 (Al-Ikhlas and An-Nas match the app's texts). |
| Feature switch restoring the 1.6 experience, with data kept | **Implemented and verified on emulator** |
| Separate deletion of companion records | **Implemented and verified** (test) |
| Localization (7 languages) | **Implemented**. Arabic and English seen on the emulator; other languages not reviewed by native speakers. |

## Measurements (same build type before and after)

| Item | Baseline 1.6.0 | 1.7.0 | Change |
| :--- | ---: | ---: | ---: |
| Release APK, unsigned, R8 | 19,283,591 B | 19,429,910 B | +146,319 B (+143 KiB; budget 1 MiB) |
| Merged release permissions | 12 | 12 | none added or removed |
| Runtime dependencies | 56 | 56 | none added |
| Core tests | 119 | 134 | +15, all pass |
| App tests | 119 | 133 | +14, all pass |
| Release lint errors | 0 | 0 | — |
| Privacy audit | PASS | PASS | — |

Not measured (**unverified**):

- cold and warm start;
- memory, CPU and battery;
- frame timing of the path.

No background service, alarm or polling was added for the companion. The widget strip refreshes only on record changes and the existing widget refresh.

## Findings register

| Id | Role | Severity | Finding | Fix / decision | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| F01 | C021 religious evidence | P0 | An al-Nasa'i *al-Kubra* hadith number for Ayat al-Kursi after prayer could not be confirmed | Rule removed; no uncertain citation ships | Fixed |
| F02 | C021 | P0 | Unreviewed rules could produce reward numbers | Release policy counts only APPROVED rules; preview limited to debug/trial builds and labelled | Fixed, tested |
| F03 | C022 calculation | P1 | "1000 hasanat **or** 1000 sins" must not be summed | `OutcomeRelation.ANY` never adds to the number | Fixed, tested |
| F04 | C027 widgets | P1 | Legacy refresh alarm cancelled when only the My day widget exists | `updateAll` counts it | Fixed |
| F05 | C028 widgets | P1 | Strip stretched to half the widget (background with intrinsic size) | Size-free shape background | Fixed, seen on emulator |
| F06 | C042 data | P2 | Undo could drive the Quran day count below zero | Clamped at zero | Fixed |
| F07 | C043 data | P2 | Widget read records by city date while stores use device date | Prayers by city date, other records by device date | Fixed |
| F08 | C012 UX | P2 | Focus mode still showed progress and streak | Hidden in focus | Fixed, seen on emulator |
| F09 | C017 wording | P3 | «بقي ١٣ أذكار», «١ أعمال» and English "1 actions" are ungrammatical | Label: number phrasing | Fixed |
| F10 | C033 build | P2 | Lint error on a literal "100%" string | Reworded | Fixed |
| F11 | C024 religious | P1 | Virtue summaries in 7 languages are AI-written paraphrases | Shown only with their rule (preview or approved) | **Open: needs review** |
| F12 | C025 religious | P1 | Letter method choices (disjoined letters, basmalah, dagger alif) | Documented; no table ships yet | **Open: needs review** |
| F13 | C046 regression | P2 | No automated pixel comparison of the legacy widgets | Code path unchanged (`PrayerWidget.views` reused); checked visually | **Open** |
| F14 | C013 accessibility | P2 | TalkBack and large-font passes not done on device | The path has a content description | **Open** |
| F15 | C031 performance | P2 | Runtime performance not measured | — | **Open** |
| F16 | C044 data | P3 | The tasbeeh after-prayer sequence (33·33·34) is not mapped to a rule | Different narration; left unmapped on purpose | Accepted |
| F17 | C018 wording | P3 | English plurals in a few strings use "label: N" instead of plural resources | Acceptable | Accepted |
| F18 | C054 Quran text | P2 | The adhkar Al-Falaq text writes «النَّفَّاثَاتِ» in standard spelling, although `AdhkarData.kt` says it follows the Uthmani spelling | Dhikr text not changed (protected content); reported for the content reviewer | **Open: needs review** |
| F19 | C052 data | P2 | A hamza written above a tatweel (U+0654) was not counted as a letter | Counted, with a test | Fixed |

## Release gates

| Gate | Result |
| :--- | :--- |
| No unauthorized change to legacy functionality | Pass: prayer timing, alerts and silence untouched; widget layouts unchanged. Two one-line additions in `PrayerWidget.updateAll` are documented. |
| No unapproved religious numerical claims in release | Pass |
| No new permissions, dependencies or network | Pass (merged manifest compared) |
| No tracking or advertising | Pass |
| No unresolved P0/P1 defects | **Not met for numeric and virtue features**: F11 and F12 need a qualified reviewer. Activity features (path, summary, goal by pages or adhkar, widget) have no open P0/P1. |
| No fabricated results | Pass |

## Scorecard (evidence-based; unverified parts not credited)

| Area | Max | Score | Basis |
| :--- | ---: | ---: | :--- |
| Preservation of existing features | 20 | 17 | All 238 old tests pass; upgrade over 1.6 data on emulator; no pixel-diff suite |
| Religious evidence and calculation integrity | 15 | 11 | Gating, AND/OR and thresholds tested; no rule approved yet |
| Innovation and integration | 15 | 13 | One record source feeds path, summary, goal and widget; per-page letters from a verified text |
| UX and accessibility | 10 | 7 | RTL checked; TalkBack and large fonts not checked on device |
| Visual design and identity | 10 | 8 | Existing tokens, glyphs and backgrounds |
| Performance and APK | 10 | 7 | +143 KiB measured; runtime not measured |
| Security and privacy | 10 | 9 | Audit pass, no new permissions or network, separate deletion |
| Widget compatibility | 5 | 4 | Legacy block reused as is; one launcher tested |
| Testing and documentation | 5 | 4 | 29 new tests; no UI instrumentation |
| **Total** | 100 | **80** | Target 95 not reached; the gaps are listed above |

## Verdict

**Experimental build: ready for the owner's device test and a scholar's review. Not production-ready for reward or virtue features.**

The activity features could ship once tested on the owner's phone: the path, focus mode, undo, the summary, page and adhkar goals, and the widget. Hasanat, expiation and protection indicators turn on only after rules are approved in `WorshipCatalog`.

## Screenshots

`screens/`:

- `home17.jpg`: home card.
- `adh_t2.jpg`: adhkar with folded item and undo.
- `focus17.jpg`: focus mode.
- `prev17.jpg`: preview card and evidence.
- `set17.jpg`: settings.
- `day-widget-preview.jpg` and `day-widget-release.jpg`: the widget in both states.
