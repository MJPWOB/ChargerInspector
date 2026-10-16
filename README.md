# Charger Inspector

Offline Android app (Kotlin, Jetpack Compose) for inspecting a fleet of EV chargers (default
CH01 to CH52), recording Pass/Fail per component with damage notes and photos, and producing a PDF
report plus a CSV for your maintenance system.

## Status: read this first

This project was written without access to the Android SDK, so **the Android code has never been
compiled or run**. What has been verified:

| Part | Verified how |
|---|---|
| Domain logic (status rules, validation, labels, CSV export, summaries) | Compiled with Kotlin 2.0.21 and 34 JUnit tests run and pass |
| Everything else (Room, Compose UI, camera, PDF writer, navigation) | **Not compiled.** Expect a few first-build errors |

The included GitHub Actions workflow is the fastest way to find out: push the repo and the first run
compiles everything, runs the tests, builds a debug APK, and checks the APK's permissions. Paste any
errors back and they are usually quick fixes.

## Build

**Android Studio:** open this folder, let Gradle sync (needs JDK 17, which Android Studio bundles),
then Run. Or from a terminal with the Android SDK installed:

```
./gradlew testDebugUnitTest assembleDebug
```

**GitHub:** push to a repo; `.github/workflows/android.yml` builds on every push and uploads the debug APK.

Before distributing, change `applicationId`/`namespace` in `app/build.gradle.kts` from the
`com.example.evinspector` placeholder, and set up release signing.

## How it works

- **Start screen:** mandatory inspector name/ID, plus the charger label prefix and count
  (configurable; remembered for next time). Not pre-filled with the previous inspector, since devices get shared.
- **Dashboard:** scrollable tile grid. Grey = not checked, yellow = partial, green = all clear,
  red = issues. Each tile also shows a text label so colour is never the only signal.
  The footer button reads "Generate Reports • 34/52 Inspected".
- **Inspection form:** Pass/Fail for Cable 1/2, Pistol 1/2, Holster 1/2, Screen. Choosing Fail requires a
  damage description and offers up to 3 photos per component. Save & Return is disabled until all seven
  are answered and every failure is described; it tells you what is missing.
- **Reports:** PDF (summary, colour overview, failure details with photos, list of chargers not inspected)
  and CSV. Generating with unfinished chargers shows a warning first. Both are shared through the
  Android share sheet and kept on the device under **Previous reports**, where you can view, re-share or delete them.
- **Finish session:** frees storage by deleting raw photos (the PDFs keep their embedded copies) and
  starts you fresh. If no report was ever generated, finishing deletes the session's data after a warning.

### Status rules

- Nothing answered: grey.
- Anything answered but not saved with Save & Return: yellow. This includes a fully answered form that was
  never saved, so green/red always mean "the inspector confirmed this".
- Saved with all seven answered: green, or red if any component failed.
- Editing a saved charger turns it yellow again until it is re-saved.
- "Inspected" in the footer counts green and red tiles only.

### Crash and power-loss recovery

Every toggle is written to the database immediately and note text within 400 ms, inside transactions.
Pending edits are also flushed when the app is backgrounded or the screen closes. On launch, an
unfinished session is resumed automatically. The path of a photo being captured is saved in instance
state, so a photo taken while Android killed the app in the background is not lost.

### CSV format

One row per charger per component (52 x 7 = 364 rows, plus header), UTF-8, CRLF line endings:

`session_id, inspector_id, charger, charger_status, inspected_at, component, result, description, photo_count`

`inspected_at` is ISO 8601 with the device's UTC offset. `result` is `PASS`, `FAIL` or `NOT_CHECKED`.
Chargers never inspected are still included so the file is always complete. Text starting with
`= + - @` gets a leading apostrophe so spreadsheets cannot execute it as a formula.

## Decisions that differ from, or go beyond, the brief

1. **Plain Room instead of SQLCipher.** The database lives in app-private storage, which Android
   encrypts with the device (file-based encryption), and backup/transfer are disabled in the manifest.
   SQLCipher would add a native dependency and a key-management problem, while photos would still sit
   unencrypted next to an encrypted database. If your policy demands an encrypted database, the swap is
   small: add `net.zetetic:sqlcipher-android` and pass a `SupportOpenHelperFactory` to
   `Room.databaseBuilder` in `InspectorApp.kt`, with the passphrase wrapped by the Android Keystore.
   Encrypt the photos too, or the benefit is partial.
2. **No CAMERA permission.** Photos use the system camera app via `ACTION_IMAGE_CAPTURE`, which needs no
   permission. This is what keeps the manifest free of `<uses-permission>` entirely (CI enforces it).
3. **Share sheet caveat.** "100% offline" holds for the app itself, but the share sheet can send reports to
   any app on the device, including ones with network access. If that matters, restrict which apps are
   installed or allowed via MDM.
4. **minSdk 29 (Android 10).** Avoids needing legacy storage and desugaring code. Lower it if you have older tablets.
5. **Scoped storage:** reports stay in app-private storage and leave through the share sheet using
   FileProvider URIs, so no storage permission is needed.
6. **History and multiple sessions** (added at your request): reports persist until you delete them.
7. **Photos are shrunk on capture** to 1280 px / JPEG 75 and capped at 3 per component, which keeps the PDF and
   disk usage reasonable for 52 chargers.
8. **Portrait lock:** set in the manifest. As I understand it, Android 16 ignores orientation locks on large
   screens for apps that target API 36 or higher; this app targets 35. The grid is adaptive, so it still works if that changes.

## Known gaps

- No launcher icon yet (the default is used).
- Release build has minification off until it has been tried on a device. Release signing is not configured.
- No instrumented/UI tests. The PDF writer and Room layer are the riskiest unverified code; try a session with
  many failures and photos on a real device and open the PDF.
- Components are fixed (the seven in the brief). The charger count/prefix is configurable, the component list is not.

## Layout

```
app/src/main/java/com/example/evinspector/
  domain/   pure Kotlin: status rules, validation, labels, CSV, report models   (unit tested)
  data/     Room entities/DAO, repository, photo processing, config store
  report/   PDF writer, share/open helpers
  ui/       Compose screens + view models: start, dashboard, inspect, history, nav
app/src/test/   JUnit tests for the domain layer
```
