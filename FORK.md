# Pulse — fork notes

Upstream: [Website Monitor](https://gitlab.com/manimaran/website-monitor)  
Upstream F-Droid id: `com.manimarank.websitemonitor`  
License: GPL-3.0

Pulse id: `app.pulse.monitor`  
Internal Kotlin package: `app.pulse.monitor`

## Behaviour that differs from upstream

- Skip remote checks only when the phone has no usable link. Airplane + Wi-Fi still checks. No false “down” while offline.
- Global retries before a downtime notification.
- Grouped notification when several sites fail together.
- Optional recovery notification with downtime duration.
- Relative last-checked time on the home list.
- AlarmManager backup next to WorkManager.
- Forced dark Lumen theme.
- Colour and neutral home-screen widgets.
- Pause until morning; All / Down / Paused filter; latency on each row.
- Backup JSON includes check state and history.

## Not done

- No Compose rewrite
- No per-site interval
- Background interval cannot go below 15 minutes
- No public git remote in this tree yet
