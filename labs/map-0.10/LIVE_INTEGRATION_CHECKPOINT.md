# Local live-service integration checkpoint

## Implemented and tested, not deployed

The existing shared Java sender coordinator, durable Node service, nearby map web entry/API and recipient visualization are now connected in this isolated source continuation.

- Android nearby configuration derives the origin from `LiveConfig.endpoint()`, eliminating independent live/map origin drift. Blank endpoint and blank provider disclosure keep default builds disabled.
- The existing live server now mounts an allowlisted `/nearby` page/modules/config and optional `/api/nearby`, `/api/tiles/...`, `/v1/map-config`. Map providers must be explicitly injected with disclosure/attribution; defaults perform no provider call.
- Map API inputs/outputs are bounded. Independent quotas/concurrency/deadlines protect this optional path. Map endpoints reject live Authorization, Cookie and X-SafeBridge headers, and callbacks receive only area/tile parameters plus cancellation. No live capability goes to map providers.
- Recipient local position plot consumes the existing validated recipient state, updates as verified fixes arrive, labels old/last-known positions, and wipes on confirmed Stop/expiry/Clear. It requests no map provider by default. Optional same-origin street tiles require configuration disclosure and a separate explicit confirmation covering visible future map-area updates.
- Existing recipient external Google snapshot action remains separate. A coordinate plot is not a street map or safe route.

## Executed evidence

Final `scripts/test-live.sh`: SDK35 compilation, Java host checks, **214/214 Node cases**, then **42 host Java-to-Node durable E2E checks** passed. Final `scripts/test-nearby.sh`: **33/33 cases** passed. Logs in the local test-run log (not distributed) and the local test-run log (not distributed).

New `java-recipient-map.test.mjs` drives the real shared Java `LiveCoordinator` over private loopback IPC/HTTP into the real encrypted-SQLite backend. It creates a prepared link, sends two synthetic fixes, verifies recipient state and plot movement, tests same-service nearby lookup, rejects live credentials at map routes, restarts the server, verifies last-known behavior, explicitly resumes the original link, and confirms Stop clears map/data. The durable snapshot contains neither raw coordinates nor raw capabilities.

The HTTP transport is the host test adapter; map interactions use DOM doubles. This is NOT Android GPS/Keystore/HTTPS/lifecycle/WebView execution or browser rendering/accessibility evidence. All coordinates, grants and keys are synthetic. Listeners/subprocesses/temp databases are closed/removed. No real recipient, location, paid account or provider was used.

## Corrections during testing

- An initial unconditional awaited map route probe introduced an async gap on existing non-map request handling. The hook is now gated to exact map routes; prior crash/reset regressions pass.
- The first Java-to-map fixture accidentally overwrote its loopback endpoint with its synthetic prepared-bundle origin. Corrected fixture field precedence; focused and full final reruns pass. This was a test setup failure, not a false pass.

## Outstanding

No configured production endpoint/provider or deployment. No API account/billing authority inferred. Previous coherent old-database rollback, operator provisioning/distribution/abuse/capacity/availability/retention decisions remain. No emulator/adb or /dev/kvm was available; no privileged system changes attempted.

This is an isolated experimental source continuation. Prior lab directories are preserved. No deployment is implied by source publication.

A source-only checkpoint can be preserved separately. The build output is unsigned; no new installable APK or public-live readiness is claimed.
