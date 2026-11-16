# Safe Bridge 0.9 durable live-location lab

A separate functional continuation of the verified 0.8 reconnect lab. Earlier source, APKs and public commits are preserved. Visual polish is deferred. **The Android live endpoint remains blank; no public live service is deployed.** No real operator keys, grants, accounts, hosted resources or recipients were created.

## Implemented direction

The existing offline safety/prevention guide, independent support and verified peer links, emergency dialer, calculator cover, paged guidance and location snapshots remain. The owner's report that phone sharing works is recorded as feedback; the APK/path/duration were not identified and it may have been a snapshot. It is not proof of continuous live delivery.

Protocol 3 adds coordinate-free encrypted SQLite management state. An intact database preserves server identity, session IDs, chosen ends, token verifiers, accepted sequences, prepared-slot epochs and revocation across process restart. Positions stay only in RAM: restart yields paused/no point until a new fresh sample arrives. Stop/reset is committed before acknowledgement; a stopped link cannot resume. No point history, sample payload or coordinate-derived update digest is written to the durable store.

Private preparation replaces per-session access-code entry. An offline operator CLI provisions a pseudonymous reserved slot and private sender file; the app imports it only with explicit confirmation into a separate encrypted store. Normal explicit Start obtains an epoch/creation-bound admission ticket automatically. One slot permits one ongoing session; an orphan consumes its own reservation. Explicit own-slot reset ends that slot's earlier link. Offline disable/rotate/enable controls recover compromised preparation without silently ending an active link; ending it is a separate explicit option. Authenticated rotation receipts support recovery across file-publication/database-commit failure. No anonymous public enrollment, master secret in the app, identity provider or external attestation was added.

Android process recovery pauses local collection. Explicit Resume from the visible app revalidates the same link, original chosen end and last sequence before requesting new fixes. System interruption is distinct from explicit Stop. Until Stop has no hidden session timeout; the 90-second point deadline still erases server coordinates without ending the session. Android can stop or delay execution, so this is not an uninterrupted-tracking guarantee.

The recipient keeps labeled last-known coordinates, timestamp, increasing age and accuracy in tab memory through outages, with no invented current position. Confirmed termination, known chosen expiry or Clear removes them. Optional external Google Maps opens a selected snapshot only after disclosure/confirmation. Street addresses/geocoding remain unavailable. No provider partnership, hotline operation or emergency dispatch is implied.

## Reproduce and inspect

Requires Node **24.22.1–24.x** with built-in SQLite, OpenSSL, Java compiler modules, Python 3 and Android SDK platform/build tools 35. No npm/Gradle dependencies are downloaded.

```sh
ANDROID_HOME=/path/to/sdk bash scripts/test-live.sh
```

[Verification and exact evidence](VERIFICATION.md), [current protocol](live/backend/API.md), [private preparation/initialization commands](live/backend/PREPARED_SETUP.md), [independent review](live/security-tests/REVIEW.md), and [tradeoffs/options](docs/09_TRADEOFFS.md).

Package `com.stange.safebridge.durablelab`, versionCode 9 / `0.9.0-durable-lab`, minimum API 26 / target 35. It is separate from 0.6/0.7/0.8 and intended for side-by-side testing, with no automatic data migration or observed installation. No signer key/password, setup bundle, state database, reference artwork or generated TLS material belongs in source artifacts.

## Unmet goals and boundaries

This is not deployment-only blocked. A person without prior trusted preparation still cannot obtain instant guaranteed public live admission. Distribution, operator trust/support, finite reserved capacity, credential loss and disaster recovery require explicit operating choices. A coherent old database backup can omit later revocations: local authenticated encryption/manifest checks do not establish that a whole backup is current. Current-state crash recovery and old-backup restoration are different claims.

Real Android HTTPS, Keystore, SAF import, foreground service, GPS, notifications, lock/Doze, Active apps Stop, process death, installation, TalkBack and recipient browser behavior remain untested. No new device or browser screenshot exists. Runtime accessibility/resizing remains open, with visual polish deferred. No hosted TLS/domain, infrastructure logging/retention, load/abuse/availability operation or incident response has been established. Service operators process coordinates; no end-to-end encryption, precision, concealment, emergency response or recipient-delivery guarantee is made.

Earlier records are clearly archived under `docs/history`. The handoff records any exact source-only test-branch publication and CI state; source publication is not a service deployment or public APK release.
