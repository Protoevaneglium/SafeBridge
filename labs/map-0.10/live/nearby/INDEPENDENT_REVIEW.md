# Independent source review checkpoint

A separate reviewer reread the final nearby and Android shell source and ran the nearby suite: 19/19 passed.

Findings corrected and confirmed:
- Manual coordinates remaining after Close/pagehide.
- Wi-Fi explicitly tagged private/no offered as available category.
- Subresource redirects not covered by WebView initial-URL interception: now bounded manual same-origin GET loader rejects redirects, and CSP is self-only.
- Persistent HTTP cache: native cache disabled/cleared; resources no-store. Operator upstream caching is a separate provider obligation.
- Late tile errors could modify cleared state: generation-guarded.
- Marker count did not match full list: limit disclosed and numbered list matched.
- Native opening needed a second page click: validated native opening now triggers automatic all-category path after consent.
- Permission callback before resume lost opening: consented pending-load state resumes; Clear cancels it.
- Resource lifetime: epoch cancellation/disconnect and 20-second overall deadline now added, checked before accepting data.

Caveats: independent source/synthetic tests, not Android runtime or security certification. No real geolocation, browser render, provider endpoint, hosting, accessibility or service operation verified. Configuration remains disabled. Best next validation is Android synthetic-location lifecycle/HTTPS/redirect/cancellation tests, not deployment.
