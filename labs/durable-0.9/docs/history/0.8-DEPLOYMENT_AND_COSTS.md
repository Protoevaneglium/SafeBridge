# Deployment, operating cost and device blockers

November 16,2026. Nothing has been deployed. No hosting/domain/account or paid resource was created. Local synthetic tests use existing execution resources and loopback only; they do not provide a public service.

## What a deployment would actually require

A separately approved HTTPS origin/domain and accountable operator are still missing. The Android `LiveConfig.ENDPOINT` is blank; no real live session can start in the default lab build. The recipient needs a reachable HTTPS URL and supported JavaScript browser, without an account or app installation. Desktop loopback is not a URL a physical recipient can use.

The implemented backend is a **single authoritative in-memory process**. Restarts invalidate all links and remove API-accessible session data. Do not run independent replicas behind a load balancer: they do not share revocation/generation state. Failover/shared-store/HA needs a new consistent design and tests. Temporary network loss pauses freshness and retries the same session. A backend process restart loses in-memory sessions and invalidates links. Neither is reliable emergency infrastructure.

Before any separately authorized deployment: configure an exact approved origin, valid TLS certificate/key and certificate renewal; keep TLS 1.2+ and hostname verification; verify network/firewall/reverse-proxy behavior; disable sensitive request/header/body/URL logs, snapshots, backups, swap/core dumps and third-party telemetry where applicable. Node application code retains only one point plus bounded management tombstones, but API deletion is not proof of secure physical RAM erasure or infrastructure retention. No such infrastructure settings have been verified here. Do not route real vulnerable-person movement through an unreviewed operator.

Production creation now requires an operator-issued one-use HMAC admission ticket, with a separate spent-ticket guard and bounded active/terminal pools. The issuer/distribution/onboarding service is NOT implemented. No real operator key exists here. Finite active capacity can still be occupied by authorized or orphan Until Stop sessions; ticket issuance, recovery and any generation-reset consequences need a separate operational policy. Terminal-cache eviction does not consume active slots; signed management receipts prevent lost-ack cleanup from relying on permanent tombstones.

Establish capacity/load limits, abuse handling, incident response, patching, maintenance ownership and an operating budget. The first valid Stop is prioritized over ordinary rate budgets; broader denial of service can still prevent network access. No uptime, continuous tracking or concealment guarantee exists. Consider independent survivor, child-safety, security/privacy and appropriate legal review before actual use. No test results here certify production safety.

## Cost evidence, not a provider selection

DigitalOcean’s official pricing currently advertises bundled Basic VMs at **$4/month for 512 MiB** and **$6/month for 1 GiB**. This is a base compute price, not a tested sizing recommendation or the total cost of this service. [Official Droplet pricing](https://www.digitalocean.com/pricing/droplets), checked November 16,2026.

Domain registration, traffic beyond allowances, monitoring, backups policy, security review and ongoing engineering/operations may add costs. They are not priced or purchased here. Pricing/availability and jurisdiction/privacy terms require rechecking when an operator and region are chosen. No provider has been selected, connected or endorsed for this app.

## Physical-device and browser blockers

The environment has Android 35 build tools/platform and a Java compiler, but no ADB, emulator, Android runtime or `/dev/kvm`. Compilation cannot verify Keystore durability, permissions, notifications, real GPS/network providers, the Android HTTPS adapter, screen lock/Doze, Active apps Stop, process death, update installation, launcher behavior or TalkBack.

Fresh Chromium/Playwright launch failed before rendering with `socket() failed: Operation not permitted`. The available browser surface could not reach the executor’s loopback URL (`ERR_CONNECTION_REFUSED`); another executor command also could not reach that isolated listener. Test servers are reachable within their own test process/context. The temporary manual listener was stopped. No port was exposed and no hosting workaround was deployed.

Recipient protocol/stream/state integration is tested against actual local HTTP responses; CSS geometry, keyboard/screen-reader operation and 200% text have not been observed in a real browser. No Android or recipient native screenshot verification exists. A shared test network/device environment or separately authorized hosted synthetic test service is needed for that next layer.

## Important Stop semantics

Stop immediately prevents new local collection and invalidates queued callbacks. A request that has passed the dispatch gate can still send or arrive after local Stop; bytes/copies cannot be recalled. A separate coalesced revocation worker avoids waiting behind upload work, but a disconnected network cannot acknowledge revocation. The server rejects updates after its terminal revocation transaction.

The 90-second upload lease runs from the **last server-accepted fix**, not from local Stop; a prior in-flight request accepted before revocation may renew that lease. A positive immutable selected end is a separate cap; Until Stop intentionally has no automatic session end. After point erasure, the session can remain paused indefinitely and the recipient can retain its own labeled last-known tab-memory copy until Clear or confirmed termination. The UI keeps remote revocation pending until authenticated acknowledgement or valid server evidence of expiry/generation invalidation. A new server generation means old links unavailable; it is not falsely described as a revocation acknowledgement. Confirmed remote termination and failed local encrypted-record cleanup are distinct.


## Remaining recipient functionality

Street address is unavailable. There is no automatic geocoding or embedded map. A consent-gated Google Maps coordinate snapshot is implemented and controller/URL tested without actually contacting Google with a position. It is not a live tracking integration; external privacy/retention and copied positions remain outside this app's control. A private address/geodata service and its coverage/privacy/license/cost review would be additional work.
