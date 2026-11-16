import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, createHmac } from 'node:crypto';
import { mkdtemp, writeFile, readFile, chmod, stat, symlink, link, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createPreparedGrant, validatePreparedGrantConfig, readPreparedGrantsFile, verifyPreparedGrant, issuePreparedAdmissionTicket, verifyPreparedAdmissionTicket, normalizePreparedEndpoint, MAX_PREPARED_GRANTS_BYTES, readPreparedBundleForRotation } from '../prepared-admission.mjs';
import { provisionPreparedSlot, prepareRotatedGrantBundle } from '../provision-prepared.mjs';
import { issueAdmissionTicket, verifyAdmissionTicket } from '../admission.mjs';

const endpoint = 'https://prepared.example.invalid';
const id = () => randomBytes(16).toString('base64url');
const token = () => randomBytes(32).toString('base64url');
const master = () => randomBytes(32);
const configFor = (grants, value = endpoint) => ({ v: 1, endpoint: value, grants });
async function temporary(t) { const dir = await mkdtemp(join(tmpdir(), 'safe-bridge-prepared-test-')); await chmod(dir, 0o700); t.after(() => rm(dir, { recursive: true, force: true })); return dir; }
function ticketOptions(extra = {}) { return { admissionKey: master(), generation: id(), slotId: id(), creationKey: token(), slotEpoch: 0, nowMs: 1_000_000, ...extra }; }

test('one prepared bundle has exact import fields and only a scoped verifier goes to the server', () => {
  const { bundle, grant } = createPreparedGrant({ endpoint });
  assert.deepEqual(Object.keys(bundle).sort(), ['endpoint','preparedToken','slotId','v']);
  assert.equal(bundle.v, 1); assert.equal(bundle.slotId.length, 22); assert.equal(bundle.preparedToken.length, 43);
  assert.deepEqual(Object.keys(grant).sort(), ['slotId','verifier']); assert.equal(grant.verifier.length, 43); assert.notEqual(grant.verifier, bundle.preparedToken);
  const config = validatePreparedGrantConfig(configFor([grant]), { endpoint, capacity: 1 });
  assert.equal(verifyPreparedGrant(config, bundle.slotId, bundle.preparedToken), true);
  assert.equal(verifyPreparedGrant(config, bundle.slotId, token()), false);
  assert.equal(verifyPreparedGrant(config, id(), bundle.preparedToken), false);
});
test('prepared verifiers are scoped to the exact service origin and slot', () => {
  const slotId = id(), preparedToken = token();
  const first = createPreparedGrant({ endpoint, slotId, preparedToken });
  const other = createPreparedGrant({ endpoint: 'https://other.example.invalid', slotId, preparedToken });
  assert.notEqual(first.grant.verifier, other.grant.verifier);
  const changedSlot = createPreparedGrant({ endpoint, slotId: id(), preparedToken });
  assert.notEqual(first.grant.verifier, changedSlot.grant.verifier);
});
test('config rejects duplicate slots, over-reservation, raw secrets, unknown fields and endpoint mismatch', () => {
  const made = createPreparedGrant({ endpoint });
  for (const value of [configFor([made.grant, made.grant]), configFor([{ ...made.grant, preparedToken: made.bundle.preparedToken }]), { ...configFor([made.grant]), name: 'not allowed' }, { ...configFor([made.grant]), v: 2 }, configFor([made.grant], 'https://other.example.invalid')]) assert.throws(() => validatePreparedGrantConfig(value, { endpoint, capacity: 1 }));
  assert.throws(() => validatePreparedGrantConfig(configFor([made.grant]), { endpoint, capacity: 0 }));
});
test('endpoint validation is HTTPS-only and excludes URL credentials, paths and loopback', () => {
  assert.equal(normalizePreparedEndpoint(endpoint + '/'), endpoint);
  for (const privateOrigin of ['https://10.0.0.1','https://192.168.0.1','https://172.16.0.1','https://[fd00::1]','https://[fe80::1]']) assert.equal(normalizePreparedEndpoint(privateOrigin), privateOrigin);
  for (const value of ['http://prepared.example.invalid', endpoint + '/path', endpoint + '?secret=x', endpoint + '#x', 'https://name:secret@prepared.example.invalid', 'https://localhost', 'https://127.0.0.1', 'https://127.0.0.2', 'https://127.255.255.255', 'https://[::ffff:127.0.0.1]', 'https://[::ffff:7f00:2]', 'https://example.localhost', 'https://example.localhost.', 'https://localhost.', 'https://127.1', 'https://2130706433', 'https://[::127.0.0.2]', 'https://[::7f00:2]', 'https://[0:0:0:0:0:0:0:1]', 'https://[::]', 'https://0.0.0.0', 'https://[::1]', ' ' + endpoint]) assert.throws(() => normalizePreparedEndpoint(value));
});
test('prepared tickets bind generation, slot, creation key and reset epoch', () => {
  const options = ticketOptions(), ticket = issuePreparedAdmissionTicket(options);
  const verified = verifyPreparedAdmissionTicket(ticket, options);
  assert.equal(verified.slotId, options.slotId); assert.equal(verified.creationKey, options.creationKey); assert.equal(verified.slotEpoch, 0); assert.equal(verified.expiresAtMs, options.nowMs + 120_000);
  for (const change of [{ generation: id() }, { slotId: id() }, { creationKey: token() }, { slotEpoch: 1 }, { admissionKey: master() }, { nowMs: options.nowMs + 120000 }, { nowMs: options.nowMs - 1 }]) assert.equal(verifyPreparedAdmissionTicket(ticket, { ...options, ...change }), null);
});
test('tickets require a safe reset epoch and bounded short lifetime', () => {
  for (const change of [{ slotEpoch: undefined }, { slotEpoch: -1 }, { slotEpoch: Number.MAX_SAFE_INTEGER + 1 }, { ttlSeconds: 301 }, { ttlSeconds: 0 }, { nowMs: Number.MAX_SAFE_INTEGER }]) assert.throws(() => issuePreparedAdmissionTicket(ticketOptions(change)));
  assert.ok(issuePreparedAdmissionTicket(ticketOptions({ ttlSeconds: 300 })).length < 1024);
});
test('tampering, noncanonical encodings and unknown signed payload fields fail closed', () => {
  const options = ticketOptions(), ticket = issuePreparedAdmissionTicket(options), [payload, signature] = ticket.split('.');
  assert.equal(verifyPreparedAdmissionTicket(payload + '.' + signature.slice(0, -1) + (signature.endsWith('A') ? 'Q' : 'A'), options), null);
  assert.equal(verifyPreparedAdmissionTicket(ticket + '.extra', options), null);
  const value = JSON.parse(Buffer.from(payload, 'base64url').toString()); value.extra = 'forbidden';
  const altered = Buffer.from(JSON.stringify(value)).toString('base64url');
  const sig = createHmac('sha256', options.admissionKey).update('SafeBridge prepared admission ticket v1\0').update(altered).digest('base64url');
  assert.equal(verifyPreparedAdmissionTicket(altered + '.' + sig, options), null);
});
test('legacy and prepared tickets use separate authentication domains', () => {
  const options = ticketOptions(), prepared = issuePreparedAdmissionTicket(options), legacy = issueAdmissionTicket(options);
  assert.equal(verifyAdmissionTicket(prepared, options), null); assert.equal(verifyPreparedAdmissionTicket(legacy, options), null);
});
test('private grant reader accepts valid config and rejects public mode/symlinks/hard links', async t => {
  const dir = await temporary(t), file = join(dir, 'grants.json'), made = createPreparedGrant({ endpoint });
  await writeFile(file, JSON.stringify(configFor([made.grant])), { mode: 0o600 });
  assert.equal((await readPreparedGrantsFile(file, { endpoint, capacity: 1 })).grants.size, 1);
  await chmod(file, 0o644); await assert.rejects(readPreparedGrantsFile(file, { endpoint, capacity: 1 })); await chmod(file, 0o600);
  const symbolic = join(dir, 'symbolic'); await symlink(file, symbolic); await assert.rejects(readPreparedGrantsFile(symbolic, { endpoint, capacity: 1 }));
  const hard = join(dir, 'hard'); await link(file, hard); await assert.rejects(readPreparedGrantsFile(file, { endpoint, capacity: 1 }));
});
test('grant file reading is byte-bounded and rejects invalid UTF-8', async t => {
  const dir = await temporary(t), file = join(dir, 'grants.json');
  await writeFile(file, Buffer.from([0xc3,0x28]), { mode: 0o600 }); await assert.rejects(readPreparedGrantsFile(file, { endpoint, capacity: 1 }));
  await writeFile(file, Buffer.alloc(MAX_PREPARED_GRANTS_BYTES + 1)); await assert.rejects(readPreparedGrantsFile(file, { endpoint, capacity: 1 }));
});
test('offline provisioning creates private files, preserves old grants and enforces reserved capacity', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'first.json');
  await provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 2 });
  const first = JSON.parse(await readFile(bundleFile, 'utf8')), originalGrant = JSON.parse(await readFile(grantsFile, 'utf8')).grants[0];
  assert.equal((await stat(bundleFile)).mode & 0o777, 0o600); assert.equal((await stat(grantsFile)).mode & 0o777, 0o600);
  assert.equal((await readFile(grantsFile, 'utf8')).includes(first.preparedToken), false);
  const secondFile = join(dir, 'second.json'); await provisionPreparedSlot({ endpoint, grantsFile, bundleFile: secondFile, capacity: 2 });
  const second = JSON.parse(await readFile(secondFile, 'utf8')), config = await readPreparedGrantsFile(grantsFile, { endpoint, capacity: 2 });
  assert.equal(config.grants.size, 2); assert.equal(verifyPreparedGrant(config, first.slotId, first.preparedToken), true); assert.equal(verifyPreparedGrant(config, second.slotId, second.preparedToken), true);
  assert.deepEqual(JSON.parse(await readFile(grantsFile, 'utf8')).grants[0], originalGrant);
  await assert.rejects(provisionPreparedSlot({ endpoint, grantsFile, bundleFile: join(dir, 'third.json'), capacity: 2 }));
});
test('provisioning never overwrites an existing bundle or changes config on that failure', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'bundle.json');
  await provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 2 }); const before = await readFile(grantsFile, 'utf8'), bundleBefore = await readFile(bundleFile, 'utf8');
  await assert.rejects(provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 2 }));
  assert.equal(await readFile(grantsFile, 'utf8'), before); assert.equal(await readFile(bundleFile, 'utf8'), bundleBefore);
});
test('a provisioning lock prevents lost config updates and is not automatically removed', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'bundle.json'), lock = grantsFile + '.lock';
  await writeFile(lock, 'synthetic lock', { mode: 0o600 });
  await assert.rejects(provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 1 }));
  assert.equal(await readFile(lock, 'utf8'), 'synthetic lock');
});
test('provisioning rejects nonprivate directories and endpoint mismatch', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'bundle.json');
  await chmod(dir, 0o755); await assert.rejects(provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 1 })); await chmod(dir, 0o700);
  await provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 2 });
  await assert.rejects(provisionPreparedSlot({ endpoint: 'https://other.example.invalid', grantsFile, bundleFile: join(dir, 'other.json'), capacity: 2 }));
});
test('actual CLI prints no capability and creates only synthetic private files', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'bundle.json');
  const { stdout, stderr } = await promisify(execFile)(process.execPath, [fileURLToPath(new URL('../provision-prepared.mjs', import.meta.url)), '--endpoint', endpoint, '--grants-file', grantsFile, '--bundle-file', bundleFile, '--capacity', '1']);
  const bundle = JSON.parse(await readFile(bundleFile, 'utf8'));
  assert.equal(stderr, ''); assert.ok(stdout.includes('controlled server restart')); assert.equal(stdout.includes(bundle.preparedToken), false); assert.equal(stdout.includes(bundle.slotId), false);
});


test('v2 verifier-only config preserves disabled policy; v1 remains enabled-only compatibility', () => {
  const made = createPreparedGrant({ endpoint });
  const config = validatePreparedGrantConfig({ v: 2, endpoint, grants: [{ ...made.grant, disabled: true }] }, { endpoint, capacity: 1 });
  assert.equal(config.grants.size, 1); assert.equal(config.disabledSlots.has(made.grant.slotId), true);
  assert.equal(verifyPreparedGrant(config, made.bundle.slotId, made.bundle.preparedToken), false);
  const old = validatePreparedGrantConfig(configFor([made.grant]), { endpoint, capacity: 1 });
  assert.equal(old.disabledSlots.size, 0); assert.equal(verifyPreparedGrant(old, made.bundle.slotId, made.bundle.preparedToken), true);
  assert.throws(() => validatePreparedGrantConfig({ v: 2, endpoint, grants: [{ ...made.grant, disabled: 'yes' }] }, { endpoint, capacity: 1 }));
});
test('provisioning appends v2 grants without clearing a pre-existing disabled policy', async t => {
  const dir = await temporary(t), grantsFile = join(dir, 'grants.json'), bundleFile = join(dir, 'new.json'), old = createPreparedGrant({ endpoint });
  await writeFile(grantsFile, JSON.stringify({ v: 2, endpoint, grants: [{ ...old.grant, disabled: true }] }), { mode: 0o600 });
  await provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity: 2 });
  const value = JSON.parse(await readFile(grantsFile, 'utf8'));
  assert.equal(value.v, 2); assert.equal(value.grants[0].disabled, true); assert.equal(value.grants[1].disabled, false);
});
test('rotation publishes a private new same-slot bundle and returns no raw capability', async t => {
  const dir = await temporary(t), bundleFile = join(dir, 'rotated.json'), old = createPreparedGrant({ endpoint });
  const rotated = await prepareRotatedGrantBundle({ endpoint, slotId: old.bundle.slotId, bundleFile });
  const bundle = JSON.parse(await readFile(bundleFile, 'utf8'));
  assert.deepEqual(Object.keys(bundle).sort(), ['endpoint','preparedToken','slotId','v']); assert.equal(bundle.slotId, old.bundle.slotId); assert.equal(bundle.endpoint, endpoint);
  assert.notEqual(bundle.preparedToken, old.bundle.preparedToken); assert.notEqual(rotated.verifier, old.grant.verifier);
  assert.deepEqual(Object.keys(rotated).sort(), ['slotId','verifier']); assert.equal((await stat(bundleFile)).mode & 0o777, 0o600);
  assert.deepEqual(await readPreparedBundleForRotation(bundleFile, { endpoint, slotId: old.bundle.slotId }), rotated);
});
test('rotation never overwrites an existing bundle or invents a missing slot', async t => {
  const dir = await temporary(t), bundleFile = join(dir, 'existing.json'), old = createPreparedGrant({ endpoint });
  await writeFile(bundleFile, JSON.stringify(old.bundle), { mode: 0o600 }); const before = await readFile(bundleFile, 'utf8');
  await assert.rejects(prepareRotatedGrantBundle({ endpoint, slotId: old.bundle.slotId, bundleFile })); assert.equal(await readFile(bundleFile, 'utf8'), before);
  await assert.rejects(prepareRotatedGrantBundle({ endpoint, bundleFile: join(dir, 'missing-slot.json') }));
});
test('rotation recovery reader rejects endpoint/slot/field mismatch, permissions and linked files', async t => {
  const dir = await temporary(t), file = join(dir, 'bundle.json'), made = createPreparedGrant({ endpoint });
  await writeFile(file, JSON.stringify(made.bundle), { mode: 0o600 });
  await assert.rejects(readPreparedBundleForRotation(file, { endpoint: 'https://other.example.invalid', slotId: made.bundle.slotId }));
  await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId: id() }));
  await writeFile(file, JSON.stringify({ ...made.bundle, extra: true })); await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId: made.bundle.slotId }));
  await writeFile(file, JSON.stringify(made.bundle)); await chmod(file, 0o644); await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId: made.bundle.slotId })); await chmod(file, 0o600);
  const symbolic = join(dir, 'symlink.json'); await symlink(file, symbolic); await assert.rejects(readPreparedBundleForRotation(symbolic, { endpoint, slotId: made.bundle.slotId }));
  const hard = join(dir, 'hard.json'); await link(file, hard); await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId: made.bundle.slotId }));
});
test('rotation recovery reader is byte-bounded and strict UTF-8', async t => {
  const dir = await temporary(t), file = join(dir, 'bundle.json'), slotId = id();
  await writeFile(file, Buffer.from([0xc3,0x28]), { mode: 0o600 }); await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId }));
  await writeFile(file, Buffer.alloc(4097)); await assert.rejects(readPreparedBundleForRotation(file, { endpoint, slotId }));
});
