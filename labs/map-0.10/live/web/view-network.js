/** Read one small JSON response without buffering an unbounded body. */
export const MAX_JSON_BYTES = 32_768;

export async function readBoundedJson(response) {
  if (!response?.body || typeof response.body.getReader !== 'function') throw new Error('A readable response stream is required');
  const reader = response.body.getReader();
  try {
    const contentType = response.headers?.get('content-type') || '';
    if (!/^application\/(?:[a-z0-9!#$&^_.+-]+\+)?json\s*(?:;|$)/i.test(contentType)) throw new Error('A JSON response is required');
    const contentLength = response.headers?.get('content-length');
    if (contentLength !== null && contentLength !== undefined && (!/^\d+$/.test(contentLength.trim()) || Number(contentLength) > MAX_JSON_BYTES)) throw new Error('Response is too large');
    const decoder = new TextDecoder('utf-8', { fatal: true });
    let byteLength = 0, text = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) throw new Error('Invalid response bytes');
      byteLength += value.byteLength;
      if (byteLength > MAX_JSON_BYTES) throw new Error('Response is too large');
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
    return JSON.parse(text);
  } catch (error) {
    // Includes UTF-8, parse, transport and AbortSignal errors. No text/json fallback.
    try { await reader.cancel(); } catch { /* Preserve the original failure. */ }
    throw error;
  } finally {
    try { reader.releaseLock(); } catch { /* The caller still treats the read as failed. */ }
  }
}
