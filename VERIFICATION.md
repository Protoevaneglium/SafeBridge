# Safe Bridge 0.3.0-preview verification

## Source provenance
The local 0.2 MainActivity matched the current GitHub default-branch file byte-for-byte before changes. Implementation was prepared in a separate local source copy, leaving the original 0.2 local project untouched. This source publication follows the reviewed 0.3 archive; README preserves the repository's earlier safety notes.

## Passed
- Android 35 native resource compilation, Java 17 target compilation and D8 packaging.
- Static manifest/privacy, route, allowed-domain, disclosure, emergency and calculator exit/alias checks.
- Explicit button accessibility role for resource cards; their child labels are hidden from separate accessibility focus. All custom action backgrounds provide pressed and keyboard-focus states. Static structure checked only, not TalkBack runtime verified.
- 21 host calculator arithmetic, repeated action, reset and error tests.
- APK signature v2/v3 verification, same certificate SHA-256 as 0.2: fd120267b316240c9c7246f8f2c789cbfc3486165068fc0f98fb9564993bb11d.
- APK package com.stange.safebridge.preview, versionCode 3, min26/target35.
- No added permissions, external libraries, artwork, network access, analytics, report backend or automatic calling.

## Not run
No emulator, ADB or attached Android test device is present in this environment. The native UI has not been visually rendered or interaction-tested here. Compilation does not prove accessibility, layout, safety or update behavior.

## Device acceptance checks (do not place calls or submit reports)
- Install over 0.2 without uninstalling; verify calculator launcher mode is preserved if selected.
- Check home, all guidance routes, scrolling, Back and Close, portrait/landscape, large text and TalkBack.
- Confirm emergency dialog then Cancel only. Do not call 911 for testing.
- Check external confirmations and missing-handler cases with mocks only. Calculator must stay reachable from dialogs.
- Calculator arithmetic, long-press = return/cancel, Back exit, interruption/relaunch and launcher icon refresh.
- Verify display readability and touch targets with actual S24 settings. Some launchers cache old icons.

## Known retained limitations
Calculator is a visual disguise only; app settings/history/monitoring can reveal it. External app traces are uncontrolled. Rotation currently resets support navigation and calculator entry. No authenticated lock or universal concealment guarantee. U.S.-only prototype; independent safety/accessibility/survivor review remains necessary before public use.
