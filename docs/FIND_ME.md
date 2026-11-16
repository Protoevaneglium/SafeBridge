# Find Me: what this test build actually does

Find Me provides **on-device location snapshots** and optional local updating. It has no internet-sharing backend, no recipient tracking URL, no emergency dispatch and no automatic outgoing messages. A recipient can read shared plain text without installing Safe Bridge, through whichever app the sender chooses. That is a snapshot, not a live feed.

## Snapshot flow

1. Open Find Me and choose Get a location fix. Android asks for location access if needed; approximate access is accepted. Other guidance remains usable without permission.
2. The app requests GPS/network location for up to 20 seconds. API 30+ uses getCurrentLocation, which may return a recent cached fix; older supported devices use a bounded listener. Leaving Find Me/entering cover/background cancels an unfinished one-shot request. A separate last-known action intentionally accesses cached data and labels it as such. Failure does not silently substitute a cached point.
3. A result shows coordinates, estimated horizontal accuracy, provider-supplied UTC time, elapsed age, freshness, approximate-permission and mock-location labels. Age over two minutes or unknown age is not labeled recent. This threshold is an interface convention, not proof of current position.
4. Copy or Share uses a short disclosure. Copy exports to Android clipboard with its sensitive-content hint; OS/app/sync access remains outside Safe Bridge. Share opens the standard Android chooser. The sender selects the app and recipient and completes sending there. Opening/selecting a target is not proof of sending or receipt. The snapshot text includes its fix metadata and says it does not update or dispatch help. No maps service receives coordinates automatically.

Coordinates are memory-only in Safe Bridge. No coordinate history, notes, contacts, location logs or saved-state location fields are created. Previously precise fixes are rejected after precision permission is removed. Other apps/recipients may retain or forward exported snapshots; Safe Bridge cannot recall them.

## Optional on-device updating

The sender can explicitly choose a 15- or 60-minute local session. This keeps the latest available fix ready for a later snapshot; **nobody receives automatic updates**. It starts only through the visible Find Me flow after location permission and enabled notifications. No background-location permission is requested.

The Android location foreground service has an unambiguous visible notification and Stop action, with no coordinates in the notification. App and channel notifications must be allowed. Disabling location/notification access or losing eligible providers stops the session when detected. Permission precision downgrade clears prior precise data. Android location providers have their own privacy/processing behavior.

Calculator, Back and Close do not stop an active local session. Calculator shows a separate Stop updates control, and the notification Stop action remains available. Disguise does not hide the notification, Active apps listing, permissions, package details, system location indicator or monitoring. There is no non-detection promise.

Stop clears local fix/listeners/timers. Expiry is checked against elapsed time before accepting or returning session data; a Handler plus an inexact idle alarm requests cleanup. Sleep/device restrictions may delay physical cleanup. No exact stop-time or uninterrupted updates are promised. Android Active apps Stop, process death, force-stop, permissions, battery and vendor behavior can end the session early. It is nonsticky and never restarts itself after process death/boot.

No remote revocation is needed or attempted in this build because no remote session is created. If a future backend is implemented, offline Stop must immediately cease local collection while clearly labeling remote revocation pending. Copies already exported cannot be recalled.

## Unimplemented and untested

A secure no-install live recipient link is unimplemented. [BACKEND_PLAN.md](BACKEND_PLAN.md) specifies separate read/write/revoke credentials, server-side revocation/expiry, latest-only retention, recipient privacy, stale/stopped states, connectivity failure and independent validation. It is not a hosted service or tested implementation. No external account or backend deployment was created.

Host tests cover location metadata/formatting/freshness only. Hardware fixes, permission dialogs, sharing UI, actual delivery, foreground-service execution, screen lock, deep sleep, expiry, Stop, launcher disguise and Android/OEM behavior remain untested in this environment. No real coordinates were collected or shared. See VERIFICATION.md for the full matrix.
