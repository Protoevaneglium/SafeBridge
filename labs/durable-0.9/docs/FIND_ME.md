# Find Me — 0.9 functional state

**No public service is deployed and the default endpoint is blank.** This source lab separates snapshots, on-device-only updates and optional configured live links. None dispatches help or automatically contacts a recipient.

Snapshots show coordinates, estimated accuracy, provider/device timestamp and age, with approximate/cache/stale/mock labels. Android Copy/Share exports one snapshot through the user's chosen app. Opening a chooser or selecting an app is not proof of sending or receipt. Neither starts a live feed. The owner reports phone sharing works, but the version/path/duration are unknown; it may have been this snapshot path.

## Prepare separately

A chosen operator privately provisions a pseudonymous reserved slot with the offline CLI. The sender file contains only its endpoint, slot ID and per-slot capability; no operator master enters the app. Setup is a separate route, not a code to obtain for every start. A rotated private key for the exact same slot can replace its local preparation while preserving the current link; different/unknown unresolved scopes are blocked. Android's file picker selects one file, a bounded strict reader rejects malformed/duplicate/foreign-endpoint content, and a second confirmation precedes encrypted no-backup saving. No location is collected or link sent by import. Provider/download/device records and copies of that file may reveal or control the slot.

The capability can request and explicitly end that slot's sessions. It does not prove a unique phone or a safe operator. The server can correlate sessions within a prepared slot. Anyone with a copied bundle can compete for/control that same reservation. No public anonymous enrollment or imaginary recovery account exists. Unprepared people still need a trusted setup channel; snapshots and guidance remain available separately.

## Start, retry, pause and resume

Start chooses Until Stop or an immutable original UTC end and obtains a short-lived epoch/creation-bound ticket automatically. The ticket and epoch are journaled before any session-creation request can escape. Once a creation may have happened, retries cannot change key, credentials, end or pinned epoch. The prepared master capability remains in its separate local store, outside the session journal and read link.

A location foreground service starts only after explicit consent/permissions and server reconciliation. Only new fresh latest samples are sent, without route history or old-fix replay. Temporary outages retry with bounded backoff on the same link. The 90-second point TTL erases server position data and makes it paused; Until Stop has no automatic session end. A positive chosen end is enforced independently and never shifted to a retry time.

Local process/permission/provider/foreground interruption pauses collection rather than silently revoking the session. Reopening never automatically collects location. Explicit Resume warns that existing holders of the same forwardable link can receive new estimates, then checks permissions, visible notification, server state, original end and accepted sequence. Terminal links cannot resume. Device/server clock disagreement and OS timing remain limits.

Calculator and Close keep a surviving foreground session distinct from Stop. Notifications/system indicators stay visible or discoverable. Android Active apps Stop can remove the entire process without callbacks; background start/while-in-use rules prevent promising automatic post-kill recovery. There is no boot/sticky restart or concealment guarantee.

## Explicit Stop and recovery

Stop first invalidates local collection/queued work, then requests authenticated remote termination. Known session IDs use their narrow revocation credential. Uncertain creation must not be mistaken for a confirmed Stop; matching prepared access can explicitly reset its own slot and invalidate old admission epochs. No different slot is reset implicitly. Offline or inaccessible management remains unconfirmed. Already dispatched bytes and exported positions cannot be recalled.

A separate explicit own-slot reset can end an orphan whose session management is absent, when the private preparation survives. Offline operator disable/rotation blocks old setup credentials while preserving an existing link by default; explicitly ending that link is a separate option. Rotation of a disabled slot does not re-enable it. An authenticated private receipt and bundle precede activation, allowing constrained recovery without replaying Stop on a later session. Forgetting preparation only removes the local setup key; it does not revoke grants, end unknown server links or erase other files. Losing both capabilities requires a trusted operator/reprovisioning choice. Corrupt/unreadable local management must not be silently discarded; any remaining recovery limit is recorded in verification.

## Recipient and persistence

The recipient needs a reachable supported HTTPS browser, without installing Safe Bridge or having an account. It retains last-known point/time/accuracy only in tab memory during transient failure, explicitly unverified with growing age. New tabs cannot invent a point after a server restart. A fresh newer upload resumes the same link. Confirmed Stop/expiry or Clear removes application state; known positive expiry still applies offline, although browser suspension can delay execution. Browser copies or physical RAM erasure are not controlled by this app.

An intact encrypted SQLite database preserves coordinate-free session/revocation metadata across backend process restart. RAM-only coordinates disappear; no route history is restored. A coherent stale full-database backup can restore old metadata despite cryptographic integrity. Disaster recovery needs a separately consistent revocation design or an explicit new service generation ending old links; no invisible backup/failover guarantee is claimed.

Google Maps remains a separately consented external snapshot, with coordinate/device/network transfer disclosed. No automatic map tiles/geocoding occurs and no address is invented. The operator processes coordinates within TLS, not end-to-end encryption. Full evidence and untested native/browser/infrastructure behavior are in `../VERIFICATION.md`.
