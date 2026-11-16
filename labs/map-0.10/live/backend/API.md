# Safe Bridge 0.9 lab backend — protocol 3

This is an undeployed private Node.js 24 test implementation. It provides crash-durable **coordinate-free control state** and keeps location payloads only in process memory. It is not an available public service, emergency monitoring, an uptime promise or a public-ready safety product. No real prepared access, operator key, public endpoint or movement was used in its tests. Earlier 0.6–0.8 checkouts are unchanged.

## What survives restart

The encrypted SQLite database retains the generation, server MAC/admission secrets, scoped credential verifiers, session/creation identity, chosen end, terminal state, last accepted sequence, prepared-slot bindings/epochs and legacy test-only spend metadata. A clean or abrupt server restart keeps the **same IDs, links and generation**. Nonterminal sessions reopen as `paused`, with `point:null` and no restored point lease. A still-consented sender must explicitly resume its foreground collection and send a fresh higher sequence. Server restart never starts Android collection, reconstructs a position or ends an Until Stop session silently.

Stop, chosen expiry, owner reset, creation and its admission-slot spend, and an accepted sequence are committed atomically before a success response is sent. Lost acknowledgements remain uncertain to the client until it retries/reconciles. Filesystem/storage failures poison the live server: positions are cleared from memory, subsequent requests receive `503 durable_state_unavailable`, and no success is invented. Repair/restart is an operator action; there is no automatic empty reset.

## Private preparation and initialization

Production requires all of: initialized durable state, prepared grants, one approved HTTPS origin, TLS certificate/key, a persistent private volume and an accountable operator. Anonymous creation is only an explicitly selected loopback fixture. Legacy operator HMAC tickets are only an explicitly selected developer fixture and cannot enable production creation.

A prepared installation receives a private four-key bundle:

```js
{v:1, endpoint:"https://APPROVED-ORIGIN", slotId:"base64url22", preparedToken:"base64url43"}
```

The endpoint must already be approved/configured by the app owner; a bundle cannot select an arbitrary host. The separate verifier-only operator configuration is:

```js
{v:2, endpoint:"https://APPROVED-ORIGIN", grants:[{slotId,verifier,disabled:false}]}
```

V1 enabled-only input remains compatible; absent disabled policy means false. Newly provisioned/exported configuration uses v2 and preserves disabled flags. The private four-key installation bundle stays v1.

Slot IDs encode 16 random bytes; prepared tokens encode 32 random bytes, canonically in base64url. Verifiers are domain-separated SHA-256 values binding the canonical origin, slot and secret. The server never stores a raw prepared token. Configured grants reserve at most the sealed active capacity. There is no public enrollment, account requirement for recipients, automatic grant generation on request, or master key embedded in the APK.

The provisioning helper creates private files without printing bundle values:

```sh
node live/backend/provision-prepared.mjs --endpoint HTTPS_ORIGIN --grants-file PRIVATE_CONFIG --bundle-file NEW_PRIVATE_BUNDLE --capacity N
```

Privately create a separate 32-byte CSPRNG state key, encoded as one canonical base64url value in a 0600 regular key file. This work does not create or commit a real operator key. Initialize only an explicitly chosen new private state directory:

```sh
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --grants-file PRIVATE_CONFIG --approved-origin HTTPS_ORIGIN --capacity N
```

The initialization command refuses an existing database and does not start a server. Missing/corrupt state is not permission to rerun initialization over it. Protect the state key separately from the database and protect both from rollback, copying, unintended backups and unauthorized operators. Losing the state key makes the existing state unreadable; creating another key does not recover sessions.

Production startup:

```sh
node live/backend/server.mjs --tls-cert CERT --tls-key TLS_KEY --approved-origin HTTPS_ORIGIN --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --port 8787
```

Default bind is loopback. A separately authorized hosted deployment must deliberately choose its network binding, maintained TLS/provider configuration and logging policy. This work did not do that. Host/Origin must match the exact approved HTTPS origin. Forwarded-address headers are not trusted.

To append grants without resetting existing links/epochs, stop the authoritative server, extend the verifier configuration with the provisioning helper, then explicitly install it:

```sh
node live/backend/init-state.mjs --install-grants --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --grants-file PRIVATE_CONFIG
```

Installation is **add-only**: same endpoint, every existing slot/verifier/disabled policy unchanged, new total within the sealed capacity. Removal, replacement or endpoint changes are refused. Existing session/epoch/generation state is preserved; only new slots start empty at epoch zero. Restart opens that same state. Merely editing a config file does not activate a grant. The server holds an exclusive SQLite lock, so another process cannot install state while it runs; shared live store objects also reject installation. Existing grant policy or secrets are changed only through the explicit offline controls below. Changing sealed capacity or migrating origin/key formats still needs a separate operator migration.

## Offline disable, enable and secret rotation

These commands require the authoritative server to be stopped. Every action opens and authenticates the existing store before any private output publication. Unknown, duplicate, conflicting or misspelled flags are refused; there is no admin HTTP endpoint.

```sh
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --disable-prepared-slot SLOT_ID
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --disable-prepared-slot SLOT_ID --end-current
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --rotate-prepared-slot SLOT_ID --bundle-file NEW_PRIVATE_BUNDLE
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --enable-prepared-slot SLOT_ID
```

Disable makes the prepared token unusable for admission or slot reset, and advances the epoch so pending old tickets cannot create a new session. **By default it preserves the existing active/Until Stop ID, writer/read/revoke capabilities, sequence and chosen end.** Repeating an already-applied disable without another change is idempotent. Only the explicit `--end-current` choice ends its current session and clears the binding. The current chosen deadline is never moved. Revoking future prepared access does not erase copies of locations already received.

Rotation publishes a new private same-slot v1 bundle with a fresh random prepared token, then atomically changes the stored verifier and epoch. It preserves the disabled flag and current session by default. Thus old prepared tokens/tickets stop working, while an existing link and its writer/read/revoke credentials deliberately remain usable; choose explicit `--end-current` with rotation if that session must also end. Rotation of a disabled slot does **not** enable it. Enable is separate, preserves any current session, and advances the epoch. Enabling without rotating restores use of the existing prepared secret; for a compromised secret, disable → rotate → enable is the distinct recovery sequence.

The new bundle is private, no-clobber and fsynced before the database can commit. A private receipt is also fsynced at `NEW_PRIVATE_BUNDLE.rotation.json` before commit. Its HMAC under the persisted private MAC key binds generation, origin, slot, old verifier/epoch, new verifier, disabled policy, explicit ending choice and expected current ID. Changing receipt fields or substituting a bundle cannot authenticate. Recovery of a destructive ending additionally requires that the current ID still matches the original intent, so a previously empty slot's later new session is not accidentally ended.

```sh
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --rotate-prepared-slot SLOT_ID --bundle-file EXISTING_PRIVATE_BUNDLE --recover-rotation
```

Repeat `--end-current` on recovery only if it was present in the original rotation; mismatched choices are refused. Recovery authenticates the receipt and bundle. If the stored verifier already equals the new verifier, it succeeds without incrementing the epoch or replaying an end against a later session. Otherwise, the old verifier/epoch/policy must still match exactly before one atomic commit. An old receipt cannot roll back a later rotation. This compare-and-swap recovery is not a defense against a privileged complete database rollback, which remains the documented independent-anchor limitation.

A failed or interrupted command never overwrites or deletes a published bundle/receipt and never prints a secret. A crash after bundle publication but before a complete authenticated receipt leaves an **unactivated** orphan bundle; the database was not committed. Keep those files private and choose a fresh output path for a new explicit attempt; do not import the orphan or blindly reuse it. If both files exist, explicit recovery can distinguish old/uncommitted state from already-committed state. A corrupt database still fails closed; recovery is not permission to initialize over it. No success acknowledgement is emitted until the SQLite transaction commits.

After an offline change, explicitly export the current verifier-only policy to a new private file:

```sh
node live/backend/init-state.mjs --state-directory PRIVATE_DIR --state-key-file PRIVATE_KEY --export-prepared-grants NEW_PRIVATE_CONFIG
```

This outputs v2 with every slot's current verifier and disabled flag, no prepared token. It never overwrites a file. Use this current export for later provisioning/appends and add-only installation. A stale pre-rotation verifier or old enabled policy is rejected; config installation cannot undo disable, rotation or enable. The export is a provisioning view, not a backup of session bindings/epochs and not a way to replay them.

## Factory and store interfaces

`durable-store.mjs` exports synchronous helpers:

```js
initializeDurableStore({directory,stateKey:Buffer32,capacity,preparedGrants,nowMs}) // explicit new state
openDurableStore({directory,stateKey:Buffer32})
installPreparedGrants(store,{endpoint,grants:Map}) // offline, add-only
readStateKeyFile(privateFile)
```

The initializer returns an open store. `load()` returns a cloned, validated coordinate-free snapshot; `commit(snapshot)` uses one FULL-synchronous transaction; `close()` releases the lock and clears its key copy. Production initialization uses `validatePreparedGrantConfig`/`readPreparedGrantsFile` from `prepared-admission.mjs`. There is no implicit production state creation.

`createSafeBridgeServer({durableStore:store,tls,approvedOrigin})` owns and closes its supplied store. A store can have only one authoritative server, including within one process. If construction fails before ownership is established, the caller must close its store. A prepared HTTP fixture uses `{development:true,durableStore:store}` and binds only to loopback. A synthetic anonymous fixture may use `{development:true,allowAnonymousCreation:true}`; optional durable fixture state must have no prepared grants. Legacy fixtures require `{development:true,allowLegacyAdmissionForTests:true,admissionKey:Buffer32}`. These modes cannot mix with prepared grants or production.

Optional test knobs retain `clock`, active `capacity` (sealed by durable metadata), terminal cache quota, lease/recent/permit durations and rate limits. The SQLite file is `PRIVATE_DIR/control.sqlite`. Temporary keys/databases used by tests are outside the source checkout and removed afterward.

## Durable format and limits

The installed Node version is 24.22.1. Its synchronous `node:sqlite` API is documented as release-candidate stability; pin and review runtime updates rather than assume every version is interchangeable. [Versioned Node SQLite documentation](https://nodejs.org/download/release/v24.22.1/docs/api/sqlite.html).

SQLite uses DELETE journaling, `synchronous=FULL`, lifetime `locking_mode=EXCLUSIVE`, in-memory temporary storage, defensive mode and no extensions. Startup verifies schema, quick integrity check and configured pragmas. The exclusive connection lock is released by the OS after process death; another stale cached server cannot concurrently open the same store. FULL synchronization and explicit transactions provide the software commit boundary, subject to the filesystem/device honoring synchronization. Real power-loss/storage-controller behavior was not tested. [SQLite transaction semantics](https://www.sqlite.org/lang_transaction.html), [SQLite synchronization pragmas](https://www.sqlite.org/pragma.html#pragma_synchronous).

Each record value uses AES-256-GCM, random 12-byte nonce, 16-byte authentication tag and AAD `SafeBridge durable v1` + NUL + bucket + NUL + ID. Values are authenticated before parsing. An encrypted manifest binds a revision and every sorted bucket/ID/ciphertext digest. Missing records, corrupt tags, swapping records across IDs/buckets and replaying just an older record against a newer manifest fail closed. [Node authenticated encryption API](https://nodejs.org/docs/latest-v24.x/api/crypto.html).

Buckets are `meta/server`, `sessions/ID`, `grants/SLOT`, and legacy fixture `spent/HASH`. Plain database indexes reveal bucket names and random identifiers; value encryption is not whole-disk encryption or anonymity. A strict schema permits only:

- Meta: schema/protocol, generation, encrypted server MAC/admission secrets, clock floor, sealed capacity and endpoint.
- Session: ID/state, chosen duration/deadline, metadata purge time, HMAC creation/credential verifiers, last sequence and optional prepared slot.
- Grant: slot/verifier, disabled policy, epoch and current session/creation-hash binding.
- Legacy test spend: hashed nonce and expiry.

**No coordinate, accuracy, sample/capture time, sample flags, received position, update payload/digest, raw session token, creation key, ticket or prepared token enters SQL.** These fields are absent even inside the encrypted plaintext. Current coordinates and duplicate-update digests exist only in RAM. Restart loses them by design.

The database/manifests do **not** provide an independent monotonic anchor. A privileged operator can restore a complete old internally consistent database plus manifest; the same encryption key can still authenticate it. Such rollback can undo a Stop or slot epoch and restore old write authorization, although no old coordinate can be restored from this database. This was explicitly demonstrated by an independent test. Preventing/detecting full rollback requires separately trusted monotonic storage/anchoring or a stronger operational protocol; it is not claimed here. Backups, snapshots, volume restores and operator access must be reviewed before deployment. An authenticated manifest detects partial-record tampering; it does not solve full-snapshot rollback.

## Protocol 3 configuration and shared authorization

`GET /v1/config` returns:

```js
{protocolVersion:3,durableSessionState:true,preparedAdmission:true,admissionRequired:true,
 generation,createPermit,serverTimeMs,minDurationSeconds:1,maxDurationSeconds:2147483647,
 untilStopSupported:true,leaseMs:90000,recentMs:30000}
```

Synthetic modes truthfully report different durability/preparation flags. A production client must require the expected protocol/features. `createPermit` is a public per-generation freshness permit, not prepared authorization. Generation and MAC/admission secrets remain stable across normal durable restart.

Session credentials remain independent canonical random 32-byte write/read/revoke tokens. Use `Authorization: Bearer TOKEN` and exact `X-SafeBridge-Generation` on session **and prepared** operations. Wrong/missing prepared generation is rejected before mutation. Session wrong scope/token/ID/generation uses uniform 404. No cookies, query credentials, wildcard CORS or redirects to arbitrary origins. JSON bodies have exact keys, 4,096-byte limit, five-second body-read timeout and strict JSON/UTF-8 content type; unsupported encodings are refused. Errors do not echo values.

## Prepared admission and explicit reset

`POST /v1/admission`, bearer prepared token, exact body:

```js
{slotId,creationKey,expectedSlotEpoch:null /* first explicit intent only */}
```

Response:

```js
{generation,slotId,creationKey,admissionTicket,expiresAtMs,slotEpoch}
```

A returned ticket is bound to this generation, slot, epoch and creation key, lasts 120 seconds (helper maximum 300), and is never a recipient credential. Its issuance does not allocate a session. The client must atomically save ticket **and slotEpoch before any POST /shares can escape**. A refresh/retry uses the saved integer `expectedSlotEpoch`; mismatch returns `409 admission_epoch_changed`, never a fallback to null. Null is allowed only for a new explicit start/phase proving that creation was never attempted. If the initial issuance acknowledgement was lost before journaling, no share request could have escaped and no link existed to resurrect.

One nonterminal session binds each prepared slot. Another creation key in that occupied slot returns `409 prepared_slot_active`; no automatic reset occurs. The same key may renew a ticket for lost-creation-ACK recovery, provided its pinned epoch still matches. Stop, positive expiry and owner reset atomically advance the epoch and release the binding, so old exact tickets cannot allocate a replacement after terminal-cache eviction. Existing idempotent creation mappings only return their original session, including a terminal one. A grant owner can deliberately authorize a **new ID** later; no new ID reactivates an old link.

`DELETE /v1/admission/current`, bearer prepared token, exact body `{slotId}`, is the separately confirmed owner reset. It stops only that slot's existing/orphan session, clears its binding and increments its epoch durably. Response is `{generation,slotId,state:"stopped"|"none",serverTimeMs,slotEpoch}` with no session ID or position. It does not revoke the reusable prepared grant or cancel every future authorized never-allocated intent. Other slots are unaffected. A compromised holder of this slot secret can interrupt its own slot; protect the installation bundle. The first legitimate reset of an active slot gets Stop priority; retries/no-current resets face ordinary limits.

Prepared spend is the durable slot/epoch/binding transition, not a shared global spent-nonce cache. An orphan Until Stop session can remain paused indefinitely and reserve **its own** slot until owner reset; it does not take another prepared installation's reserved slot. Grant count cannot exceed capacity. There is no hidden one-hour, 90-second, or 4,096-session lifetime expiry. Shared IP/global budgets and CPU/network/disk failure can still affect other users; reserved quota isolation is not availability isolation or a DoS guarantee.

## Session creation, chosen end and replay

`POST /v1/shares`, bearer write token, exact eight keys:

```js
{generation,createPermit,creationKey,readToken,revokeToken,durationSeconds,
 admissionTicket,requestedExpiresAtMs}
```

Duration metadata is integer 0–2,147,483,647. Requested expiry is a nonnegative safe integer, zero iff duration is zero; otherwise it must be after current server time and no more than INT_MAX seconds ahead. The server uses the **exact immutable requested deadline**, never creation time plus duration. Delayed creation cannot extend the chosen end. Android absolute starts preserve the original picked epoch time; relative starts establish one conservative server-clock deadline from the original remaining monotonic budget. Device/server wall-clock disagreement is a real limitation, not a reason to silently shift the end.

Creation atomically validates the prepared ticket/epoch/reserved slot, binds the slot, stores scoped HMAC verifiers and commits before returning 201. Identical retained creation key + credentials + duration + requested deadline returns 200 with the same ID/current state, before ticket/permit expiry checks. Different immutable identity returns 404. A new already-elapsed deadline returns `400 chosen_end_elapsed` without allocation or spend. Expired/malformed prepared tickets give 403; stale epoch/occupied slot 409. No raw ticket or session token is stored.

A new explicit start uses new credentials/key. An unknown creation result must retry its journaled immutable identity and pinned epoch; it must never silently reset the slot or generate another identity. Until Stop means no chosen deadline, not guaranteed uninterrupted collection.

## Position and management endpoints

`PUT /v1/shares/{id}/latest`, bearer writer, keeps exact fields:

```js
{sequence,latitude,longitude,accuracyM,sampleTimeMs,sampleAgeMs,approximate,mock}
```

Sequence is safe integer >=1; finite latitude -90…90, longitude -180…180, accuracy 0…100,000; nonnegative safe sample time; integer age 0…30,000; boolean flags. For a fresh sequence, observed body delay is added to claimed age; over 30 seconds gives `400 stale_point`. Device claims are not verified physical truth. A new accepted point replaces only the RAM point and commits its sequence before ACK. Duplicate identical updates within the same process do not renew TTL; changed/equal or old sequences return 409. After restart, no digest is retained, so the sender must query management `lastSequence` and choose a higher fresh sequence; it cannot require payload-idempotence from a deliberately unpersisted sample.

Point TTL is 90 seconds capped by a positive chosen end. After TTL, server point/digest availability becomes paused/nonterminal; a fresh higher sequence can resume the same ID. Positive chosen end or Stop is terminal and cannot resume. Missing/terminal PUT never recreates a session.

Management create/PUT/status/DELETE responses:

```js
{id,generation,state,serverTimeMs,expiresAtMs,leaseUntilMs,managementReceipt,lastSequence}
```

`GET /v1/shares/{id}/status` accepts writer or revoke; DELETE accepts revoke only. Last sequence is management-only, not added to recipient latest responses. The position's own sequence remains part of a point received while live. First valid session Stop bypasses ordinary request budget once; terminal retries are limited. Offline Stop immediately halts local collection but remains remotely pending until acknowledged/reconciled. Already sent bytes/copies cannot be recalled.

`GET /v1/shares/{id}/latest`, bearer read, returns status **without managementReceipt/lastSequence**, plus `point`, `pointAgeMs`, `freshness`. Paused/waiting/terminal has null point/age; freshness is waiting/paused/recent/stale/ended. It never returns disk-restored location. A recipient can retain only a point it previously received in tab memory, clearly marked last known/unverified; confirmed Stop/expiry/manual Clear erase it. The service does not imply that a person is watching or safe.

Terminal cache remains bounded separately and can purge old coordinate-free session rows. A stable signed managementReceipt plus correct write/revoke capability can reconcile a missing terminal row via management status/DELETE to `state:"unavailable"`, no point, lease zero and receipt-bound last sequence. This means no retained session, not proof of a specific Stop acknowledgement or safety. Receipt never grants read or PUT/recreation rights. Its MAC/generation survive normal durable restart. Privileged full database rollback remains the stated limitation.

## Runtime limits and ship gates

Default nonterminal capacity 1,000 (sealed at initialization), terminal cache 1,000; each prepared grant reserves one slot within capacity. Terminal metadata is bounded to 24 hours after transition or earlier cache eviction. Positive expiry/Stop/reset release the reserved active binding; an Until Stop orphan never receives a hidden end. Prepared epochs/bindings persist independently of terminal cache. Legacy developer nonce cache is not production admission.

Global/IP limits remain 12,000/minute, 600/minute per rotating network digest and 20 creation attempts/15-minute digest window. Prepared issuance/no-current resets add a per-slot 30/minute guard. The first legitimate active Stop/reset is prioritized, not every request. Request size/time/header limits remain. Raw IP addresses are not retained in application rate buckets, but hosts/providers necessarily see connections. App logging has no coordinates, tokens, tickets, receipts, IDs or raw IPs; production proxy/storage/crash/swap policies still need review.

Self-hosted viewer/assets use no-store/no-referrer, strict first-party CSP, no cookies/CORS, no recipient geolocation prompt or automatic map/geocoding. Traversal/symlink escapes and oversized/unapproved static files are refused. Capability links remain forwardable secrets exposed to browser history/extensions/messages/screenshots or monitoring. Calculator mode does not conceal OS location/notification activity.

Local process-kill/HTTP/TLS/cryptographic/SQLite tests are evidence for the cases run, not real Android, real power-loss, production uptime, disk-controller guarantees, legal compliance or survivor safety certification. Deployment still needs persistent-volume/backup/rollback controls, private grant delivery and tested operator recovery practices, TLS/domain/provider setup, operational incident/abuse ownership, independent security and survivor/child-safety/privacy/legal review, and accessible receiver plus real Android lifecycle/network testing. No public service, real keys or deployment were created by this work.
