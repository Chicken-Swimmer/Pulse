# Pulse

Personal uptime monitor for self-hosted services (Immich, Jellyfin, dashboards, …).

GPL-3.0 fork of [Website Monitor](https://gitlab.com/manimaran/website-monitor) by Manimaran.

## What it does

- Checks URLs on a schedule (15 minutes or longer in the background)
- No down-alert when the phone has no Wi-Fi or mobile data
- Airplane mode with Wi-Fi on still checks
- Pause for 1 hour, until a chosen time, or a backup window
- Optional alert when a site comes back
- TLS expiry on each site (warning when it is close)
- History, uptime bar and latency
- Refresh one site from the list
- Home screen widgets
- JSON backup / restore
- No account, no tracking — data stays on the phone

## Build

Open the project in Android Studio and press Run.

## License

GNU GPL v3. Original copyright: Manimaran. Pulse is a fork under the same license. See `LICENSE` and `FORK.md`.
