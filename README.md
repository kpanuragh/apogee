# Apogee

A Windows 10 Mobile–style home screen for Android.

Apogee replaces your launcher with the two-page Start experience from Windows Phone: a
vertically scrolling grid of flat live tiles, and an alphabetical app list one swipe to the
right. No app drawer grid, no rounded icons, no drop shadows.

## What it does

**Start screen**

- Live tiles on a six-unit grid (eight with *show more tiles*), packed flush the way Windows
  10 Mobile packs them — a small tile drops back into the gap a wide tile left behind. Gaps
  sit only between tiles, so the grid is symmetric against both screen edges.
- Six tile footprints: small (1x1), medium (2x2), wide (4x2), large (4x4), full width and
  full width tall — the last two stretch to the grid, so they stay edge to edge whether
  "show more tiles" is on or off. Long-press a tile to start editing, then tap the chevron to
  cycle sizes, long-press again to pick one outright, or tap the cross to unpin.
- Drag to rearrange, with the rest of the grid reflowing live around the tile you are holding.
- The Windows Phone press effect: tiles pivot about their centre so the corner under your
  finger sinks into the screen.
- Tiles that actually say something, flipping between faces:
  - **clock** — the time, flipping to the next alarm (`AlarmManager`, no permission)
  - **calendar** — today's date, flipping to the next event and when it starts
  - **now playing** — the current track with its artist and album
  - **app tiles** — an unread count, and the latest notification line behind it
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

## Two builds

| Flavour | Live sources | Play Protect |
| --- | --- | --- |
| `standard` (default) | clock, next alarm, next calendar event | installs cleanly |
| `badges` | the above, plus now playing, notification counts and text | blocked as a sideload in some regions |

Apogee asks for nothing at install time. The wallpaper picker, uninstalling through
`ACTION_DELETE`, haptics and the next alarm all work without a permission. The calendar tile
needs `READ_CALENDAR`, which you grant from settings when you want it and which nothing
touches until you do.

Live content has one hard limit: Android has no live-tile API, so what an arbitrary app is
doing can only be read from its notifications or a media session, and both need a
`NotificationListenerService`. Google Play Protect blocks sideloaded APKs that declare one
("App blocked to protect your device"), so that service lives in its own `badges` flavour
and the default build ships without it. Take `badges` if you want now playing, counts and
notification text, and are willing to approve that warning.

## Building

The Android SDK is the only prerequisite; everything else comes down with the wrapper.

```sh
./gradlew assembleStandardRelease   # app/build/outputs/apk/standard/release/
./gradlew assembleBadgesRelease     # the badges flavour
./gradlew testStandardDebugUnitTest # unit tests
./gradlew lintStandardDebug         # lint
./gradlew installStandardDebug      # install on a connected device
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

Notification badges (the `badges` flavour only) need notification access, which Android
grants explicitly — the badges toggle in settings sends you to the right screen. Nothing is
counted until you grant it, and the toggle is hidden entirely in the standard build.

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
  both flavours' APKs under it. A version that already has a release is left alone, so pushing again does not
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
           TilePacker (the grid packing rule), Prefs
data/live/ LiveTileRepository (alarm and calendar), LiveFeed (what only a notification
           listener can see), LiveModels; BadgeListenerService lives in src/badges, the
           flavour that declares it
ui/start/  LiveTileText turns the raw state into the line each tile kind shows
ui/start/  TileGrid (custom ViewGroup: pixel geometry, drag reorder, edit mode), TileView
           (draws one tile, including the press tilt and live faces), StartPage
ui/applist/AppListPage, AppListAdapter, LetterPickerView (the jump list)
ui/        LauncherActivity (two pages in a ViewPager2), MetroContextMenu
util/      AccentPalette, IconLoader (peels the glyph layer off adaptive icons), Launch
```

The grid packing rule lives in `TilePacker` rather than in the view, so it can be reasoned
about and tested on its own. `app/src/test` covers packing, the pixel geometry (tile edges
land flush with both margins, and leftover pixels from a grid that does not divide evenly
are spread rather than dumped on the last column), layout persistence and app-list
bucketing.
