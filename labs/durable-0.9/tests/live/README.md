# Protocol 3 host verification

Run from the `safe-bridge-durable-lab` checkout. All credentials and points are synthetic. These tests do not verify Android permissions, location hardware, foreground-service lifetime, TLS deployment, recipient delivery or monitored-device safety.

Compile the coordinator with the shared, network-free origin validator and the explicitly listed host fixtures:

```sh
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d /tmp/safebridge09-tests \
  app/src/main/java/com/stange/safebridge/EndpointOriginRules.java \
  app/src/main/java/com/stange/safebridge/live/LiveCoordinator.java \
  tests/live/LiveCoordinatorTest.java tests/live/HostHttpTransport.java \
  tests/live/LiveDurableE2ETest.java
java -cp /tmp/safebridge09-tests com.stange.safebridge.live.test.LiveCoordinatorTest
java -cp /tmp/safebridge09-tests com.stange.safebridge.live.test.LiveDurableE2ETest
```

The unit harness retains prior intent, credential, Stop, race, reconnect, immutable-end and storage-failure coverage while adding explicit recovery, prepared admission and epoch pinning.

The actual HTTP suite owns a loopback-only Node subprocess and private temporary directory. It initializes the real encrypted SQLite backend, abruptly kills and reopens the process, checks stable generation/ID and metadata-only durable sequence, then requires explicit sender reconciliation before a fresh higher-sequence upload. It also covers separate reserved slots, own-slot reset, wrong-slot credentials, late acknowledgements, selected expiry, expired prepared-ticket refresh, old epoch rejection, and unknown-session Stop without create-to-revoke. Temporary state keys/database files are removed after the fixture closes. Prepared bundles pass only through the captured private operator test pipe and are never printed by the test runner.

`LiveHttpE2ETest`, `LiveRestartE2ETest` and `LiveReconnectE2ETest` are migrated compatibility entry points that invoke this same protocol-3 suite. Do not count repeated wrapper runs as additional coverage. The separate 0.8 checkout retains its own original fixtures and evidence.
