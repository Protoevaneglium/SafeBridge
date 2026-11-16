# Safe Bridge verification history

Current source: 0.5.0-ui-test. Earlier 0.4 results below are historical; the 0.5 section identifies current checks. Publication is source-only on the existing UI-test branch; no public APK release or device-runtime certification.

## Historical 0.4 verification
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
