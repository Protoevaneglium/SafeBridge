/** V2 recipient state. Latest point stays only in this tab's memory; no persistence or network APIs. */
export const RECENT_FIX_MS = 30_000;
export const REVALIDATION_MS = 30_000;
const TERMINAL = new Set(['stopped', 'expired']);
const STATES = new Set(['active', 'waiting_for_fix', 'paused', ...TERMINAL]);
const number = value => typeof value === 'number' && Number.isFinite(value);
const milliseconds = value => number(value) && value >= 0 && value <= Number.MAX_SAFE_INTEGER;
const tokenPattern = /^[A-Za-z0-9_-]+$/;

export function parseLink(fragment) {
  const params = new URLSearchParams(fragment.replace(/^#/, ''));
  if (params.getAll('s').length !== 1 || params.getAll('g').length !== 1 || params.getAll('r').length !== 1) return null;
  const id = params.get('s'), generation = params.get('g'), readToken = params.get('r');
  if (id.length !== 22 || generation.length !== 22 || readToken.length !== 43 || ![id, generation, readToken].every(value => tokenPattern.test(value))) return null;
  return { id, generation, readToken };
}

export function initialState() { return { mode: 'connecting', terminal: null, point: null, pointClock: null, clock: null, failures: 0, reason: '' }; }

export function validatePacket(packet, expected) {
  if (!packet || typeof packet !== 'object' || packet.id !== expected.id || packet.generation !== expected.generation || !STATES.has(packet.state)) throw new Error('Invalid location response');
  if (![packet.serverTimeMs, packet.expiresAtMs, packet.leaseUntilMs].every(milliseconds)) throw new Error('Invalid response times');
  const times = { state: packet.state, serverTimeMs: packet.serverTimeMs, expiresAtMs: packet.expiresAtMs, leaseUntilMs: packet.leaseUntilMs };
  if (TERMINAL.has(packet.state)) return { ...times, point: null, pointAgeMs: null };
  const point = packet.point;
  if (packet.state !== 'active' && point !== null) throw new Error('Inconsistent paused or waiting state');
  if (point === null) {
    if (!['waiting_for_fix','paused'].includes(packet.state) || packet.pointAgeMs !== null) throw new Error('Missing location');
    return { ...times, point: null, pointAgeMs: null };
  }
  if (!point || !number(point.latitude) || Math.abs(point.latitude) > 90 || !number(point.longitude) || Math.abs(point.longitude) > 180 || !number(point.accuracyM) || point.accuracyM < 0 || ![point.sampleTimeMs, point.sampleAgeMs, point.receivedAtMs, packet.pointAgeMs].every(milliseconds) || typeof point.approximate !== 'boolean' || typeof point.mock !== 'boolean') throw new Error('Invalid location data');
  return { ...times, point: { latitude: point.latitude, longitude: point.longitude, accuracyM: point.accuracyM, sampleTimeMs: point.sampleTimeMs, approximate: point.approximate, mock: point.mock }, pointAgeMs: packet.pointAgeMs };
}

export function end(state, terminal, reason = '') { return { ...state, mode: 'terminal', terminal, point: null, pointClock: null, clock: null, reason }; }

export function received(state, packet, now, transportMs = 0) {
  if (state.mode === 'terminal') return state;
  if (TERMINAL.has(packet.state)) return end(state, packet.state);
  const transit = number(transportMs) && transportMs >= 0 ? transportMs : 0;
  const candidateDeadline = packet.expiresAtMs === 0 ? null : now + Math.max(0, packet.expiresAtMs - packet.serverTimeMs - transit);
  const previousDeadline = state.clock?.deadlineAt;
  const deadlineAt = number(previousDeadline) ? (candidateDeadline === null ? previousDeadline : Math.min(previousDeadline, candidateDeadline)) : candidateDeadline;
  const next = {
    mode: packet.state === 'paused' || (!packet.point && state.point) ? 'paused' : 'verified', terminal: null,
    point: packet.point || state.point,
    pointClock: packet.point ? { at: now, baseAge: packet.pointAgeMs + transit } : state.pointClock,
    clock: { at: now, deadlineAt, remaining: deadlineAt === null ? null : Math.max(0, deadlineAt - now), leaseRemaining: Math.max(0, packet.leaseUntilMs - packet.serverTimeMs - transit), validationOffset: transit, verifiedAt: packet.serverTimeMs },
    failures: 0, reason: packet.state === 'paused' ? 'sender_paused' : ''
  };
  return advance(next, now);
}

export function failed(state, reason = 'connection', permanent = false) {
  if (state.mode === 'terminal') return state;
  if (permanent) return end(state, 'unavailable', reason);
  return { ...state, mode: 'connection_lost', failures: state.failures + 1, reason };
}

/** Keep RAM-only last-known data, but never expose coordinates in the hidden page view. */
export function paused(state) { return state.mode === 'terminal' ? state : { ...state, mode: 'hidden', reason: '' }; }
export function reconnecting(state) { return state.mode === 'terminal' ? state : { ...state, mode: 'connecting', reason: '' }; }

export function advance(state, now) {
  if (state.mode === 'terminal' || !state.clock) return state;
  const elapsed = now - state.clock.at;
  if (!number(elapsed) || elapsed < 0) return { ...state, mode: 'connection_lost', reason: 'clock' };
  // A positive chosen deadline is immutable and still ends viewing during an outage.
  if (state.clock.remaining !== null && elapsed >= state.clock.remaining) return end(state, 'expired');
  // A point lease/revalidation lapse pauses freshness; it never ends an until-stopped link.
  if (state.mode === 'verified' && (elapsed + state.clock.validationOffset > REVALIDATION_MS || elapsed >= state.clock.leaseRemaining)) return { ...state, mode: 'connection_lost', reason: 'unverified' };
  return state;
}

export function view(state, now) {
  state = advance(state, now);
  const elapsed = state.clock ? now - state.clock.at : 0;
  const remainingMs = state.clock?.remaining === null || !state.clock ? null : Math.max(0, state.clock.remaining - Math.max(0, elapsed));
  const common = { remainingMs, untilStopped: Boolean(state.clock && state.clock.remaining === null), verifiedAtMs: state.clock?.verifiedAt ?? null };
  if (state.mode === 'terminal') return { ...common, phase: state.terminal, point: null, ageMs: null };
  if (state.mode === 'hidden') return { ...common, phase: 'hidden', point: null, ageMs: null };
  const pointElapsed = state.pointClock ? now - state.pointClock.at : -1;
  const ageMs = state.pointClock && number(pointElapsed) && pointElapsed >= 0 ? state.pointClock.baseAge + pointElapsed : null;
  if (state.point && state.mode !== 'verified') return { ...common, phase: 'last_known', point: state.point, ageMs };
  if (state.mode !== 'verified') return { ...common, phase: state.mode, point: null, ageMs: null };
  return { ...common, phase: state.point ? (ageMs === null || ageMs > RECENT_FIX_MS ? 'stale' : 'recent') : 'waiting', point: state.point, ageMs };
}
