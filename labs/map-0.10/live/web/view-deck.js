/** Measured text pages. Source text remains complete; the UI never shrinks it to fit. */
export function wordBoundary(text, fittedLength) {
  if (fittedLength >= text.length) return text.length;
  let end = fittedLength;
  // Do not split a UTF-16 surrogate pair.
  if (end > 0 && /[\uD800-\uDBFF]/.test(text[end - 1]) && /[\uDC00-\uDFFF]/.test(text[end] || '')) end -= 1;
  for (let index = end - 1; index > 0; index--) if (/\s/u.test(text[index])) return index + 1;
  return end;
}


const WHOLE_BLOCK_END = Number.MAX_SAFE_INTEGER;
export function layoutSignature(mode, phase, width, height, sources, metrics = '') {
  // Privacy text does not change when location freshness changes elsewhere.
  return JSON.stringify([mode, ['privacy','map'].includes(mode) ? mode : phase, width, height, metrics, sources.map(({ tag, text, style }) => [tag, text, style])]);
}
export function pieceKey(piece) { return JSON.stringify([piece.source, piece.start, piece.end, piece.tag, piece.style]); }
export function syncPageNodes(container, pieces, makeElement) {
  const existing = new Map(Array.from(container.children).map(node => [node.dataset.pieceKey, node]));
  const retained = new Set();
  pieces.forEach((piece, index) => {
    const key = pieceKey(piece);
    const node = existing.get(key) || makeElement(piece);
    node.dataset.pieceKey = key;
    if (node.textContent !== piece.text) {
      // Retain the text node too, when it is a plain semantic text block.
      if (node.firstChild?.nodeType === 3 && !node.firstChild.nextSibling) node.firstChild.data = piece.text;
      else node.textContent = piece.text;
    }
    retained.add(node);
    if (container.children[index] !== node) container.insertBefore(node, container.children[index] || null);
  });
  for (const node of Array.from(container.children)) if (!retained.has(node)) node.remove();
}
function attribute(node, name, value) { if (node.getAttribute(name) !== value) node.setAttribute(name, value); }
function text(node, value) { if (node.textContent !== value) node.textContent = value; }

export class RecipientDeck {
  constructor() {
    this.container = document.getElementById('deck-content');
    this.previous = document.getElementById('page-previous');
    this.next = document.getElementById('page-next');
    this.modeButton = document.getElementById('page-mode');
    this.position = document.getElementById('page-index');
    this.announcer = document.getElementById('page-announcer');
    this.mode = 'position'; this.current = 0; this.pages = []; this.phase = ''; this.pending = false; this.layoutKey = ''; this.mapSources = null; this.mapReturn = null; this.mapReady = false; this.onMapChange = null;
    this.previous.addEventListener('click', () => this.move(-1));
    this.next.addEventListener('click', () => this.move(1));
    this.modeButton.addEventListener('click', () => { if (this.mode === 'map') { this.closeMap(); return; } this.mode = this.mode === 'position' ? 'privacy' : 'position'; this.current = 0; this.pages = []; this.refresh(this.phase); this.announce(); });
    new ResizeObserver(() => { if (this.pending) return; this.pending = true; requestAnimationFrame(() => { this.pending = false; this.refresh(this.phase); }); }).observe(this.container);
  }
  openMap(parts) { this.mapReturn = { mode: this.mode, current: this.current }; this.mapSources = parts; this.mapReady = false; this.mode = 'map'; this.current = 0; this.pages = []; this.layoutKey = ''; this.refresh(this.phase); this.announce(); }
  closeMap() { const previous = this.mapReturn; this.clearMap(); this.mode = previous?.mode || 'position'; this.current = previous?.current || 0; this.refresh(this.phase); this.onMapChange?.(); this.announce(); }
  clearMap() { this.mapSources = null; this.mapReturn = null; this.mapReady = false; if (this.mode === 'map') { this.mode = 'position'; this.current = 0; this.pages = []; this.layoutKey = ''; } }
  sources() {
    if (this.mode === 'map') return (this.mapSources || []).map(part => ({ ...part }));
    const parts = [];
    const append = (tag, text, style = '') => { if (text.trim()) parts.push({ tag, text, style }); };
    if (this.mode === 'privacy') {
      document.querySelectorAll('#privacy-source h2,#privacy-source h3,#privacy-source p').forEach(element => append(element.tagName.toLowerCase(), element.textContent, element.className));
    } else {
      const source = document.getElementById('position-card');
      if (!source.hidden) {
        append('h2', document.getElementById('position-title').textContent);
        const warning = document.getElementById('position-warning');
        if (!warning.hidden) append('p', warning.textContent, 'warning');
        for (const id of ['latitude','longitude','fix-age','accuracy','street-address','fix-time','checked-time']) { const field = document.getElementById(id).closest('div'); append('p', `${field.querySelector('dt').textContent}:\n${field.querySelector('dd').textContent}`, 'field'); }
        source.querySelectorAll(':scope > p.subtle').forEach(element => append('p', element.textContent, 'subtle'));
      } else append('h2', 'Position and connection');
      append('p', document.getElementById('status-message').textContent);
      append('p', document.getElementById('retry-message').textContent, 'subtle');
      append('p', 'Anyone with this link can view available location updates until sharing ends. This page does not dispatch help. Use Privacy to read the limits.', 'scope-note');
    }
    return parts;
  }
  element(part, text = part.text) {
    const node = document.createElement(part.tag); node.textContent = text; node.className = `deck-block ${part.style}`; return node;
  }
  refresh(phase) {
    const terminal = ['stopped','expired','unavailable','cleared'].includes(phase);
    const anchor = this.pages[this.current]?.[0];
    const terminalChanged = terminal && phase !== this.phase;
    if (terminalChanged) { this.clearMap(); this.mode = 'position'; this.current = 0; this.pages = []; }
    this.phase = phase;
    const computed = getComputedStyle(this.container);
    const width = this.container.clientWidth;
    const available = this.container.clientHeight - parseFloat(computed.paddingTop || 0) - parseFloat(computed.paddingBottom || 0);
    const sources = this.sources();
    const metrics = [computed.fontSize, computed.lineHeight, computed.fontFamily, computed.letterSpacing].join('|');
    const signature = layoutSignature(this.mode, phase, width, available, sources, metrics);
    text(this.modeButton, this.mode === 'map' ? 'Cancel' : this.mode === 'position' ? 'Privacy' : 'Position');
    attribute(this.modeButton, 'aria-label', this.mode === 'map' ? 'Cancel external map and return' : this.mode === 'position' ? 'Show privacy pages' : 'Show position pages');
    if (signature === this.layoutKey) return;
    this.layoutKey = signature;
    this.pages = [];
    if (width < 1 || available < 1) { this.tooSmall(); return; }
    const probe = document.createElement('div'); probe.className = 'measure-probe'; probe.setAttribute('aria-hidden', 'true'); this.container.append(probe);
    const height = (part, text) => { const node = this.element(part, text); probe.replaceChildren(node); return node.getBoundingClientRect().height; };
    const pieces = [];
    for (let source = 0; source < sources.length; source++) {
      const part = sources[source];
      if (height(part, part.text) <= available) { pieces.push({ ...part, source, start: 0, end: WHOLE_BLOCK_END, height: height(part, part.text) }); continue; }
      if (height(part, 'M') > available && part.style === 'warning') part.style = 'warning bare';
      if (height(part, 'M') > available) { probe.remove(); this.tooSmall(); return; }
      let start = 0;
      while (start < part.text.length) {
        const remaining = part.text.slice(start);
        let low = 1, high = remaining.length, fitted = 0;
        while (low <= high) { const middle = Math.floor((low + high) / 2); if (height(part, remaining.slice(0, middle)) <= available) { fitted = middle; low = middle + 1; } else high = middle - 1; }
        const count = wordBoundary(remaining, fitted);
        if (count < 1) { probe.remove(); this.tooSmall(); return; }
        const text = remaining.slice(0, count);
        pieces.push({ ...part, text, source, start, end: count === remaining.length ? WHOLE_BLOCK_END : start + count, height: height(part, text) }); start += count;
      }
    }
    probe.remove();
    const gap = 10; let page = [], used = 0;
    for (const piece of pieces) { const needed = piece.height + (page.length ? gap : 0); if (page.length && used + needed > available) { this.pages.push(page); page = []; used = 0; } used += piece.height + (page.length ? gap : 0); page.push(piece); }
    if (page.length) this.pages.push(page);
    if (!this.pages.length) { this.tooSmall(); return; }
    if (anchor && !terminalChanged) {
      const index = this.pages.findIndex(sheet => sheet.some(piece => piece.source === anchor.source && piece.start <= anchor.start && piece.end > anchor.start));
      if (index >= 0) this.current = index;
    }
    this.current = Math.max(0, Math.min(this.current, this.pages.length - 1)); this.render();
  }
  tooSmall() {
    this.container.replaceChildren(); this.pages = []; this.mapReady = false; this.onMapChange?.(); this.previous.disabled = true; this.next.disabled = true;
    text(this.position, 'Expand window'); attribute(this.position, 'aria-label', 'Window too small for readable content. Expand the window or change orientation. Text has not been removed.');
    attribute(this.container, 'aria-label', 'Expand the window or change orientation to read this page. Full text is retained.');
  }
  render() {
    attribute(this.container, 'aria-label', `${this.mode === 'map' ? 'Map disclosure' : this.mode === 'position' ? 'Position' : 'Privacy'} page ${this.current + 1} of ${this.pages.length}`);
    syncPageNodes(this.container, this.pages[this.current], piece => this.element(piece));
    this.previous.disabled = this.current === 0; this.next.disabled = this.current + 1 === this.pages.length;
    text(this.position, `${this.mode === 'map' ? 'Map' : this.mode === 'position' ? 'Position' : 'Privacy'} ${this.current + 1} / ${this.pages.length}`);
    attribute(this.position, 'aria-label', `${this.mode === 'map' ? 'Map disclosure' : this.mode === 'position' ? 'Position' : 'Privacy'} page ${this.current + 1} of ${this.pages.length}`);
    this.mapReady = this.mode === 'map' && this.current + 1 === this.pages.length; this.onMapChange?.();
  }
  move(delta) { const target = this.current + delta; if (target < 0 || target >= this.pages.length) return; this.current = target; this.render(); this.announce(); }
  announce() { this.announcer.textContent = this.position.getAttribute('aria-label') || this.position.textContent; this.container.focus({ preventScroll: true }); }
}
