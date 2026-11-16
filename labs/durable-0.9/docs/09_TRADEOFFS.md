# 0.9 continuity, admission and operating tradeoffs

November 16, 2026. This is the separate durable laboratory; earlier 0.6, 0.7 and 0.8 source/artifacts are not modified by this work. No hosting account, paid service, external attestation provider or public live endpoint has been created. This document distinguishes the intended 0.9 contract from runtime proof; implementation status is recorded below after source/test review.

## Three different continuity problems

| Event | Intended behavior | Limit that must remain visible |
|---|---|---|
| Temporary network loss while the consented foreground sender survives | Pause uploads/retry fresh fixes on the same session/link; retain no route or queued old coordinates | A reconnect is not proof of delivery, freshness or safety; a request already dispatched may arrive before remote Stop acknowledgement. |
| Backend process restart with intact durable metadata | Preserve session ID/generation, authorization and chosen end; return paused with no server point until a fresh upload | Latest coordinates are RAM-only and deliberately lost on restart. Metadata continuity is not position continuity or high availability. |
| Android process death, force-stop or Active apps Stop | Recover management state without collecting/sending; offer explicit resume from the visible app, or Stop/revocation | The app cannot silently guarantee resumption or infer that a killed process successfully revoked the server. |

Android says Active apps Stop removes the whole app from memory without callbacks. Android 12+ restricts background foreground-service starts; Android 14+ checks the location service's while-in-use permissions when creating it. Therefore reopening the app and explicitly confirming resume from a visible Activity is the straightforward supported design here. A previously started location foreground service can retain foreground-location access through Home/display-off, but that does not guarantee uninterrupted execution or concealment. [Active apps Stop](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions).

A resume prompt must say that the **same forwardable read link and its existing recipients** can receive new estimates. It must not ask only to “restore the screen” while silently restoring location collection. Check permissions, notification visibility, server state, immutable chosen end and last accepted sequence, then accept only new fresh fixes. Stop is terminal; paused/process-killed is not Stop. Automatic post-boot collection, covert restart or bypassing a user's system Stop is outside this design.

The Android 15 six-hour restriction documented for `dataSync`/`mediaProcessing` is not a blanket six-hour lifetime for the location foreground-service type. This does not justify an unlimited reliability promise: the user, OS, permissions, power and providers can still interrupt it. [Foreground-service launch prerequisites](https://developer.android.com/develop/background-work/services/fgs/launch), [service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

## Prepared admission improves crisis-time usability, with a real first-use boundary

The implemented source flow is: an operator privately provisions one pseudonymous installation slot and an independent bearer credential; the user imports a private bundle **before** a crisis into a separate encrypted Android store. Normal explicit Start then obtains a bounded, scoped session ticket automatically. No operator master secret enters the app, public repository or recipient link. There is no per-session emergency code to obtain or type, and no public anonymous enrollment endpoint. Source and synthetic checks do not establish a working operator or Android import in the field.

The bundle is a capability, not proof of a unique physical phone or a safe person. A copied/stolen bundle can control that same slot. “Per installation” describes intended distribution; no unreviewed device attestation, identity provider or hardware uniqueness claim is added. Import files, clipboard/email delivery and device records may expose setup. Keystore-backed encryption makes key extraction harder but does not make a compromised app/device safe. [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

One reserved slot permits one active session for that installation. It limits one grant's ability to consume other installations' capacity. An explicit **own-slot reset** can end an orphan session before creating a new one; it warns that the old link stops working. It does not automatically reset on a capacity conflict, terminate other slots, or issue a new identity to evade the one-slot limit. Lost management with a retained prepared credential can support this explicit recovery. Losing both stores requires a trusted operator to identify the right slot and privately restore prepared access; there is no invented account recovery service. Stable slot metadata can link that slot's activity across sessions, even without a name or account.

### Implemented offline operator controls

The bounded private operator workflow now supports **disable, rotate, re-enable and verifier-only export** against the durable database. These are explicit offline commands with the server stopped and its exclusive lock released, not public administration or enrollment endpoints. Default disable/rotation preserves an already active session's ID, original end and read/write/revoke capabilities. The separate `--end-current` choice deliberately ends that session; it is not implicit in key rotation. A disabled slot remains disabled after rotation until explicitly enabled. See [prepared setup and recovery commands](../live/backend/PREPARED_SETUP.md#offline-disable-rotation-and-verifier-export).

Rotation publishes a private same-slot replacement bundle and scoped authenticated intent receipt before committing the database change. Explicit recovery checks the matching bundle, receipt and expected old verifier/epoch/session binding; an already committed recovery does not replay ending a later session. File existence alone is not proof of activation. The workflow uses new, private output paths and rejects conflicting recovery rather than silently retargeting it. Operator and independent synthetic recovery checks are recorded in the project's verification record; this audit does not add or duplicate their totals.

Android import now permits a rotated credential for the **same** saved/managed slot during active, paused or pending-revocation state, without ending its link, resuming collection or writing session-management state. Read-only local scope guards run after parsing and again before the encrypted save. A different unresolved slot or unknown startup scope is blocked. This does not verify that the replacement token is activated at the server; endpoint/format/scope validation is separate from operator authorization.

The remaining choices are operational: how to identify the correct pseudonymous slot, distribute the replacement privately, communicate whether an existing session should remain usable, confirm activation, and handle compromise of both setup and active-session capabilities. Disabling setup access alone does not revoke an already issued active recipient/writer capability; choose `--end-current` when that is intended. Export is a verifier/policy provisioning view, not a complete session backup. Grant removal/reassignment, public enrollment, trusted identity recovery and a deployed support operation are not supplied. These controls do not remove the finite-capacity or first-contact tradeoffs below.

The first-use tradeoff is fundamental: an unprepared person with no trusted grant cannot obtain guaranteed live admission from this closed model during an emergency. Guidance, independent support links and on-device snapshots must remain available. The setup requirement must be visible before anyone relies on live sharing.

| Product policy | Benefit | Cost or unmet requirement |
|---|---|---|
| Prepared, privately issued finite slots (current laboratory direction) | No crisis-time ticket entry; pseudonymous scoped recovery; no public anonymous capacity creation | Prior trusted preparation, operator distribution/support and finite reserved capacity; copied/lost grants remain a risk. |
| Public first-use enrollment with bounded admission | Broader immediate reach | Needs an explicit abuse/Sybil policy, cost/capacity model and privacy-reviewed enrollment. Accounts, identity proof, payment or attestation create new exclusions and data obligations; none is silently added. |
| Anonymous best-effort first use under a hard quota | Lowest onboarding friction | Admission can be denied or exhausted, including by abuse. It cannot promise a reserved place for every unknown person or unbounded Until Stop sessions. |

These are operating choices, not invitations to create third-party accounts or deploy a provider. No finite service can promise admission to unlimited unknown identities while reserving non-expiring sessions without a trusted allocation/reclamation policy. That conclusion follows from the capacity model, not a measured attack claim.

## Expiry, data erasure and slot lifetime are separate

- A short ticket/creation permit authorizes a request; it is **not** a hidden session end time.
- The 90-second point lease is a freshness/coordinate-erasure boundary. Its lapse pauses the session and erases the server point; it does not silently free a non-expiring slot or revoke its read link.
- A positive user-chosen deadline stays immutable across retries and restarts. Until Stop has no chosen deadline; an operator must not relabel arbitrary timeout or quota eviction as the user's consent.
- Explicit Stop/reset ends the affected session. Offline local Stop prevents new collection while remote revocation can remain pending. Copies and previously viewed receiver points cannot be recalled.
- Durable metadata, grant ownership and operational/audit backups have different retention from the RAM-only point. “No coordinate history” must not be shortened to “nothing is stored.”

Until Stop orphan sessions can occupy reserved slots indefinitely. Own-slot reset addresses an authorized install's orphan, not global capacity for a new untrusted person. The operator must budget how many slots it can maintain, which grants are issued, how to handle compromised/lost credentials, and any explicit decommissioning policy. Silently killing other users' links or silently expiring Until Stop is not a capacity fix.

## Durable SQLite metadata is an operational dependency

The current store uses strict coordinate-free records containing identifiers, scoped authorization verifiers/management metadata, sequence/end/state and prepared-slot ownership. Record payloads use AES-GCM authentication/encryption and an authenticated catalog; SQL bucket/index identifiers are still metadata. Fresh coordinates stay in process RAM and restart paused. The database and the server's continuity/authentication material must survive instance replacement together; loss/corruption can still break old links. Persisting metadata creates at-rest privacy and access obligations even without real names or coordinate columns.

The source explicitly selects DELETE journaling, FULL synchronization and exclusive single-process ownership. SQLite transactions can make a set of metadata changes atomic, but storage/locking/flush behavior matters. WAL can have companion `-wal`/`-shm` files, requires processes on the same host, and is not a substitute for a distributed consistent store. Journal mode, synchronous settings, backup/restore, corruption/disk-full handling and permissions must be explicitly tested. A local database file alone does not provide redundant hosting or safe multi-instance failover. [SQLite atomic commit](https://www.sqlite.org/atomiccommit.html), [WAL behavior](https://www.sqlite.org/wal.html), [synchronous settings](https://www.sqlite.org/pragma.html#pragma_synchronous).

Installed test runtime reports Node 24.22.1 / SQLite 3.53.4. Production must pin a maintained version and inspect journal settings; the current SQLite WAL documentation describes a corruption bug fixed in 3.51.3+ and some backports. This runtime/version check is not a durability or deployment certification. Restoring a complete older valid database and manifest under the service's **existing configured state key** can omit a later Stop/reset, disable or rotation; extracting or stealing that key is not required for this stale-backup failure. Local encryption authenticates content, not that it is the latest backup. Ordinary crash restart of the current store is therefore distinct from disaster recovery. Stale restore needs a separately designed policy that invalidates old links/new generation or an independently consistent revocation anchor; silently trusting an old snapshot is not safe same-link recovery.

Keep coordinate/token request logs, heap/core dumps and unreviewed telemetry out of production; define metadata backups/key custody/retention separately. API deletion or SQL row deletion is not proof of physical erasure across infrastructure copies.

## Budget and service operation

Illustrative arithmetic, **not load-test evidence**: at a 15-second upload interval and one visible recipient polling every 15 seconds, a continuously active pair makes about 11,520 update/read requests per day, or 345,600 per 30 days, before retries, setup, TLS/HTTP overhead and extra viewers. One thousand such pairs imply about 345.6 million requests per 30 days. Hidden recipients do not poll, and real traffic varies. A nominal slot quota is not proof the server can sustain that load.

As a current base-compute price illustration only, AWS's public-IPv4 Linux bundles list 1 GB memory / 40 GB SSD / 2 TB transfer at US$7/month and 2 GB / 60 GB / 3 TB at US$12/month. These are not tested sizing recommendations, a complete service budget, a provider selection or a statement that the required privacy/retention controls are available. Pricing/region/tax/transfer terms need rechecking before any purchase. [Official Lightsail pricing](https://aws.amazon.com/lightsail/pricing/), checked November 16, 2026.

The operating budget also needs domain/TLS renewal, persistent storage and verified metadata restore, capacity/abuse controls, grant provisioning and private recovery support, patching, redacted monitoring, incident response, privacy/security review, representative Android test devices and accessibility testing. No supplier quote, staffing estimate, availability commitment or purchase exists here. No service can be made dependable merely by paying a small base VM charge.

## Remaining user-visible and verification limits

Prepared-grant bootstrap, explicit resume/reset UX, durable metadata and pending-revocation behavior have source and synthetic test evidence below; they still need Android/device verification. Public first use without a trusted bundle remains outside the prepared model; indefinite finite-slot occupancy is an operating constraint. These are product/operations issues as well as deployment work.

No street address or private geocoding service is supplied. The optional Google Maps action is a separately consented coordinate **snapshot**, not live tracking or a verified address. The external provider receives that selected point/device-network data under its own policy; no capability tokens are included. Recipient last-known RAM can outlive a connection loss, clearly unverified, until Clear, confirmed termination or a known chosen deadline; copies remain outside the app's control.

No real Android, TalkBack, recipient-browser layout, GPS, foreground continuation, process-death, screen-lock, system Stop, encrypted-store or installation verification is claimed. Local model/HTTP tests cannot prove those behaviors, survivor safety, infrastructure retention or a reachable public service. There is no deployment, public live endpoint or real user-location test.

## Implementation/source review update

Independent recipient tests in [recipient-privacy-v3.test.mjs](../live/security-tests/recipient-privacy-v3.test.mjs) passed **7/7** against actual loopback HTTP with a private temporary SQLite store. They use the real prepared-grant → ticket → protocol3 creation path, not anonymous or legacy admission. They close/reopen the authoritative store and server on the same local port and verify:

- complete read URL, session ID and generation survive an ordinary restart; no server point is restored and the receiver's prior tab-memory point is unverified;
- a fresh upload updates that same link, while a new recipient tab cannot retrieve the pre-restart point;
- the semantic durable snapshot contains no coordinate fields or raw sender/recipient credentials, with additional plaintext canary checks against the DB file;
- seven-day downtime does not silently expire an Until Stop session;
- confirmed Stop and a positive selected expiry survive restart and clear receiver state;
- an explicit own-slot reset ends only that installation's old link while another reserved installation remains paused.

These are synthetic HTTP/model/storage tests. They do not measure Android resumption, Keystore import, browser layout or production storage/hosting. Full stale-backup rollback, corruption/failure and other security tests are reported separately by the security reviewer; the 7 tests above do not certify disaster recovery. Earlier inherited documents about an in-memory protocol2 process are historical where they conflict with protocol3 durable behavior.

## Final targeted UX source audit

Read-only review of `MainActivity`, `PagedDialog`, `PreparedSetupFile`, `PreparedAccessStore`, `LiveLocationService` and the coordinator confirms the following source behavior. This is not a rendered-screen, Android lifecycle or accessibility test.

| Owner requirement | Current source behavior | Remaining limit |
|---|---|---|
| Immediate safety choices | Home has paired Support now, Find Me, Can't speak, Can't leave, Warning signs, Peer groups, Safety tools and For someone actions. 911/Find Me/calculator controls sit outside the guidance deck. | Eight choices fitting simultaneously is unverified; measured pagination may place choices on another page, especially with large text or a narrow/short viewport. Overwhelmed remains one additional route through Support now. |
| Preparation before a crisis | Separate preparation page; bounded selected-file parser requires the configured HTTPS origin and canonical credentials. A second, paged confirmation names the operator and explains bearer-key/copy risks before encrypted saving. Unconfigured builds disable import/start. | There is no instant first-use public service. Private file delivery and a trustworthy operator still have to exist; encryption and validation do not establish operator trust. |
| Same-slot prepared-key replacement | Confirmed import can replace the same saved/managed slot's prepared key while active, paused or pending. Local scope guards do not start/stop collection, make HTTP requests or alter session management; they reject a different unresolved slot or unknown startup scope. | Import does not prove server activation or erase old setup files. Actual Android chooser, Keystore replacement and lifecycle behavior remain untested. |
| No emergency ticket entry | Prepared Start automatically requests a scoped ticket; there is no per-start code text field. One slot is preserved through pause. | A missing, corrupt or incompatible setup blocks new sessions. Guidance and snapshots remain independently available. |
| Explicit same-link resume | Resume disclosure says existing link holders may receive new estimates, preserves the original end choice and warns about visible indicators. Service source reconciles the earlier session/sequence before registering new location updates. | System process death cannot provide a callback or guarantee remote Stop. A user must reopen/confirm, regain permissions where needed and reconnect. This sequence has not run on Android here. |
| Stop, reset and forgetting are distinct | Paused Stop link sits outside the page deck, including calculator view. Own-slot reset has an explicit disclosure/confirmation; forgetting preparation says it does not revoke a link or erase exported files and is blocked while local management is unresolved. | A modal covers the strip: Cancel then Stop, or a visible notification action for an active foreground session. Offline reset/revocation needs server confirmation; remote copies remain. |
| Cancel and stale callbacks | Dialog forward actions are gated by the final disclosure page and single-handled. Import epochs, chosen-file state and Activity checks reject late selection/read callbacks. Leaving preparation or calculator cancels the pending selection/read; picker errors are caught. | Rotation/process loss during file selection discards the unfinished attempt; the file must be selected again. Saving already explicitly confirmed is asynchronous. No actual picker, repeated-tap, rotation or permission-dialog run was performed. |
| Honest sharing and map use | Snapshot versus recipient link, chooser versus receipt, visible foreground indicators, stale data and external coordinate-snapshot map use are disclosed. No address/geocoder is supplied. | Delivery, live hosting, location accuracy, browser layout and recipient privacy outside the controlled page are not guaranteed. |

### Source minimum tap counts from Home

These are **lower bounds**, assuming the requested control is visible: additional PageDeck/disclosure Next taps, Android permission/picker/chooser actions and recipient-app actions are excluded. They do not demonstrate that all controls fit on the first screen. Live Start/Resume require a separately configured reachable operator; this unhosted lab is disabled by default.

| Goal | Minimum app taps / path |
|---|---|
| Support, quiet/hiding, unable-to-leave, warning signs, peers or family/friend choices | 1 to the named home route; actual external handoff then needs the provider action and its confirmation. |
| Open 911 dialer | 2: 911 → Open 911 dialer. Placing a call remains a separate phone-app action; no real call was tested. |
| Snapshot export | 4: Find Me → Get a location fix → Share/Copy snapshot → confirm; a successful fix is required. |
| Start Until Stop with prior preparation | 4: Find Me → Live links → Share until I stop → Start sharing. Creating a recipient link does not send it. |
| Import preparation | 6: Find Me → Live links → Prepare → Import → Choose setup file → Save setup, plus the system file picker and disclosure pages. |
| Resume an interrupted earlier link | 4: Find Me → Live links → Resume earlier recipient link → confirm, plus any permission prompts. |
| Export known live/paused read link | 4: Find Me → Live links → Share/Copy recipient link → confirm, plus any Android chooser. |
| Explicit own-slot reset | 5: Find Me → Live links → Review preparation → End earlier link → confirm. |
| Stop an active/paused link | 1 from an unobscured persistent session strip; with an app modal open, Cancel → Stop requires 2. |

No new functional blocker was found in this targeted source review. It does not resolve the stated first-use, finite capacity, stale-backup, runtime, access or operations limits. No additional UI polish was undertaken in this audit.
