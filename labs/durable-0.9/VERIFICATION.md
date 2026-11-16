# Safe Bridge 0.9 verification — November 16, 2026

This is a separate functional lab. Earlier checkouts, source commits and APK/source artifacts remain preserved. **No public live service is deployed; the Android endpoint is blank and the default build cannot create a public live link.** Visual polish is deferred. The owner's report that phone sharing works is useful feedback with unknown APK/path/duration; it may describe a snapshot and is not independent live-tracking evidence.

## Final integrated execution

```sh
ANDROID_HOME=/workspace/scratch/150d4492c6eb/classic-recovery-tools/sdk bash scripts/test-live.sh
```

[Complete output](docs/LIVE_LAB_CHECKS.txt). The frozen integrated run passed **197/197 Node cases**, with **982 separately printed Java checks/assertions**. They are different units, include explicitly identified source contracts, and are not Android runtime tests. Each distinct suite ran once; compatibility wrappers that invoke the same Java durable suite are not run/counts again.

| Evidence | Executed result and boundary |
|---|---|
| Android build | SDK 35 resource/link, Java compilation, D8 packaging; compiler source/modules, deprecated and unchecked notices remain. Private APK signature/alignment metadata are recorded separately. No installation/runtime. |
| Existing host models | 21 calculator, 22 navigation history/recreation/repeated taps, 21 location age/accuracy/share-text, 26 endpoint-config checks. No device UI/network. |
| Actual pure Java coordinator | 315 assertions: prepared admission/epoch journaling before create, no post-uncertainty null-epoch fallback, same-link recovery, last-sequence reconciliation, original chosen end, explicit Stop versus interruption pause, scope-safe setup replacement and read-only presence checks. |
| Independent Java | 206 coordinator assertions plus 94 retry/interruption/end/reset-only/source checks. Includes deterministic concurrency gates, lost acknowledgements, unknown Stop without create-to-revoke, key scope, foreground-denial pause and explicit-resume-before-provider source contracts. No Android callbacks executed. |
| Executed shared helpers | 75 retry-policy, 16 chosen-end, 35 canonical-origin, 22 prepared-access rules, 46 bounded/strict setup-file parser, 14 preparation-replacement, 15 interruption-policy and 12 reset-only recovery cases. Source-only import-boundary checks are separately printed without being counted as device tests. |
| Actual Java → Node → encrypted SQLite | 42 checks using real private loopback subprocesses: abrupt server death/reopen, stable IDs/generation, no restored point, accepted sequence, explicit client pause/resume, chosen expiry, reserved slots, late ACK, old epochs and unknown Stop/no new allocation. Host HTTP adapter, not Android HTTPS. |
| Backend/module Node | 54 cases: 33 backend/durable/operator cases and 21 private provisioning/rotation module cases, including actual CLI initialization/install/recovery. See backend TEST_RESULTS.md. |
| Independent Node | 99 cases: 28 HTTP, nine actual prepared production-mode TLS, 11 SQLite/encryption, 14 separate-process SIGKILL/crash-ACK, three private grant-install CLI, six private recovery/rotation CLI, 21 recipient privacy/controller and seven durable-recipient restart. Included in the 197 total, not added again. |
| Recipient/integration remainder | 38 web state/stream/word-splitting/deck/map cases and six backend-to-recipient HTTP cases. Includes actual bounded HTTP responses and simulated DOM/controller consent flows; no browser render. |
| Static app boundaries | Exact permission allowlist; unexported location services; no app backup/cleartext/logging; separate encrypted stores; blank endpoint; no per-session code entry; dialer-only 911; provider/reporting/peer disclosures; pinned Find Me/Stop; navigation-only Bundle; explicit import/resume/reset and same-slot checks. These are source assertions. |

All locations, credentials, grants, keys and SQLite/TLS fixtures were synthetic. No real location, recipient, support call, emergency call, SMS, report, forum participation or image submission was used. No external map request with coordinates occurred. Test listeners terminate and temporary state/key/certificate directories are removed. No production key, account, grant, domain, paid resource, public APK release or deployment was created.

## What the new evidence establishes

An intact authenticated encrypted SQLite database preserves server identity, session/capability verifiers, accepted sequence, prepared-slot state and revocation across tested process kills. A commit followed by a withheld acknowledgement and SIGKILL survives reopening. Coordinates/sample payloads/update digests are deliberately absent from persistent records; a restart returns paused with no point, then accepts a fresh higher sequence on the same link. Until Stop remains open in long synthetic clock advances, without an idle lifetime cutoff.

Prepared access is now an actual offline provisioning/import and automatic-ticket path, not an invented available issuer. One slot reserves one nonterminal session; its orphan does not take another reservation. Pinned epoch/ticket state is journaled before any creation request can escape. Reset/Stop cannot silently allocate merely to learn an uncertain session ID. Old links do not revive after their terminal rows are evicted.

Offline disable/rotation preserves the existing active ID, end and read/write/revoke capabilities by default; `--end-current` is a separate explicit choice. Tests verify old setup tokens fail, disabled policy survives rotation/import, explicit enable is required, and stale configuration cannot revert current policy. A private bundle and authenticated compare-and-swap receipt precede database activation. An actual CLI SIGKILL before its transaction leaves files unactivated; authenticated recovery applies once. It does not replay Stop against a later session, and changed/tampered receipt/bundle/state preconditions fail.

Android source now treats nonexplicit interruption as pause and requires explicit visible resume before new collection. Setup-key rotation for the exact same managed slot can be saved without ending that link. Different or unknown in-flight scopes are blocked. If the main journal is unreadable but separate preparation is valid, an isolated reset-only coordinator can end only that prepared slot without reading, replacing or deleting the bad journal. Fresh Start/Resume remain blocked by that unreadable record; separate explicit discard/recovery design is still needed. The host helper/source checks do not establish real Keystore or lifecycle behavior.

Independent scope, findings and exact test commands are in [REVIEW.md](live/security-tests/REVIEW.md), [the backend evidence](live/backend/TEST_RESULTS.md) and [the current API](live/backend/API.md).

## Demonstrated limitation: complete stale-state rollback

A passing adversarial test intentionally demonstrates that a **whole coherent older encrypted database/manifest** can be accepted under the server's already configured state key. The attacker/operator need not extract that key. Restoring the old DB can roll back Stop, slot reset, disable or rotation state; it restores no coordinates, because none were persisted, but old authorization can become paused/resumable. Record AAD and the authenticated manifest reject tested partial modifications/replay, not this complete rollback.

Normal process restart with the current disk state and old-backup disaster recovery are different guarantees. Preventing/detecting complete rollback needs separately trusted monotonic anchoring or a stronger consistent recovery architecture. Alternatively an explicit new generation can invalidate old links, with continuity loss. No such external anchor/failover service was introduced or tested. This remains a substantive production blocker, not a deployment checkbox.

## Not tested

| Area | Missing execution |
|---|---|
| Android | Real GPS/network fixes; approximate/one-time/denied/revoked permissions; HTTPS adapter; Android Keystore/AtomicFile durability; selected SAF providers/cancellation; foreground start/notification/Stop; lock/Doze/battery/OEM behavior; Active apps Stop; reboot/process death and real resume; sharing/receipt; absent browser/dialer/chooser handlers; installation or coexistence. |
| Native UI/accessibility | TalkBack, keyboard/switch access, Back/Cancel/repeated taps/rotation on device, extreme text, narrow/landscape/split-screen clipping, focus continuity and calculator launcher behavior. Host models/source contracts passed, not native interactions. |
| Browser | Real CSS/layout, 200% text, keyboard/screen reader, suspension/back-forward restore, external map navigation/popup behavior and a real recipient. DOM doubles are not screenshots or browser accessibility evidence. |
| Infrastructure | Real power-loss/filesystem-controller durability, TLS/domain/renewal, public reachability, provider/edge/OS logging, swap/core dumps, backups/rollback controls, load/abuse/availability, failover, operational response or legal/safeguarding certification. |

There is no ADB, emulator, Android runtime or `/dev/kvm` in the task environment. Earlier Chromium startup failed before rendering with `socket() failed: Operation not permitted`; the cloud browser could not reach the executor listener (`ERR_CONNECTION_REFUSED`). No new native/browser screenshot or visual-polish test was performed.

## Remaining unmet goals and choices

Prior trusted preparation and an operating HTTPS service are still required; an unprepared person cannot obtain guaranteed instant public live admission. Private distribution/support, operator trust, finite reserved membership/capacity migration, lost-key recovery and deployment/maintenance must be chosen and operated. Offline code exists for issuer and token recovery; no real deployment/distribution service exists. Shared rate limits, CPU/network/disk failures can affect users despite reservation isolation.

Android cannot guarantee surviving a user/system process stop; this lab deliberately requires new visible consent before collecting again. Offline recipients can retain labeled last-known tab memory until Clear or confirmed termination, and already exported copies/bytes cannot be recalled. Known positive expiry is distinct from Until Stop. Device/server clock disagreement remains a chosen-time limit.

Street addresses and private geodata are not implemented. Google Maps is an explicitly disclosed external snapshot action, not an embedded or continuously updating map. UI polish and actual accessibility/rendering remain deferred/unverified. [09_TRADEOFFS.md](docs/09_TRADEOFFS.md) presents concrete policy options and their costs instead of claiming these are hosting-only gaps.

## APK/package boundary

Package `com.stange.safebridge.durablelab`, versionCode 9 / `0.9.0-durable-lab`, minimum API 26 / target 35. It is distinct from the prior 0.6/0.7/0.8 packages and intended for side-by-side testing, with installation and data migration untested. The existing test signer is reused; certificate SHA-256 `c879898f4a5bc589a98c3cce4b94732feded817cd53bc630908b9c7f00dffcbe`. No key/password, real preparation bundle or runtime database is included. Final APK metadata, hashes, old-artifact preservation and any source-only publication/CI state accompany the artifacts.
