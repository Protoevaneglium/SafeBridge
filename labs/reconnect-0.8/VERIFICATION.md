# Safe Bridge 0.8 reconnect lab verification — November 16, 2026

This separate fork corrects the undeployed 0.7 lab. The delivered 0.6 interim app and 0.6/0.7 artifacts are preserved. **No live service is deployed and the Android endpoint is blank. The default build cannot create a working public live link.** All network fixtures use synthetic positions/capabilities on loopback, without real recipients.

## Executed evidence

Full command:

```sh
ANDROID_HOME=/workspace/scratch/150d4492c6eb/classic-recovery-tools/sdk bash scripts/test-live.sh
```

[Complete final output](docs/LIVE_LAB_CHECKS.txt). The runner builds the Android 35 resources/Java/D8 package and executes the following. Standard compiler source/modules, deprecated and unchecked notices remain.

| Evidence level | Checks actually executed |
|---|---|
| Existing pure host logic | 21 calculator, 22 navigation history/recreation/repeated taps and 21 location metadata/freshness/share-text cases. |
| Endpoint validation | 26 offline checks for blank gating, HTTPS origin and forbidden credential/query/fragment/path configurations. |
| Actual pure Java coordinator | 232 unit assertions covering consent, same-link retries, Until Stop, immutable chosen end, admission rejection, journals, receipts, Stop/late callbacks, expiry and recovery. |
| Independent pure Java sender | 126 adversarial journal/Stop/expiry/capability assertions; 67 independent retry/end-choice and explicit source-integration checks. These are host and source evidence, not Android execution. |
| Shared Android policy helpers | 75 executed retry-policy cases and 16 chosen-end conversion cases. The actual Service uses these helpers, but its Android callbacks were not run. |
| Backend HTTP/TLS | 60 Node cases: 23 author HTTP, 28 independent HTTP and nine independent actual local TLS cases. TLS tests use disposable self-signed certificates trusted only by synthetic clients, also testing default trust/hostname failures. |
| Recipient logic/controller | 59 Node cases: 38 web tests and 21 independently written privacy/consent tests. Includes state/freshness, bounded streaming/UTF-8, word splitting, stable DOM doubles, simulated production-controller disclosure/Cancel/Clear/hidden/expiry behavior and actual loopback assets. No browser is rendered. |
| Backend-to-recipient | Six actual HTTP cases pass through production bounded parsing/validation/state logic, including last-known retention, pause/reconnect and confirmed Stop. |
| Actual Java-to-Node | 18 scoped HTTP checks, six actual process restart/generation checks and 36 V2 outage/reconnect/admission/eviction/chosen-end checks. These use a host transport, not Android HTTPS. |
| Static source contracts | Permission allowlist, unexported services, no backup/cleartext, blank endpoint, encrypted journal/network boundaries, dialer-only emergency, reviewed provider disclosures, pinned Find Me/Stop, navigation Bundle without sensitive data, explicit chosen-end extra and bounded access-code entry. |
| Private APK | Android metadata, zip alignment and APK v2/v3 signatures verify. Installation is untested. [Metadata and previous artifact hashes](docs/LAB_APK_AND_PRESERVATION.txt). |

The recorded integrated run reports **125/125 Node test cases** and separately printed Java suites totaling **666 checks/assertions**. Source assertions, DOM doubles and host fixtures are not Android runtime tests. Repeated suite execution is not counted as additional coverage.

No real coordinates were obtained, copied, sent or published. No real emergency/hotline call, SMS, report, forum registration, image submission or recipient contact occurred. No external map request was made with coordinates. Test listeners terminate; disposable TLS material is deleted. No real operator admission key/ticket, hosting account, domain or paid resource was created.

## Confirmed findings corrected

0.7 upload errors stopped/revoked the sender; the 90-second lease was terminal; only 15/60-minute live choices existed; terminal tombstones occupied active capacity; the recipient hid last-known data after connection failure. The separate 0.8 code corrects these and adds explicit external-map consent. Independent tests also caught an extendable recipient deadline and creation delays moving the chosen remote end later. Immutable `requestedExpiresAtMs` now carries the original absolute choice and late creation allocates nothing or spends no admission ticket. Original duration retries retain their initial elapsed budget. A further independently failing slow-POST/status regression demonstrated early local expiry from double-subtracting request delay; hard expiry now uses response arrival with the original consent cap, while point freshness keeps its conservative request-start anchor.

Management receipts resolve missing bounded-cache rows conservatively as unavailable, without calling that a revocation acknowledgement. Exact replay/ticket/receipt scopes, slow bodies, terminal races and capacity/nonce guards are tested. No terminal Stop/expiry is silently revived. Review scope and residual concerns are in [the independent review](live/security-tests/REVIEW.md).

## Not executed

| Requested behavior | What remains untested |
|---|---|
| Android live sender | Actual GPS/network providers, precision/one-time/denied/revoked permissions, HTTPS adapter, Keystore/AtomicFile durability, foreground launch, notification/Stop, lock/Doze/battery/OEM delays, Active apps Stop, reboot and process death. Process-recovery logic was tested on host; Android lifecycle was not. |
| Navigation and interactions | Native Back/Cancel, repeated taps, rotation/restart, chooser, absent browser/dialer/share targets and calculator launcher behavior. Pure navigation/calculator models and static handler checks passed, without device UI execution. |
| Accessibility and resizing | TalkBack, keyboard/switch access, font scaling, landscape/narrow/split-screen clipping, focus continuity and 200% browser text. Pages avoid shrinking text in source, but this is not a rendered fit/accessibility guarantee. Home choices may paginate under constrained dimensions. |
| Recipient runtime | Real browser CSS/layout, maps handoff/pop-up blocking, screen reader, browser suspension/restore and actual no-app recipient delivery. Controller/deck tests use simulated DOM. |
| APK compatibility | Actual installation/update/coexistence on an Android device. The distinct package metadata indicates side-by-side intent, not observed installation. |
| Production | Public TLS/domain/operator, infrastructure/edge logging and retention, swap/backups/core dumps, load/abuse availability, private admission distribution and real operational incident response. |

The environment has SDK/build tools but no ADB, emulator, Android runtime or `/dev/kvm`. Earlier fresh Chromium/Playwright launch failed before rendering with `socket() failed: Operation not permitted`. The available cloud browser could not reach the executor loopback listener (`ERR_CONNECTION_REFUSED`). The manual listener was stopped. No successful native or recipient screenshot exists; DOM tests are not screenshots.

## Remaining unmet requests and tradeoffs

This is **not deployment-only blocked**. There is no ticket issuance/distribution/onboarding service. Production requires private one-use tickets; the operator must define authorized issuance, key custody/rotation and lost-credential/orphan Until Stop capacity/recovery. Active capacity is finite even though terminal cache no longer locks it for 24 hours. A generation reset ends every current link. Private address/geodata is not implemented; the recipient clearly says street address unavailable. Optional Google Maps is a consented coordinate snapshot, not a live mapping integration.

The server is one authoritative in-memory process. Restart invalidates links; unrelated replicas would break consistency. Server/API erasure is not proof of physical RAM or infrastructure erasure. Until Stop sessions have no automatic session end. After 90 seconds without an accepted point, server data is erased and the session pauses; a recipient may still have its own labeled tab-memory copy. Known positive expiry, confirmed termination or Clear removes recipient state, but browser suspension may delay execution and exported copies cannot be recalled.

Stop first invalidates local collection/queued work, then attempts authenticated remote revocation. A request already past dispatch can arrive before server Stop; network cancellation cannot recall bytes. Offline Stop remains pending. Signed unavailable/generation evidence and confirmed Stop are described separately. TLS, authorization/protocol and secure-storage failures still stop locally. Device/server wall-clock disagreement can reject a chosen end or make its real-world meaning differ from what the user expects; retries never move that absolute timestamp. No continuity, precision, concealment, emergency response or production safety guarantee is made.

## Package and signer

`com.stange.safebridge.reconnectlab`, versionCode 8 / `0.8.0-reconnect-lab`, minimum API 26 / target 35. Existing test-certificate SHA-256: `c879898f4a5bc589a98c3cce4b94732feded817cd53bc630908b9c7f00dffcbe`. This differs in application ID from both 0.6 (`com.stange.safebridge.pasteltest`) and 0.7 (`com.stange.safebridge.livelab`); it is intended for side-by-side installation rather than updating either. No key/password is included. Final hashes and publication state are recorded with the delivered artifacts.
