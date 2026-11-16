# 0.8 correction scope

This is a separate fork; the delivered 0.6 and immutable 0.7 artifacts remain unchanged. Review confirmed that 0.7 stopped/revoked on upload I/O failure, ended sessions after a 90-second lease lapse, offered only 15/60-minute live sessions, and counted stopped/expired tombstones against the 1,000-session quota. Its recipient also hid last-known coordinates on connection failure and had no map/address.

## Corrections

- Protocol 2 distinguishes session lifetime from point freshness. Duration zero means Until Stop; a positive chosen end is immutable. After 90 seconds the server erases its point and exposes nonterminal `paused`. Fresh newer samples resume the same ID/link. Confirmed Stop/expiry remain terminal.
- Temporary network failures, selected HTTP errors and rate limits preserve foreground consent and subscriptions. Retry uses bounded 1–60-second backoff and only a new fresh latest sample. Stale-sample rejection drops that sample. Protocol, TLS, storage and authorization failures still stop locally.
- Native controls offer Until Stop or a future end date/time. The original absolute UTC end is sent unchanged as `requestedExpiresAtMs`; delayed creation/retries cannot move it later. New creation after the chosen end fails before allocation/ticket consumption. Device/server wall-clock disagreement is still a documented limit. A separately reproduced slow-POST/status early-stop bug was also corrected: hard expiry uses response-arrival timing capped by original consent; data freshness remains conservatively anchored to request start.
- Active and terminal quotas are separate and bounded. Production creation requires a short-lived one-use operator-issued HMAC ticket; no master key is in the APK. Anonymous creation is an explicit loopback-only synthetic fixture. Replay prevention is separate from bounded terminal retention.
- Signed management receipts allow conservative cleanup after terminal-cache eviction without falsely claiming a remote revocation acknowledgement. Local encrypted cleanup failure remains distinct from confirmed remote end.
- Recipient transient failure/paused data retains the last received point in tab memory with timestamp, age, accuracy and current-status uncertainty. Known positive expiry, confirmed Stop and Clear remove it. Hidden pages suppress coordinates and revalidate on return.
- Optional Google Maps opens only after separate privacy disclosure and final confirmation. The URL includes only selected coordinates, no session capability. No map tiles/geocoder run automatically. Street address remains unavailable.

## Evidence and remaining gaps

The final exact checks are in `VERIFICATION.md` and `LIVE_LAB_CHECKS.txt`; they use synthetic data on loopback and source/host tests. They do not establish real Android foreground, lock/Doze, Keystore, GPS, browser rendering or assistive-technology behavior.

This work is not deployment-only blocked: private admission distribution/onboarding, authorized/orphan Until Stop capacity/recovery, street address/geodata, runtime device/browser accessibility and production operations remain unresolved. The default HTTPS endpoint is blank. No public live service, account, domain, real recipient, emergency call or position submission was created.
