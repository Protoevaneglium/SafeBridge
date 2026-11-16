import { constants } from 'node:fs';
import { open, stat, realpath, link, rename, unlink } from 'node:fs/promises';
import { dirname, resolve, isAbsolute, basename } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomBytes } from 'node:crypto';
import { createPreparedGrant, normalizePreparedEndpoint, readPreparedGrantsFile, validatePreparedGrantConfig, MAX_PREPARED_GRANTS_BYTES } from './prepared-admission.mjs';

async function privateParent(path) {
  if (typeof path !== 'string' || !isAbsolute(path)) throw new Error('Provisioning paths must be absolute');
  const parent = dirname(path), info = await stat(parent);
  if (!info.isDirectory() || (info.mode & 0o077) !== 0 || (process.getuid && info.uid !== process.getuid()) || await realpath(parent) !== resolve(parent)) throw new Error('Provisioning requires an existing private owned directory without symbolic links');
  return parent;
}
async function syncDirectory(path) { const handle = await open(path, constants.O_RDONLY | constants.O_DIRECTORY); try { await handle.sync(); } finally { await handle.close(); } }
async function stage(path, value) {
  const handle = await open(path, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
  try { await handle.chmod(0o600); await handle.writeFile(value, 'utf8'); await handle.sync(); } finally { await handle.close(); }
}

/** Offline operator-only provisioning. Never called by the service or Android app. */
export async function provisionPreparedSlot({ endpoint, grantsFile, bundleFile, capacity }) {
  endpoint = normalizePreparedEndpoint(endpoint);
  if (!Number.isSafeInteger(capacity) || capacity < 1) throw new Error('A positive reserved server capacity is required');
  if (typeof constants.O_NOFOLLOW !== 'number' || typeof constants.O_DIRECTORY !== 'number') throw new Error('Private POSIX file controls are required');
  const grantsParent = await privateParent(grantsFile), bundleParent = await privateParent(bundleFile);
  if (resolve(grantsFile) === resolve(bundleFile) || basename(grantsFile).endsWith('.lock')) throw new Error('Grant and sender-bundle destinations must be distinct');
  const lockPath = grantsFile + '.lock';
  const lock = await open(lockPath, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
  let bundleTemp, grantsTemp, bundleCommitted = false, grantsCommitted = false;
  try {
    let current;
    try { current = await readPreparedGrantsFile(grantsFile, { endpoint, capacity }); }
    catch (error) { if (error.code !== 'ENOENT') throw error; current = validatePreparedGrantConfig({ v: 1, endpoint, grants: [] }, { endpoint, capacity }); }
    if (current.grants.size >= capacity) throw new Error('Prepared grants already reserve all configured capacity');
    let generated; do { generated = createPreparedGrant({ endpoint }); } while (current.grants.has(generated.grant.slotId));
    const config = { v: 2, endpoint, grants: [...current.grants].map(([slotId, verifier]) => ({ slotId, verifier: verifier.toString('base64url'), disabled: current.disabledSlots.has(slotId) })).concat({ ...generated.grant, disabled: false }) };
    validatePreparedGrantConfig(config, { endpoint, capacity });
    const configText = JSON.stringify(config, null, 2) + '\n', bundleText = JSON.stringify(generated.bundle, null, 2) + '\n';
    if (Buffer.byteLength(configText) > MAX_PREPARED_GRANTS_BYTES || Buffer.byteLength(bundleText) > 4096) throw new Error('Provisioning output exceeds its bounded size');
    bundleTemp = bundleFile + '.' + randomBytes(12).toString('hex') + '.tmp'; grantsTemp = grantsFile + '.' + randomBytes(12).toString('hex') + '.tmp';
    await stage(bundleTemp, bundleText); await stage(grantsTemp, configText);
    // Hard-link publication is no-clobber. The temporary second link is then removed.
    await link(bundleTemp, bundleFile); bundleCommitted = true; await unlink(bundleTemp); bundleTemp = null;
    await syncDirectory(bundleParent);
    // Grant replacement is atomic within its private directory and protected by the lock.
    await rename(grantsTemp, grantsFile); grantsTemp = null; grantsCommitted = true;
    await syncDirectory(grantsParent);
    return { slotId: generated.grant.slotId, grantCount: config.grants.length, restartRequired: true };
  } finally {
    if (bundleTemp) await unlink(bundleTemp).catch(() => {});
    if (grantsTemp) await unlink(grantsTemp).catch(() => {});
    if (bundleCommitted && !grantsCommitted) await unlink(bundleFile).catch(() => {});
    await lock.close(); await unlink(lockPath);
  }
}

/** Publish a new private same-slot bundle before the caller commits any database rotation. */
export async function prepareRotatedGrantBundle({ endpoint, slotId, bundleFile }) {
  endpoint = normalizePreparedEndpoint(endpoint);
  if (typeof slotId !== 'string') throw new Error('A known prepared slot is required for rotation');
  const parent = await privateParent(bundleFile);
  const generated = createPreparedGrant({ endpoint, slotId });
  const content = JSON.stringify(generated.bundle, null, 2) + '\n';
  if (Buffer.byteLength(content) > 4096) throw new Error('Rotation bundle exceeds its bounded size');
  let temporary = bundleFile + '.' + randomBytes(12).toString('hex') + '.tmp';
  try {
    await stage(temporary, content);
    await link(temporary, bundleFile); // Atomic no-clobber publication, never replacement.
    await unlink(temporary); temporary = null;
    await syncDirectory(parent);
    return generated.grant;
  } finally {
    if (temporary) await unlink(temporary).catch(() => {});
    // A published bundle remains on any later failure. Its presence is not activation.
  }
}

async function main(args) {
  const allowed = new Set(['--endpoint','--grants-file','--bundle-file','--capacity']);
  if (args.length !== 8 || args.some((value, index) => index % 2 === 0 && !allowed.has(value)) || new Set(args.filter((_, index) => index % 2 === 0)).size !== 4) throw new Error('Usage: node provision-prepared.mjs --endpoint HTTPS_ORIGIN --grants-file ABSOLUTE_PRIVATE_FILE --bundle-file ABSOLUTE_NEW_PRIVATE_FILE --capacity RESERVED_CAPACITY');
  const values = Object.fromEntries(Array.from({ length: 4 }, (_, index) => [args[index * 2], args[index * 2 + 1]]));
  await provisionPreparedSlot({ endpoint: values['--endpoint'], grantsFile: values['--grants-file'], bundleFile: values['--bundle-file'], capacity: Number(values['--capacity']) });
  process.stdout.write('Prepared files written privately. Activation requires the offline operator state workflow and a controlled server restart; distribute the sender bundle privately.\n');
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.umask(0o077);
  try { await main(process.argv.slice(2)); }
  catch { process.stderr.write('Prepared provisioning failed. Check private paths, capacity, existing destinations and configuration; no secret is printed. Inspect local files before retrying after an interrupted write.\n'); process.exitCode = 1; }
}
