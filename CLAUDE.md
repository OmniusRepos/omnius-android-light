# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Omnius** (this build: "Omnius Kali", `lol.omnius.android.kali`) — an Android TV
streaming app for **movies, series, and live TV**, including **torrent (P2P) streaming**
via libtorrent4j. Built with **Compose for TV + Navigation Compose** and
**Media3/ExoPlayer** (HLS + DASH). It talks to its **own backend at `https://api.omnius.lol/`**
(not the Negenet playlist-backend). This is the **low-RAM "Kali" variant** for 1 GB /
Android 9 boxes, with a distinct app id so it installs alongside the standard build.
It ships as a *managed app* auto-updated by the Negenet launcher — see workspace root `../CLAUDE.md`.

## Commands

```bash
# Build a signed release APK (Android Studio's bundled JDK 17)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease

./gradlew :app:lint
./gradlew clean

# Release: bump versionName + versionCode in app/build.gradle.kts FIRST, then:
./release.sh   # builds, uploads the APK to the `apps` GitHub release, merges only the
               # "omnius" key into apps.json (preserves other managed apps). Needs `gh`.
```

No test suite. `compileSdk 35` / `minSdk 24` / `targetSdk 35`, JVM 17, R8 minify on.

## Architecture / conventions

- **"Kali" = low-RAM variant.** `applicationId = lol.omnius.android.kali`,
  `buildConfigField LOW_RAM = true`, `android:largeHeap`. The app reads `BuildConfig.LOW_RAM`
  to apply low-memory defaults. Keep changes friendly to 1 GB / Android 9 hardware.
- **Backend is `api.omnius.lol`** (default in `data/ServerManager` + `api/ApiClient`;
  user-overridable). The Retrofit interface is `api/OmniusApi` (kotlinx.serialization
  converter): `list_movies` / `movie_details`, `list_series` / `season_episodes`,
  `list_channels` (+ countries/categories), `home.json`, `subtitles/search`,
  `config.json`, and `refresh_movie` / `refresh_series` (detail screens pull fresh torrents).
- **Torrent streaming** is `torrent/TorrentStreamManager` (a `libtorrent4j` singleton,
  `TorrentStreamState` flow) feeding `torrent/TorrentDataSource` into ExoPlayer — it
  prioritizes head pieces for seek-ahead and exposes embedded subtitle files. ABI-specific
  `libtorrent4j-android-arm`/`-arm64` natives are bundled.
- **UI is Navigation-Compose**, routes centralized in `ui/navigation/NavRoutes` +
  `SidebarItem` (Home, Movies, Series, Live TV, Favorites, Settings). Screens live under
  `ui/<feature>/`; reusable cards/rows under `ui/components/`. Use the `NavRoutes` helpers
  (`movieDetail(id)`, `liveCountry(code)`, …) rather than hand-built route strings.
- **Local persistence** uses DataStore: `data/FavoritesManager`, `data/WatchHistoryManager`,
  `data/ServerManager` (selected backend).
- Release is signed with the **committed `debug.keystore`** so launcher OTA updates install
  in place — don't change the key.
- **This is a git repo** (unlike the workspace root) — changes here are recoverable.
