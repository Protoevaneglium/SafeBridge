# Independent security checks: durable lab 0.9

Reviewed November 16, 2026. Scope: `safe-bridge-durable-lab` only. Earlier 0.6, 0.7 and 0.8 artifacts were not changed by this reviewer. Tests use synthetic coordinates, credentials and clocks, private temporary files and loopback networking. No deployment, real recipient, real location or emergency call was involved.

## Final executed results

| Independent suite | Result | Actual execution |
|---|---:|---|
| `backend-adversarial.test.mjs` | 28 cases passed | General HTTP regression cases using the explicit anonymous, ephemeral loopback fixture |
| `backend-tls.test.mjs` | 9 cases passed | Production-mode protocol-3 prepared admission, durable SQLite and actual loopback TLS |
| `durable-store-adversarial.test.mjs` | 11 cases passed | Actual SQLite reopen, independent AES-GCM decoding, corruption/replay/ownership checks; includes one known-limit demonstration |
| `durable-backend-crash.test.mjs` | 14 cases passed | Separate server processes, actual HTTP, withheld replies, SIGKILL and reopening persisted state |
| `prepared-install-adversarial.test.mjs` | 3 cases passed | Actual private provisioning/init/install CLIs, admission and add-only state preservation |
| `prepared-recovery-adversarial.test.mjs` | 6 cases passed | Actual disable/rotate/enable/recovery CLIs; deterministic pre-transaction crash, authenticated CAS and active-session preservation |
| `recipient-privacy-v2.test.mjs` | 21 cases passed | Historical filename; runs the current web modules/controller in a simulated DOM and actual local asset serving |
| `recipient-privacy-v3.test.mjs` | 7 cases passed | Prepared durable HTTP restart/recipient state integration |
| `LiveCoordinatorAdversarialTest.java` | 206 assertions passed | Actual pure-Java coordinator with injected transport, journal, clock and thread gates |
| `LiveServicePolicyAdversarialTest.java` | 94 checks passed | Actual pure helpers and explicitly identified Android source contracts |

The eight Node files pass **99/99 cases** together. This includes a test that intentionally demonstrates the undetected complete-database rollback limit; it does not establish rollback protection. The recipient files were independently authored in parallel and rerun with the other security suites. These checks are included in the parent's full verification run and must not be added to that total again. Node cases, Java assertions and source contracts are different units.

Reproduce from the lab root:

```sh
node --test live/security-tests/*.test.mjs
java -m jdk.compiler/com.sun.tools.javac.Main -d build/security-host app/src/main/java/com/stange/safebridge/EndpointOriginRules.java app/src/main/java/com/stange/safebridge/live/LiveCoordinator.java app/src/main/java/com/stange/safebridge/live/LiveRetryPolicy.java app/src/main/java/com/stange/safebridge/live/LiveInterruptionPolicy.java app/src/main/java/com/stange/safebridge/live/LiveResetOnlyStore.java app/src/main/java/com/stange/safebridge/LiveSessionChoice.java live/security-tests/LiveCoordinatorAdversarialTest.java live/security-tests/LiveServicePolicyAdversarialTest.java
java -cp build/security-host LiveCoordinatorAdversarialTest
java -cp build/security-host LiveServicePolicyAdversarialTest
```

TLS tests generate a disposable certificate/key in a private OS temporary directory, trust it only in the explicit Node test client and delete it. Default untrusted-CA and wrong-hostname connections reject. No test or operator key is stored as a project fixture. These tests do not execute Android HTTPS or certify a deployed domain/certificate.

## What is established

Ordinary server crash recovery keeps the same generation, session ID and recipient link. Stop, chosen expiry, prepared-slot epoch and last accepted sequence survive process death. The server recovers a nonterminal session as paused with no point; a fresh newer sample can use the same link. Until Stop has no hidden one-hour/day lifetime. Coordinates and point metadata are excluded from the persistent control schema; actual accepted synthetic points were checked against reopened snapshots and plaintext file canaries, and records were independently decrypted.

A normal handler's reply is withheld after its durable commit, then the child is SIGKILLed. Reopening verifies create/PUT/Stop/reset state without reusing process memory. Incomplete-body kills allocate nothing. A reset acknowledged while an old creation body is incomplete advances the epoch; finishing that old body cannot allocate an empty orphan. Exclusive SQLite ownership prevents simultaneous stale server owners and is released by SIGKILL.

AES-GCM record AAD binds bucket and ID. An encrypted manifest binds the current record set and revision; corruption, missing records, schema injection, ciphertext swaps and partial same-ID old-record replay reject. Missing state or the wrong key fails closed rather than initializing a replacement identity. Explicit initialization refuses an existing database. Private paths, permissions and symlink boundaries are checked.

Prepared access requires prior private operator provisioning, offline installation and explicit sender import. Production has no anonymous or public self-enrollment fallback. Each prepared slot has one active session, and cycling one slot does not consume another slot's reserved capacity or a shared prepared nonce allowance. Admission tickets are bound to creation identity and slot epoch. The sender journals ticket and epoch before any creation POST; uncertain retries keep that integer pin and never silently request a new epoch. A fresh null epoch is allowed only before a ticket was acknowledged and creation could have been dispatched.

Offline operator commands can disable, enable or rotate prepared access. Default disable/rotation preserves the current session ID, writer/read/revoke capabilities, sequence and chosen end; an explicit `--end-current` is separate. Old prepared tokens cannot obtain new admission or reset the slot after disable/rotation. Disabled state survives rotation and requires explicit enable. Stale grant configuration cannot undo these changes, and a verifier-only v2 export preserves disabled flags. Actual CLI tests verify the behavior.

Rotation publishes a private fsynced no-clobber bundle and HMAC-authenticated recovery receipt before its SQLite commit. A test-only preload stops the actual CLI before `BEGIN IMMEDIATE`; SIGKILL then proves the files can exist while the DB is unchanged. Authenticated recovery applies the intended change once. Replaying an already applied `--end-current` receipt cannot end a later session, and the destructive choice is bound to the originally reviewed active ID. Bundle/receipt tampering rejects. Same-slot preparation replacement is allowed by the sender guard without automatically ending its current link.

An Android process restart restores a paused journal with zero HTTP and no active collection consent. Resume requires a user action, verifies the same server/session and reconciles sequence before fresh sending. System/TLS/protocol/storage interruption pauses rather than silently revoking. Explicit Stop and chosen end remain distinct. Unknown-ID prepared Stop uses authenticated own-slot reset when the matching private grant is available; missing access leaves it pending and does not create a temporary session just to discover its ID.

Recipient transient failure/restart can retain last-known coordinates in tab RAM with age, time, accuracy and unverified wording. A new tab cannot retrieve the old point from durable storage. Clear, confirmed Stop and known positive expiry clear it. Optional Google Maps requires separate disclosure and final confirmation; its URL carries coordinates without session capabilities. There is no automatic map/geocoder request and street addresses remain unavailable. These tests use modules/simulated DOM, not a real browser.

## Material remaining limits

- A complete coherent old database plus manifest can be restored under the same retained key and roll back Stop/epoch state. The test demonstrates this. Key theft or decryption is not necessary for replaying a valid encrypted snapshot. No independent rollback anchor is implemented. Stale backup restoration is not equivalent to an ordinary crash restart.
- Prepared access solves access for previously provisioned senders. Someone without it cannot obtain a reserved slot from public config during first use. Private onboarding/distribution, finite reservations, lost-installation administration, operator availability, costs and equitable emergency access remain unresolved product/operational issues.
- Own-slot reset and local Forget do not revoke a copied long-lived grant. Offline disable/rotation now blocks that old grant for admission/reset, but needs a reachable trusted operator and a stopped server. Default rotation does not end an existing session or revoke its separate read/write/revoke credentials; `--end-current` is explicit. Enable alone reauthorizes the currently stored grant. Private distribution, recovery, rotation output handling and maintenance availability remain operational work. File presence alone does not prove rotation committed; stale/full backups can roll these controls back.
- Android runtime was not exercised: Keystore, actual HTTPS, permissions, sharing/clipboard, location hardware, foreground start/notification denial, screen lock/Doze, Active apps Stop, process death and OEM battery behavior remain unverified. No real-browser rendering, TalkBack, large-text/landscape or real sender-to-recipient field test occurred in this independent review.
- SIGKILL tests cover pre-body-completion and post-commit/pre-reply boundaries, not physical power loss, torn writes, lying storage sync, disk-full failure or kernel/filesystem faults. The backend uses FULL-synchronous SQLite DELETE journaling and exclusive ownership; no stale-WAL attack experiment or infrastructure backup audit was performed.
- The default Android endpoint is blank. No production service, monitoring/incident-response operation, hosting/logging/backup policy, load/SLO evidence or high availability exists. A SQLite/key loss or authentication failure makes the service unavailable rather than silently resetting it.
- Device/server clock accuracy, network denial of service, forwarded links, copied coordinates and monitored-device traces remain limits. The operator sees location plaintext within TLS; this is not end-to-end encryption or anonymity. Until Stop has no universal hard remote lifetime. A last in-flight update may arrive after a local Stop/pause; neither cancellation nor remote Stop can recall copies.

See [REVIEW.md](REVIEW.md) for findings and the precise recovery/Stop boundaries. No security certification or guaranteed concealment, continuous tracking, rescue or recipient attention is claimed.
