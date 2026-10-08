# Apogee

A Windows 10 Mobile–style home screen for Android.

Apogee replaces your launcher with the two-page Start experience from Windows Phone: a
vertically scrolling grid of flat live tiles, and an alphabetical app list one swipe to the
right. No app drawer grid, no rounded icons, no drop shadows.

## What it does

**Start screen**

- Live tiles on a six-unit grid (eight with *show more tiles*), packed flush the way Windows
  10 Mobile packs them — a small tile drops back into the gap a wide tile left behind.
- Four tile footprints: small, medium, wide and large. Long-press a tile and tap the chevron
  to cycle through them, or the cross to unpin.
- Drag to rearrange, with the rest of the grid reflowing live around the tile you are holding.
- The Windows Phone press effect: tiles pivot about their centre so the corner under your
  finger sinks into the screen.
- Built-in live tiles for the clock and the date, which flip between faces, plus optional
  notification-count badges on app tiles.
- Transparent tile mode, which lets your wallpaper show through the Start screen.

**App list**

- Every app in one alphabetical column with square accent letter headers.
- Tap a header for the jump list — the grid of letters that scrolls you straight to a bucket.
- Search, hold-to-pin, hide an app from the list, app info, uninstall, and the app's own
  shortcuts where it publishes them.

**Settings**

- All twenty Windows Phone accent colours.
- Light, dark or follow-system theme.
- Monochrome tile glyphs (the metro look) or the app's own icon; accent-coloured tiles or
  tiles coloured from each icon.
- Toggles for live tile animation, press tilt and notification badges, plus wallpaper and a
  Start screen reset.

## Building

The Android SDK is the only prerequisite; everything else comes down with the wrapper.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint
./gradlew installDebug           # install on a connected device
```

Point Gradle at your SDK with `local.properties` (`sdk.dir=/path/to/android-sdk`) or the
`ANDROID_HOME` environment variable.

`assembleRelease` always produces an installable APK: with no signing credentials configured
it falls back to your local debug key. That is fine for sideloading and testing, but a
debug-signed APK cannot be published to Play. To sign with your own key, set
`APOGEE_KEYSTORE_FILE`, `APOGEE_KEYSTORE_PASSWORD`, `APOGEE_KEY_ALIAS` and
`APOGEE_KEY_PASSWORD` as environment variables, or as `apogee.keystoreFile` and friends in
`~/.gradle/gradle.properties`.

After installing, make Apogee your home app: **Settings → Apps → Default apps → Home app**, or
use *set Apogee as your home screen* in Apogee's own settings.

Notification badges need notification access, which Android only grants explicitly — the
badges toggle in settings sends you to the right screen. Nothing is counted until you grant
it.

- `minSdk` 24, `targetSdk` 35
- Kotlin, Android views (no Compose); `androidx.appcompat`, `recyclerview`, `viewpager2`
  and `palette`

## CI and releases

`.github/workflows/ci.yml` runs on every push and pull request (and on demand from the
Actions tab): lint, unit tests, then both APKs. The debug and release APKs are uploaded as
build artifacts, so a green run on any branch gives you something to install — open the run
and download **apogee-release-apk**.

`.github/workflows/release.yml` publishes a GitHub Release with the APK attached. It runs
when you push to `main`, when you push a `v*` tag, or on demand from the Actions tab:

- **Push to `main`** — tags `v<versionName>` (read from `app/build.gradle.kts`) and releases
  it. A version that already has a release is left alone, so pushing again does not
  re-release it; bump `versionName` to cut the next one.
- **Push a tag** — `git tag v1.1 && git push origin v1.1` releases exactly that tag.
- **Manual run** — name a tag, or leave it blank for `v<versionName>`.

For a stable signature across builds, add these repository secrets (Settings → Secrets and
variables → Actions):

| Secret | Value |
| --- | --- |
| `APOGEE_KEYSTORE_BASE64` | your keystore, `base64 -w0 release.jks` |
| `APOGEE_KEYSTORE_PASSWORD` | keystore password |
| `APOGEE_KEY_ALIAS` | key alias |
| `APOGEE_KEY_PASSWORD` | key password |

Without them the workflow still builds and publishes an APK, but each build is signed with a
fresh debug key — Android will refuse to install it over a previous build, so uninstall first.

## How it is put together

```
data/      AppRepository (LauncherApps-backed app list), TileStore (persisted layout),
           TilePacker (the grid packing rule), Prefs, BadgeListenerService
ui/start/  TileGrid (custom ViewGroup: packing, drag reorder, edit mode), TileView (draws
           one tile, including the press tilt and live faces), StartPage
ui/applist/AppListPage, AppListAdapter, LetterPickerView (the jump list)
ui/        LauncherActivity (two pages in a ViewPager2), MetroContextMenu
util/      AccentPalette, IconLoader (peels the glyph layer off adaptive icons), Launch
```

The grid packing rule lives in `TilePacker` rather than in the view, so it can be reasoned
about and tested on its own; `app/src/test` covers packing, layout persistence and app-list
bucketing.
