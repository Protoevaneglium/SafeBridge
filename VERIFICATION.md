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

## Source publication check
The 0.4 UI test source is published on branch `ui-test-0.4`, separate from the main 0.3 application baseline. Static checks were rerun successfully during publication. The recorded build and 21 calculator test results above are prior build-stage results; a publication-time calculator rerun was unavailable because `javac` was not on PATH. No new Android runtime or device tests are claimed. No APK or signing key is included.
