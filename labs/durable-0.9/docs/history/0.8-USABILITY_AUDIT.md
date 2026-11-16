# Safe Bridge 0.8 design and usability audit

Reviewed November 16, 2026 in `safe-bridge-reconnect-lab`. This is a source and synthetic-test review of the 0.8 reconnect laboratory, not a rendered-interface or accessibility certification. The earlier 0.6/0.7 artifacts were not changed by this documentation update.

## Evidence and historical context

The earlier audit inspected the actual pixels of both owner-supplied reference images, rather than relying on OCR or filenames: a 1500×1100 PNG with mint/cyan/blush panels and paired pills, and a 1600×1600 PNG with translucent blue/cyan surfaces and white edge highlights. That is historical reference evidence retained for this design direction. This 0.8 documentation update did not re-open those images or produce an application screenshot. See `VISUAL_REFERENCE_REVIEW.md` for the distinction between reference inspection and rendered-app verification.

The old 0.6 baseline findings—nested advocate access, late Stop, overlapping More destinations, and delayed first-fix UI refresh—remain useful history. They do not describe all current 0.8 behavior. Earlier laboratory test counts of 25 or 30, old source hashes and 0.7 recipient behavior that hid all coordinates after a transient failure are superseded by the current source and verification record.

For 0.8, the review inspected MainActivity action ordering, the session strip, page/disclosure components, live configuration and the recipient state/controller/deck/map modules. The observed recipient check run passed **59 tests**: 38 web tests and 21 independent recipient privacy tests. These include actual loopback HTTP requests and a deliberate socket failure, an injected clock advancing beyond the 90-second server point deadline, production state/response-reader code, simulated controller consent flows, and dependency-free DOM identity tests. The 90-second outage is simulated by an injected clock; it is not a 90-second real-device network trial. Test totals are a snapshot of that run, not additional independent evidence for each repeated execution. The main verification document owns the final project-wide counts.

**No successful real-browser or native Android rendering was obtained.** Historical fresh-Chromium launches failed with a kernel socket restriction before rendering; an attempted cloud-browser connection to the local test surface returned connection refused. Neither is a failed visual assertion. No actual recipient screenshot, native screenshot, viewport measurement or assistive-technology session was produced.

## Current Android design and access

| Area | Current source behavior | What the evidence does not establish |
|---|---|---|
| Home | A compact heading and monitored-device warning precede four paired rows: Support now/Find Me, Can’t speak/Can’t leave, Warning signs/Peer groups, Safety tools/For someone. Pastel fills distinguish the choices. | All eight choices are not proven to fit on a first phone screen, particularly with larger text. They remain in a measured page deck. |
| Immediate shell controls | Short visible 911 and Find Me controls plus the calculator control sit outside guidance pagination. The 911 control has a full accessible name and retains confirmation before an ACTION_DIAL handoff. | No device geometry or handler behavior was verified. Actual Call remains a separate phone-app action and must never be tested with 911. |
| Useful actions before detail | Support now goes directly to advocate choices; For someone goes directly to that topic. Recruitment, online pressure, supporting someone and safety-plan pages put relevant action pairs before longer prose. | These are source ordering and route improvements, not measured completion times or universal first-screen access. Child reporting, image tools and several secondary needs still use menus. |
| Find Me | Snapshots and Live links are distinct pages. Fix/copy/share actions precede the full readout; last-known lookup remains explicitly labeled as possibly old. One-shot requests cancel when leaving Find Me. | Location permission, GPS, accuracy, system sharing and absent-handler behavior have not been exercised on Android. A chooser is not proof of sending or receipt. |
| Pinned Stop | An active-session strip is placed before the page body on guidance screens and Calculator. It distinguishes local updating, confirmed/unverified sharing, and local-stop/remote-revocation recovery states, with Stop or retry controls. | A modal sheet covers underlying controls: the in-app route there is Cancel, then Stop. Notification Stop is another intended route. Touch geometry and interruption behavior remain untested. |
| State and focus updates | Unchanged strips avoid rebuilding. Feature refresh retains reading anchors and attempts to restore tagged keyboard/accessibility focus. | Android feature views can still be rebuilt. Source bookkeeping does not establish actual TalkBack, keyboard or switch-access continuity. |
| Disguise | Calculator and Close remain distinct from Stop. Disclosures explain visible notifications, system indicators and discoverable app/device records. | No concealment, silence, continuous tracking or trace-erasure guarantee follows from the disguise. |

### Source route lower bounds

These counts assume the relevant control is visible. They exclude content/disclosure Next taps, permission prompts and external-app actions; they are not observed task results.

| Need | Lower bound from Home |
|---|---|
| Trained advocate | Support now, call/chat choice, confirmation: 3 app taps to external handoff. |
| Cannot speak or hiding | Can’t speak, official text/chat options, confirmation: 3 app taps to browser; provider choices remain external. |
| Concerned family/friend | For someone: 1 route tap to the topic; its support/reporting choices precede the long explanation. |
| Get a location snapshot | Find Me, Get a location fix: 2 app taps, plus any permission prompt. Export requires a separate choice and disclosure. |
| Stop an active session | 1 tap on its visible shell strip; Cancel then Stop when a modal is open. |
| Calculator | 1 tap from guidance or an applicable disclosure sheet; it does not stop a location session. |
| U.S. emergency dialer | 2 app taps through confirmation. Actual Call is external and must not be tested with a real emergency number. |
| Live-link information | Find Me, Live links: 2 route taps. There is no working public live handoff in this build because the endpoint is blank. |

## 0.8 reconnect and recipient behavior

The sender offers **Share until I stop** or **Choose an end date & time**. The consent and status language distinguishes connection retries, local collection, acknowledged updates, local Stop and pending remote revocation. Calculator and Close do not stop an explicitly started session. These are implemented client/backend states, not evidence of reliable Android continuation or a deployed service.

The V2 recipient uses measured **Position**, **Privacy** and optional map-disclosure pages. Global status, Refresh, Clear and Map controls remain outside the text deck. Verified position fields—coordinates, fix age, estimated accuracy and timestamps—precede longer connection explanations. Text keeps its chosen size; long blocks continue across pages. Native and web decks have an **Expand window** fallback when readable content cannot fit. This is a fail-closed fallback, not proof that every narrow, landscape or large-text viewport is usable.

Important changes from historical 0.7 recipient behavior:

- A transient fetch/revalidation failure retains the latest received point in **tab memory only** and displays **Last known; current position unverified**, its device-supplied UTC fix time, growing age and estimated accuracy. No storage-based recovery or invented street address is used.
- The server’s 90-second point deadline can produce nonterminal `paused` with no server point. An existing viewer can still show its labeled tab-only last-known copy; a new viewer with no prior point has nothing to display. A fresh update on the same capability can resume verified viewing.
- Lease loss is not Stop or chosen expiry. `expiresAtMs = 0` means no requested end, labeled until stopped. A positive chosen deadline remains pinned to the earliest verified monotonic deadline and clears locally even during an outage when code can run. Browser suspension can delay execution; no exact cleanup or RAM-erasure guarantee is made.
- Confirmed Stop, chosen expiry, Clear and permanent link unavailability clear the page’s point/timing state and viewing credential. Hidden pages stop polling and hide displayed coordinates while retaining the tab-only point for an unverified return. A known positive deadline is advanced by available timer/resume execution. Copies outside this page cannot be recalled.
- Age is anchored separately from server revalidation. A paused response with no point does not reset an old fix’s age. A recent estimate is not labeled as a guaranteed current position or proof of safety.

Visible-only requests retain bounded retries and manual refresh. A browser online event can restart visible retry attempts; it does not guarantee connectivity or uninterrupted tracking. Response reading is byte-bounded and rejects malformed data. The one-second age display skips unchanged layouts, ignores unrelated age changes on Privacy pages, and patches stable semantic/text nodes instead of replacing all visible content. Simulated DOM tests support that source behavior; actual screen-reader behavior remains unknown.

## Optional external map and address limits

Map first opens measured disclosure pages. The final Open action is gated until their last page, with Cancel available. Consent names Google, the selected last-known snapshot, its fix time/age/accuracy, device/network data disclosure and Google’s own privacy practices. Google may keep its own records. The selected map point does not silently change to a newer point while consent is being read.

Only explicit final confirmation requests the fixed Google Maps coordinate URL. Viewing tokens, share identifier and generation are excluded; the action requests no page referrer. No map tiles, third-party fonts/scripts, geocoding or address lookup load automatically. **Street address unavailable; coordinates are a device estimate** is explicit. Clear, confirmed terminal states and hiding the page invalidate an open map disclosure. Tests execute the production controller with a simulated external-navigation function; no external map request or provider rendering was performed.

## Availability and unresolved proof

`LiveConfig.ENDPOINT` remains blank. No live endpoint was deployed, no public service is configured and no public release was performed by this work. The recipient and backend source, local HTTP tests and sender logic do not establish a working public sender-to-recipient experience. Operator deployment, admission configuration, operational/privacy review and independent end-to-end testing remain separate work.

Still unverified: native installation/rendering, real device taps/GPS, permission and notification dialogs, Android sharing, lock/sleep/foreground-service continuation, launcher caching, real browser rendering/navigation, keyboard and switch access, TalkBack or other assistive technology, landscape, split screen and 200% or extreme text/display scaling. No runtime visual-polish, no-clipping or full-accessibility certification is claimed.

Follow-up should measure complete paths including all pages and disclosures at representative compact portrait sizes, narrow split screen, landscape and large text; exercise Back/Cancel/repeated taps, focus preservation, missing handlers, network loss/reconnect, chosen expiry and Stop/revocation recovery. Use synthetic coordinates and mocked dialer intents or Cancel. Never place a real emergency call. Review browser back-forward/cache, screenshot/print and monitoring risks without promising erasure or concealment.
