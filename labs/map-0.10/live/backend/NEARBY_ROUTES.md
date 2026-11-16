# Optional nearby/map integration (unconfigured)

`createSafeBridgeServer({ nearby: ... })` now serves allowlisted map assets at
`/nearby/index.html` and `/nearby/{map,model,tiles,config}.mjs` plus `map.css`.
`config.mjs` is generated from explicit operator configuration, not read from disk.
Fixtures, provider implementation and source documents are not served.
The existing CLI does not enable a provider. Default APIs return an explicit
not-configured response. This is source integration, not hosted deployment.

Optional trusted, server-side injection:

- `provider({ origin: {latitude,longitude}, radius, signal })` returns the normalized
  `items`, `possiblyTruncated` shape used by `live/nearby/provider.mjs`.
- `providerDisclosure`: nonempty public explanation identifying provider/retention.
- `tileProvider({ z, x, y, signal })`: returns a PNG Buffer, at most 1 MiB,
  dimensions at most 1024x1024. No user headers/capabilities are passed.
- `tileAttribution`: required public provider/data-source attribution.
- `timeoutMs`: 10–20000ms, default 18000; low values exist for synthetic tests.

Providers are trusted application code, not user-supplied scripts or URLs. They
must honor abort, allowlist upstream targets, reject redirects, bound upstream
bytes, handle provider-required caching privately, avoid logging location data,
and keep credentials server-side. The route cannot preempt synchronous blocking
provider code. A provider that ignores abort retains its concurrency slot until
settlement, so requests cannot create unbounded orphaned operations.

## Routes and isolation

`POST /api/nearby`: JSON containing exactly latitude, longitude, integer radius
100–10000m. Body limit 2048 bytes / 2 seconds. Provider results are validated and
copied to an output allowlist; distances recalculated, availability unverified,
and response capped at 1 MiB / 500 places. No remote text is used as HTML.

`GET /api/tiles/{z}/{x}/{y}.png`: integer zoom 3–18 and bounded indices. Requires
explicit tile provider and nearby provider/disclosure. Only bounded PNG output.

`GET /v1/map-config`: returns only `tileTemplate`, `tileAttribution`, and
`providerDisclosure`, null by default. A recipient must obtain explicit consent
before requesting street tiles. No recipient capability is needed or accepted.

Map routes reject Authorization, Cookie and x-safebridge-* headers. Query strings
and foreign Origins remain rejected by the existing server handler. Native and
web callers must omit live-session credentials. Provider callbacks receive only
validated public parameters and an abort signal, never raw HTTP requests.

Map limits are separate from live update/Stop budgets: maximum 1200 requests/min
across map routes, 240/min per hashed network address, 12 nearby queries/min per
address, 512 transient address buckets, eight active provider calls. Buckets use
a per-process keyed digest and clear each minute; addresses are not persisted.
These are finite pilot safeguards, not proof of attack resistance or capacity.

All map responses are no-store with restrictive CSP. Only configured nearby HTML
allows geolocation=(self); live recipient responses continue to deny geolocation.
No live API/authentication/Stop format changed. The server checks map paths before
awaiting the map handler, preserving existing live request-body listener timing.

## Verification

`node --test live/backend/tests/nearby-routes.test.mjs`: 11 real loopback HTTP
cases, synthetic coordinates and raster only. Covers disabled/default state,
header isolation, input/output limits, timeout/concurrency, tiles, rate separation,
and preservation of recipient Permissions Policy. No external provider call,
real location, TLS deployment, Android/WebView or rendered-browser execution.
