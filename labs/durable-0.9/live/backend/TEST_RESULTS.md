# Final protocol 3 backend check evidence

Executed in the private task environment with Node.js v24.22.1. No real operator installation, recipient account, public endpoint or movement was used; keys, bundles, encrypted SQLite directories and TLS certificates were temporary synthetic fixtures.

```sh
node --test --test-timeout=15000 live/backend/tests/*.test.mjs live/security-tests/*.test.mjs
```

Result: **153 tests, 153 passed, 0 failed, 0 skipped**.

Included cases:

- 33 backend cases: scoped state/expiry/input contracts; actual prepared/durable HTTP restart; generation binding; slot isolation; private initialization/add-only installation CLI; immutable chosen end; actual disable/rotate/enable/export/recovery CLI; and strict operator flags/publication.
- 21 prepared module/provisioning cases: strict bundle/verifier scope, epoch tickets, private bounded files, capacity, v2 disabled-aware configuration, same-slot rotation publication and recovery-bundle reading.
- 28 independent HTTP cases and 9 actual prepared production-TLS cases, including certificate trust/hostname checks and plaintext rejection.
- 14 independent child-process crash cases, including actual SIGKILL after committed create/PUT/Stop/reset with withheld ACK, same IDs/generation and no restored point, last sequence, concurrent-owner lock rejection, and incomplete/in-flight request boundaries.
- 11 independent SQLite/encryption cases: decode record plaintext to establish no coordinate/sample payload/digest/raw capability storage; AAD/manifest authentication; missing/wrong/corrupt state; partial same-record replay rejection; exclusive locking; and the full-rollback limitation below.
- 3 independent private operator/configuration checks covering strict reserved configuration and actual provision/init/install preservation/refusal.
- 6 independent offline disable/rotation/recovery cases, including actual CLI `--import` interception before BEGIN IMMEDIATE, SIGSTOP/SIGKILL after private bundle+authenticated receipt publication, CAS recovery/idempotence, no replay of ending against a later active ID, forged/mismatched files, stale config/export/add-install, old-token denial and default current-link preservation.
- 21 recipient privacy/state/controller regressions and 7 actual durable recipient-restart cases.

No hidden Until Stop end is introduced. Offline disable/rotation preserves the current session by default; `--end-current` is an explicit separate choice. Rotation preserves disabled policy, enable is explicit, and installation cannot undo those choices. New private bundle and HMAC-authenticated recovery receipt are synchronized before DB commit. Interrupted commands keep published files private and unmodified; recovery either commits the exact prepared intent once or refuses mismatched state. No success ACK precedes the durable commit.

One passing adversarial case deliberately demonstrates the known boundary: restoring an entire coherent older database plus its authenticated manifest is accepted without an independent monotonic anchor. That can undo Stop/epoch authorization state. This is evidence of the limitation, not a claim that full rollback is prevented. No coordinate can be recovered from this database because none was stored; that does not make rollback of authorization safe. API.md and PREPARED_SETUP.md explain the separate operational/anchor requirement.

Final checked core SHA-256:

```text
a4761d17911e754d98bb6a1f31e880595d3a798ddc16470b1cb22085901a9fd6  server.mjs
a7c8a8a8f4054126e6c98d8502ab607b01d8614070af25e02d3cb94dd0ab0acb  durable-store.mjs
8c625c9220ee9e9a0f955250e317b34207997f37c02e7a7254e76c072cd5bdea  init-state.mjs
6da934af5b7903e17d33bf865222b9f9fca75f6207121b5bdc18665313f70342  prepared-admission.mjs
2872a8a081400b79c73001961e5055fb06f6692ad10d5d726ea0a2c6f953da9f  provision-prepared.mjs
```

Not tested here: real power loss/controllers, production persistent volumes/backups/rollback anchors, external uptime, real grant distribution, actual Android permission/foreground/device lifecycle, monitored-device resistance, complete accessibility behavior, or survivor/legal/privacy certification. No public deployment, real emergency call, report or real-location session was performed. The Node suite does not establish those properties.
