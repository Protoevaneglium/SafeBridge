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

Produces `build/SafeBridge-unsigned.apk`. Sign with your own test/release key using Android `apksigner`. No signing keys are distributed or committed. Application ID `com.stange.safebridge.preview`, min API 26, target API 35, version 0.1.0-preview.

## Validation and limitations
Native compilation, static safety checks and APK signature verification completed for delivered preview. No emulator or S24 runtime verification completed. No real calls/reports sent. Do not treat compilation as a functional or safety certification.

Before any public use: survivor-led review, child-safety and privacy/legal review, accessibility testing, monitored-device threat modeling, link maintenance, real Android device and interruption tests. Check external cancellation/return flows, large text, TalkBack, absent dialer/browser and offline behavior. Resource descriptions are informational, not a tailored safety plan.

## Scope
Public development is intended to be transparent. Do not submit personal stories, case details, private identifiers or abuse imagery in issues. No license granted yet; licensing requires the owner's decision. See SECURITY.md for reporting limitations.

## Unreleased safety wording revision
Clarifies Take It Down original-device guidance and CyberTipline alternative, limited platform coverage, uninstall traces, safer devices/accounts, and app-only scope of privacy statements. This source revision is not the already-delivered preview APK. Compilation and static checks rerun; no new runtime test or release.

The unreleased revision also adds destination-specific disclosure before hotline/CyberTipline navigation: optional identifying details and hotline reporting exceptions; CyberTipline processing/law-enforcement referral; opening a destination is not submitting a report. Official policy link included.
