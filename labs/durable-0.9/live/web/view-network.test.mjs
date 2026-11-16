import test from 'node:test';
import assert from 'node:assert/strict';
import { readBoundedJson, MAX_JSON_BYTES } from './view-network.js';

const utf8 = new TextEncoder();
const response = (chunks, headers = {}, cancelled = () => {}) => new Response(new ReadableStream({
  start(controller) { for (const chunk of chunks) controller.enqueue(chunk); controller.close(); },
  cancel: cancelled
}), { headers: { 'content-type': 'application/json; charset=utf-8', ...headers } });

test('reads real Response byte chunks with UTF-8 split across chunks', async () => {
  const value = { state: 'active', label: '🌎', point: { latitude: 0, longitude: 0 } };
  const bytes = utf8.encode(JSON.stringify(value));
  const chunks = [...bytes].map(byte => new Uint8Array([byte]));
  assert.deepEqual(await readBoundedJson(response(chunks)), value);
});
test('accepts exactly the byte limit', async () => {
  const text = `"${'a'.repeat(MAX_JSON_BYTES - 2)}"`;
  assert.equal((await readBoundedJson(response([utf8.encode(text)]))).length, MAX_JSON_BYTES - 2);
});
test('rejects a streamed body exceeding the byte limit and cancels unread data', async () => {
  let cancelled = false, calls = 0;
  const body = new ReadableStream({
    pull(controller) { calls++; controller.enqueue(new Uint8Array(16_384).fill(0x61)); },
    cancel() { cancelled = true; }
  });
  await assert.rejects(readBoundedJson(new Response(body, { headers: { 'content-type': 'application/json' } })), /too large/);
  assert.equal(cancelled, true); assert.ok(calls <= 5);
});
test('checks bytes, rather than UTF-16 string length', async () => {
  const text = `"${'🌎'.repeat(9000)}"`;
  assert.ok(text.length < MAX_JSON_BYTES);
  await assert.rejects(readBoundedJson(response([utf8.encode(text)])), /too large/);
});
test('rejects declared oversized or invalid Content-Length before reading', async () => {
  await assert.rejects(readBoundedJson(response([utf8.encode('{}')], { 'content-length': String(MAX_JSON_BYTES + 1) })), /too large/);
  await assert.rejects(readBoundedJson(response([utf8.encode('{}')], { 'content-length': 'invalid' })), /too large/);
});
test('rejects invalid and truncated UTF-8', async () => {
  await assert.rejects(readBoundedJson(response([new Uint8Array([0x22,0xc3,0x28,0x22])])), TypeError);
  await assert.rejects(readBoundedJson(response([new Uint8Array([0x22,0xe2,0x82])])), TypeError);
});
test('rejects empty, whitespace-only and malformed JSON', async () => {
  for (const value of ['', '   ', '{"broken":']) await assert.rejects(readBoundedJson(response([utf8.encode(value)])), SyntaxError);
});
test('rejects missing or unsupported content types', async () => {
  await assert.rejects(readBoundedJson(new Response('{}')), /JSON response/);
  await assert.rejects(readBoundedJson(response([utf8.encode('{}')], { 'content-type': 'text/html' })), /JSON response/);
});
test('fails closed without a reader; it never uses text or json fallback', async () => {
  let fallback = false;
  await assert.rejects(readBoundedJson({ body: {}, text() { fallback = true; }, json() { fallback = true; } }), /readable response stream/);
  assert.equal(fallback, false);
});
test('transport or abort-style stream failure is propagated', async () => {
  const error = new DOMException('The operation was aborted', 'AbortError');
  const body = new ReadableStream({ start(controller) { controller.error(error); } });
  await assert.rejects(readBoundedJson(new Response(body, { headers: { 'content-type': 'application/json' } })), caught => caught === error);
});
