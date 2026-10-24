# JamTV Extreme Lite

Native Android TV digital prayer clock, intentionally implemented without WebView, third-party libraries, images, video, ads, or a database.

## Features
- Fullscreen black screen, large 24-hour digital clock.
- Small Indonesian Gregorian date and Hijri date from AlAdhan API.
- Prayer times for Fajr, Dhuha (Sunrise + 20 minutes), Dzuhur, Ashar, Maghrib, Isya, and estimated Tahajjud (two-thirds of the night interval from Isha to Fajr).
- Next-prayer countdown recalculated locally every second.
- Daily API sync at startup/day change; last successful timings are cached in SharedPreferences for offline fallback.
- Screen stays awake while app is foregrounded.

## Build from a phone
1. Extract this ZIP on your phone.
2. Upload the **contents** of this folder to a GitHub repository (including `.github/workflows/build-apk.yml`).
3. In GitHub, open Actions and run **Build JamTV Extreme Lite APK** (or push to `main`).
4. Download the `JamTV-ExtremeLite-debug-apk` artifact and extract `app-debug.apk`.
5. Copy the APK to USB and install it on Android TV using a file manager; allow installs from that file manager if requested.

## API and calculation notes
- Daily data source: `https://api.aladhan.com/v1/timingsByCity?city=Kudus&country=Indonesia&method=20` (method 20 is the Southeast Asia calculation method; verify suitability against the local authority's official timetable).
- Dhuha is estimated as 20 minutes after Sunrise. Tahajjud is estimated at two-thirds of the interval between Isha and next Fajr. These are estimates, not official separate API prayer times.
- On API failure, cached values remain visible. Retry is throttled to at most once per 15 minutes to avoid repeated network calls.

## RAM / 24-7 caveat
This is designed to minimize memory and keep the visible clock updating locally, but **64 MB RAM is a target, not a verified guarantee**. Actual process memory depends on Android/Google TV version, device, screen density, and OS overhead. Measure on the target TV (for example via `adb shell dumpsys meminfo id.blackwhite.jamtv`) before claiming compliance. The app cannot guarantee uninterrupted operation if the TV OS kills it, loses power, or changes system time/network settings.
