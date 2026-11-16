# Nearby safety map component

See [current implementation checkpoint](IMPLEMENTATION_CHECKPOINT.md). This component now has an Android viewer shell and optional same-origin street-tile rendering, but no deployed API/map route/provider. Defaults are disabled; no real location requests occurred.

`node --test live/nearby/nearby.test.mjs`: 18 synthetic tests pass. Browser/Android runtime behavior remains unverified. Do not describe the relative fallback plot as a street map or the new unsigned APK as a working live-map update.
