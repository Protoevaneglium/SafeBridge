# Safe Bridge 0.10: integrated live/map experimental lab

No public live service or provider is configured. Default Android endpoint and provider disclosure remain blank. No production readiness or emergency reliability is claimed.

This isolated continuation connects the shared Java sender, durable Node live service, recipient position visualization and same-service nearby map routes. The Android map shell requests consent and location permission before automatic all-category discovery. Unconfigured plots are not street maps or safe routes.

See [integration checkpoint](LIVE_INTEGRATION_CHECKPOINT.md), [proxy deployment boundary](live/backend/TRUSTED_PROXY.md), and [map routes](live/backend/NEARBY_ROUTES.md). Earlier nearby implementation documents and historical baseline reports describe earlier stages; current checkpoints supersede their integration status.

Verification: final Node regression 220/220 including six proxy tests; nearby suite 33/33; SDK35 build and Java host/durable E2E checks passed before the backend-only proxy addition. Synthetic HTTP and DOM doubles are not Android runtime or rendered-browser tests.

Run `ANDROID_HOME=/path/to/sdk bash scripts/test-live.sh` and `bash scripts/test-nearby.sh`.

Package: `com.stange.safebridge.maplab`, version 0.10.0-map-lab. No APK, signing material, production credentials or user data is included. No GitHub action deploys this service. Actual hosting, provider selection, private ingress/TLS, operator provisioning, backup/revocation recovery, Android lifecycle and browser verification remain outstanding.
