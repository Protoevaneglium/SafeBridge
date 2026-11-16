# Private prepared setup for the 0.9 laboratory

This workflow prepares one installation **before it is needed**. After explicit file import, that installation can request a narrowly scoped admission ticket without asking the person to obtain or type a new code during each emergency. It does not provide anonymous public enrollment, an account system, a public issuer or an operational service.

No real operator setup, account, grant distribution, service deployment or external request was performed for this work. Tests generate synthetic capabilities only in process memory and private temporary directories, then remove their test files. The shipped app endpoint remains an independent configuration/deployment decision.

## Capability and privacy boundaries

The sender bundle has exactly four keys:

```json
{"v":1,"endpoint":"https://service.example.invalid","slotId":"<canonical 16-byte base64url value>","preparedToken":"<canonical 32-byte base64url value>"}
```

The placeholders above are not valid credentials. The provisioning CLI generates the real values with the platform cryptographic random generator: a 22-character slot identifier and 43-character token. The endpoint must match the Android app’s configured canonical HTTPS origin; importing a bundle cannot change the app’s server. No name, email address or phone number is required in the bundle or grants configuration.

**The prepared token can create sharing in its own reserved slot and explicitly end that slot’s prior/orphan session.** Someone who obtains a copy can use those powers. It does not grant a location-read endpoint, disclose the prior recipient link, or administer other slots. It is still a sensitive long-lived capability, not authentication that protects a monitored phone. Import/distribution can leave file, clipboard, browser, account or device records. No concealment or guaranteed erasure is promised.

The operator chooses how to distribute the private bundle, how to establish that an installation should receive one, and how to manage lost or compromised setup credentials. This implementation deliberately does not invent a trusted issuer or silently open registration to everyone. A genuinely first-time, unprepared person still faces an unresolved enrollment/usability versus abuse-control tradeoff. Preparation makes subsequent use practical; it does not solve that first-contact problem.

## Files and capacity

- `provision-prepared.mjs` creates one new sender bundle and appends a verifier-only record to a private operator configuration. It never takes a server master key as an argument and never prints the setup token or slot identifier.
- Current configuration/export shape is exactly `{v:2, endpoint, grants:[{slotId,verifier,disabled}]}`, with an explicit Boolean policy for every slot. Version 1 input `{v:1,endpoint,grants:[{slotId,verifier}]}` remains enabled-only compatibility; it cannot represent a disabled slot. Each verifier is SHA-256 over a domain-separated, origin-and-slot-scoped high-entropy token. It is not a hash suitable for user-chosen passwords. Raw prepared tokens and server master keys are not stored in this configuration.
- Every grant reserves one active slot. The number of grants cannot exceed the server’s sealed active capacity. Paused/orphan sessions occupy only their own slot; one prepared installation cannot take another grant’s reserved slot. This does not eliminate network denial of service, device compromise or operator-availability risks.
- Server-owned epochs and active-session bindings are durable state, not fields supplied by the grants file. Grant installation is **add-only**: existing identifiers, verifiers and disabled policy must match the sealed database; generation, epochs and bindings are preserved. Stale source configuration is rejected, rather than silently reverting a rotation or disabled policy. Only the explicit offline management flags below may disable, enable or rotate a grant; importing configuration is not a way to perform those transitions. Grant removal/reassignment is not provided by this workflow.
- Files use POSIX private-file controls. Existing parent directories must be owned by the operator, private (`0700` or stricter), and free of symbolic-link aliases. Output files are `0600`; grant reads are byte-bounded, strict UTF-8, regular-file-only, and reject symbolic/hard links and group/other permissions. These checks are not a substitute for suitable filesystem, account, backup and host security.

## Offline provisioning

Run from the checkout root. The following paths and reserved domain are examples, not an executed setup. Create the private directories under the intended operator account first; the CLI does not make a public directory private for you.

```sh
node live/backend/provision-prepared.mjs \
  --endpoint https://service.example.invalid \
  --grants-file /ABSOLUTE/PRIVATE/operator/grants.json \
  --bundle-file /ABSOLUTE/PRIVATE/distribution/install-001.json \
  --capacity 100
```

Use the exact capacity that the backend state will seal. A later call may use a new bundle filename to append another grant while below that capacity. Existing bundle files are never overwritten. Concurrent invocations are excluded by `grants.json.lock`; a stale lock after interruption requires an operator to establish that no provisioning process is running before resolving it. No automatic stale-lock deletion occurs.

New output is staged privately. Bundle publication is no-clobber; grant replacement is atomic under the lock, with file/directory synchronization. If interrupted or a filesystem sync fails, inspect both files and the state-installation result before retrying. No cross-file or power-loss recovery guarantee is claimed. A configuration file change alone does not activate a grant, and a normal server restart does not automatically merge it.

## Explicit durable activation and controlled restart

These examples assume the backend’s separate private state-encryption key file and TLS material already exist through its operator workflow. This prepared-setup CLI does not generate, reveal or distribute master keys. Never include the state key, admission signing key, TLS key or durable database in an Android bundle/APK.

For first initialization, use the backend’s explicit initialization command, rather than allowing a public request to create state:

```sh
node live/backend/init-state.mjs \
  --state-directory /ABSOLUTE/PRIVATE/state \
  --state-key-file /ABSOLUTE/PRIVATE/keys/state.key \
  --grants-file /ABSOLUTE/PRIVATE/operator/grants.json \
  --approved-origin https://service.example.invalid \
  --capacity 100
```

To install **additional** prepared grants into existing durable state, stop the server and use the explicit offline add-only installation mode:

```sh
node live/backend/init-state.mjs --install-grants \
  --state-directory /ABSOLUTE/PRIVATE/state \
  --state-key-file /ABSOLUTE/PRIVATE/keys/state.key \
  --grants-file /ABSOLUTE/PRIVATE/operator/grants.json
```

The backend’s exclusive lifetime database lock is intended to prevent installation while it is running. The configuration must retain every existing slot/verifier/disabled policy unchanged; installation cannot reset epochs, remove old grants, change enablement or exceed sealed capacity. Do not initialize a replacement empty database to apply an ordinary configuration change: that would lose the continuity the durable workflow is designed to preserve.

Start/restart with the same durable directory and keys:

```sh
node live/backend/server.mjs \
  --tls-cert /ABSOLUTE/PRIVATE/tls/cert.pem \
  --tls-key /ABSOLUTE/PRIVATE/tls/key.pem \
  --approved-origin https://service.example.invalid \
  --state-directory /ABSOLUTE/PRIVATE/state \
  --state-key-file /ABSOLUTE/PRIVATE/keys/state.key
```

Follow the backend’s deployment, backup, TLS, origin and failure-recovery instructions in `API.md`; these commands are not evidence that a service is hosted. After successful activation, distribute only the corresponding sender bundle by the operator-chosen private method. The person imports it through Android’s explicit bounded file chooser, reads the powers/privacy disclosure and chooses whether to save it. Device records and copied bundles remain outside the app’s control.

## Offline disable, rotation and verifier export

These are explicit private operator operations, not public administrative HTTP endpoints. Stop the server first; the durable lifetime lock prevents concurrent use. Every command below also requires `--state-directory /ABSOLUTE/PRIVATE/state --state-key-file /ABSOLUTE/PRIVATE/keys/state.key`.

| Offline `init-state.mjs` mode | Behavior |
|---|---|
| `--disable-prepared-slot SLOT_ID` | Rejects that prepared token and its old epoch-bound tickets. Default preserves an already active sharing session and its ID/end/read/write/revoke capabilities. |
| `--disable-prepared-slot SLOT_ID --end-current` | Also explicitly ends the current sharing session. It cannot recall positions or links already copied outside the service. |
| `--rotate-prepared-slot SLOT_ID --bundle-file NEW_PRIVATE_FILE` | Publishes a fresh high-entropy same-slot four-key sender bundle, then replaces its verifier and advances the epoch in durable state. Default preserves the current active session/credentials and preserves any disabled policy. The new bundle is not a server master key. |
| `--rotate-prepared-slot SLOT_ID --bundle-file NEW_PRIVATE_FILE --end-current` | Also explicitly ends the active session as part of the rotation transaction. |
| `--enable-prepared-slot SLOT_ID` | Explicitly re-enables the slot under its current verifier; it does not arise from rotation or configuration import. |
| `--export-prepared-grants NEW_PRIVATE_CONFIG` | Exports all current slot identifiers, verifiers and disabled flags in version 2 configuration, without raw setup tokens, master keys or location. The destination is private and no-clobber. |

For a compromised setup token, an operator can disable it, rotate while it remains disabled, distribute/import the replacement privately, then explicitly enable the new setup credential. The choice to preserve or end a current session must be made deliberately. **Rotating or disabling setup access does not by itself revoke already issued active read/write/revoke capabilities.** Use the explicit `--end-current` option when ending that existing session is appropriate. No automatic expiry, replacement identity or public enrollment is introduced.

### Interrupted rotation recovery

The replacement bundle is written privately, synchronized and published without overwriting any existing file **before** the database rotation commits. The state CLI also publishes a private `BUNDLE_FILE.rotation.json` intent receipt before that commit. The receipt is scoped and authenticated under the durable private MAC key; it binds the generation, endpoint, slot, expected old verifier/epoch/active-session binding, new verifier, disabled policy and `endCurrent` choice. It contains no raw prepared token. The publication helpers themselves never receive that server key.

A new bundle or receipt on disk does **not** establish activation. After an interrupted operation, keep the files private and inspect the outcome. Recovery is explicit:

```sh
node live/backend/init-state.mjs \
  --state-directory /ABSOLUTE/PRIVATE/state \
  --state-key-file /ABSOLUTE/PRIVATE/keys/state.key \
  --rotate-prepared-slot SLOT_ID \
  --bundle-file /ABSOLUTE/PRIVATE/distribution/rotation-001.json \
  --recover-rotation
```

If the original operation requested `--end-current`, recovery must include the same option. Recovery requires both the valid authenticated receipt and the matching private bundle; a random or old bundle is not evidence of an interrupted rotation. The database must still have the exact old verifier/epoch/binding, or already have the intended new verifier. Already-committed recovery is a no-op: it does not increment the epoch again or replay `end-current`, even if a newer session has since started. A changed old-state precondition fails rather than retargeting a later session.

If publication stopped before a valid receipt existed, the unactivated private bundle is retained but cannot be blindly recovered. Choose a fresh output path for a new explicit operation; do not overwrite, delete or assume activation automatically. Filesystem/host failure can leave unactivated files, and no cross-file/database or RAM-erasure guarantee is claimed.

### Export before adding more grants

After disable/enable/rotation, the original provisioning source file may be stale. Export the current verifier-only **version 2** configuration to a new private path, then use that exported file as the input/output of `provision-prepared.mjs` when appending a new grant. Install it through the stopped-server add-only workflow and restart. An old verifier or disabled-policy mismatch must fail installation; it must never revert current sealed policy.

This export is a provisioning view, **not a complete backup**. It omits generation, signing/encryption keys, session capabilities, epochs, bindings and other recovery state. Preserve the actual encrypted durable database and its protected key through the backend backup procedure. Initializing a fresh database from a grants export does not preserve live links or constitute ordinary recovery.

## Admission and explicit own-slot reset protocol

The recipient read-link format is unchanged. Prepared access is a sender-side capability and must never be placed in that link.

| Operation | Authorization and exact request | Result and limits |
|---|---|---|
| `POST /v1/admission` | `Authorization: Bearer <preparedToken>`; JSON `{slotId,creationKey,expectedSlotEpoch}` | First explicit intent sends `expectedSlotEpoch:null`. Retried/refreshed intent uses its journaled epoch. Response is `{generation,slotId,creationKey,admissionTicket,expiresAtMs,slotEpoch}`. An epoch change is a conflict, not permission to silently restart. |
| Existing share creation | The sender uses the ticket for its original creation key and epoch | Ticket is signed in a separate authentication domain and binds generation, slot, creation key, epoch, nonce and a short lifetime (default 120 seconds, maximum 300). The server commits the single-slot transition before acknowledging it. A different active creation key gets `409 prepared_slot_active`; a same-key lost-ack retry is distinct from creating a second session. |
| `DELETE /v1/admission/current` | `Authorization: Bearer <preparedToken>`; JSON `{slotId}` | Explicitly ends only that slot’s prior/orphan session and advances its epoch. Response contains generation, slot, `stopped`/`none`, server time and epoch—no location or prior recipient identifier/link. It is not an automatic setup/import side effect. |

Stop, expiry and explicit own-slot reset invalidate old epoch-bound tickets. Per-slot durable bindings/epochs protect capacity without making one installation’s retries consume a shared nonce-history budget. No network administrative enrollment or master-key endpoint is introduced. The older `issue-ticket.mjs` laboratory helper is not a production enrollment path.

## Code and verification

`prepared-admission.mjs` exports:

- `createPreparedGrant`, `validatePreparedGrantConfig`, `readPreparedGrantsFile`, `verifyPreparedGrant` and `normalizePreparedEndpoint`.
- `readPreparedBundleForRotation` returns only a verified bundle’s slot/verifier after private bounded reading and synchronization. `issuePreparedAdmissionTicket` and `verifyPreparedAdmissionTicket`; issuing requires `slotEpoch`, and verification can enforce expected slot, creation key and epoch. Ticket consumption, epoch updates and durable admission/reset transactions belong to the backend.

`provision-prepared.mjs` exports `provisionPreparedSlot` and `prepareRotatedGrantBundle` and has the four-argument provisioning CLI above. The rotation publication helper returns only `{slotId,verifier}` after private file/directory synchronization, never a raw token. It leaves any published bundle unactivated on a later failure; the state CLI owns the authenticated receipt and database transition. Endpoint validation rejects non-HTTPS origins, URL credentials/paths/queries/fragments, loopback and IPv4-mapped IPv6 origins. No DNS or external-service probe occurs.

Run its focused tests from the backend directory:

```sh
node --test tests/prepared-admission.test.mjs
```

The observed focused run passed 21 cases covering scoping, wrong credentials, strict config/capacity, short ticket lifetime, creation/epoch/generation binding, tampering and domain separation, private/linked/invalid-UTF-8 files, no-clobber/locking, config preservation, an actual CLI subprocess that prints no capability, disabled-aware configuration preservation, same-slot rotation publication, no-clobber behavior and strict recovery-bundle reading. All test credentials are synthetic. Backend transaction/restart/admission/reset integration and Android import/service behavior are separate checks in the project’s main verification record; these focused tests do not establish their results or production readiness.

The primitives and file flags were checked against the official [Node crypto documentation](https://nodejs.org/api/crypto.html) and [Node filesystem documentation](https://nodejs.org/api/fs.html). A constant-time digest comparison does not establish that an entire authentication request is timing-safe. No cryptographic audit, real Android import, monitored-device test, operator distribution trial or deployment is claimed.
