import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { constants } from 'node:fs';
import { open, stat, realpath } from 'node:fs/promises';
import { dirname, resolve, isAbsolute } from 'node:path';

const ID = /^[A-Za-z0-9_-]{22}$/;
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const GRANT_DOMAIN = 'SafeBridge prepared grant v1\0';
const TICKET_DOMAIN = 'SafeBridge prepared admission ticket v1\0';
export const MAX_PREPARED_GRANTS_BYTES = 1_048_576;
const ownKeys = (value, keys) => value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).sort().join(',') === keys.slice().sort().join(',');
const canonical = (value, bytes) => typeof value === 'string' && (bytes === 16 ? ID : TOKEN).test(value) && Buffer.from(value, 'base64url').length === bytes && Buffer.from(value, 'base64url').toString('base64url') === value;
const creationKeyValid = value => typeof value === 'string' && /^[A-Za-z0-9_-]{22,86}$/.test(value) && Buffer.from(value, 'base64url').length >= 16 && Buffer.from(value, 'base64url').length <= 64 && Buffer.from(value, 'base64url').toString('base64url') === value;
const timeValid = value => Number.isSafeInteger(value) && value >= 0;
const keyValid = key => Buffer.isBuffer(key) && key.length === 32;

function prohibitedHost(value) {
  const host = value.toLowerCase().replace(/\.+$/, '');
  if (host === 'localhost' || host.endsWith('.localhost') || /^127\./.test(host) || host === '0.0.0.0') return true;
  if (!host.startsWith('[')) return false;
  const address = host.slice(1, -1), halves = address.split('::');
  const left = halves[0] ? halves[0].split(':').map(part => parseInt(part, 16)) : [];
  const right = halves.length === 2 && halves[1] ? halves[1].split(':').map(part => parseInt(part, 16)) : [];
  const groups = halves.length === 2 ? [...left, ...Array(8 - left.length - right.length).fill(0), ...right] : left;
  const zero = count => groups.slice(0, count).every(group => group === 0);
  return groups.length === 8 && (zero(8) || (zero(7) && groups[7] === 1) || (zero(5) && groups[5] === 0xffff) || (zero(6) && (groups[6] >>> 8) === 127));
}

export function normalizePreparedEndpoint(value) {
  try {
    if (typeof value !== 'string' || value.length > 2048 || value !== value.trim() || /[\u0000-\u0020\u007f\\]/.test(value)) throw new Error();
    const url = new URL(value);
    const loopback = prohibitedHost(url.hostname);
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.search || url.hash || (url.pathname !== '' && url.pathname !== '/') || loopback) throw new Error();
    return url.origin;
  } catch { throw new Error('A configured HTTPS origin without credentials, path, query, fragment or loopback is required'); }
}
function grantHash(endpoint, slotId, preparedToken) { return createHash('sha256').update(GRANT_DOMAIN).update(endpoint).update('\0').update(slotId).update('\0').update(preparedToken).digest(); }

/** Generates one installation capability. It does not contact a service or use a master key. */
export function createPreparedGrant({ endpoint, slotId = randomBytes(16).toString('base64url'), preparedToken = randomBytes(32).toString('base64url') }) {
  endpoint = normalizePreparedEndpoint(endpoint);
  if (!canonical(slotId, 16) || !canonical(preparedToken, 32)) throw new Error('Invalid prepared slot or token');
  return { bundle: { v: 1, endpoint, slotId, preparedToken }, grant: { slotId, verifier: grantHash(endpoint, slotId, preparedToken).toString('base64url') } };
}

/** Grants reserve capacity; server-owned epochs/session bindings are never accepted here. */
export function validatePreparedGrantConfig(value, { endpoint, capacity }) {
  const expected = normalizePreparedEndpoint(endpoint);
  if (!Number.isSafeInteger(capacity) || capacity < 1 || !ownKeys(value, ['v','endpoint','grants']) || ![1,2].includes(value.v) || value.endpoint !== expected || !Array.isArray(value.grants) || value.grants.length > capacity) throw new Error('Invalid prepared grant configuration or reserved capacity');
  const grants = new Map(), disabledSlots = new Set();
  for (const grant of value.grants) {
    if (!ownKeys(grant, value.v === 2 ? ['slotId','verifier','disabled'] : ['slotId','verifier']) || (value.v === 2 && typeof grant.disabled !== 'boolean') || !canonical(grant.slotId, 16) || !canonical(grant.verifier, 32) || grants.has(grant.slotId)) throw new Error('Invalid or duplicate prepared grant');
    grants.set(grant.slotId, Buffer.from(grant.verifier, 'base64url'));
    if (grant.disabled === true) disabledSlots.add(grant.slotId);
  }
  return { endpoint: expected, grants, disabledSlots };
}
export function verifyPreparedGrant(config, slotId, preparedToken) {
  if (!config || !(config.grants instanceof Map) || config.disabledSlots?.has(slotId) || !canonical(slotId, 16) || !canonical(preparedToken, 32)) return false;
  let candidate;
  try { candidate = grantHash(config.endpoint, slotId, preparedToken); } catch { return false; }
  const stored = config.grants.get(slotId), expected = Buffer.isBuffer(stored) && stored.length === 32 ? stored : Buffer.alloc(32);
  const equal = timingSafeEqual(candidate, expected); candidate.fill(0);
  return Boolean(stored) && equal;
}

/** POSIX private regular file, no symlink following; bounded bytes and strict UTF-8. */
export async function readPreparedGrantsFile(path, options) {
  if (typeof constants.O_NOFOLLOW !== 'number') throw new Error('Prepared grants require no-follow file support');
  const handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const info = await handle.stat();
    if (!info.isFile() || info.nlink !== 1 || info.size > MAX_PREPARED_GRANTS_BYTES || (info.mode & 0o177) !== 0) throw new Error('Prepared grants must be a private regular file (0600 or stricter), without hard links');
    const bytes = Buffer.alloc(MAX_PREPARED_GRANTS_BYTES + 1); let length = 0;
    for (;;) { const { bytesRead } = await handle.read(bytes, length, bytes.length - length, null); length += bytesRead; if (length > MAX_PREPARED_GRANTS_BYTES) throw new Error('Prepared grant file is too large'); if (!bytesRead) break; }
    let value; try { value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length))); } catch { throw new Error('Prepared grant file must contain valid UTF-8 JSON'); }
    return validatePreparedGrantConfig(value, options);
  } finally { await handle.close(); }
}

/** Read an already published private rotation bundle; returns only its verifier, never the token. */
export async function readPreparedBundleForRotation(path, { endpoint, slotId }) {
  endpoint = normalizePreparedEndpoint(endpoint);
  if (!canonical(slotId, 16) || typeof path !== 'string' || !isAbsolute(path) || typeof constants.O_NOFOLLOW !== 'number' || typeof constants.O_DIRECTORY !== 'number') throw new Error('Invalid private rotation bundle path or slot');
  const parent = dirname(path), directory = await stat(parent);
  if (!directory.isDirectory() || (directory.mode & 0o077) !== 0 || (process.getuid && directory.uid !== process.getuid()) || await realpath(parent) !== resolve(parent)) throw new Error('Rotation bundle requires a private owned directory without symbolic links');
  const handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const info = await handle.stat();
    if (!info.isFile() || info.nlink !== 1 || info.size > 4096 || (info.mode & 0o177) !== 0) throw new Error('Rotation bundle must be a private bounded regular file without links');
    const bytes = Buffer.alloc(4097); let length = 0;
    for (;;) { const { bytesRead } = await handle.read(bytes, length, bytes.length - length, null); length += bytesRead; if (length > 4096) throw new Error('Rotation bundle is too large'); if (!bytesRead) break; }
    let value; try { value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length))); } catch { throw new Error('Rotation bundle must contain valid UTF-8 JSON'); } finally { bytes.fill(0); }
    if (!ownKeys(value, ['v','endpoint','slotId','preparedToken']) || value.v !== 1 || value.endpoint !== endpoint || value.slotId !== slotId || !canonical(value.preparedToken, 32)) throw new Error('Rotation bundle endpoint, slot or fields do not match');
    const verifier = grantHash(endpoint, slotId, value.preparedToken).toString('base64url');
    await handle.sync();
    const parentHandle = await open(parent, constants.O_RDONLY | constants.O_DIRECTORY | constants.O_NOFOLLOW); try { await parentHandle.sync(); } finally { await parentHandle.close(); }
    return { slotId, verifier };
  } finally { await handle.close(); }
}

function ticketSignature(key, payload) { return createHmac('sha256', key).update(TICKET_DOMAIN).update(payload).digest(); }
export function issuePreparedAdmissionTicket({ admissionKey, generation, slotId, creationKey, slotEpoch, nowMs, ttlSeconds = 120, nonce = randomBytes(16).toString('base64url') }) {
  if (!keyValid(admissionKey) || !canonical(generation, 16) || !canonical(slotId, 16) || !creationKeyValid(creationKey) || !timeValid(slotEpoch) || !timeValid(nowMs) || !canonical(nonce, 16) || !Number.isInteger(ttlSeconds) || ttlSeconds < 1 || ttlSeconds > 300 || !timeValid(nowMs + ttlSeconds * 1000)) throw new Error('Invalid prepared ticket parameters');
  const payload = Buffer.from(JSON.stringify({ v: 1, kind: 'prepared', generation, slotId, creationKey, slotEpoch, nonce, issuedAtMs: nowMs, expiresAtMs: nowMs + ttlSeconds * 1000 })).toString('base64url');
  return payload + '.' + ticketSignature(admissionKey, payload).toString('base64url');
}
export function verifyPreparedAdmissionTicket(ticket, { admissionKey, generation, nowMs, slotId, creationKey, slotEpoch }) {
  if (!keyValid(admissionKey) || !canonical(generation, 16) || !timeValid(nowMs) || typeof ticket !== 'string' || ticket.length > 1024) return null;
  const [payload, sig, extra] = ticket.split('.');
  if (extra !== undefined || !payload || !canonical(sig, 32) || Buffer.from(payload, 'base64url').toString('base64url') !== payload) return null;
  if (!timingSafeEqual(Buffer.from(sig, 'base64url'), ticketSignature(admissionKey, payload))) return null;
  try {
    const value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.from(payload, 'base64url')));
    if (!ownKeys(value, ['v','kind','generation','slotId','creationKey','slotEpoch','nonce','issuedAtMs','expiresAtMs']) || value.v !== 1 || value.kind !== 'prepared' || value.generation !== generation || !canonical(value.slotId, 16) || !creationKeyValid(value.creationKey) || !timeValid(value.slotEpoch) || !canonical(value.nonce, 16) || !timeValid(value.issuedAtMs) || !timeValid(value.expiresAtMs) || value.issuedAtMs > nowMs || value.expiresAtMs <= nowMs || value.expiresAtMs - value.issuedAtMs < 1000 || value.expiresAtMs - value.issuedAtMs > 300000 || (slotId !== undefined && value.slotId !== slotId) || (creationKey !== undefined && value.creationKey !== creationKey) || (slotEpoch !== undefined && value.slotEpoch !== slotEpoch)) return null;
    return { slotId: value.slotId, creationKey: value.creationKey, slotEpoch: value.slotEpoch, nonce: value.nonce, expiresAtMs: value.expiresAtMs };
  } catch { return null; }
}
