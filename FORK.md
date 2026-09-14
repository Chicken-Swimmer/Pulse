# Pulse — fork notes

Upstream: [Website Monitor](https://gitlab.com/manimaran/website-monitor)  
Upstream F-Droid id: `com.manimarank.websitemonitor`  
License: GPL-3.0

Pulse id: `app.pulse.monitor`  
Source: https://github.com/Chicken-Swimmer/Pulse

## Behaviour that differs from upstream

- Skip remote checks when the phone has no Wi-Fi or mobile data. Airplane + Wi-Fi still checks.
- No retry setting; one rematch after a failure before a down alert.
- Grouped notification when several sites fail together.
- Optional recovery notification with downtime duration.
- Relative last-checked time on the home list.
- AlarmManager backup next to WorkManager.
- Forced dark Lumen theme.
- Colour and neutral home-screen widgets.
- Pause 1 hour, until a chosen time, or a backup window.
- TLS expiry on the site screen.
- Backup JSON includes check state and history.
