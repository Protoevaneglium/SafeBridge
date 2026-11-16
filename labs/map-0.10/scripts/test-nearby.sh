#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node --check live/nearby/map.mjs
node --check live/nearby/fixtures/server.mjs
node --experimental-vm-modules --test live/nearby/nearby.test.mjs live/nearby/fixtures/*.test.mjs
