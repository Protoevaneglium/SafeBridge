import test from 'node:test';
import assert from 'node:assert/strict';
import { wordBoundary } from './view-deck.js';
import { initialState, parseLink, validatePacket, received, failed, paused, reconnecting, advance, end, view } from './view-state.js';
const expected = { id: 'a'.repeat(22), generation: 'b'.repeat(22) };
const packet = (extra = {}) => ({ ...expected, state: 'active', serverTimeMs: 1_000_000, expiresAtMs: 0, leaseUntilMs: 1_090_000, pointAgeMs: 1000, freshness: 'recent', point: { sequence: 1, latitude: 37.5, longitude: -122.25, accuracyM: 15, sampleTimeMs: 999000, sampleAgeMs: 0, receivedAtMs: 999000, approximate: false, mock: false }, ...extra });
const stateAt = () => received(initialState(), validatePacket(packet(), expected), 500);

test('opaque fragment parsing and duplicate-field rejection are unchanged', () => {
  const link = `#s=${expected.id}&g=${expected.generation}&r=${'c'.repeat(43)}`;
  assert.deepEqual(parseLink(link), { ...expected, readToken: 'c'.repeat(43) });
  assert.equal(parseLink(`${link}&r=${'c'.repeat(43)}`), null);
  assert.equal(parseLink(link.replace('g=' + expected.generation, 'g=1')), null);
});
test('wrong scope, inconsistent V2 states and invalid coordinates are rejected', () => {
  assert.throws(() => validatePacket(packet({ generation: 'wrong' }), expected));
  assert.throws(() => validatePacket(packet({ point: { ...packet().point, latitude: 91 } }), expected));
  assert.throws(() => validatePacket(packet({ pointAgeMs: -1 }), expected));
  assert.throws(() => validatePacket(packet({ point: { ...packet().point, accuracyM: NaN } }), expected));
  assert.throws(() => validatePacket(packet({ state: 'paused' }), expected));
  assert.throws(() => validatePacket(packet({ state: 'interrupted' }), expected));
});
test('age grows from server-provided age using monotonic elapsed time', () => {
  const state = stateAt();
  assert.equal(view(state, 1500).ageMs, 2000);
  assert.equal(view(state, 29500).phase, 'recent');
  assert.equal(view(state, 29501).phase, 'stale');
});
test('transient failure retains a clearly unverified last-known point', () => {
  const original = stateAt(), state = failed(original);
  assert.equal(state.point, original.point); assert.equal(state.pointClock, original.pointClock);
  assert.equal(view(state, 501).phase, 'last_known'); assert.equal(state.terminal, null);
  assert.equal(view(state, 1500).ageMs, 2000);
});
test('an outage longer than 90 seconds keeps the last received point with growing age', () => {
  let state = failed(stateAt()); state = advance(state, 130500);
  const current = view(state, 130500);
  assert.equal(current.phase, 'last_known'); assert.equal(current.point.latitude, 37.5);
  assert.equal(current.ageMs, 131000); assert.equal(current.untilStopped, true); assert.equal(current.remainingMs, null);
});
test('backend paused/null does not reset age or erase tab-only last-known data', () => {
  const state = received(failed(stateAt()), validatePacket(packet({ state: 'paused', serverTimeMs: 1_130_000, leaseUntilMs: 1_090_000, point: null, pointAgeMs: null, freshness: 'paused' }), expected), 130500);
  assert.equal(view(state, 130500).phase, 'last_known'); assert.equal(view(state, 130500).ageMs, 131000);
  assert.equal(state.point.sampleTimeMs, 999000); assert.equal(state.point.accuracyM, 15);
});
test('a new viewer receiving paused has no point to invent', () => {
  const state = received(initialState(), validatePacket(packet({ state: 'paused', point: null, pointAgeMs: null, freshness: 'paused' }), expected), 500);
  assert.equal(view(state, 600).phase, 'paused'); assert.equal(state.point, null); assert.equal(state.pointClock, null);
});
test('same-id fresh reconnect resumes verified updates without changing credentials', () => {
  const lost = reconnecting(failed(stateAt()));
  assert.equal(view(lost, 130500).phase, 'last_known');
  const updated = received(lost, validatePacket(packet({ serverTimeMs: 1_130_000, leaseUntilMs: 1_220_000, pointAgeMs: 200, point: { ...packet().point, latitude: 38, sampleTimeMs: 1_129_800 } }), expected), 130500);
  assert.equal(view(updated, 130500).phase, 'recent'); assert.equal(view(updated, 130500).point.latitude, 38); assert.equal(view(updated, 130500).ageMs, 200);
});
test('expired point lease and missed revalidation never mean Stop or chosen expiry', () => {
  const state = advance(stateAt(), 90500);
  assert.equal(view(state, 90500).phase, 'last_known'); assert.notEqual(state.point, null); assert.equal(state.terminal, null);
  assert.equal(view(state, 100_000_000).untilStopped, true); assert.equal(view(state, 100_000_000).remainingMs, null);
});
test('confirmed Stop and expiry discard point and all timing metadata', () => {
  for (const terminal of ['stopped','expired']) {
    const state = received(stateAt(), validatePacket(packet({ state: terminal }), expected), 700);
    assert.equal(state.mode, 'terminal'); assert.equal(state.point, null); assert.equal(state.pointClock, null); assert.equal(state.clock, null); assert.equal(view(state, 700).phase, terminal);
  }
});
test('positive immutable chosen deadline still clears locally during outage', () => {
  const state = failed(received(initialState(), validatePacket(packet({ expiresAtMs: 1_005_000 }), expected), 500));
  const result = advance(state, 5500);
  assert.equal(result.terminal, 'expired'); assert.equal(result.point, null); assert.equal(result.pointClock, null); assert.equal(result.clock, null);
});
test('a later server response cannot extend an already established positive deadline', () => {
  const original = received(initialState(), validatePacket(packet({ expiresAtMs: 1_005_000 }), expected), 500);
  const rechecked = received(original, validatePacket(packet({ serverTimeMs: 900000, expiresAtMs: 1_005_000 }), expected), 1500);
  assert.equal(advance(rechecked, 5500).terminal, 'expired');
});
test('hidden view hides DOM coordinates while retaining tab memory, then labels reconnect unverified', () => {
  const original = stateAt(), hidden = paused(original);
  assert.equal(hidden.point, original.point); assert.equal(hidden.pointClock, original.pointClock); assert.equal(view(hidden, 600).point, null);
  const visible = reconnecting(hidden);
  assert.equal(view(visible, 600).phase, 'last_known'); assert.equal(view(visible, 600).point, original.point);
});
test('waiting has no placeholder coordinate and is nonterminal', () => {
  const state = received(initialState(), validatePacket(packet({ state: 'waiting_for_fix', point: null, pointAgeMs: null, freshness: 'waiting' }), expected), 500);
  assert.equal(view(state, 600).phase, 'waiting'); assert.equal(state.point, null); assert.equal(state.terminal, null);
});
test('a backwards clock never makes retained data appear recent', () => {
  const state = advance(stateAt(), 499);
  assert.equal(view(state, 499).phase, 'last_known'); assert.equal(view(state, 499).ageMs, null);
});
test('manual Clear is terminal, wipes metadata and cannot be resurrected by late responses', () => {
  const cleared = end(stateAt(), 'cleared');
  assert.equal(cleared.point, null); assert.equal(cleared.pointClock, null); assert.equal(cleared.clock, null);
  assert.equal(received(cleared, validatePacket(packet(), expected), 700), cleared); assert.equal(failed(cleared), cleared);
});
test('transit is conservatively included in age and a positive chosen deadline', () => {
  const state = received(initialState(), validatePacket(packet({ expiresAtMs: 1_900_000, pointAgeMs: 28000 }), expected), 1000, 3000);
  assert.equal(view(state, 1000).ageMs, 31000); assert.equal(view(state, 1000).phase, 'stale'); assert.equal(state.clock.remaining, 897000);
  assert.equal(view(advance(state, 28001), 28001).phase, 'last_known');
});
test('word splitting preserves every character and surrogate pair', () => {
  const original = 'A short paragraph. Second line \u{1f30e} with a verylongword.';
  let remaining = original, rebuilt = '';
  while (remaining) { const count = wordBoundary(remaining, Math.min(13, remaining.length)); assert.ok(count > 0); const part = remaining.slice(0, count); assert.ok(!/[\uD800-\uDBFF]$/.test(part)); rebuilt += part; remaining = remaining.slice(count); }
  assert.equal(rebuilt, original); assert.equal(wordBoundary('a\u{1f30e}z', 2), 1);
});
