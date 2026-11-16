# Historical notes from before 0.6

These describe earlier builds, not the current verification.

# Safe Bridge — rough Android prototype

A U.S.-focused, independent safety/resource navigator. **Not an emergency service, hotline, reporting system or public-ready survivor tool.** Built at the owner's request as an initial APK for review.

## What works
- Offline guidance: support, child exploitation reporting, intimate-image resources, device-safety caveats.
- Confirmed external navigation to official sites, using the user's browser.
- Dialer intents (never automatic calls), with confirmation.
- Back/start navigation, scrollable adaptive native UI, Close removes task from Recents.

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

Produces `build/SafeBridge-unsigned.apk`. Sign with your own test/release key using Android `apksigner`. No signing keys are distributed or committed. Application ID `com.stange.safebridge.pasteltest`, min API 26, target API 35, version 0.4.0-preview.

## Validation and limitations
Native compilation, static safety checks and APK signature verification completed for delivered preview. No emulator or S24 runtime verification completed. No real calls/reports sent. Do not treat compilation as a functional or safety certification.

Before any public use: survivor-led review, child-safety and privacy/legal review, accessibility testing, monitored-device threat modeling, link maintenance, real Android device and interruption tests. Check external cancellation/return flows, large text, TalkBack, absent dialer/browser and offline behavior. Resource descriptions are informational, not a tailored safety plan.

## Scope
Public development is intended to be transparent. Do not submit personal stories, case details, private identifiers or abuse imagery in issues. No license granted yet; licensing requires the owner's decision. See SECURITY.md for reporting limitations.

## 0.3 visual refresh (private test build)
Original light cyan/blue gradient, softly translucent rounded panels, prominent resource cards and pill controls inspired by broad glass-UI principles. No Apple logos, reference artwork, screenshots, fonts or third-party icons are embedded. Native static gradients, not live blur, animation or background rendering loops.

Emergency confirmation and dialer handoff, external navigation disclosures, calculator arithmetic, persistent launcher aliases and all privacy caveats are retained from 0.2. The camouflage is not authentication or guaranteed concealment. Long-press = to confirm return; Back closes calculator without opening help. Calculations remain memory-only.

This is a UI revision, not a public safety certification. The reference image is not distributed with source or APK. No repository publish or release was performed.

See VERIFICATION.md for checks and remaining device tests.

## 0.4 UI reference blend
Original mint hero, cyan/mint/blush/lilac resource accents and generous rounded cards blend with the previous translucent glass surfaces. No reference artwork or mood tracking is included. Same offline links-only feature scope.

This private UI test uses a distinct package and new test signer because the prior key password was unavailable. It installs alongside 0.3 and does not inherit its settings or replace it. Keep the existing app installed. No public release performed.

### 0.5 UI test
Pages and pop-ups replace the long scrolling interface. Back/Next navigation adapts guidance to available height; Home/Privacy/About remain available. Disclosure sheets keep Cancel and Calculator accessible and enable the external handoff only after the final disclosure page. This is a test build, not a public-deployment-ready safety product. See VERIFICATION.md for untested Android layout and accessibility cases.


# Safe Bridge 0.4.0-preview verification
## Provenance and scope
Derived from local 0.3 source in safe-bridge-glass. UI-only reference blend: mint hero, pastel resource accents and rounded translucent cards. Original artwork, not a copy of the reference illustrations. No feature, service, permissions or external action scope added.

## Passed
- Android 35 resources, Java 17 compilation and D8 packaging.
- Static manifest, resource allowlist, emergency/external confirmations, calculator escapes and accessibility-role/state checks.
- 21 freshly compiled host calculator arithmetic/reset/error tests.
- No added permissions, analytics, accounts, network code or background rendering loop.

## Install boundary
Prior signing password is unavailable. A separate test package com.stange.safebridge.pasteltest uses a new private test signer. It does not replace the existing preview or transfer its launcher/settings state. Signing keys are excluded from source.

## Not run
No ADB, emulator or Android test device available. Native UI appearance, large text, TalkBack, launcher alias switching, update/install behavior and interruption flows are not runtime verified. Compilation is not a safety certification.

## Device checks
Check all routes, scrolling, portrait/landscape and large fonts. Verify TalkBack card names and controls. Emergency dialog: Cancel only, never place a test 911 call. Test external dialogs and missing handlers with mocks, not real reports. Test calculator return/cancel, Back/Close and launcher cache behavior.

## Known limits
U.S.-only prototype. Disguise is visual only and app/settings/history/monitoring can reveal it. Rotation resets navigation and calculator entry. External app traces are uncontrolled. Survivor/privacy/accessibility review is still needed before public deployment.

## Independent source review and package
Source review found no blocking regressions. New icon contrast 9.60–10.48:1; frame contrast at least 11.84:1, calculated (not device measured).
APK v2/v3 signature verified. Package metadata checked: versionCode 4, min26/target35.
Certificate SHA-256: c879898f4a5bc589a98c3cce4b94732feded817cd53bc630908b9c7f00dffcbe
APK SHA-256: 3d912fbebecea0133543fce1827b09144021b2d41eecc6defa08700e6ea50232

## 0.5 UI test (November 16, 2026)
- Reworked navigation as measured pages with Back/Next rather than scrolling.
- Guidance paragraphs split at word boundaries without dropping text; oversized actionable resource cards become paged actionable text.
- Added original pastel/glass arrangement inspired by supplied references; no reference artwork, branding, photographs or illustrations included.
- Custom paged confirmation sheets retain Cancel/Calculator and require reaching the last disclosure page before the forward action is enabled. No calls or reports were performed in testing.
- Calculator uses a dedicated keypad (four columns portrait, five landscape), not the guidance page deck; calculations and launcher alias behavior unchanged.
- Compact short-window shell; Home/Privacy/About are also reachable as page actions. No scrolling widgets in app-owned UI.
- Build completed; Android signature verified and certificate matches 0.4 UI Test. Same package com.stange.safebridge.pasteltest, versionCode 5. Original 0.3 installation is separate.
- Static privacy/safety/intent/resource checks PASS; 21 calculator unit cases PASS.
- Independent source review found overflow risks in first implementation. Changes made: paged custom dialogs, split oversized button labels, actionable-card fallback, dedicated calculator and compact short-window shell.
- NOT VERIFIED: Android runtime, screenshots/rendering, TalkBack, extreme font sizes, split-screen/landscape geometry, S24 installation. Very small windows or extreme text size may still exceed minimum touch-target space; do not claim universal no-clipping or accessibility certification.
- Native browser/dialer UI is outside this app's no-scroll design.
- Final review hardening: sheets bounded to visible viewport; exact word-split bound corrected; if a styled text line cannot fit, show an Expand window state rather than rendering one-character disclosure pages. Forward disclosure action remains disabled. Full original source is retained for resizing. Native Cancel/Calculator alternatives remain outside the content deck. Extremely constrained overall control geometry remains a device-test limitation.
