# Najwa visual, accessibility and localization review — 2026-11-15

Scope: 15 review areas in the Android working tree. This is one specialist-agent
review, not a claim of 15 independent human reviewers. The root agent owns build,
automated test execution and release approval. No Gradle tasks were run by this reviewer.

## Corrected findings

- **P2: untranslated contact feedback and dynamic labels.** `InformationScreen.kt`
  now uses localized Android resources for email/clipboard feedback, character count,
  version and privacy email labels. Validation uses the existing translated interface label.
- **P2: contact draft lost on configuration recreation.** Contact fields now use
  `rememberSaveable`. The root agent added removal of the CONTACT saved state on
  navigation away, preserving the requested clear-on-leave behavior.
- **P2: untranslated widget transparency accessibility and pin feedback.**
  `WidgetScreen.kt` now localizes its display value, TalkBack label/state and pin instructions.
- **P2: untranslated notification switch accessibility labels.**
  `NotificationScreen.kt` now resolves all three labels through `uiText` before
  using them in the semantics lambda.
- **P2: raw Arabic/platform audio errors in other app languages.**
  `AdhanSoundSettings.kt` now presents localized save/reset/picker/unavailable
  feedback. Imported filenames remain unchanged in a localized format string.
  Raw platform exception messages are not displayed. The UI uses the current
  localized context. Missing notification channels are no longer presented as a
  user-selected silent channel. File validation and platform error handling were not changed.
- **P3: small widget refresh touch targets.** Medium, large and slim layouts had
  refresh heights of 30, 42 and 36 dp. The refresh button now occupies a root-level
  48 x 48 dp target over the existing bottom area; the body retains its prior space.

Resources: `ui_feedback.xml` in values, values-en, values-in, values-ur,
values-bn, values-hi and values-tr; 23 matching keys per locale.

## Fifteen-area assessment

| Area | Evidence / outcome |
| --- | --- |
| 1. Brand palette and logo | Source and sampled existing previews consistently use the Madar palette and existing mark. |
| 2. Text contrast | Calculated opaque palette ratios: dark body 14.55:1, dark secondary 8.32:1; light body 12.96:1, secondary 4.94:1, accent 6.17:1, gold 6.34:1. These figures do not cover every translucent surface. |
| 3. Large-text home layout | Existing home-large-text preview inspected; no overlap observed in visible content. Not a physical-device test. |
| 4. Contact IME layout | Source hides bottom navigation while contact editing has an active IME. OEM keyboard behavior remains untested. |
| 5. Draft recreation | Corrected state persistence; root integration test required. |
| 6. Contact navigation | Clear-on-leave retained by the root saveable-state cleanup; not claimed as manually exercised here. |
| 7. Locale structure | Six translated interface arrays each contain 222 entries, matching the key array. Arabic is the source language. |
| 8. Dynamic feedback localization | Contact, widget and audio feedback corrected using typed format resources in seven locales. |
| 9. Accessibility labels | Transparency state and three notification switch descriptions localized. Actual TalkBack speech order remains untested. |
| 10. Widget sizing | Seven layouts and both resize axes present. Sampled previous widget-large-text preview is clear. All launcher resize paths require device testing. |
| 11. Widget touch targets | Three corrected XML targets independently parsed and checked as 48 x 48 dp. Post-change snapshots are owned by root. |
| 12. Expanded notification identity | Existing iqama-expanded preview inspected; coherent branding and readable text. System-decorated OEM notifications remain untested. |
| 13. RTL and countdown direction | Widget/notification layout direction derives from locale; numerical countdowns use independent LTR direction. Native Urdu/Indic typography review still needed. |
| 14. Phase color compatibility | API 26–30 progress bars retain the fixed drawable mint while iqama timer uses gold; see remaining P3 below. |
| 15. Translation and device acceptance | Resource consistency checked; native-speaker review, real launcher/OEM checks and full TalkBack acceptance are not complete. |

## Remaining low-severity finding

**P3 — phase tint on Android 8–11:** `PrayerWidget.kt` tints the progress bar by
phase only inside the API >= 31 branch (around lines 98–100). On older supported
versions the progress drawable stays mint while the iqama counter becomes gold.
This is a visual consistency issue, not evidence of scheduling failure. It was
outside the assigned edit scope.

## Validation boundary

XML parsing, key equality and typed format-placeholder matching passed locally.
The three refresh targets passed structural size checks. Palette contrast was
calculated from source colors. Images inspected were existing generated previews,
not new device captures and not proof of post-edit rendering. Build, regression
tests and refreshed snapshots must be reported from the root agent's actual runs.
No claim of complete bug absence or 100% release readiness is made here.
