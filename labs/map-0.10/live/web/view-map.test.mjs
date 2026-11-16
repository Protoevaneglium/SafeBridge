import test from 'node:test';
import assert from 'node:assert/strict';
import { buildMapUrl, mapDisclosure } from './view-map.js';
const point = { latitude: 37.5, longitude: -122.25, accuracyM: 20, sampleTimeMs: 1700000000000 };
test('map URL is fixed Google Maps with coordinates only', () => {
  const url = new URL(buildMapUrl(point));
  assert.equal(url.origin, 'https://www.google.com'); assert.equal(url.pathname, '/maps/search/');
  assert.deepEqual([...url.searchParams.keys()], ['api','query']); assert.equal(url.searchParams.get('api'), '1'); assert.equal(url.searchParams.get('query'), '37.500000,-122.250000'); assert.equal(url.hash, '');
});
test('viewing credentials and arbitrary metadata never enter the map URL', () => {
  const url = buildMapUrl({ ...point, id: 'sensitive-share-identifier', generation: 'sensitive-generation', readToken: 'sensitive-read-token', arbitrary: 'secret' });
  for (const value of ['sensitive-share-identifier','sensitive-generation','sensitive-read-token','secret']) assert.equal(url.includes(value), false);
});
test('map URL rejects injection and invalid coordinate inputs without coercion', () => {
  for (const change of [{ latitude: 91 }, { longitude: -181 }, { latitude: '0&token=secret' }, { longitude: NaN }, { latitude: Infinity }]) assert.throws(() => buildMapUrl({ ...point, ...change }));
});
test('map disclosure covers last-known status, provider records and unavailable address', () => {
  const text = mapDisclosure(point, { ageText: '130 seconds', fixTimeText: '2023-11-14 22:13:20 UTC' }).map(part => part.text).join('\n');
  for (const term of ['Google','last-known','device/network','privacy policy','not live tracking','130 seconds','2023-11-14','20 meters','Street address unavailable','No automatic address lookup','viewing token','Choose Open','Choose Cancel']) assert.ok(text.includes(term), term);
});
