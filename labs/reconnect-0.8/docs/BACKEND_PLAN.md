# Current backend work and remaining plan

November 16, 2026. The separate 0.8 reconnect lab implements a single-process Node service, Android sender integration and first-party recipient. It is unhosted, disabled by default in the APK and unverified on Android/browser runtime. [API.md](../live/backend/API.md) is the current protocol. [The historical proposal](HISTORICAL_06_BACKEND_PLAN.md) is retained only as a superseded record.

## Implemented and synthetically tested boundaries

Independent read/write/revoke capabilities; journal-before-create; immutable idempotent creation; lost-ack handling; stopped-first recovery; latest point only; freshness/erasure separate from session lifetime; same-link reconnect; Until Stop and chosen expiry; terminal Stop; bounded parsing/rates; separate active/terminal pools; signed one-use admission and spent-ticket guard; signed management receipts for evicted terminal rows; explicit map privacy consent; last-known tab-memory state with freshness labels. Exact final checks and omissions are in [verification](../VERIFICATION.md).

## Concrete work before a usable service

1. Name an accountable operator, region, HTTPS origin/domain and owner of security/maintenance. Configure private admission key generation, rotation and secure custody. Implement and review private ticket issuance/distribution; do not expose the master key or pretend the public freshness permit grants admission.
2. Design lost-credential/orphan Until Stop session recovery and issuance limits. Active capacity remains finite; authorized or compromised issuers can consume it. An operational generation reset ends all links, including legitimate sessions. No anonymous infinite-capacity guarantee is possible.
3. Provision TLS/renewal and retention/logging controls only with separate authorization. Validate proxy/edge/request/body/trace/crash logging, swap/backups/core dumps and operator access. Application API deletion alone does not establish infrastructure erasure.
4. Keep a single authoritative process until consistent shared-state/failover and revocation are designed/tested. A restart invalidates links and requires fresh explicit consent; do not place unrelated replicas behind a load balancer.
5. Complete Android physical-device and recipient-browser tests using synthetic positions first: permissions, notifications, Keystore, lock/Doze/network loss/reconnect, Stop, process death, installation, accessibility, resize and absent target apps. Evaluate real operational failure/abuse/load and ongoing costs.
6. For a street address or embedded map, separately design a private/self-hosted data source, precision/coverage/attribution and retention. Current optional external Google Maps opens only after informed consent; no automatic geocoding or address is supplied.

No hosted account, paid resource, public endpoint, real location, report, emergency call or recipient contact was used. This work is additional verified source, not an operating or certified emergency service.
