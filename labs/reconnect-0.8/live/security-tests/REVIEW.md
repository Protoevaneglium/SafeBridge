# Independent security review: reconnect lab 0.8

November 16, 2026. Scope is the separate `safe-bridge-reconnect-lab` protocol-2 experiment. Earlier 0.6/0.7 artifacts were not modified by this reviewer. No deployment, hosting account, real location, real recipient or emergency call was involved. All execution used synthetic data and loopback or injected transports.

## Executed independent evidence

| Suite | Result | What actually ran |
|---|---:|---|
| `backend-adversarial.test.mjs` | 28 cases passed | Actual HTTP server requests, controlled server clock and slow-body/race scenarios |
| `backend-tls.test.mjs` | 9 cases passed | Actual TLS listener, temporary certificate/key, signed admission and isolated Node clients |
| `recipient-privacy-v2.test.mjs` | 21 cases passed | Actual state/controller/deck modules in simulated DOM; actual local asset/header requests |
| `LiveCoordinatorAdversarialTest.java` | 126 assertions passed | Actual pure-Java coordinator with injected transport/store/clock and controlled thread gates |
| `LiveServicePolicyAdversarialTest.java` | 67 checks passed | Actual pure-Java retry/end-choice helpers, plus explicitly source-based service integration checks |

The recipient suite was independently authored in parallel by the usability reviewer and rerun with this review's HTTP/TLS files. These checks are already included in the parent verification run. Do not count them again or combine Java assertions and Node cases into a single claim of test coverage. Reproduction commands are in [README.md](README.md).

TLS material is generated in a private OS temporary directory, trusted only by the explicit test client, and removed afterward. Tests require default untrusted-CA and wrong-hostname rejection, approved Host/Origin handling, TLS privacy headers and plaintext refusal. They do not exercise Android HTTPS, audit a real domain/certificate or establish production security. No key material is stored as a source fixture.

## Findings corrected and checked

The 0.7 behavior was confirmed, rather than dismissed as deployment-only work: upload I/O failure stopped the sender, the 90-second lease ended consent, durations were restricted, terminal records consumed active quota, and the recipient discarded offline last-known location. The protocol-2 tests establish narrower, usable replacements:

- A point ages out after 90 seconds and the server removes its coordinates. The session becomes nonterminal `paused`. Existing in-memory foreground consent can reconnect with a fresh newer sample using the same ID and read link. Duplicate old data cannot restore a purged point or renew freshness.
- Duration zero is Until Stop, including after more than a day of synthetic elapsed time. Positive chosen end time remains an independent immutable bound. Stop during an outage cancels local consent; retry cannot resume collection or allocate a replacement session.
- Transient connection and selected HTTP failures preserve consent and use bounded backoff. Stale samples are dropped. TLS, authorization, malformed protocol and storage failures fail closed. The actual service catch branch is source-checked to call the executed policy; it is not an Android runtime test.
- Active quota is separate from the bounded terminal cache. Repeated Stop cycles beyond terminal-cache capacity release active quota. Production cannot use anonymous creation. Missing/forged/expired/old-generation tickets cannot fill it. Spent-ticket nonce guards survive terminal eviction and fail closed when full rather than discarding unexpired replay protection.
- Idempotent creation preserves capabilities and one allocation across acknowledgement loss. A specific authenticated no-allocation admission rejection clears its journal so a new explicit valid code can be used. Ambiguous I/O/5xx/lost acknowledgement does not pretend creation failed.
- Management receipts are scoped to server generation, ID, immutable lifetime and management credentials. An authenticated missing-row result is `unavailable`, not a claimed Stop acknowledgement. Read credentials cannot use receipt recovery, recipients do not receive it, and it cannot revive a session or accept a PUT.
- Read/write/revoke/status separation, generic errors, token canonicalization, no secret reflection, latest-only retention, strict schema/body/media/coordinate/time limits, server-observed body age, hostile Host/Origin/query requests, no-store/no-referrer/CSP headers, static path boundaries and first-valid-Stop priority are covered by actual HTTP/TLS cases.
- Journal acknowledgement failures suppress sending/read-link activation; offline Stop persists stopped management; process restoration does not silently resume collection. Remote terminal acknowledgement remains distinct from local credential-cleanup failure. Persisted management has no coordinates or recipients.

Two additional deadline issues were found during final independent review:

1. Delayed creation originally applied a full duration at server creation, moving availability past the user's selected end. Creation now requires exact `requestedExpiresAtMs`, carried unchanged through retry. A newly elapsed end rejects before allocation or ticket consumption; a new explicit end may reuse that unspent ticket. The actual HTTP and signed-ticket TLS cases verify these conditions.
2. The local coordinator subtracted network delay twice from a hard chosen deadline by anchoring remaining server time to request start. A controlled 90-second POST with 30 seconds remaining reproduced premature local termination. The regression also covers a delayed status response. Hard-deadline calculation now uses response-arrival time capped by the original local elapsed deadline, while sample/lease freshness remains conservative. It cannot extend the original consent budget. Absolute/relative precreation-outage regressions verify the original target/budget is preserved.

The recipient reviewer also reproduced a stale-revalidation deadline extension in the first version of the RAM cache. The receiver now pins the earliest known positive deadline and cannot erase it with an Until Stop sentinel or stale response. Offline/hidden chosen-expiry, confirmed Stop and Clear wipe cached state and pending map consent. Transient failure/paused state retains RAM-only last-known coordinates with growing age, accuracy/time and explicit uncertainty.

Optional Google Maps requires separate paged disclosure and final confirmation; cancellation, hiding, clearing and termination invalidate pending consent. The URL contains selected coordinates only, without session capabilities. No map tiles, geolocation API or geocoder run automatically. These are simulated-controller/module and local HTTP tests, not visual browser tests or external Google requests. A street address is explicitly unavailable.

## Stop, expiry and network dispatch

Local collection Stop and authenticated remote Stop are separate. Generation invalidation, retry cancellation, pending-fix clearing and provider removal precede journal/network work in the service source. The coordinator releases its state lock before blocking transport so Stop can stay responsive. An update that passed its generation check can still dispatch or finish after the Stop tap. The Android HTTPS adapter has no cancellation handle, and canceling could not recall bytes already queued or received. No zero-post-Stop-packets claim is justified.

The backend rechecks state/time after reading a body. Acknowledged Stop is terminal, and delayed writes cannot revive it. A separate revocation executor avoids serializing Stop behind a blocked update. Before acknowledgement an already in-flight update may arrive. Its point erasure interval starts at the server's last accepted fresh update, not necessarily the Stop tap. For positive chosen ends, exact server expiry is an additional bound. Until Stop has no universal hard expiry: offline local Stop may leave a remote session open until confirmed revocation, process restart or shutdown, even though its coordinates age out. Copied positions and forwarded links cannot be recalled.

## Remaining technical, product and runtime blockers

- **Admission and abuse operations:** the offline issuer exists, but private code distribution, user onboarding, operator authorization/rotation/recovery and an accessible admission process are not implemented as a public service. A bearer code can be stolen or spent before the intended user. Recipients need no account, but that does not solve sender admission. Nonce saturation deliberately rejects starts, and network denial of service still affects acknowledgements.
- **Indefinite orphan capacity:** losing management or the device can leave an admitted Until Stop session paused indefinitely and occupying active quota. Point erasure does not free that quota. Automatically expiring it would violate the chosen semantics; a real operator recovery/administration and capacity policy remains unresolved.
- **Lifetime clocks:** the original absolute end depends on device/server wall-clock accuracy. Clock disagreement may reject or shorten a choice. Local monotonic caps and exact remote targets avoid silent deadline extension; they do not establish synchronized clocks or exact OS wakeup.
- **Recipient privacy and address:** an offline Until Stop recipient tab can retain last-known RAM until Clear or a confirmed terminal response. This does not prove current location or consent to further redistribution. Copied data and forwarded read links remain usable outside the app's control. After explicit map consent Google receives coordinates and device/network metadata; street address/geocoding remains unimplemented.
- **Backend architecture:** this is one memory-only process, not durable/high-availability infrastructure. Restart invalidates old links/tickets; no multi-instance replication, recovery SLO or production capacity/load evidence exists. The operator sees coordinate plaintext within TLS; this is not end-to-end encryption or anonymity. Removing references is not physical RAM erasure.
- **Deployment and infrastructure:** the Android endpoint remains blank. No approved domain/TLS deployment, operator/budget, monitoring, incident response or ongoing maintenance exists. Proxy/access logs, swap, core dumps, backups, provider metadata and real retention have not been configured or audited.
- **Native and browser runtime:** no independent execution exercised Android Keystore, platform HTTPS, permission dialogs, hardware location, clipboard/share delivery, foreground-service start/notification changes, screen lock/Doze, Active apps Stop, force-stop, process death or OEM battery behavior. No real browser rendering, TalkBack, native large-text/landscape/accessibility or real sender-to-recipient field test occurred. Android source compilation and simulated DOM tests are not substitutes.

After the documented corrections, this review has no remaining demonstrated critical defect within its executed cases and inspected source. That is not a comprehensive security assessment, safeguarding approval or a guarantee of continuous tracking, concealment, rescue or recipient attention. Independent operational security, survivor-led privacy/safeguarding review and accessible real-device/browser tests remain necessary before offering live location to people in danger.
