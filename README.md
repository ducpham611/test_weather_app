# VN Weather — Thời Tiết VN

A lightweight Android weather app built to the project plan: **minSdk 21 (Android 5.0)**,
Vietnamese-first interface, and forecasts from **Open-Meteo using the ECMWF IFS HRES model**.

---

## Opening the project

1. Open Android Studio → **Open** → select this folder.
2. Android Studio creates the Gradle wrapper and downloads dependencies on first sync.
   (From a terminal with Gradle installed you can also run `gradle wrapper` first.)
3. Run on a device or emulator. An **API 21** emulator is the important one to test.

Build a release APK/AAB with `./gradlew assembleRelease` or `./gradlew bundleRelease`
after adding your signing config.

## If the app cannot load data

See **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)**, and run
**Settings → Connection diagnostics** on the device.

## Building an APK without Android Studio

Push this project to GitHub and the included workflows build the APK for you.
See **[BUILD_APK.md](BUILD_APK.md)** for the full walkthrough.

---

## What is implemented

| Plan phase | Status | Where |
|---|---|---|
| 1. Setup & networking | Done | `build.gradle.kts`, `util/NetworkModule.kt`, `WeatherApp.kt`, `data/remote/` |
| 2. Main screen | Done | `ui/main/` |
| 3. Location, search, cache | Done | `util/LocationProvider.kt`, `ui/search/`, `data/local/` |
| 4. Vietnamese & settings | Done | `res/values-vi/`, `util/LocaleHelper.kt`, `ui/settings/` |
| 5. Performance choices | Done | Views not Compose, flat layouts, vector icons, R8 |
| 6. Widget & release prep | Widget done | `ui/widget/` |

### Features

- **Current weather** — temperature, feels-like, condition, humidity, wind, rain.
- **24-hour hourly forecast** — well inside IFS HRES's native 90-hour hourly window,
  so every cell is real model output rather than interpolation.
- **Upcoming days forecast** — 7 days from the API, 3 shown by default and the rest behind **"Xem thêm ngày"**.
- **Responsive to the system font size** — the detail row stacks vertically above ~1.15x
  scaling, and every row uses flexible widths so nothing wraps or overlaps.
- **Place picker** — search any city or district through Open-Meteo geocoding with
  `language=vi`, save favourites, or follow GPS.
- **Offline** — the last forecast per place is cached and shown with
  **"Cập nhật lúc HH:mm"**.
- **Settings** — language (Tiếng Việt / English / system), °C or °F, km/h or m/s,
  refresh interval, light/dark theme.
- **Home screen widget** — reads only the cache, refreshed by WorkManager.

---

## Android 5.0 / 6.0 specifics

- **TLS** — Conscrypt is installed as the first security provider in
  `WeatherApp.installConscrypt()`. Without it, the HTTPS handshake with
  `api.open-meteo.com` fails on many Android 5 builds. OkHttp is also pinned to
  TLS 1.2/1.3.
- **No Google Play services** — location uses the platform `LocationManager`
  (last known fix → network provider → GPS, with a 12-second timeout).
- **Views, not Compose** — classic XML layouts with ViewBinding.
- **Vector icons only** — no PNG density buckets, including the launcher icon.
- **Pinned dependencies** — every version in `app/build.gradle.kts` still supports
  API 21. Check `minSdk` before upgrading any of them.
- **Slow networks** — 15-second timeouts, gzip, and retries with 1 s / 2 s / 4 s backoff.

---

## API

```
https://api.open-meteo.com/v1/forecast
  ?latitude={lat}&longitude={lon}
  &models=ecmwf_ifs
  &current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,
           weather_code,wind_speed_10m,wind_direction_10m,is_day
  &hourly=temperature_2m,precipitation_probability,precipitation,weather_code,
          wind_speed_10m,is_day
  &daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,
         precipitation_probability_max,sunrise,sunset
  &timezone=auto&forecast_days=7
```

Geocoding: `https://geocoding-api.open-meteo.com/v1/search?name={q}&count=20&language=vi`

No API key is needed.

---

## Tests

```
./gradlew test
```

Covers the WMO code mapper, JSON parsing of a real-shaped HRES response,
cache freshness, and formatting.

---

## Before you publish

- Add a signing config and keep the keystore safe.
- Write a privacy policy covering location use (Google Play requires it).
- Keep the Open-Meteo attribution visible — it is a CC BY 4.0 requirement.
  It is already on the main screen and the About screen.
- **The free Open-Meteo tier is non-commercial.** Buy a paid plan if you monetise the app.

## Not included yet

- A bundled offline list of Vietnam's provinces and districts (search covers most of
  them, but a few small districts may be missing).
- Rain alert notifications and the temperature chart (marked as v2 in the plan).

## Modules

| Module | Purpose |
| --- | --- |
| `app` | The application itself. |
| `ui-weather-view` | Animated weather backgrounds, copied unmodified from [Breezy Weather](https://github.com/breezy-weather/breezy-weather) and used under the **GNU LGPL v3**. See `ui-weather-view/README.md` and `ui-weather-view/LICENSE`. |

The animation is controlled by **Settings → Appearance → Animated weather
background**. It defaults to off on devices the system reports as low-RAM.
