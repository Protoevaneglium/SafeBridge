import test from 'node:test';
import assert from 'node:assert/strict';
import { layoutSignature, pieceKey, syncPageNodes } from './view-deck.js';

const whole = (source, value) => ({ source, start: 0, end: Number.MAX_SAFE_INTEGER, tag: 'p', style: 'field', text: value });
class TextNode { constructor(data) { this.nodeType = 3; this.data = data; this.nextSibling = null; } }
class Element {
  constructor(piece) { this.tagName = piece.tag.toUpperCase(); this.dataset = {}; this.firstChild = new TextNode(piece.text); this.parent = null; }
  get textContent() { return this.firstChild?.data || ''; }
  set textContent(value) { this.firstChild = new TextNode(value); }
  remove() { if (this.parent) { this.parent.children.splice(this.parent.children.indexOf(this), 1); this.parent = null; } }
}
class Container {
  constructor() { this.children = []; this.insertions = 0; }
  insertBefore(node, reference) { node.remove(); const at = reference ? this.children.indexOf(reference) : this.children.length; this.children.splice(at, 0, node); node.parent = this; this.insertions++; }
}

test('unchanged content, dimensions and mode have the same layout signature', () => {
  const sources = [{ tag: 'p', text: 'One paragraph', style: '' }];
  assert.equal(layoutSignature('position', 'recent', 320, 400, sources, '16px'), layoutSignature('position', 'recent', 320, 400, structuredClone(sources), '16px'));
  assert.notEqual(layoutSignature('position', 'recent', 320, 400, sources, '16px'), layoutSignature('position', 'recent', 320, 400, sources, '32px'));
  assert.notEqual(layoutSignature('position', 'recent', 320, 400, sources), layoutSignature('position', 'recent', 320, 300, sources));
});
test('privacy layout ignores position freshness changes elsewhere', () => {
  const privacy = [{ tag: 'p', text: 'Links can be forwarded.', style: '' }];
  assert.equal(layoutSignature('privacy', 'recent', 320, 400, privacy), layoutSignature('privacy', 'stale', 320, 400, privacy));
  assert.notEqual(layoutSignature('position', 'recent', 320, 400, privacy), layoutSignature('position', 'stale', 320, 400, privacy));
});
test('one-second age changes preserve semantic element and text-node identities', () => {
  const container = new Container(); let created = 0;
  const make = piece => { created++; return new Element(piece); };
  syncPageNodes(container, [whole(0, 'Latitude: 0.000000'), whole(1, 'Fix age: 9 seconds')], make);
  const [coordinate, age] = container.children;
  const coordinateText = coordinate.firstChild, ageText = age.firstChild;
  const inserts = container.insertions;
  syncPageNodes(container, [whole(0, 'Latitude: 0.000000'), whole(1, 'Fix age: 10 seconds')], make);
  assert.equal(created, 2); assert.equal(container.insertions, inserts);
  assert.equal(container.children[0], coordinate); assert.equal(container.children[1], age);
  assert.equal(coordinate.firstChild, coordinateText); assert.equal(age.firstChild, ageText);
  assert.equal(age.textContent, 'Fix age: 10 seconds');
});
test('composition changes reuse retained blocks and remove obsolete coordinate blocks', () => {
  const container = new Container(); const make = piece => new Element(piece);
  syncPageNodes(container, [whole(0, 'Latitude: 0.000000'), whole(1, 'Longitude: 0.000000')], make);
  const latitude = container.children[0], longitude = container.children[1];
  syncPageNodes(container, [whole(1, 'Longitude: 0.000000'), whole(2, 'Fix age: 2 seconds')], make);
  assert.equal(container.children.length, 2); assert.equal(container.children[0], longitude);
  assert.equal(latitude.parent, null); assert.equal(container.children.includes(latitude), false);
  syncPageNodes(container, [whole(3, 'Sharing stopped. Coordinates cleared.')], make);
  assert.equal(container.children.length, 1); assert.equal(longitude.parent, null);
  assert.equal(container.children[0].textContent.includes('0.000000'), false);
});
test('semantic keys include source range, element tag and style', () => {
  const original = whole(0, 'One');
  assert.equal(pieceKey(original), pieceKey({ ...original, text: 'Two' }));
  for (const change of [{ source: 1 }, { start: 2 }, { end: 3 }, { tag: 'h2' }, { style: 'warning' }]) assert.notEqual(pieceKey(original), pieceKey({ ...original, ...change }));
});
