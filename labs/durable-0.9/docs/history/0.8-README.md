# Safe Bridge 0.8 reconnect lab

A separate correction of the undeployed 0.7 live-location lab. The delivered 0.6 interim app and both earlier source/APK artifacts are preserved. **The Android HTTPS endpoint is blank. This build cannot start a public live link.** No service, account, domain or public release was created.

## Implemented source

The offline app retains the pastel/glass direction, compact action-first choices, pinned Find Me and emergency dialer, paged guidance/disclosures and calculator cover. Support providers are independent links. Snapshots show coordinates, accuracy, timestamps and age; Copy/Share exports one snapshot through Android, without claiming delivery. No sensitive safety-plan answers or recipient list are collected.

The optional Android sender has explicit foreground consent, location/notification permissions, visible notification and Stop, Until Stop or a chosen end, encrypted no-backup management credentials and HTTPS without redirects. Transient network failures retain consent and retry fresh newest-only samples with bounded backoff. No route history or automatic collection after process recovery is implemented. Calculator/Close keep a running session distinct from Stop; neither hides system indicators or monitoring.

Protocol 2 separates point freshness from session lifetime: after 90 seconds the server erases its point and pauses the session. A fresh newer upload can resume the same link; confirmed Stop and chosen expiry are terminal. The single authoritative in-memory server uses separate active/terminal quotas and one-use operator admission tickets, rather than anonymous public allocation. No master admission key is embedded in the app.

The browser recipient needs no Safe Bridge app/account. It retains last-known coordinates only in tab memory during connection failures, showing age, timestamp, accuracy and unverified current status. Confirmed Stop/expiry or Clear removes them. An optional Google Maps action requires separate disclosure and final confirmation; no automatic third-party map/geocoding request occurs. Street addresses remain unavailable. The operator processes coordinates; no end-to-end encryption is claimed.

## Reproduce checks

Requires Node 24, OpenSSL, Java compiler modules, Python 3 and Android SDK platform/build tools 35. No npm/Gradle dependencies are downloaded.

```sh
ANDROID_HOME=/path/to/sdk bash scripts/test-live.sh
```

This builds the Android package and runs host logic, actual loopback HTTP/TLS, adversarial sender/backend tests, recipient state/stream/consent tests and Java-to-Node integration. All fixtures use synthetic data. See [verification](VERIFICATION.md) and its complete recorded output. Compilation and DOM doubles do not verify Android runtime or browser geometry.

Package `com.stange.safebridge.reconnectlab`, versionCode 8, minimum API 26 / target 35. This application ID is distinct from 0.6 and 0.7, so it is intended for side-by-side installation. It is not an update to either prior app. Actual installation is untested. The private APK uses the existing test certificate; signing keys/passwords and reference artwork are excluded from source.

## Remaining work

This is not blocked only by hosting. Private admission issuance/distribution and orphan-session capacity/recovery need an operator design. Street addresses/geocoding are not implemented. Android HTTPS/Keystore/location/foreground behavior, real recipients, installation, lock/Doze/process death and native accessibility have not been executed. Browser rendering/200% text/assistive technology remain unverified. Public infrastructure, logging/retention, availability, incident response, maintenance and budget are unresolved. No uninterrupted tracking, concealment, emergency response or production safety claim is made.

- [Correction scope](docs/08_CHANGE_SCOPE.md)
- [Implemented protocol and admission](live/backend/API.md)
- [Independent review](live/security-tests/REVIEW.md)
- [Find Me behavior](docs/FIND_ME.md)
- [Usability and visual review](docs/USABILITY_AUDIT.md)
- [Deployment, costs and blockers](docs/DEPLOYMENT_AND_COSTS.md)
- [Primary safety/resource sources](docs/SOURCES.md)

The 0.6 public test-branch commit remains `d03871f47a2f22a730fe8ae81767cb9ced208a3b`; additional lab source is kept separately for review under `labs/reconnect-0.8` when committed to the test branch. The handoff records the exact remote commit/CI state. No service was deployed. No license decision is introduced here.
