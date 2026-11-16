#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to SDK with platform/build-tools35}"
bash scripts/build.sh
python3 scripts/checks.py
python3 tests/PreparedReplacementSourceCheck.py
mkdir -p build/live-host-tests
java -m jdk.compiler/com.sun.tools.javac.Main -d build/live-host-tests \
  app/src/main/java/com/stange/safebridge/Calculator.java \
  app/src/main/java/com/stange/safebridge/NavigationState.java \
  app/src/main/java/com/stange/safebridge/LocationFix.java \
  app/src/main/java/com/stange/safebridge/LiveConfig.java \
  app/src/main/java/com/stange/safebridge/EndpointOriginRules.java \
  app/src/main/java/com/stange/safebridge/PreparedAccessRules.java \
  app/src/main/java/com/stange/safebridge/PreparedSetupFile.java \
  app/src/main/java/com/stange/safebridge/PreparedReplacementGate.java \
  app/src/main/java/com/stange/safebridge/live/LiveInterruptionPolicy.java \
  app/src/main/java/com/stange/safebridge/live/LiveResetOnlyStore.java \
  app/src/main/java/com/stange/safebridge/LiveSessionChoice.java \
  app/src/main/java/com/stange/safebridge/live/LiveRetryPolicy.java \
  app/src/main/java/com/stange/safebridge/live/LiveCoordinator.java \
  tests/CalculatorTest.java tests/NavigationTest.java tests/LocationFixTest.java tests/LiveRetryPolicyTest.java tests/LiveSessionChoiceTest.java tests/EndpointOriginRulesTest.java tests/PreparedAccessRulesTest.java tests/PreparedSetupFileTest.java tests/PreparedReplacementGateTest.java tests/LiveInterruptionPolicyTest.java tests/LiveResetOnlyStoreTest.java \
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
java -cp build/live-host-tests com.stange.safebridge.EndpointOriginRulesTest
java -cp build/live-host-tests com.stange.safebridge.PreparedAccessRulesTest
java -cp build/live-host-tests com.stange.safebridge.PreparedSetupFileTest
java -cp build/live-host-tests com.stange.safebridge.PreparedReplacementGateTest
java -cp build/live-host-tests com.stange.safebridge.live.LiveInterruptionPolicyTest
java -cp build/live-host-tests com.stange.safebridge.live.LiveResetOnlyStoreTest
node --test --test-timeout=15000 live/backend/tests/*.test.mjs live/security-tests/*.test.mjs live/web/*.test.mjs live/integration-tests/*.test.mjs
node --check live/backend/server.mjs
node --check live/backend/admission.mjs
node --check live/backend/issue-ticket.mjs
node --check live/backend/durable-store.mjs
node --check live/backend/init-state.mjs
node --check live/backend/prepared-admission.mjs
node --check live/backend/provision-prepared.mjs
node --check live/web/view.js
node --check live/web/view-deck.js
node --check live/web/view-network.js
node --check live/web/view-map.js
java -cp build/live-host-tests com.stange.safebridge.live.test.LiveDurableE2ETest
printf '%s\n' 'All lab checks completed. No deployment or real-device verification is implied.'
