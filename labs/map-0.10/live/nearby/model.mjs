/** Pure nearby-place normalization. No network, storage, location acquisition or routing. */
export const CATEGORIES = Object.freeze(['police', 'hospital', 'wifi', 'public_phone']);
export function validPoint(p) { return p && Number.isFinite(p.latitude) && Math.abs(p.latitude)<=90 && Number.isFinite(p.longitude) && Math.abs(p.longitude)<=180; }
export function distanceM(a,b) { if(!validPoint(a)||!validPoint(b)) throw Error('Invalid location'); const r=Math.PI/180, d=(b.latitude-a.latitude)*r, l=(b.longitude-a.longitude)*r; const h=Math.sin(d/2)**2+Math.cos(a.latitude*r)*Math.cos(b.latitude*r)*Math.sin(l/2)**2; return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h))); }
export function makeQuery(point,radius=3000) { if(!validPoint(point)||!Number.isInteger(radius)||radius<100||radius>10000)throw Error('Invalid search area'); const near=`(around:${radius},${point.latitude.toFixed(5)},${point.longitude.toFixed(5)})`; return `[out:json][timeout:15][maxsize:4194304];(nwr[amenity=police]${near};nwr[amenity=hospital]${near};nwr[amenity=telephone]${near};nwr[internet_access=wlan]${near};);out center tags 500;`; }
export function normalize(payload,origin,radius=3000) {
 if(!validPoint(origin)||!Number.isFinite(radius)||radius<100||radius>10000)throw Error('Invalid search area');
 if(!payload||!Array.isArray(payload.elements)||payload.elements.length>500||payload.remark)throw Error('Incomplete or invalid provider response');
 const seen=new Set(), items=[];
 for(const e of payload.elements){if(!e||!['node','way','relation'].includes(e.type)||!Number.isSafeInteger(e.id))continue;const id=`${e.type}/${e.id}`;if(seen.has(id))continue;seen.add(id);const t=e.tags||{};if(t.access==='private'||t.access==='no'||t.disused==='yes'||t.abandoned==='yes')continue;const p={latitude:e.lat??e.center?.lat,longitude:e.lon??e.center?.lon};if(!validPoint(p))continue;const distance=distanceM(origin,p);if(distance>radius)continue;let categories=[];if(t.amenity==='police')categories.push('police');if(t.amenity==='hospital')categories.push('hospital');if(t.amenity==='telephone')categories.push('public_phone');if(t.internet_access==='wlan'&&!['private','no'].includes(t['internet_access:access']))categories.push('wifi');if(!categories.length)continue;
 const name=typeof t.name==='string'?t.name.slice(0,160):categories.map(x=>x.replace('_',' ')).join(', ');
 items.push({id,...p,name,categories,distanceM:distance,availability:'unverified',openingHours:typeof t.opening_hours==='string'?t.opening_hours.slice(0,160):null,fee:typeof t.fee==='string'?t.fee.slice(0,40):null,emergencyDepartment:t.emergency==='yes',source:'OpenStreetMap'});
 }
 items.sort((a,b)=>a.distanceM-b.distanceM||a.id.localeCompare(b.id));return {items,possiblyTruncated:payload.elements.length===500,categories:Object.fromEntries(CATEGORIES.map(c=>[c,items.filter(p=>p.categories.includes(c)).length]))};
}
/** Prevent a late location/query response restoring a map after Close or a newer search. */
export class SearchGeneration { current=0; begin(){return ++this.current;} cancel(){++this.current;} accepts(id){return id===this.current;} }
