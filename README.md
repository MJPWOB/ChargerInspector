# Charger Inspector

Offline Android app (Kotlin, Jetpack Compose) for inspecting a fleet of EV chargers (default
CH01 to CH52). Each charger gets a Pass / Note / Fail answer per component, with comments and
photos, and the app produces a PDF report plus a CSV for your maintenance system.

Current version: **1.1.0**

## Status: read this first

| Part | Verified how |
|---|---|
| Domain logic (status rules, validation, mark-all-pass, labels, CSV export, summaries) | Compiled with Kotlin 2.0.21 and **59 JUnit tests pass** |
| Version 1.0 as a whole (Room, Compose UI, PDF, camera, navigation) | Compiled, unit-tested and built into an APK on GitHub Actions |
| **Version 1.1 changes to the Android-dependent code** (Note and Misc UI, bigger buttons, PDF notes section, icon) | **Not compiled yet.** Push to GitHub and check the Actions run |

Version 1.1 was written without the Android SDK. The first CI run after you push is the real
compile check; paste any errors back and they are usually quick fixes. Nothing has been run on a
phone yet, so work through the test checklist, especially photos and the PDF.

## Build

**GitHub:** push the project root (the folder containing `app/`, `gradle/` and `.github/`).
`.github/workflows/android.yml` runs the tests, builds the debug APK, checks its permissions and
uploads it as the `charger-inspector-debug-apk` artifact.

**Android Studio:** open this folder, let Gradle sync (JDK 17 is bundled), then Run. Or, with the
Android SDK installed: `./gradlew testDebugUnitTest assembleDebug`

Before distributing, change `applicationId`/`namespace` in `app/build.gradle.kts` from the
`com.example.evinspector` placeholder, and set up release signing.

## How it works

- **Start screen:** mandatory inspector name/ID, plus the charger label prefix and count
  (configurable and remembered). The inspector field is deliberately not pre-filled, since devices get shared.
- **Dashboard:** scrollable tile grid with five colours, each also labelled in text:

  | Colour | Meaning |
  |---|---|
  | Grey | Not checked |
  | Yellow | Partial: started or edited but not saved with Save & Return |
  | Green | Saved, all clear |
  | Blue | Saved, no faults, but has notes or comments |
  | Red | Saved, at least one fault |

  The footer button reads "Generate Reports • 34/52 Inspected" (green, blue and red all count as inspected).
- **Inspection form:**
  - **Mark all as pass** at the top fills in every component you have not answered yet. Fails and
    notes are kept, and Misc is never touched.
  - Seven required components (Cable 1/2, Pistol 1/2, Holster 1/2, Screen), each with large
    colour-coded buttons: green **Pass**, blue **Note**, red **Fail**.
  - **Fail** needs a damage description (photos optional, up to 3). Turns the charger red.
  - **Note** is for things worth recording that are not faults. It needs a comment or at least one
    photo (up to 3), and turns the charger blue, never red.
  - **Misc** is an optional eighth item for anything else. It only has Note and Fail buttons;
    tapping the selected one again clears it. Leave it blank if there is nothing to report.
  - **Save & Return** stays disabled until all seven components are answered, every Fail is
    described, and every Note has a comment or photo. It says what is missing.
- **Reports:** PDF (summary, colour overview, failure details, notes and comments, both with
  photos, plus a list of chargers not fully inspected) and CSV. Generating with unfinished
  chargers shows a warning first. Both are shared through the Android share sheet and kept on the
  device under **Previous reports**, where you can view, re-share or delete them.
- **Finish session:** frees storage by deleting raw photos (the PDFs keep their embedded copies)
  and starts you fresh. If no report was generated, finishing deletes the session's data after a warning.

### Status rules

- Nothing answered: grey.
- Anything answered but not saved with Save & Return: yellow. This includes a fully answered form
  that was never saved, so green, blue and red always mean "the inspector confirmed this".
- Saved with all seven named components answered: red if anything (Misc included) failed;
  otherwise blue if anything has a note; otherwise green. Red outranks blue.
- Editing a saved charger turns it yellow again until it is re-saved.
- Comments and photos only survive on components that end as Fail or Note. If you flip one to
  Pass, or clear Misc, they are discarded when you save.

### Crash and power-loss recovery

Every button press is written to the database immediately and comment text within 400 ms, inside
transactions. Pending edits are also flushed when the app is backgrounded or the screen closes. On
launch, an unfinished session is resumed automatically. The path of a photo being captured is saved
in instance state, so a photo taken while Android killed the app in the background is not lost.

### CSV format

One row per charger per component, eight components per charger (52 chargers = 416 rows plus a
header), UTF-8, CRLF line endings:

`session_id, inspector_id, charger, charger_status, inspected_at, component, result, description, photo_count`

- `charger_status` is `UNCHECKED`, `PARTIAL`, `CLEAR`, `NOTED` or `ISSUES`.
- `result` is `PASS`, `NOTE`, `FAIL`, `NOT_CHECKED`, or `N/A` (Misc with nothing reported).
- `description` and `photo_count` are filled for `NOTE` and `FAIL` rows only.
- `inspected_at` is ISO 8601 with the device's UTC offset, blank if the charger was never saved.
- Chargers never inspected are still included so the file is always complete.
- Text starting with `= + - @` gets a leading apostrophe so spreadsheets cannot run it as a formula.

## Decisions that differ from, or go beyond, the brief

1. **Plain Room instead of SQLCipher.** The database lives in app-private storage, which Android
   encrypts with the device (file-based encryption), and backup/transfer are disabled in the manifest.
   SQLCipher would add a native dependency and a key-management problem, while photos would still sit
   unencrypted next to an encrypted database. If your policy demands an encrypted database, the swap is
   small: add `net.zetetic:sqlcipher-android` and pass a `SupportOpenHelperFactory` to
   `Room.databaseBuilder` in `InspectorApp.kt`, with the passphrase wrapped by the Android Keystore.
   Encrypt the photos too, or the benefit is partial.
2. **No CAMERA permission.** Photos use the system camera app via `ACTION_IMAGE_CAPTURE`, which needs no
   permission. This keeps the manifest free of `<uses-permission>` entirely (CI enforces it).
3. **Share sheet caveat.** "100% offline" holds for the app itself, but the share sheet can send reports to
   any app on the device, including ones with network access. If that matters, restrict which apps are
   installed or allowed via MDM.
4. **minSdk 29 (Android 10).** Avoids legacy storage and desugaring code. Lower it if you have older tablets.
5. **Scoped storage:** reports stay in app-private storage and leave through the share sheet using
   FileProvider URIs, so no storage permission is needed.
6. **History and multiple sessions:** reports persist until you delete them.
7. **Photos are shrunk on capture** to 1280 px / JPEG 75 and capped at 3 per component, which keeps the PDF and
   disk usage reasonable for 52 chargers.
8. **Portrait lock:** set in the manifest. As I understand it, Android 16 ignores orientation locks on large
   screens for apps that target API 36 or higher; this app targets 35. The grid is adaptive, so it still works if that changes.
9. **Note is a third state, not a flag on Pass.** A component is Pass, Note or Fail, so a charger's colour
   always follows directly from what was tapped.
10. **Misc is optional and has no Pass button.** Requiring it would add a tap per charger across the whole fleet.

## Icon

An adaptive icon (`res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/`): a white charger with a
bolt and a green inspected-check badge on navy, plus a single-colour layer for Android 13+ themed icons.
It is vector-only, which is fine at minSdk 29.

## Known gaps

- Release build has minification off until it has been tried on a device. Release signing is not configured.
- No instrumented/UI tests. The PDF writer and Room layer are the riskiest unverified code; try a session with
  many failures, notes and photos on a real device and open the PDF.
- The seven named components are fixed. The charger count and prefix are configurable, the component list is not.

## Layout

```
app/src/main/java/com/example/evinspector/
  domain/   pure Kotlin: status rules, validation, labels, CSV, report models   (unit tested)
  data/     Room entities/DAO, repository, photo processing, config store
  report/   PDF writer, share/open helpers
  ui/       Compose screens + view models: start, dashboard, inspect, history, nav
app/src/test/   JUnit tests for the domain layer (59)
app/src/main/res/   adaptive icon, manifest resources
```
