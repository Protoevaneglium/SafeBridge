import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createSafeBridgeServer } from '../backend/server.mjs';
import { readBoundedJson } from './view-network.js';
import { initialState, validatePacket, received, failed, advance, view } from './view-state.js';

test('actual loopback HTTP: injected >90s outage retains last-known; same link reconnects; Stop clears', async () => {
  let serverTime = 10_000_000, monotonic = 1000, cutRecipientConnection = false;
  const fixture = createSafeBridgeServer({ development: true, allowAnonymousCreation: true, clock: () => serverTime });
  // A deliberate socket cut exercises a real fetch failure without contacting a third party.
  fixture.server.prependListener('request', (request) => { if (cutRecipientConnection && request.method === 'GET' && request.url.endsWith('/latest')) request.socket.destroy(); });
  await new Promise((resolve, reject) => { fixture.server.once('error', reject); fixture.server.listen(0, '127.0.0.1', resolve); });
  const base = `http://127.0.0.1:${fixture.server.address().port}`;
  const writeToken = randomBytes(32).toString('base64url'), readToken = randomBytes(32).toString('base64url'), revokeToken = randomBytes(32).toString('base64url');
  let generation;
  async function json(path, { method = 'GET', token, body, headers = {} } = {}) {
    const response = await fetch(base + path, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(generation ? { 'X-SafeBridge-Generation': generation } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined, cache: 'no-store', signal: AbortSignal.timeout(2000) });
    assert.ok(response.status === 200 || (method === 'POST' && response.status === 201), 'synthetic API request succeeded');
    return readBoundedJson(response);
  }
  try {
    const config = await json('/v1/config'); generation = config.generation; assert.equal(config.protocolVersion, 2);
    const created = await json('/v1/shares', { method: 'POST', token: writeToken, body: { generation, createPermit: config.createPermit, creationKey: randomBytes(16).toString('base64url'), readToken, revokeToken, durationSeconds: 0, requestedExpiresAtMs: 0, admissionTicket: null } });
    const expected = { id: created.id, generation }, path = `/v1/shares/${created.id}`;
    const point = (sequence, latitude) => ({ sequence, latitude, longitude: 0, accuracyM: 25, sampleTimeMs: serverTime, sampleAgeMs: 0, approximate: true, mock: true });
    await json(path + '/latest', { method: 'PUT', token: writeToken, body: point(1, 0) });
    let raw = await json(path + '/latest', { token: readToken });
    assert.equal(Object.hasOwn(raw, 'managementReceipt'), false);
    let state = received(initialState(), validatePacket(raw, expected), monotonic);
    assert.equal(view(state, monotonic).phase, 'recent'); assert.equal(view(state, monotonic).untilStopped, true);

    cutRecipientConnection = true;
    await assert.rejects(json(path + '/latest', { token: readToken }));
    state = failed(state);
    assert.equal(view(state, monotonic).phase, 'last_known');
    serverTime += 90_001; monotonic += 90_001; state = advance(state, monotonic);
    assert.equal(view(state, monotonic).phase, 'last_known'); assert.ok(view(state, monotonic).ageMs > 90_000);
    assert.equal(view(state, monotonic).point.latitude, 0); assert.equal(view(state, monotonic).point.accuracyM, 25);
    assert.equal(view(state, monotonic).point.sampleTimeMs, 10_000_000); assert.equal(view(state, monotonic).remainingMs, null);

    cutRecipientConnection = false;
    raw = await json(path + '/latest', { token: readToken });
    assert.equal(raw.state, 'paused'); assert.equal(raw.point, null); assert.equal(raw.expiresAtMs, 0);
    state = received(state, validatePacket(raw, expected), monotonic);
    assert.equal(view(state, monotonic).phase, 'last_known'); assert.ok(view(state, monotonic).ageMs > 90_000);
    const newViewer = received(initialState(), validatePacket(raw, expected), monotonic);
    assert.equal(view(newViewer, monotonic).point, null);

    await json(path + '/latest', { method: 'PUT', token: writeToken, body: point(2, 1) });
    raw = await json(path + '/latest', { token: readToken });
    state = received(state, validatePacket(raw, expected), monotonic);
    assert.equal(raw.id, created.id); assert.equal(view(state, monotonic).phase, 'recent'); assert.equal(view(state, monotonic).point.latitude, 1);
    await json(path, { method: 'DELETE', token: revokeToken, headers: { 'X-SafeBridge-Management-Receipt': created.managementReceipt } });
    raw = await json(path + '/latest', { token: readToken });
    state = received(state, validatePacket(raw, expected), monotonic);
    assert.equal(view(state, monotonic).phase, 'stopped'); assert.equal(state.point, null); assert.equal(state.pointClock, null); assert.equal(state.clock, null);
    assert.equal(JSON.stringify(state).includes(readToken), false);
  } finally { await fixture.close(); }
});
