# Independent reconnect security checks (0.8)

Review date: November 16, 2026. Scope: `safe-bridge-reconnect-lab` only. Earlier delivered 0.6 and 0.7 artifacts are unchanged by this review. All data, credentials, clocks and coordinates are synthetic; HTTP/TLS traffic stays on loopback. No service was deployed, real recipient contacted or emergency call placed.

## Results and reproduction

| Independent suite | Passed | Execution boundary |
|---|---:|---|
| `backend-adversarial.test.mjs` | 28 cases | Actual local HTTP requests and controlled server time |
| `backend-tls.test.mjs` | 9 cases | Actual local TLS, signed admission tickets and explicit test-only CA trust |
| `recipient-privacy-v2.test.mjs` | 21 cases | Actual web state/controller modules in a simulated DOM, plus local HTTP serving |
| `LiveCoordinatorAdversarialTest.java` | 126 assertions | Actual pure-Java coordinator; injected transport, journal, clock and thread gates |
| `LiveServicePolicyAdversarialTest.java` | 67 checks | Actual pure retry/end-choice helpers and explicitly identified service source contracts |

These are selected independent checks, not a security certification. They are included in the parent verification run; do not add their counts to that run again. Java assertion counts and Node test-case counts are different units.

From the lab root:

```sh
node --test live/security-tests/backend-adversarial.test.mjs live/security-tests/backend-tls.test.mjs live/security-tests/recipient-privacy-v2.test.mjs
java -m jdk.compiler/com.sun.tools.javac.Main -d build/security-host app/src/main/java/com/stange/safebridge/live/LiveCoordinator.java app/src/main/java/com/stange/safebridge/live/LiveRetryPolicy.java app/src/main/java/com/stange/safebridge/LiveSessionChoice.java live/security-tests/LiveCoordinatorAdversarialTest.java live/security-tests/LiveServicePolicyAdversarialTest.java
java -cp build/security-host LiveCoordinatorAdversarialTest
java -cp build/security-host LiveServicePolicyAdversarialTest
```

TLS tests generate a disposable certificate/key in a private OS temporary directory and delete it. Only the explicit synthetic Node client trusts that CA. Default CA trust and hostname mismatch must reject. No test or operator key is a project fixture. Android HTTPS was not executed by these TLS tests.

## Covered behavior

- Protocol 2 separates a 90-second point erasure/freshness interval from session lifetime. An outage longer than 90 seconds pauses and purges the server point; a new fresh sample can resume the same ID/link under existing foreground consent. A duplicate old sample cannot restore the point or renew freshness. Until Stop uses zero and has no hidden one-hour/day session limit.
- Exact `requestedExpiresAtMs` remains immutable through delayed config, creation and retries. A newly elapsed target cannot allocate or spend an admission ticket. Relative API budgets count initial elapsed time; chosen absolute ends are passed unchanged. Delayed creation/status responses cannot double-subtract network time from the local hard deadline or extend its original cap.
- Production creation requires an operator-signed, short-lived, one-use ticket. Public config, missing/forged/wrong-generation/expired tickets cannot occupy active quota. Spent-ticket protection survives terminal-cache eviction. A full nonce guard fails closed without spending a rejected ticket. Active quota is released by Stop even after more terminations than the bounded terminal cache holds.
- Read/write/revoke/status capabilities, session/generation binding, management-receipt scope/forgery and missing-row cleanup are exercised. A receipt cannot read coordinates, create/revive a session or falsely prove explicit revocation.
- Lost acknowledgements, journal failures, offline Stop, callbacks racing Stop, restart, specific no-allocation admission rejection and credential cleanup are exercised. Management persistence contains capabilities and lifecycle fields, not coordinates or recipients.
- Schema, range, body/media limits, server-observed sample age, slow-body races, canonical secrets, Host/Origin/query restrictions, no redirects, privacy headers, rate limits and priority for a first valid Stop are checked.
- Recipient state retains last-known data in tab RAM with increasing age and unverified wording during transient failure. Clear, confirmed terminal response and known positive hard expiry remove it. The earliest known positive deadline cannot be extended by a stale response. Until Stop has no invented expiry.
- Actual recipient controller tests require separate map disclosure and final confirmation; Cancel/Clear/hidden/Stop/expiry invalidate consent. Map URLs contain coordinates only, never session capabilities. No automatic map/geocoder request or persistent location storage is introduced. No real browser/provider request occurs in these tests.

## Boundaries and unresolved work

The default Android live endpoint is blank. No operating public service exists. Admission issuance/distribution/onboarding, bearer-code theft or premature use, orphan Until Stop sessions occupying active quota, operator recovery policy, production abuse/capacity limits, hosting and maintenance remain real technical/product decisions. The backend is a single memory-only process; restarts invalidate links and there is no high availability. A full nonce guard intentionally rejects starts rather than weakening replay prevention.

Device/server clock disagreement remains a chosen-end limitation. An Until Stop session has no hard remote lifetime: stopping locally while offline does not close it remotely. Its last server point is erased after the last accepted sample's freshness interval, while the session itself remains open until confirmed Stop, process restart or operator shutdown. Previously copied data cannot be recalled.

Recipient last-known data is RAM-only but can remain while an Until Stop tab is offline until Clear or confirmed termination. It is not proof of current location. Screenshots, clipboard contents, forwarded bearer links and external map copies cannot be recalled. Google receives selected coordinates and network/device metadata after explicit map consent. A street address/geocoder is not implemented.

No Android runtime checks exercised Keystore, actual HTTPS transport, permissions, location hardware, sharing/clipboard, foreground start/notification behavior, screen lock, Doze, force-stop, process death or OEM battery behavior. No real-browser rendering, TalkBack or field sender-to-recipient test occurred in this independent review. Hosting/proxy/OS logs, swap, core dumps, backups and infrastructure retention have not been configured or audited. The operator processes location plaintext within TLS; this is not end-to-end encryption, anonymity or a concealment guarantee.

See [REVIEW.md](REVIEW.md) for findings and the precise Stop/network boundary.
