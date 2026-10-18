# MyWeather

A native Android weather app built directly on the U.S. **National Weather
Service (NWS) public API** — offline-first, privacy-respecting, and free of any
Google or third-party services.

MyWeather is a learning sandbox for best-practices Android engineering. It ports
the caching and grid concepts from a sibling .NET/Blazor reference dashboard
([`collabskus/weather`](https://github.com/collabskus/weather)) into idiomatic
Kotlin + Jetpack Compose.

- **Repository:** https://github.com/kusl/myweather
- **Data source:** https://api.weather.gov (no API key required)

---

## What it does

- Resolves a coordinate to its NWS forecast grid and shows the headline forecast
  for your own cell, plus the upcoming periods.
- Renders a **neighborhood matrix** — a north-up grid of the surrounding cells —
  so you get a sense of conditions right across a grid boundary, not just in the
  single cell you happen to fall in.
- Works **offline**: forecasts are cached locally and a stale-but-valid copy is
  shown whenever NWS is unreachable.
- Lets you set a custom **NWS User-Agent** (the agency asks every client to
  identify itself) and choose how large a neighborhood to fetch.
- Optionally uses your **device location**, but is fully usable without it via
  manual coordinate entry and saved locations.

---

## Design at a glance

```
UI (Compose)            ViewModels              Domain                 Data
────────────────        ──────────────          ─────────────          ─────────────────────────
DashboardScreen   ───>  DashboardViewModel ───> WeatherRepository ───> WeatherRepositoryImpl
LocationsScreen   ───>  LocationsViewModel      (interface)            ├─ NwsApi (Retrofit/OkHttp)
SettingsScreen    ───>  SettingsViewModel                             ├─ Room cache (SQLite)
                                                                       └─ LocationProvider (framework)
```

- **Single source of freshness.** `WeatherRepositoryImpl` is the only type that
  knows the caching policy: long-lived `coordinate → grid` metadata, short-lived
  forecasts honouring `Cache-Control`, conditional `If-None-Match` revalidation,
  and stale-on-error fallback. See [docs/CACHING.md](docs/CACHING.md).
- **Manual dependency injection.** A small hand-wired `AppContainer` builds the
  graph — no DI framework, to keep the dependency surface (and APK) minimal. Room
  is the only annotation processor (via KSP).
- **Interface seams** (`WeatherRepository`, `SettingsRepository`, `LocationSource`)
  keep the ViewModels unit-testable without Android.

Full write-up: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Location: graceful degradation

Location is optional and every failure mode maps to a clear, usable state — the
location call **never throws**.

| Situation | What MyWeather does |
| --- | --- |
| Permission granted, fix obtained | Loads the forecast for your position |
| Permission granted, no fix yet | Falls back to last-known location; if none, prompts for manual entry |
| Permission denied | Shows a note and keeps manual entry available |
| Location services off / no provider | Shows a note and keeps manual entry available |
| Coordinate outside the U.S. | Explains NWS only covers U.S. locations |
| Offline | Serves the last cached forecast and flags it as stale |

Only **coarse** location is requested (city-level accuracy is plenty for a
forecast and is the more privacy-respecting choice). There is no background
location and no fine-location permission.

---

## Privacy

MyWeather sends **no analytics or telemetry**, bundles **no** Google Play
Services / Firebase / Google Sign-In, and talks to **only** `api.weather.gov`.
The on-device cache is excluded from cloud backup and device-to-device transfer.
Details: [docs/PRIVACY.md](docs/PRIVACY.md).

---

## Build & run

Requirements: **JDK 17** and the Android SDK (compileSdk 36). No local Android
Studio is required — the Gradle wrapper is committed (including
`gradle-wrapper.jar`), so the command line works out of the box.

```bash
# Debug build (installs on a device/emulator running Android 14+)
./gradlew assembleDebug

# Run the JVM unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Release APK (see "Signing" below)
./gradlew assembleRelease
```

Minimum / target: **minSdk 34 (Android 14), targetSdk 35**.

### Application ID

The installable application identity is **`com.github.kusl.myweather`** (debug
builds get a `.debug` suffix, so a debug and a release build can sit side by side
on one device). This is intentionally distinct from the Kotlin source namespace
`com.kusl.myweather`, which is internal and never user-visible — AGP allows the
two to differ, and changing the store-facing id does not require touching any
package declarations or imports.

### Signing

`assembleRelease` is designed to **never fail for lack of secrets**:

- If you provide signing material, the release APK is signed with your key.
- If you don't, it transparently falls back to the debug signing config so the
  build still produces an installable APK (useful on forks and in CI).

Provide signing material either via environment variables:

```bash
export MYWEATHER_KEYSTORE_PATH=/abs/path/to/release.keystore
export MYWEATHER_KEYSTORE_PASSWORD=…
export MYWEATHER_KEY_ALIAS=…
export MYWEATHER_KEY_PASSWORD=…
./gradlew assembleRelease
```

…or via a git-ignored `keystore.properties` in the project root:

```properties
storeFile=/abs/path/to/release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

For the CI build, the same four values are supplied as repository secrets
(`KEYSTORE_BASE64`, `MYWEATHER_KEYSTORE_PASSWORD`, `MYWEATHER_KEY_ALIAS`,
`MYWEATHER_KEY_PASSWORD`); see `.github/workflows/build_apk.yml`.

---

## Continuous integration

Three workflows under `.github/workflows/`:

- **`ci.yml`** — on every push and PR: `assembleDebug`, `testDebugUnitTest`,
  `lintDebug`. Needs no secrets, runs on forks. Least-privilege permissions,
  concurrency cancellation, and report artifacts.
- **`build_apk.yml`** — on push to `main`: builds and signs a release APK,
  uploads it, and publishes a rolling **pre-release**. Decodes the keystore from
  a base64 secret when present; the publish step is skipped on forks/PRs.
- **`instrumented-tests.yml`** — opt-in (manual): runs the Room instrumented
  tests on an AOSP emulator (no Google Play image).

Dependency and action updates are proposed weekly by Dependabot
(`.github/dependabot.yml`).

---

## Testing

- **JVM unit tests** (`app/src/test/…`) cover the pure logic and the repository
  orchestration with in-memory fakes: coordinate math, the grid-neighborhood
  geometry, NWS DTO→domain mapping, `Cache-Control` parsing and TTL clamping,
  the User-Agent interceptor, the cache-aside flows (fresh hit, cold fetch + tile
  assembly, stale-on-error, no-coverage, conditional-GET wiring), and the
  dashboard ViewModel state transitions.
- **Instrumented tests** (`app/src/androidTest/…`) exercise the real Room/SQLite
  layer in an in-memory database. Run them with:

  ```bash
  ./gradlew connectedDebugAndroidTest   # requires a device/emulator
  ```

---

## Project structure

```
app/src/main/java/com/kusl/myweather/
├─ core/            Pure Kotlin: GeoPoint, GridPoint, GridMath, TimeSource
├─ domain/          Models + the WeatherRepository contract
├─ data/
│  ├─ remote/       Retrofit NwsApi, DTOs, User-Agent plumbing, NetworkModule
│  ├─ local/        Room entities, DAOs, database
│  ├─ mapper/       DTO → domain mapping
│  ├─ location/     LocationSource + framework LocationProvider
│  ├─ settings/     SettingsRepository (+ DataStore impl)
│  └─ WeatherRepositoryImpl.kt   ← the cache-aside orchestrator
├─ di/              AppContainer (manual DI) + ViewModel factory
└─ ui/              Theme, custom icons, navigation, screens, components
```

(The `java/com/kusl/myweather/…` path is the source **namespace**, which is
separate from the installable application id — see *Application ID* above.)

A short module-level note lives in [app/README.md](app/README.md).

---

## Versioning note

The dependency and toolchain versions in `gradle/libs.versions.toml` are a
**mid-2026 baseline**, pinned for reproducibility. They are meant to be bumped
over time (Dependabot does this), with CI verifying each change builds.

The catalog is the single source of truth for the Android Gradle Plugin, Kotlin,
and Compose-BOM versions — read the exact numbers there rather than trusting a
value copied into prose. CI (`.github/workflows`) is what verifies that a given
set actually builds.

---

## Attribution

Forecast data and icons come from the U.S. National Weather Service, a public
domain U.S. government service. Please review the
[NWS API documentation](https://www.weather.gov/documentation/services-web-api)
and set a descriptive User-Agent (MyWeather lets you do this in Settings).
