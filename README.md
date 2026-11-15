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

Produces `build/SafeBridge-unsigned.apk`. Sign with your own test/release key using Android `apksigner`. No signing keys are distributed or committed. Application ID `com.stange.safebridge.preview`, min API 26, target API 35, version 0.2.0-preview.

## Validation and limitations
Native compilation, static safety checks and APK signature verification completed for delivered preview. No emulator or S24 runtime verification completed. No real calls/reports sent. Do not treat compilation as a functional or safety certification.

Before any public use: survivor-led review, child-safety and privacy/legal review, accessibility testing, monitored-device threat modeling, link maintenance, real Android device and interruption tests. Check external cancellation/return flows, large text, TalkBack, absent dialer/browser and offline behavior. Resource descriptions are informational, not a tailored safety plan.

## Scope
Public development is intended to be transparent. Do not submit personal stories, case details, private identifiers or abuse imagery in issues. No license granted yet; licensing requires the owner's decision. See SECURITY.md for reporting limitations.

## Safety wording included in 0.2 preview
Clarifies Take It Down original-device guidance and CyberTipline alternative, limited platform coverage, uninstall traces, safer devices/accounts, and app-only scope of privacy statements. These revisions are included in the privately delivered 0.2 preview. Compilation and static checks were run; no Android runtime verification or public APK release has been completed.

This revision also adds destination-specific disclosure before hotline/CyberTipline navigation: optional identifying details and hotline reporting exceptions; CyberTipline processing/law-enforcement referral; opening a destination is not submitting a report. Official policy link included.

## 0.2 experimental preview controls
- Persistent red Emergency • 911 control opens a confirmation, then Android’s dialer with 911. The user must tap Call; no automatic call, dispatch or location upload. Never test using a real emergency call.
- The ◈ button switches immediately to a functional colorful calculator and toggles launcher aliases to a Calculator icon/name. Long-press = (or accessibility long-click action) to confirm return to Safe Bridge. This return action is not authentication.
- Launcher alias choice persists across process restart. Calculations are memory-only and reset after recreation. Back exits calculator without opening support content.
- This is only visual camouflage. Android settings/app info, package names, installation/download/account records, launcher caches and device monitoring can expose the app. Some launchers delay alias/icon refresh or may display duplicate/stale shortcuts. No invisibility or safety guarantee.
- No additional permission or tracker introduced. Native compilation, static checks and 21 host calculator cases pass. Device testing of aliases, update migration, large text, TalkBack long-click, interruptions and dialer handoff remains required.
- Signing key is private and excluded. The 0.2 test APK was delivered privately to the owner. This repository publishes its source, not a public APK release or an emergency-service product.

External website/dialer confirmations, emergency confirmation and missing-handler dialogs include a Calculator button so switching does not require canceling first. The calculator return dialog can be canceled without leaving calculator mode. Native confirmation layout/accessibility remains device-untested.
