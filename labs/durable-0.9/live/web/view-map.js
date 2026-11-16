/** Explicit optional external navigation. This module performs no requests or geocoding. */
export function buildMapUrl(point) {
  const latitude = point?.latitude, longitude = point?.longitude;
  if (!Number.isFinite(latitude) || Math.abs(latitude) > 90 || !Number.isFinite(longitude) || Math.abs(longitude) > 180) throw new Error('Valid coordinates are required');
  const url = new URL('https://www.google.com/maps/search/');
  url.searchParams.set('api', '1');
  url.searchParams.set('query', `${latitude.toFixed(6)},${longitude.toFixed(6)}`);
  return url.href;
}

export function mapDisclosure(point, { ageText, fixTimeText }) {
  buildMapUrl(point); // Validate only. No URL is inserted into the document and no request occurs.
  return [
    { tag: 'h2', text: 'Open Google Maps?', style: '' },
    { tag: 'p', text: 'Google will receive the chosen last-known coordinates and device/network data, and may retain records under its own privacy policy.', style: 'warning' },
    { tag: 'p', text: 'This opens an external map for one location snapshot. It is not live tracking and does not establish the person’s current position or safety.', style: '' },
    { tag: 'p', text: `Selected latitude: ${point.latitude.toFixed(6)}\nSelected longitude: ${point.longitude.toFixed(6)}`, style: 'field' },
    { tag: 'p', text: `Fix time (UTC, supplied by device): ${fixTimeText}\nFix age when these options opened: ${ageText}\nEstimated horizontal accuracy: ${point.accuracyM} meters radius, not a guaranteed boundary.`, style: 'field' },
    { tag: 'p', text: 'Street address unavailable; coordinates are a device estimate. No automatic address lookup or geocoding is performed.', style: '' },
    { tag: 'p', text: 'The map URL contains coordinates only. The Safe Bridge viewing token, share identifier and generation are excluded. No page referrer is sent by this action; browser and provider records remain outside Safe Bridge’s control.', style: '' },
    { tag: 'p', text: 'Choose Open only if sending this snapshot to Google fits your situation. Choose Cancel to stay here. Google Maps and Google’s privacy policy apply.', style: '' }
  ];
}
