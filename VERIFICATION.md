# Safe Bridge 0.6 private-test verification

Date: November 16, 2026. Source: existing `safe-bridge-pastel` 0.5 UI test. No applicable AGENTS.md or checkout .agents/skills instructions were found; the checkout and ancestor .agents directories were checked. No Git checkout exists in this project directory. The owner subsequently requested updating the existing public experimental test branch. Remote commit/CI outcome is recorded separately in the delivery evidence; no public APK/product release is performed.

## Passed

- Android 35 resource compilation/linking, Java compilation, D8 dexing and zip alignment. Compiler reports the standard source/modules-path warning and deprecated Android API notice; no compilation errors.
- 21 host calculator cases: arithmetic, precision, division by zero, repeated equals, reset, backspace, signs and interruption-like key sequences.
- 22 host navigation cases: parent route/page restoration, recreated state arrays, same-route repeated taps, Home reset, invalid-state fallback, defensive copies and bounded history.
- 21 host location-metadata cases: monotonic age, stale/unknown fixes, estimated accuracy, timestamps, coordinate validation/formatting, cache/approximate/mock labels, session origin and snapshot limits. Synthetic coordinates only.
- Static safety checks: exact five-permission allowlist for optional location/session mode; no app network/storage code, dialer-only handoff with confirmations, two missing-handler catch branches, launcher alias/disguise controls, critical disclosures, reviewed URL-domain allowlist and route graph coverage.
- Static UI contracts: no ScrollView/text shrinking; eight home choices in four paired rows; page-based Back and Bundle restoration; single active popup and one-shot callbacks; forward handoff gated on final disclosure page; whole action labels; 48dp minimum controls. These assertions do not execute Android views.
- Independent source reviews of practical safety, service eligibility, reporting disclosures and navigation integration. Follow-up fixed Take It Down pre-handoff warning, dialog lifecycle cleanup, explicit Android Back compatibility, reading-anchor restoration and pending-layout Back handling.
- Optional-location source contracts: bounded/cancelable one-shot reads, no automatic send, user sharesheet text handoff, nonsticky location foreground service, expiry/Stop cleanup, notification permission/channel checks, provider and precision-downgrade guards. These are source reviews, not platform execution.
- APK v2/v3 signature verification. Existing 0.5 certificate matches the new private build. Package/version/min/target values inspected with aapt2. See build evidence for digests.

## Runtime not tested

No ADB, emulator or connected Android device is available. No real-device verification, screenshots, Android instrumentation, TalkBack session, install or update trial occurred.

| Case | What was checked | Still untested |
|---|---|---|
| Home choices/navigation | Native action-first source order; route graph; host history tests | Actual tap paths/rendering on phone |
| Large text / narrow / landscape / split screen | Measured reflow code, source anchors, whole-label handling, Expand window state, minimum sizes | Actual geometry, clipping, eight-choice first-screen fit, very small/large-font shells |
| Accessibility | Native Button controls, heading guards, names, focus/pressed states, page feedback and focus code | TalkBack order/announcements, keyboard/switch access, actual touch target dimensions and screen-reader focus |
| Back / Cancel | Host route behavior and source paths; popup Back/Cancel has no external callback | System gesture, popup dismissal and interruption timing on Android |
| Rotation | Bundle route/page/reading-anchor code; no saved calculator entry | Platform recreation and exact observed reading continuity; parent-route pages retain numeric positions |
| Repeated taps | Host duplicate routes, active-dialog/single callback guards | Actual event timing and double-launch prevention |
| App restart / disguise | Existing aliases, persistent component state, calculator reset and close code | Launcher caches, restart state, task behavior on devices |
| Absent browser/dialer | ActivityNotFoundException branches and immediate Close/Calculator options | Actual absent-handler conditions, vendor behavior and restricted handlers |
| Find Me one-shot / copy / share | Host metadata tests; compile; cancellation/export/permission source review | Real fixes, precision/approximate changes, clipboard previews, sharesheet/recipient behavior, missing share apps |
| Location session / cover / lock / Stop / expiry | Foreground manifest/type/permissions, visible stop notification, elapsed deadline plus inexact alarm, source review | Android foreground start, notification denial/revocation, screen lock/deep sleep, expiry cleanup timing, provider changes, Active apps Stop, battery/OEM restrictions |
| Live recipient tracking | No app INTERNET permission or backend; explicit unavailability; concrete backend plan | Entire secure backend, receiver page, operational controls and independent end-to-end tests are unimplemented |
| Offline / external links | No INTERNET permission; offline strings and official source review | Airplane-mode runtime, real browser/dialer execution and provider response availability |

No emergency calls, regular calls, SMS, reports, image submissions, forum accounts, peer sessions or messages to providers were made. No real coordinates were obtained, copied, shared, uploaded or published; no real recipient was selected. Emergency testing must use Cancel only or mocked intents; never call 911 to test.

## Package and update compatibility

- Application ID: `com.stange.safebridge.pasteltest` (unchanged).
- Version code 6; version name `0.6.0-private-test`.
- Minimum API 26, target/compile API 35.
- Existing pastel test signer reused, certificate SHA-256 `c879898f4a5bc589a98c3cce4b94732feded817cd53bc630908b9c7f00dffcbe`.
- Eligible as update to 0.4/0.5 pastel test package by package/certificate/version metadata. Actual installation is untested. Original 0.3 package is separate and is not replaced.
- Keys/passwords remain outside project and are excluded from artifacts. Unsigned rebuild requires private signing separately.

## Practical limits

This remains a private prototype. Source compilation/host tests are not safety or accessibility certification. Survivor-led, child-safety, privacy/legal and Android accessibility review are needed before any public use. Disguise does not defeat monitoring or erase traces. External services control data and availability. RAINN current sessions remain unverified and are explicitly labeled that way.

## Delivery/privacy boundaries for Find Me

Guidance does not require location permission. Optional snapshots/local updating are not a rescue or emergency service. Accuracy and freshness are estimates; stopped and stale states are distinct. Calculator/Close do not stop local updating. Android may stop the whole app without callbacks via Active apps; foreground status never guarantees continuity or non-detection. Session expiry blocks returned/accepted fixes after its elapsed deadline, but OS cleanup timing is not guaranteed. No automatic restart, boot receiver, wake lock, background-location access or remote-sharing backend exists.

Live browser links with revocation/expiry remain **unimplemented**. docs/BACKEND_PLAN.md is a proposed contract and test gate, not an operating service. There is no pending remote revocation in this build because it never creates a remote location session; already copied/shared snapshots cannot be recalled.
