# Evidence catalog and approval status

Source of truth: `core/src/main/kotlin/org/najwa/core/WorshipCatalog.kt`. Display wording: `virtue_*` strings in `scripts/gen_release_17_strings.py`.

**Approval status: no rule is approved.** Every rule is `UNREVIEWED`. It was prepared by an AI assistant from the cited collections and has not been checked by a qualified scholar. Release builds count only `APPROVED` rules (`RulePolicy.RELEASE`). Until a review approves rules, a release build shows:

- no hasanat number;
- no expiation count;
- no virtue list.

It shows only plain activity: prayers, pages, and completed adhkar. Debug and trial builds have a "preview" switch (`RulePolicy.REVIEW`) that marks everything "not approved". It lets a reviewer see the behaviour before deciding.

## Rules

Model fields:

- **Required** is the repetitions in one sitting. 99 of a required 100 never qualifies.
- **Window**:
  - `DAY` rules count at most once a day.
  - `MORNING`, `EVENING` and `BEFORE_SLEEP` rules count at most once.
  - `AFTER_PRAYER` rules count up to five times.
- **Relation `ANY`** means the text says "or", so no number is added.

| Id | Action | Required | Window | Outcomes | Evidence |
| :--- | :--- | ---: | :--- | :--- | :--- |
| `quran.letters` | each confirmed Quran letter | 1 | any | HASANAT 10 per letter | الترمذي 2910 |
| `prayer.five` | each prayer marked prayed | 1 | any | EXPIATION (condition: major sins avoided) | مسلم 233 |
| `prayer.jamaah` | prayer marked "in congregation" | 1 | any | VIRTUE (27 degrees, not hasanat) | البخاري 645، مسلم 650 |
| `dhikr.tasbih_bihamdihi.100` | سبحان الله وبحمده | 100 | day | EXPIATION (no number) | البخاري 6405، مسلم 2691 |
| `dhikr.tahlil.100` | full tahlil | 100 | day | HASANAT 100 + EXPIATION 100 (shown, never summed) + PROTECTION + VIRTUE (ALL) | البخاري 3293، مسلم 2691 |
| `dhikr.tahlil.10` | full tahlil | 10 | day | VIRTUE (four souls freed) | البخاري 6404، مسلم 2693 |
| `dhikr.tasbih.100` | سبحان الله | 100 | day | HASANAT 1000 **or** EXPIATION 1000 (ANY, never summed) | مسلم 2698 |
| `routine.after_prayer.100` | 33 + 33 + 33 and the closing tahlil | 1 | after prayer | EXPIATION | مسلم 597 |
| `dhikr.salawat` | blessings upon the Prophet ﷺ | 1 | any | VIRTUE (ten blessings, not hasanat) | مسلم 408 |
| `dhikr.kursi.sleep` | Ayat al-Kursi (sleep adhkar) | 1 | before sleep | PROTECTION | البخاري 2311 |
| `dhikr.muawwidhat.morning` / `.evening` | Al-Ikhlas, Al-Falaq and An-Nas | 3 | morning / evening | PROTECTION | أبو داود 5082، الترمذي 3575 |
| `dhikr.bismillah.morning` / `.evening` | بسم الله الذي لا يضر | 3 | morning / evening | PROTECTION | أبو داود 5088، الترمذي 3388 |
| `dhikr.kalimat.evening` | أعوذ بكلمات الله التامات | 1 | evening | PROTECTION | مسلم 2709 |
| `dhikr.sayyid_istighfar.morning` / `.evening` | Sayyid al-istighfar | 1 | morning / evening | VIRTUE | البخاري 6306 |

### Rejected while preparing the catalog

- **Ayat al-Kursi after each prayer.** I could not confirm the hadith number in al-Nasa'i's *al-Kubra*, so the rule was left out rather than risk an invented citation.
- **A universal ten-hasanat multiplier for every dhikr.** It is unsupported and was never modelled.
- **Converting expiation, protection, degrees or blessings into hasanat.** These kinds only list virtues and are never summed (`VirtueKind`).
- **Erased-sin totals.** The expiation indicator counts *recorded actions*, not sins. The "100 sins erased" of the tahlil narration appears only in the evidence wording.

## What a reviewer checks for each rule

1. The narration, its grading, and the exact numbers and conditions.
2. Whether the app's text and repetition count for the linked dhikr match the narration (see `WorshipRecorder.actionOf` and `AdhkarData.kt`).
3. Window and frequency. For example, does a hundred said twice in one day count once?
4. The Arabic summary and its translations (`virtue_*`).

To approve a rule, set `status = EvidenceStatus.APPROVED`. If the rule's meaning changes, increase `version` and record the reviewer and date in this file. Totals are always rebuilt from the stored records, so approving or correcting a rule changes what is shown without rewriting any stored record.

## Review log

| Rule | Reviewer | Date | Decision |
| :--- | :--- | :--- | :--- |
| (none yet) | | | |
