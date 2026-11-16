# Safe Bridge 0.8 lab backend — protocol 2

Node.js 24, built-in dependencies only. This is an **undeployed private test implementation**, not emergency monitoring, an available public service, or a guarantee of uninterrupted tracking. Its tests use synthetic positions, ephemeral test credentials and local HTTP/TLS listeners. No operator admission key or issued ticket is committed. Older 0.6/0.7 checkouts are unchanged.

## Corrected behavior

The prior lab counted 24-hour terminal tombstones against the same 1,000-session quota as live sessions. Repeated starts/stops could prevent new sessions despite no current sharing. Protocol 2 tracks active and terminal pools separately, bounds terminal retention, and uses a separate spent-admission-ticket guard to prevent terminal-cache eviction from permitting creation replay.

The 90-second lease now erases a server-held position and produces nonterminal `paused`; it does not end consent or the session. A fresh newer upload can resume the same ID/link after an outage. Only `stopped` and `expired` are terminal. Duration zero means until explicitly stopped, with `expiresAtMs:0`; positive durations 1–2,147,483,647 seconds are supported. Android process death still must not silently restart collection. A recipient may retain a previously received position only in tab memory, label it as last known/unverified, and clear it on confirmed Stop/expiry or manual Clear. The server never returns an erased point.

## Running locally

From this reconnect-lab checkout:

```sh
node live/backend/server.mjs --dev-loopback --allow-anonymous-test-creation --port 8787
node --test live/backend/tests/*.test.mjs live/security-tests/backend*.test.mjs
```

The explicitly named anonymous test mode binds only to `127.0.0.1` and rejects non-loopback requests and hostile Host values. Without `--allow-anonymous-test-creation`, even loopback mode requires a private admission key file. This flag is rejected outside development mode. Anonymous mode is a synthetic-test exception, not a production access policy.

Production construction/startup requires all of: TLS certificate/key, one exact approved HTTPS origin, and a private 32-byte admission key. CLI flags are `--tls-cert PATH --tls-key PATH --approved-origin https://APPROVED-HOST --admission-key-file PRIVATE_FILE`; the default bind remains loopback unless separately configured. Production rejects mismatched Host/Origin and never trusts forwarded-address headers. No certificate, domain, hosting account, forwarding rule or public endpoint was provisioned for this work. Do not put the admission master key in the APK, recipient page, tickets, logs, or a checked-in environment file.

The module exports `createSafeBridgeServer(options) -> {server,generation,stats,close}`. Tests may inject `clock:()=>integerMs`. Explicit anonymous fixtures use `{development:true,allowAnonymousCreation:true}`. Signed-ticket loopback fixtures use `{development:true,allowAnonymousCreation:false,admissionKey:Buffer32}`. Other testable options include `capacity` (active quota, default 1,000), `terminalCapacity` (default 1,000), `nonceCapacity` (10,000), `rateCapacity` (2,000), `leaseMs` (90,000), `recentMs` (30,000), `permitTtlMs` (60,000), and `tombstoneMs` (86,400,000). No HTTP endpoint changes these values. Stats expose only aggregate numbers.

## Private operator admission

Recipients need no account, installed app, admission ticket, or operator key. Admission controls **creation by the sender**. An accountable operator must decide how authorized senders obtain tickets through a private process; this lab does not implement that distribution service or claim any eligibility/identity verification.

`admission.mjs` exports:

```js
issueAdmissionTicket({admissionKey: Buffer32, generation, nowMs, ttlSeconds: 900})
verifyAdmissionTicket(ticket, {admissionKey: Buffer32, generation, nowMs})
readAdmissionKeyFile(privatePath)
```

The issuer generates a random 128-bit nonce unless a synthetic test supplies one. Ticket format is:

```
base64url(JSON({v:1,generation,nonce,issuedAtMs,expiresAtMs})) + "." + base64url(HMAC-SHA256)
```

The signature uses the private admission key and domain `SafeBridge admission v1` followed by a NUL and the encoded payload. Generation and nonce are canonical 22-character base64url values. The ticket lifetime is 1–3,600 seconds; the server rejects future-issued, expired, wrong-generation, forged, malformed or reused tickets. Tickets are opaque to the Android app and at most 1,024 characters. They are bearer admission capabilities, so copied/stolen tickets can be used by whoever presents them first.

An offline operator CLI reads a separately supplied private regular key file, maximum 128 bytes, mode 0600 or stricter, containing one canonical 43-character base64url encoding of 32 CSPRNG bytes:

```sh
node live/backend/issue-ticket.mjs --key-file PRIVATE_FILE --generation CURRENT_GENERATION --ttl-seconds 900
```

The generation is public configuration from the intended server boot. The CLI intentionally prints the requested short-lived ticket only; distribute it privately and avoid shell recordings/history containing ticket values. It never prints the master key. This work does not create a real key file or commit issued tickets. Production key generation/storage/rotation and ticket distribution remain operator responsibilities.

The server stores only a keyed hash of each spent nonce until that ticket expires. This guard is separate from terminal-session retention, bounded by `nonceCapacity`, and checked before mutation. Capacity failure does not consume a ticket. If the nonce guard is full, creation fails closed with `503 admission_capacity_reached`; it does not evict unexpired spent nonces. Tickets are generation-bound, so restart invalidates old tickets even though the in-memory guard is lost. Signed admission prevents anonymous clients from filling open-ended active slots, but does not solve malicious/compromised authorized senders or lost-credential orphan sessions. The active quota remains a finite service limit; an operator must control ticket issuance and plan recovery, potentially ending all sessions by changing generation. Do not present this as an unlimited reliable service.

## Shared transport and credentials

Every session uses independent random 32-byte `writeToken`, `readToken`, and `revokeToken`, each canonical base64url, 43 characters. Session ID and server generation are random 128-bit base64url, 22 characters. `creationKey` is a canonical base64url random 16–64-byte idempotency key. Equal credentials are rejected. The server stores scoped HMAC verifiers and uses constant-time comparison, not raw session credentials. The admission master key is independent of session credentials and per-boot verification keys.

Use `Authorization: Bearer TOKEN` plus `X-SafeBridge-Generation: GENERATION` on authenticated endpoints. No cookies, query credentials, URL-path credentials, wildcard CORS or cross-origin browser calls. All query strings are rejected. Unknown IDs, wrong scopes/credentials/session/generation return uniform `404 {"error":"not_found"}` on authenticated operations, subject to ordinary rate limiting. Errors never echo body contents.

Bodies must be JSON objects with exactly the documented keys; `application/json` optionally with UTF-8 charset; absent/identity Content-Encoding only. Limit 4,096 bytes, 5-second body timeout, 8,192-byte headers. All state changes are synchronous after body parsing, with time/auth rechecked afterward. TLS is mandatory outside the explicitly opted-in loopback test mode.

## API contract

### `GET /v1/config`

Public JSON fields:

```js
{
  protocolVersion: 2,
  admissionRequired: true, // false only for explicit anonymous loopback fixtures
  generation, createPermit, serverTimeMs,
  minDurationSeconds: 1, maxDurationSeconds: 2147483647,
  untilStopSupported: true, leaseMs: 90000, recentMs: 30000
}
```

`createPermit` is a separate per-boot signed public freshness permit, valid 60 seconds by default. It is not admission authorization. A new creation needs a valid permit plus a private operator ticket, except anonymous loopback fixtures.

### `POST /v1/shares`

Bearer write token. Exact JSON keys:

```js
{generation, createPermit, creationKey, readToken, revokeToken,
 durationSeconds, admissionTicket, requestedExpiresAtMs}
```

`admissionTicket` is a nonempty opaque string in admitted mode. It must be JSON `null` only in anonymous loopback mode; omitted keys/empty string are invalid. Duration is integer 0–2,147,483,647. `requestedExpiresAtMs` is a required nonnegative safe integer: exactly zero iff duration is zero; otherwise an absolute chosen deadline after current server time and at most 2,147,483,647 seconds ahead. The server sets expiry to this exact value, never creation time plus duration. Duration remains immutable selection metadata; it is not recomputed into a new deadline after an outage. New creation returns 201 and a management status. An identical retained creation key plus write/read/revoke credentials, duration and requested deadline returns 200 with the same ID/current state and immutable expiry; this lookup precedes permit/ticket expiry and spent checks. It never resumes a stopped/expired session or extends TTL. Different credentials/duration/requested deadline for a retained key return uniform 404. Once its terminal row is evicted, replay cannot pass the spent-ticket guard while the ticket is live, or ticket expiry afterward. Freshly authorized explicit creation uses a new ID/link, never revives the old ID.

Persist encrypted management credentials, creation key, and pending ticket **before** the first request. A lost response is an uncertain result: retry the same immutable request, not new credentials. Active/paused rows are never evicted to make room; their creation mappings remain available. An unchanged syntactically valid pending request rejected with an invalid/expired permit or admission error has no retained active mapping in that generation. Do not apply that inference to a mutated request, credential mismatch, timeout, or arbitrary 404.

A new request whose chosen end has elapsed is rejected before allocation or ticket consumption. An existing identical mapping is resolved first, so a lost acknowledgement retried after its end returns that same expired session rather than allocating or shifting its end. Android absolute-end starts retain the original picked epoch deadline unchanged; the relative-duration engine computes one server-clock deadline from its initial remaining monotonic budget, conservatively subtracting configuration latency. Subsequent permit/admission/idempotent retries preserve it. Device/server wall-clock disagreement can cause rejection or a different real-world interpretation of a picked time; the app must disclose that limitation and must not silently move the end to make creation succeed.

Creation errors: `400 invalid_creation`, `400 chosen_end_elapsed`, `400 invalid_create_permit`, `403 invalid_admission_ticket`, `403 admission_ticket_spent`, `503 capacity_reached`, or `503 admission_capacity_reached`. No raw ticket is retained or logged. A malformed/null admission field outside anonymous mode is `invalid_creation`.

### Management status shape

Create, successful update, GET status and DELETE return:

```js
{id, generation, state, serverTimeMs, expiresAtMs, leaseUntilMs, managementReceipt}
```

`expiresAtMs:0` means no chosen end; otherwise it equals the original `requestedExpiresAtMs` exactly. `leaseUntilMs` is only the server-point erasure deadline and can be in the past for `paused`. Nonterminal states: `waiting_for_fix`, `active`, `paused`. Terminal session states: `stopped`, `expired`. Only management reconciliation may return `unavailable` as described below.

### `PUT /v1/shares/{id}/latest`

Bearer write token. Exact keys:

```js
{sequence, latitude, longitude, accuracyM, sampleTimeMs, sampleAgeMs, approximate, mock}
```

Sequence is a safe integer >=1. Coordinates/accuracy must be finite: latitude -90…90, longitude -180…180, accuracy 0…100,000 meters. Sample time is nonnegative safe integer milliseconds; sample age is integer 0…30,000; flags are booleans. Device claims are untrusted; neither the server nor a mock flag proves position accuracy/authenticity. For a new sequence, add observed time spent receiving/processing this body to claimed sample age; reject effective age over 30 seconds with `400 stale_point`. This prevents a body delayed through a server/process pause from presenting an old fix as fresh.

A valid newer point replaces the one point, records server `receivedAtMs`, sets `active` even after pause, and resets point TTL to 90 seconds capped by a positive chosen expiry. It returns 200 management status. Identical sequence/payload retries return current status without restoring a purged point or renewing TTL/expiry. Changed same sequence gives `409 sequence_conflict`; older sequence `409 out_of_order`; terminal session `409 session_ended`. Invalid fields return `400 invalid_point`. Same-ID resume requires a fresh newer sequence. Missing IDs always return 404, even with a management receipt; no PUT can resurrect missing or terminal state.

### `GET /v1/shares/{id}/latest`

Bearer read token. Returns status fields **without managementReceipt**, plus `point`, `pointAgeMs`, and `freshness`. With an available point, the point contains exactly the accepted fields plus `receivedAtMs`; stored sample age includes observed body delay. Point age is server elapsed time since receipt plus effective sample age. `freshness` is `recent` at most 30 seconds, then `stale`; `waiting` before a first fix; `paused` after point TTL; `ended` for stopped/expired. Waiting/paused/terminal responses contain `point:null` and `pointAgeMs:null`. There is no server route history or last-known fallback after erasure. An evicted terminal record returns generic 404 to the read capability.

A recipient may show only a position it previously received in its own tab memory, clearly labeled with time, age, accuracy and unknown current state during interruption. It cannot infer safety or ongoing sharing from a cached point. The browser's copies, screenshots and forwarded links cannot be recalled.

### `GET /v1/shares/{id}/status` and `DELETE /v1/shares/{id}`

Status accepts writer or revoke capability, returns no coordinates. DELETE accepts revoke only, atomically clears point/digest and ends an active/waiting/paused session as `stopped`. Repeated Stop is idempotent; an already expired session retains the truthful `expired` reason. The first valid revoke transition bypasses ordinary shared-IP/global budgets once per session; invalid attempts and terminal retries retain rate limits. Offline Stop must halt local collection immediately and say remote revocation is pending until acknowledged or reconciled. Already authorized in-flight bytes cannot be recalled.

### Bounded-cache management reconciliation

`managementReceipt` is an opaque, per-boot HMAC-signed receipt binding protocol, generation, ID, chosen expiry/duration, and write/revoke HMAC verifiers. Only management responses receive it; never include it in recipient links, read responses, plaintext journals, or logs. Persist it encrypted and send `X-SafeBridge-Management-Receipt` on management status/DELETE.

If a terminal row has been evicted/purged, valid receipt plus matching writer capability on GET status, or matching revoke capability on status/DELETE, returns 200 with `state:"unavailable"`, original chosen expiry, `leaseUntilMs:0`, and no point. It proves no retained session in that generation; it does **not** claim an acknowledged revocation, safe arrival, or reason for disappearance. Read tokens, mismatched/forged receipts, wrong IDs/scopes, and missing PUTs get no recovery access. Existing rows cannot be overridden by a receipt. A receipt cannot survive server restart because signing keys/generation change; clients can compare current configuration to establish that the old generation no longer exists.

This prevents a lost Stop acknowledgement followed by bounded terminal eviction from leaving an until-Stop sender permanently pending. A lost creation response without a receipt still resolves through the retained active creation mapping; never discard its management credentials merely because the first request timed out.

## Bounds, erasure and reliability

Default bounds: 1,000 nonterminal sessions including paused/open-ended sessions; 1,000 terminal cached rows; 10,000 spent ticket nonces; 2,000 rotating network-rate buckets. Ending/expiring a session immediately frees the active slot. Terminal metadata is retained for up to 24 hours **after the terminal transition**, or evicted oldest-first sooner at its separate quota. Eviction removes its creation mapping, not the separate spent nonce guard. Active/paused until-Stop sessions have no automatic chosen end; lost sender credentials can orphan authorized slots. Capacity and ticket issuance are deliberate finite admission controls, not a solution for unlimited orphan sessions.

Point TTL is enforced on each access and by a one-second cleanup timer. A paused row retains credential verifiers, sequence and a keyed digest for idempotence, but no coordinates. Stop/expiry clear the point and digest. Cleanup timers can be delayed by event-loop/process suspension; next access still checks time before returning coordinates. Physical RAM/swap/crash capture, hosting logs and process-forensic deletion are not certified. There is no database, replica, disk coordinate queue, analytics, route history or backup code. The operator can inspect plaintext memory; this is not end-to-end encryption.

Rate defaults remain 12,000 requests/minute globally, 600/minute per rotating network digest and 20 creation attempts per digest per 15-minute window. Digests use rotating HMAC keys, retain no raw IP in application state, and expire within that window. They are not identity and do not prevent distributed abuse. Incoming network addresses remain visible to the network/host. Application errors/logs do not print tokens, tickets, IDs, receipts, coordinates, URLs or IP addresses. Production providers, proxies, memory protections, retention and incident response need separate verification.

## Recipient files and deployment limits

`/`, `/view`, `/view/` serve `live/web/index.html`; safe self-hosted assets stay below that directory, with traversal/symlink escapes, directory listing, unknown extensions and files >1 MiB refused. Responses use no-store, no-referrer, nosniff, no framing, no cookies/CORS, a strict first-party CSP, and disabled recipient geolocation/camera/microphone. Capability link form remains `/view#s=ID&g=GENERATION&r=READ_TOKEN`. Browser fragment handling does not prevent copies/history/extensions/monitoring from exposing it. No third-party map or geocoding request occurs before an informed recipient choice, and no capability/receipt goes to a map provider.

Passing local tests does not verify production deployment, uptime, real device location, Android/OEM/battery behavior, notification visibility, survivor safety, legal compliance, or exact forensic erasure. Operator admission/ticket distribution, domain/TLS/provider configuration, access/logging review, survivor-led/child-safety/privacy/legal review, independent security testing, accessible receiver and real Android lifecycle/network testing remain ship gates. No production service was activated by this work.
