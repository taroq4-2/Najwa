# Worship companion — architecture and integration

## Layers

| Layer | File | Responsibility |
| :--- | :--- | :--- |
| Domain (pure Kotlin) | `core/.../Worship.kt` | Evidence model, `WorshipEngine.evaluate`, goals, routine state |
| Domain | `core/.../WorshipCatalog.kt` | Rule catalog (all pending review), Quran letter method |
| Storage | `app/.../platform/WorshipStore.kt` | `WorshipSettingsStore`, `WorshipStore` (records), `WorshipRecorder` (maps existing screens to records) |
| Summary | `app/.../platform/WorshipSummary.kt` | Builds a `DaySummary` from the prayer log, adhkar progress, Quran store and records; `QuranPageLetters` |
| Widget | `app/.../platform/DayWidget.kt`, `res/layout/day_widget.xml`, `drawable/day_strip_background*.xml` | Optional "My day" widget |
| UI | `app/.../ui/WorshipUi.kt` | Luminous path, home card, evidence dialog, settings |

## Boundaries

The engine never computes prayer times, schedules alarms, posts notifications or touches silence. It reads records and returns a value.

Prayers are **not copied**. `WorshipSummary.prayerRecords` derives them from the existing prayer log each time, so marking, correcting or clearing a prayer is reflected everywhere at once.

Adhkar progress stays in the existing `AdhkarProgressStore`. `WorshipRecorder.onAdhkar` recomputes a category's completion records from its current state every time the set of finished items changes:

- a completion adds a record;
- an undo removes it;
- a repeated delivery changes nothing.

Record ids are deterministic within a session, and restarting a category starts a new session.

Tasbeeh rounds and Quran ranges get fresh ids from a counter, so an intentional repeat is a new record. A duplicate write of the same id replaces it rather than adding a second one.

## Changes to existing files

All are minimal and kept apart from prayer timing.

| File | Change |
| :--- | :--- |
| `PrayerWidget.updateAll` | Also refreshes `DayWidget`; keeps the refresh alarm alive when only a My day widget exists. No change to layouts, timing or rendering. |
| `NajwaApplication.setPrayed/setStatus` | Refreshes `DayWidget` after a log change. |
| `DevotionStores` | Tasbeeh round → record; Quran addPages → range record; new `QuranStore.undoLast`; day count never negative. |
| `DevotionScreens` | Adhkar screen gains the luminous path, undo, restart confirmation, focus mode and category status. These appear only with the companion on; otherwise the 1.6 screen renders. The Quran screen gains undo and a letters line. |
| `CompanionScreens` | Prayer log offers post-prayer adhkar after a prayer is marked today. |
| `HomeScreen` | Optional "My day" card above the tools. |
| `SettingsHub` | Worship section gains the companion settings; what's new lists 1.7. |
| `NajwaApp`, `MainActivity`, `PrayerNotifications` | New `ACTION_QURAN` for the widget; navigation to after-prayer adhkar. |
| `AdhkarData` | Exposes the texts the rules refer to; no text changed. |
| `AndroidManifest` | One non-exported widget receiver. No permissions. |

## Data

| Store | Kind | Content | Lifetime |
| :--- | :--- | :--- | :--- |
| `worship` | SharedPreferences | `d_<date>` → `id,action,count,window,from;…`, session counters, id sequence | 60 days, pruned on write |
| `worship_settings` | SharedPreferences | switches, goal kind and target | until changed |

Integrity:

- **Durability.** Writes use `commit()`: a record is on disk before the widget is refreshed.
- **Arithmetic.** Counts are `Long`; sums saturate instead of overflowing; goal progress uses integer thousandths. No floating point is used for quantities.
- **Day attribution.** A record belongs to the phone's local date on which it was confirmed, like the existing adhkar, tasbeeh and Quran stores. Prayers come from the prayer log by the city's date, as before. A later timezone change does not move stored records, because the date is the storage key. Evidence windows (morning, evening, after prayer) come from the adhkar category the person chose, not from the clock.
- **Rule changes.** Rules carry a version. Totals are rebuilt on every read, so approving or correcting a rule changes what is shown without rewriting any record.

## Privacy

- **No new permissions, dependencies or network.** The merged release manifest has the same 12 permissions as the baseline.
- **Not backed up.** `allowBackup="false"` is unchanged.
- **Never logged or shared.** Records never leave the app's private storage, are not logged and are not shared.
- **Separate deletion.** *Delete worship companion records* clears only the `worship` store. The prayer log, adhkar progress and settings stay.
- **Widget opt-in.** The widget strip appears only on the new My day widget, which a person must add deliberately. Each of its three cells can be hidden.
- **Encryption.** App-private storage is protected by the platform sandbox, not by extra encryption.
