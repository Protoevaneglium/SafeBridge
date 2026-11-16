# 0.10 nearby-map continuation checkpoint

## Implemented locally

- Find Me now opens an unexported Android `NearbyMapActivity`. New package `com.stange.safebridge.maplab`, version 10 / 0.10.0-map-lab, intended not to replace prior lab installations.
- Explicit native provider disclosure and OS location permission; native accepted opening triggers all-category discovery automatically after page load (no per-category search). Denied location leaves manual-area flow.
- Isolated HTTPS-origin WebView: no JavaScript native bridge, file/content access, DOM storage, cookies or mixed content. Same-origin GET subresources use a bounded manual HTTPS loader with redirects disabled; web API POST uses fetch redirect:error. CSP restricts scripts/images/connect to same origin. No live-session tokens passed to map.
- Closing/stopping clears the page, manual input, geolocation grants, history/form data and HTTP cache. GET resources are memory-loaded, no-store. This is not a forensic-erasure guarantee. Upstream map tile proxy must cache as its provider requires while returning no-store to the app; direct public OSM tiles are not configured.
- Configurable same-origin raster street-tile layer, zoom controls, bounded tile count, Web Mercator placement and antimeridian wrapping. Without tile configuration the plot explicitly remains a relative-position plot, not a road map. Up to 100 markers / 25 numbered; complete returned list retained.
- Nearby normalization and bounded HTTPS Overpass-format adapter support police/hospitals/Wi-Fi/public phones together, filter known restricted listings, and never claim availability or emergency departments from absent tags.

## Not implemented / configured

No endpoint, provider, hosted map route, tile proxy, API route, account, production credential, deployment or service purchase. No real nearby search, public live link or recipient route operated. The built map shell displays an honest unavailable state until an operator-approved same-origin HTTPS service and provider disclosure are configured. Existing live backend remains blank-endpoint/prepared-access lab with its previous rollback and operational limitations.

The Android shell references `/nearby/index.html`; deployment must serve `.mjs` as JavaScript, set the reviewed CSP/no-store headers, allow geolocation only for this map page (existing recipient policy disables it), expose bounded/rate-limited authorized `/api/nearby`, and only an allowlisted tile proxy. These are requirements, not implemented routes. Keep map access separate from live recipient capabilities.

## Verification

Final 0.10 continuation aggregate passed Android SDK35 compilation and all existing 197 Node cases plus printed Java host checks and 42 real host-Java-to-Node synthetic E2E assertions. Added nearby suite: 19 passing tests; includes geometry/provider normalization/limits and source-only Android privacy contracts. Source assertions do not prove Android runtime behavior.

Independent review found redirect/cache gaps; manual redirect-disabled native GET loading, CSP and cache clearing were then added. Also fixed stale tile-error callbacks and disclosed marker limit, and native automatic opening. No final independent runtime security certification.

No emulator/device/browser execution. Chromium startup was blocked by sandbox socket permissions. No Android GPS, permissions/lifecycle, WebView redirects/JS/geolocation, actual tile render, TalkBack or touchscreen tests. No guaranteed safety/accessibility/concealment. Package is not signed/delivered as a new APK; `build/SafeBridge-unsigned.apk` is the new compile output. The copied `build/SafeBridge-0.9.0-durable-lab.apk` is old and contains none of these changes.

Prior blanket WebView prohibition was changed only in this continuation to a narrow NearbyMapActivity exception with explicit source checks; all other classes retain the ban. HTTPS connection exception is similarly limited to NearbyMapResource. Previous labs are preserved.

Final independent review also identified permission callback-before-resume and in-flight native resource lifetime gaps. The source now queues consented load until resume, clears the pending flag on Close, disconnects tracked resource requests, discards canceled-epoch responses, and applies a 20-second overall deadline in addition to socket timeouts. Cancellation is best-effort and cannot recall already sent requests. Runtime ordering remains untested.

## Synthetic-service follow-through

Added `fixtures/server.mjs` plus actual HTTP and actual-controller/DOM-double suites. `bash scripts/test-nearby.sh` passes **33 cases total**: 19 prior helper/source cases + 6 fixture HTTP cases + 8 controller executions. Fixture binds only 127.0.0.1, serves allowlisted modules and fake data/tiles, refuses arbitrary coordinates, persists nothing and contacts no external provider. A reviewed manual-browser risk was corrected: served fixture code replaces device geolocation with fixed synthetic coordinates, and Permissions-Policy denies real geolocation. Production map source/config remain unchanged.

The actual map controller now executes in tests with mocked DOM/geolocation and real loopback HTTP, covering success/automatic categories, denial/manual fallback, Close-before-fix, Close-in-flight, stale tile callback, outage/redirect/oversize, disabled configuration and zoom. This is not image rendering, browser CSP enforcement or Android runtime.

No adb/emulator in available PATH/SDK and no /dev/kvm; no privileged changes or downloads attempted. The Android shell remains HTTPS-only, so the HTTP fixture is not passed off as an Android E2E test. Use an available emulator/device and trusted synthetic HTTPS setup for the next runtime stage.
