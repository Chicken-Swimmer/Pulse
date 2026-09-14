# Pulse

Personal uptime monitor for self-hosted services (Immich, Jellyfin, dashboards, …).

GPL-3.0 fork of [Website Monitor](https://gitlab.com/manimaran/website-monitor) by Manimaran.

- **Name:** Pulse
- **applicationId:** `app.pulse.monitor`
- **Version:** 1.0.30
- UI language: English

Pulse is a different app from the F-Droid original. Uninstall any old `st.holsty.*` build if you only want one list — IDs do not clash with upstream.

## What it does

- Checks configured URLs on a schedule (15 minutes minimum in the background)
- Retries before a “down” notification
- No down-alert when the phone has no network (airplane without Wi-Fi, radios off)
- Airplane mode **with Wi-Fi on** still checks
- Quiet hours, pause-until-morning, optional recovery notification
- Per-site history, uptime bar and latency chart
- Home screen widgets (colour and neutral)
- JSON backup / restore (sites, last-checked, latency, history)
- No accounts, no tracking, data stays on the device

## Build

Android Studio, AGP 8.7+, JDK bundled with Studio, `compileSdk` 35.

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install over the previous Pulse (`app.pulse.monitor`).

## F-Droid

Not listed yet. Store text lives in `fastlane/metadata/android/en-US/`. To submit later:

1. Source: https://github.com/Chicken-Swimmer/Pulse
2. Open a merge request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
3. Replace `fastlane/.../phoneScreenshots/` with Pulse screenshots (current files are still upstream)

Do not submit until Pulse has run a few days on a real phone.

## License

GNU GPL v3. Original copyright: Manimaran. Pulse is a fork under the same license. See `LICENSE` and `FORK.md`.
