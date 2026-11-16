# Safe Bridge — 0.5 UI test branch

This branch publishes experimental source for review, not a public-ready safety app. Package `com.stange.safebridge.pasteltest` updates the separately signed 0.4 test app, not the original 0.3 app. Main remains unchanged. No APK, signing keys or reference images are published with this update. Android runtime, S24 layout and accessibility testing remain unverified.

A U.S.-focused, independent safety/resource navigator. **Not an emergency service, hotline, reporting system or public-ready survivor tool.** Built at the owner's request as an initial APK for review.

## What works
- Offline guidance: support, child exploitation reporting, intimate-image resources, device-safety caveats.
- Confirmed external navigation to official sites, using the user's browser.
- Dialer intents (never automatic calls), with confirmation.
- Back/start navigation, paged native UI with disclosure pop-ups, Close removes task from Recents.

## Privacy boundaries
No app permissions, internet permission, accounts, analytics, notifications, geolocation, saved notes, uploads, AI or background service. Android/device records and external sites/phone records are outside the app's control. Close does not erase traces, hide installation or clear browser/call history. No guarantee of anonymity or safety on a monitored device.

This prototype intentionally does not collect allegations, identifying details or child sexual abuse material. Never download or forward such material for this app. Do not use it for investigating or confronting anyone.

## Sources checked 2026-11-14
- https://humantraffickinghotline.org/en/get-help
- https://humantraffickinghotline.org/en/safety-planning-information
- https://humantraffickinghotline.org/en/chat
- https://www.missingkids.org/gethelpnow/cybertipline
- https://report.cybertip.org/
- https://takeitdown.ncmec.org/ (links to StopNCII for adult images)
- https://stopncii.org/
- https://takeitdown.ncmec.org/faq/
- https://www.techsafety.org/choosingapps
- https://humantraffickinghotline.org/en/confidentiality-policy
- https://www.missingkids.org/gethelpnow/exploitation-resources

No affiliation/endorsement implied. Links and policies can change. U.S. 911 only, no international directory.

## Build
Android SDK platform/build tools 35, JDK 17, zip. No Gradle dependency downloads.

```sh
ANDROID_HOME=/path/to/sdk bash scripts/build.sh
python3 scripts/checks.py
```

Produces `build/SafeBridge-unsigned.apk`. Sign with your own test/release key using Android `apksigner`. No signing keys are distributed or committed. Application ID `com.stange.safebridge.pasteltest`, min API 26, target API 35, version 0.5.0-ui-test.

## Validation and limitations
Native compilation, static safety checks and APK signature verification completed for delivered preview. No emulator or S24 runtime verification completed. No real calls/reports sent. Do not treat compilation as a functional or safety certification.

Before any public use: survivor-led review, child-safety and privacy/legal review, accessibility testing, monitored-device threat modeling, link maintenance, real Android device and interruption tests. Check external cancellation/return flows, large text, TalkBack, absent dialer/browser and offline behavior. Resource descriptions are informational, not a tailored safety plan.

## Scope
Public development is intended to be transparent. Do not submit personal stories, case details, private identifiers or abuse imagery in issues. No license granted yet; licensing requires the owner's decision. See SECURITY.md for reporting limitations.

## 0.3 visual refresh (private test build)
Original light cyan/blue gradient, softly translucent rounded panels, prominent resource cards and pill controls inspired by broad glass-UI principles. No Apple logos, reference artwork, screenshots, fonts or third-party icons are embedded. Native static gradients, not live blur, animation or background rendering loops.

Emergency confirmation and dialer handoff, external navigation disclosures, calculator arithmetic, persistent launcher aliases and all privacy caveats are retained from 0.2. The camouflage is not authentication or guaranteed concealment. Long-press = to confirm return; Back closes calculator without opening help. Calculations remain memory-only.

This is a UI revision, not a public safety certification. The reference image is not distributed with source or APK. Historical note: this was initially a private test; source is now published for review, not as a public APK release.

See VERIFICATION.md for checks and remaining device tests.

## 0.4 UI reference blend
Original mint hero, cyan/mint/blush/lilac resource accents and generous rounded cards blend with the previous translucent glass surfaces. No reference artwork or mood tracking is included. Same offline links-only feature scope.

This private UI test uses a distinct package and new test signer because the prior key password was unavailable. It installs alongside 0.3 and does not inherit its settings or replace it. Keep the existing app installed. No public release performed.

### 0.5 UI test
Pages and pop-ups replace the long scrolling interface. Back/Next navigation adapts guidance to available height; Home/Privacy/About remain available. Disclosure sheets keep Cancel and Calculator accessible and enable the external handoff only after the final disclosure page. This is a test build, not a public-deployment-ready safety product. See VERIFICATION.md for untested Android layout and accessibility cases.
