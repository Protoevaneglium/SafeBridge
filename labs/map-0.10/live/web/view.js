import { LiveMapPanel } from './view-live-map.js';
import { RecipientDeck } from './view-deck.js';
import { buildMapUrl, mapDisclosure } from './view-map.js';
import { readBoundedJson } from './view-network.js';
import { initialState, parseLink, validatePacket, received, failed, paused, reconnecting, advance, end, view } from './view-state.js';

const nodes = Object.fromEntries(['status-title','status-label','status-message','retry-message','position-card','position-warning','latitude','longitude','accuracy','fix-age','fix-time','checked-time','refresh','clear','open-map'].map(id => [id, document.getElementById(id)]));
const card = document.querySelector('.status-card');
const deck = new RecipientDeck();
const liveMap = new LiveMapPanel();
const statusAnnouncer = document.getElementById('status-announcer');
let credentials = parseLink(location.hash);
// A fragment is not sent in HTTP requests. Remove it before doing any network work.
// Browser history, copied links and device records are outside this page's control.
try { history.replaceState(null, '', location.pathname + location.search); } catch { /* No concealment promise. */ }
let state = initialState();
let pendingMap = null;
let requestVersion = 0, activeRequest = null, retryTimer = null, timeoutTimer = null;
let manualOnly = false;
const now = () => performance.now();
const duration = milliseconds => {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000));
  if (seconds < 60) return `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`;
  return `${Math.floor(seconds / 60)} min ${seconds % 60} sec`;
};
const utc = milliseconds => { const date = new Date(milliseconds); return milliseconds > 0 && Number.isFinite(date.getTime()) ? date.toISOString().replace('T', ' ').replace('.000Z', ' UTC').replace('Z', ' UTC') : 'Time not available'; };

function wipePosition() {
  nodes['position-card'].hidden = true;
  for (const id of ['latitude','longitude','accuracy','fix-age','fix-time','checked-time','position-warning']) nodes[id].textContent = '';
  nodes['position-warning'].hidden = true;
}

function stopNetwork() {
  requestVersion += 1;
  clearTimeout(retryTimer); retryTimer = null;
  clearTimeout(timeoutTimer); timeoutTimer = null;
  if (activeRequest) activeRequest.abort();
  activeRequest = null;
}

function clearPendingMap() { pendingMap = null; deck.clearMap(); }
function updateMapButton() {
  if (deck.mode !== 'map' && pendingMap) pendingMap = null;
  const mapMode = deck.mode === 'map' && Boolean(pendingMap);
  const available = view(state, now()).point;
  nodes['open-map'].disabled = !credentials || document.visibilityState !== 'visible' || (mapMode ? !deck.mapReady : !available);
  const label = mapMode ? 'Open' : 'Map';
  if (nodes['open-map'].textContent !== label) nodes['open-map'].textContent = label;
  nodes['open-map'].setAttribute('aria-label', mapMode ? 'Open Google Maps with the disclosed last-known coordinates' : 'Read disclosure before opening an external map');
}
deck.onMapChange = updateMapButton;

function render() {
  state = advance(state, now());
  if (state.mode === 'terminal') { stopNetwork(); credentials = null; clearPendingMap(); }
  const current = view(state, now());
  liveMap.render(current);
  if (card.dataset.phase !== current.phase) card.dataset.phase = current.phase;
  const labels = {
    connecting: ['CHECKING LINK', 'Checking for an update', 'A location is shown only after this page verifies a response.'],
    recent: ['RECENT DEVICE FIX', 'A recent estimate is available', 'This is a device estimate, not proof of the person’s current position or safety.'],
    stale: ['OLDER DEVICE FIX', 'This position is over 30 seconds old', 'The device may have moved. Do not treat these coordinates as a current position.'],
    waiting: ['WAITING FOR A FIX', 'No position is available yet', 'The link is responding, but the sender’s device has not supplied a usable position.'],
    last_known: ['LAST KNOWN · CURRENT POSITION UNVERIFIED', 'Last known; current position unverified', 'These are the latest coordinates this tab received. Updates are paused or cannot be verified. The device may have moved; this does not establish the person’s current position or safety.'],
    connection_lost: ['CONNECTION NOT VERIFIED', 'No previously received position in this tab', 'Updates cannot be verified. This tab has no prior coordinates to show. A connection problem does not tell you whether the person is safe.'],
    paused: ['UPDATES PAUSED', 'No previously received position in this tab', 'The sender’s point is no longer available from the service. A fresh update on this same link can resume viewing; no position is invented here.'],
    hidden: ['VIEW PAUSED', 'Recheck when this page is visible', 'Requests pause while this page is hidden. Its last received point remains only in tab memory until Clear, chosen expiry or a confirmed Stop.'],
    stopped: ['SHARING STOPPED', 'The sender stopped sharing', 'Location data has been cleared from this page. Previously copied links, messages or screenshots cannot be recalled.'],
    expired: ['SHARING EXPIRED', 'The requested sharing time ended', 'Location data has been cleared from this page. Ask the sender for a new link only if it is safe to contact them.'],
    unavailable: ['LINK UNAVAILABLE', 'This link cannot show a position', state.reason === 'insecure' ? 'A secure HTTPS connection is required. No location request was sent.' : 'It may be invalid, revoked or no longer available. No position is shown.'],
    cleared: ['PAGE CLEARED', 'The viewing link was cleared from this page', 'Opening a saved copy may still work until the sender stops sharing or the link expires. Clearing this page cannot erase device or browser records.']
  };
  const [label, title, message] = labels[current.phase] || labels.connection_lost;
  if (nodes['status-label'].textContent !== label) nodes['status-label'].textContent = label;
  if (nodes['status-title'].textContent !== title) nodes['status-title'].textContent = title;
  if (nodes['status-message'].textContent !== message) { nodes['status-message'].textContent = message; statusAnnouncer.textContent = `${title}. ${message}`; }
  nodes.refresh.disabled = !credentials || Boolean(activeRequest) || document.visibilityState !== 'visible';
  const refreshLabel = activeRequest ? 'Checking…' : 'Refresh';
  if (nodes.refresh.textContent !== refreshLabel) nodes.refresh.textContent = refreshLabel;
  if (state.mode === 'terminal') nodes['retry-message'].textContent = '';
  else if (activeRequest) nodes['retry-message'].textContent = 'Checking for a verified update…';
  else if (state.failures >= 3 || manualOnly) nodes['retry-message'].textContent = 'Automatic retries are paused. Use Refresh now to try again.';
  else if (state.mode === 'connection_lost') nodes['retry-message'].textContent = 'A limited retry will run while this page stays visible. You can also refresh now.';
  else if (current.remainingMs !== null) nodes['retry-message'].textContent = `Requested sharing time remaining: about ${duration(current.remainingMs)}. Checked every 15 seconds while visible.`;
  else nodes['retry-message'].textContent = '';
  if (current.untilStopped && state.mode !== 'terminal') nodes['retry-message'].textContent += ' Requested duration: until stopped; no chosen end time.';
  if (!current.point) { wipePosition(); deck.refresh(current.phase); updateMapButton(); return; }
  const point = current.point;
  nodes['latitude'].textContent = point.latitude.toFixed(6);
  nodes['longitude'].textContent = point.longitude.toFixed(6);
  nodes['accuracy'].textContent = `${point.accuracyM.toLocaleString(undefined, { maximumFractionDigits: 1 })} meters estimated radius · about 68% confidence`;
  nodes['fix-age'].textContent = current.ageMs === null ? 'Age unavailable; current position unverified' : `${duration(current.ageMs)} old${['stale','last_known'].includes(current.phase) ? ' · current position unverified' : ''}`;
  nodes['fix-time'].textContent = utc(point.sampleTimeMs);
  nodes['checked-time'].textContent = utc(current.verifiedAtMs);
  const positionTitle = ['stale','last_known'].includes(current.phase) ? 'Last-known device position' : 'Reported device position';
  if (document.getElementById('position-title').textContent !== positionTitle) document.getElementById('position-title').textContent = positionTitle;
  const warnings = [];
  if (current.phase === 'last_known') warnings.push('Last known; current position unverified. This tab retains the earlier fix, not proof of a current position.');
  if (current.phase === 'stale') warnings.push('Older position: do not treat it as current.');
  if (point.approximate) warnings.push('The sender allowed approximate location.');
  if (point.mock) warnings.push('Android marked this as a mock/test location. Do not rely on it as someone’s position.');
  nodes['position-warning'].textContent = warnings.join(' ');
  nodes['position-warning'].hidden = warnings.length === 0;
  nodes['position-card'].hidden = false;
  deck.refresh(current.phase); updateMapButton();
}

function schedule(delay) {
  clearTimeout(retryTimer);
  if (!credentials || manualOnly || document.visibilityState !== 'visible' || state.mode === 'terminal' || state.failures >= 3) return;
  retryTimer = setTimeout(() => requestUpdate(), delay);
}

async function requestUpdate() {
  if (!credentials || activeRequest || document.visibilityState !== 'visible' || state.mode === 'terminal') return;
  clearTimeout(retryTimer); retryTimer = null;
  const version = ++requestVersion;
  const abort = new AbortController();
  activeRequest = abort;
  timeoutTimer = setTimeout(() => abort.abort(), 8_000);
  const currentCredential = credentials;
  const startedAt = now();
  render();
  let retryAfter = 0;
  try {
    const response = await fetch(`/v1/shares/${encodeURIComponent(currentCredential.id)}/latest`, {
      method: 'GET', headers: { Authorization: `Bearer ${currentCredential.readToken}`, 'X-SafeBridge-Generation': currentCredential.generation, Accept: 'application/json' },
      credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer', signal: abort.signal
    });
    if (version !== requestVersion || document.visibilityState !== 'visible') return;
    if ([401,403,404,410].includes(response.status)) { state = end(state, response.status === 410 ? 'expired' : 'unavailable'); return; }
    if (!response.ok) {
      if (response.status === 429) retryAfter = Math.max(5_000, Math.min(60_000, Number(response.headers.get('Retry-After')) * 1000 || 15_000));
      throw new Error('Update unavailable');
    }
    const body = await readBoundedJson(response);
    if (version !== requestVersion || document.visibilityState !== 'visible') return;
    const packet = validatePacket(body, currentCredential);
    const receivedAt = now();
    state = received(state, packet, receivedAt, Math.max(0, receivedAt - startedAt));
  } catch {
    if (version !== requestVersion || document.visibilityState !== 'visible') return;
    state = failed(state);
  } finally {
    if (version === requestVersion) {
      clearTimeout(timeoutTimer); timeoutTimer = null;
      activeRequest = null;
      render();
      schedule(state.failures ? (retryAfter || Math.min(15_000, state.failures * 5_000)) : 15_000);
    }
  }
}

function suspend() { liveMap.suspend(); clearPendingMap(); stopNetwork(); state = paused(state); render(); }
function resume() {
  if (document.visibilityState !== 'visible' || !credentials || state.mode === 'terminal') return;
  state = advance(state, now());
  if (state.mode === 'terminal') { render(); return; }
  state = reconnecting(state);
  render();
  if (!manualOnly && state.failures < 3) requestUpdate();
}

nodes.refresh.addEventListener('click', () => {
  if (!credentials || activeRequest) return;
  manualOnly = false;
  state = reconnecting({ ...state, failures: 0 });
  requestUpdate();
});
nodes.clear.addEventListener('click', () => { clearPendingMap(); stopNetwork(); credentials = null; state = end(state, 'cleared'); render(); nodes['status-title'].focus({ preventScroll: true }); });
nodes['open-map'].addEventListener('click', () => {
  state = advance(state, now());
  if (!credentials || state.mode === 'terminal' || document.visibilityState !== 'visible') { render(); return; }
  if (deck.mode === 'map' && pendingMap) {
    if (!deck.mapReady) return;
    const url = buildMapUrl(pendingMap.point);
    pendingMap = null;
    deck.closeMap();
    // Explicit user confirmation only. Credentials never enter this URL or referrer.
    try { window.open(url, '_blank', 'noopener,noreferrer'); statusAnnouncer.textContent = 'External map opening requested. Google’s privacy rules apply; opening is not confirmed.'; }
    catch { statusAnnouncer.textContent = 'The browser could not request the external map. No opening is confirmed.'; }
    return;
  }
  const selected = view(state, now());
  if (!selected.point) return;
  pendingMap = { point: { ...selected.point } };
  deck.openMap(mapDisclosure(pendingMap.point, { ageText: selected.ageMs === null ? 'unavailable; current position unverified' : duration(selected.ageMs), fixTimeText: utc(selected.point.sampleTimeMs) }));
  updateMapButton();
});
window.addEventListener('online', () => {
  if (!credentials || document.visibilityState !== 'visible' || state.mode === 'terminal') return;
  manualOnly = false; state = reconnecting({ ...state, failures: 0 }); requestUpdate();
});
nodes['status-title'].tabIndex = -1;
document.addEventListener('visibilitychange', () => document.visibilityState === 'hidden' ? suspend() : resume());
window.addEventListener('pagehide', suspend);
window.addEventListener('pageshow', () => { if (!activeRequest) resume(); });
setInterval(() => { const advanced = advance(state, now()); const justEnded = advanced.mode === 'terminal' && state.mode !== 'terminal'; state = advanced; if (document.visibilityState === 'visible' || justEnded) render(); }, 1000);
const loopback = ['localhost','127.0.0.1','[::1]'].includes(location.hostname);
if (!credentials) state = end(state, 'unavailable');
else if (location.protocol !== 'https:' && !loopback) state = end(state, 'unavailable', 'insecure');
render();
if (credentials && document.visibilityState === 'visible') requestUpdate();
