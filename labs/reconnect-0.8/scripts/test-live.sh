#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to SDK with platform/build-tools35}"
bash scripts/build.sh
python3 scripts/checks.py
mkdir -p build/live-host-tests
java -m jdk.compiler/com.sun.tools.javac.Main -d build/live-host-tests \
  app/src/main/java/com/stange/safebridge/Calculator.java \
  app/src/main/java/com/stange/safebridge/NavigationState.java \
  app/src/main/java/com/stange/safebridge/LocationFix.java \
  app/src/main/java/com/stange/safebridge/LiveConfig.java \
  app/src/main/java/com/stange/safebridge/LiveSessionChoice.java \
  app/src/main/java/com/stange/safebridge/live/LiveRetryPolicy.java \
  app/src/main/java/com/stange/safebridge/live/LiveCoordinator.java \
  tests/CalculatorTest.java tests/NavigationTest.java tests/LocationFixTest.java tests/LiveRetryPolicyTest.java tests/LiveSessionChoiceTest.java \
  tests/live/*.java live/security-tests/LiveCoordinatorAdversarialTest.java live/security-tests/LiveServicePolicyAdversarialTest.java
java -cp build/live-host-tests CalculatorTest
java -cp build/live-host-tests NavigationTest
java -cp build/live-host-tests com.stange.safebridge.LocationFixTest
java -cp build/live-host-tests com.stange.safebridge.LiveConfigTest
java -cp build/live-host-tests com.stange.safebridge.live.test.LiveCoordinatorTest
java -cp build/live-host-tests LiveCoordinatorAdversarialTest
java -cp build/live-host-tests LiveServicePolicyAdversarialTest
java -cp build/live-host-tests com.stange.safebridge.live.LiveRetryPolicyTest
java -cp build/live-host-tests com.stange.safebridge.LiveSessionChoiceTest
node --test live/backend/tests/*.test.mjs live/security-tests/*.test.mjs live/web/*.test.mjs live/integration-tests/*.test.mjs
node --check live/backend/server.mjs
node --check live/backend/admission.mjs
node --check live/backend/issue-ticket.mjs
node --check live/web/view.js
node --check live/web/view-deck.js
node --check live/web/view-network.js
node --check live/web/view-map.js
node live/backend/server.mjs --dev-loopback --allow-anonymous-test-creation --port 18787 > build/live-http-server.txt 2>&1 &
sb_lab_server_pid=$!
cleanup(){ kill "$sb_lab_server_pid" 2>/dev/null || true; wait "$sb_lab_server_pid" 2>/dev/null || true; }
trap cleanup EXIT
node --input-type=module - <<'JS'
let available=false;
for(let i=0;i<30;i++){
  try{const result=await fetch('http://127.0.0.1:18787/v1/config');if(result.ok){available=true;break;}}catch{}
  await new Promise(resolve=>setTimeout(resolve,100));
}
if(!available)throw new Error('Loopback synthetic server did not become ready');
JS
java -cp build/live-host-tests com.stange.safebridge.live.test.LiveHttpE2ETest http://127.0.0.1:18787
cleanup
trap - EXIT
java -cp build/live-host-tests com.stange.safebridge.live.test.LiveRestartE2ETest
java -cp build/live-host-tests com.stange.safebridge.live.test.LiveReconnectE2ETest
printf '%s\n' 'All lab checks completed. No deployment or real-device verification is implied.'
