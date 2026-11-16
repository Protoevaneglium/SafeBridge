# Safe Bridge 0.6 — private Android test

An independent U.S.-focused resource guide for people facing trafficking, exploitation or related danger. This is a private review prototype, not an emergency service, hotline, reporting system or public-ready survivor tool. Source updates are intended for the existing experimental public test branch; no public APK/product release is authorized by this handoff.

## Changes from 0.5

- Compact home with eight native choices: Need help now, Find Me, Can’t speak, Can’t leave, Warning signs, Peer groups, Safety tools and More help. Direct paths cover hiding, distress, suspicious recruitment/grooming, concerned family/friends and prevention learning. Emergency/dialer and calculator remain outside guidance pages. The eight choices use four paired rows; constrained windows may require pagination.
- Short offline information on coercion, recruitment, online enticement, image threats, supporting someone and considering a tailored next step. No checklists collect answers or planning details.
- Action-first support/reporting pages with required disclosures before external navigation. Submitted CyberTipline reports cannot be canceled and every submitted report is made available to law enforcement.
- Peer options with service eligibility and privacy limits: After Silence (18+ forum), 1in6 (male-identifying 18+ groups), RAINN Youth HelpRoom information (14–24; current session availability/schedule unverified), NCMEC Team HOPE (matched phone support, not a forum). No partnership or live integration is implied.
- Find Me: optional location snapshots with coordinates, estimated accuracy, fix time/age, cache/approximate/mock labels, copy and Android sharing. Sharing UI is not proof of sending or receipt. No contact selection or sending is performed by Safe Bridge.
- Optional 15/60-minute **on-device** location updating with an Android location foreground service, visible notification and Stop. It continues independently of Activity/cover, subject to Android/device restrictions; it does not transmit location. No live recipient link/backend exists. See [docs/FIND_ME.md](docs/FIND_ME.md) and the concrete [backend plan](docs/BACKEND_PLAN.md).
- Preserved pastel gradients, translucent rounded panels and page/popup design; no app-owned scrolling widgets or text shrinking. Native external services have their own interfaces.
- Back first moves to a previous guidance page, then the parent section. Rotation saves navigation coordinates and the first visible content anchor. Disclosure sheets close on recreation and never auto-open a website or dialer.
- Adaptive page measurement, whole action labels, 48dp minimum controls, focus feedback and page announcements. The calculator display stays visible above a paged keypad if all rows do not fit. Extreme window/font combinations can show Expand window; universal accessibility/layout correctness is not claimed.

## Safety and privacy

Device activity may be monitored. Installed apps, browser/email/call records, network activity and operating-system records may reveal use. Changing passwords, tracking or account access can alert an abusive person and increase danger; consider changes with an advocate.

The calculator is visual camouflage only. It does not hide package/settings/download records, defeat monitoring or erase traces. Long-press equals to request returning to Safe Bridge; Back in calculator closes the task. Cover/Close do not stop an active on-device session; Stop updates and the notification Stop action do. Location indicators and the foreground notification may reveal use. Calculator mode persists through the launcher alias, while calculations are not saved.

Guidance requires no runtime permission. Optional Find Me requests coarse/fine location; session mode also requires enabled notifications. Manifest adds those permissions and the two location-foreground-service permissions. No INTERNET, background-location, contacts, SMS-sending, microphone, storage or exact-alarm permission is requested. Android location providers have their own processing/privacy.

No accounts, analytics, backend, image uploads or saved planning notes. Coordinates/latest fix stay in process memory unless the user explicitly copies or shares a snapshot; destination apps, clipboard and recipients may retain it. Android may retain navigation coordinates across recreation; no location, personal planning details or calculator input is put in app-controlled saved state. Close removes the task from Recents and does not erase activity. External services/calls have their own privacy rules.

## Build and verification

Requires Android SDK platform/build tools 35, Java 17+ compiler, Python 3 and zip. No Gradle downloads.

```sh
ANDROID_HOME=/path/to/sdk bash scripts/build.sh
python3 scripts/checks.py
java -m jdk.compiler/com.sun.tools.javac.Main -d build/host-tests app/src/main/java/com/stange/safebridge/Calculator.java app/src/main/java/com/stange/safebridge/NavigationState.java app/src/main/java/com/stange/safebridge/LocationFix.java tests/CalculatorTest.java tests/NavigationTest.java tests/LocationFixTest.java
java -cp build/host-tests CalculatorTest
java -cp build/host-tests NavigationTest
java -cp build/host-tests com.stange.safebridge.LocationFixTest
```

Build produces `build/SafeBridge-unsigned.apk`. Sign privately with your own key. Signing keys and passwords are excluded from the source artifact.

Package remains `com.stange.safebridge.pasteltest`, versionCode 6, versionName `0.6.0-private-test`, min API 26 / target API 35. The delivered signed APK uses the existing 0.4/0.5 pastel test certificate and is eligible to update that package. Installation/update behavior was not tested. The original 0.3 package remains separate.

See [VERIFICATION.md](VERIFICATION.md) for exact passed checks and untested runtime cases, and [docs/SOURCES.md](docs/SOURCES.md) for primary-source evidence checked November 16, 2026. Previous-build notes are retained in `docs/PREVIOUS_0.5_NOTES.md`.
