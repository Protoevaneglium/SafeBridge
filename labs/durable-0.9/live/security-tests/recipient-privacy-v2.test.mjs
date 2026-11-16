import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { initialState, validatePacket, received, failed, paused, reconnecting, advance, end, view } from '../web/view-state.js';

// Independent synthetic fixtures only. No real person, link, coordinate or recipient.
const scope = { id: 'u'.repeat(22), generation: 'v'.repeat(22) };
const secrets = { readToken: 'W'.repeat(43), writeToken: 'X'.repeat(43), revokeToken: 'Y'.repeat(43) };
const serverTime = 10_000_000;
const fixture = (overrides = {}) => ({ ...scope, protocolVersion: 2, state: 'active', serverTimeMs: serverTime, expiresAtMs: 0, leaseUntilMs: serverTime + 90_000, freshness: 'recent', pointAgeMs: 5_000, point: { sequence: 7, latitude: 12.345678, longitude: -45.678901, accuracyM: 125, sampleTimeMs: serverTime - 5_000, sampleAgeMs: 0, receivedAtMs: serverTime - 4_000, approximate: true, mock: true, address: 'Unverified address must never be displayed', ...secrets, id: scope.id, generation: scope.generation }, ...overrides });
const active = (overrides = {}, mono = 1_000, transit = 0) => received(initialState(), validatePacket(fixture(overrides), scope), mono, transit);
const packet = overrides => validatePacket(fixture(overrides), scope);

function assertWiped(state) {
  assert.equal(state.point, null, 'point erased');
  assert.equal(state.pointClock, null, 'point age/sample state erased');
  assert.equal(state.clock, null, 'validation/deadline state erased');
  const serialized = JSON.stringify(state);
  for (const value of ['12.345678', '-45.678901', 'Unverified address', ...Object.values(secrets)]) assert.ok(!serialized.includes(value), 'sensitive canary absent from terminal model');
}

test('V2 memory: normalized points discard credentials, identity and unverified address fields', () => {
  const normalized = validatePacket(fixture(), scope);
  const state = received(initialState(), normalized, 1_000);
  assert.deepEqual(Object.keys(normalized.point).sort(), ['accuracyM','approximate','latitude','longitude','mock','sampleTimeMs'].sort());
  const serialized = JSON.stringify(state);
  for (const value of [...Object.values(secrets),scope.id,scope.generation,'Unverified address']) assert.ok(!serialized.includes(value));
});

test('V2 memory: failed fetch keeps last-known tab state, displays growing age, never calls it current', () => {
  const original = active();
  const offline = failed(original);
  assert.equal(offline.point.latitude, 12.345678);
  assert.equal(offline.pointClock, original.pointClock);
  const projected = view(offline, 21_000);
  assert.equal(projected.phase, 'last_known');
  assert.equal(projected.ageMs, 25_000);
  assert.equal(projected.point.sampleTimeMs, serverTime - 5_000);
  assert.equal(offline.terminal, null);
});

test('V2 memory: hidden tab suppresses displayed coordinates but retains RAM; resuming remains unverified', () => {
  const hidden = paused(active());
  assert.equal(hidden.point.longitude, -45.678901);
  assert.equal(view(hidden, 11_000).point, null);
  assert.equal(view(hidden, 11_000).phase, 'hidden');
  const resumed = reconnecting(hidden);
  assert.equal(view(resumed, 21_000).phase, 'last_known');
  assert.equal(view(resumed, 21_000).ageMs, 25_000);
  assert.equal(view(resumed, 21_000).point.latitude, 12.345678);
});

test('V2 memory: server paused cannot reset last-known age or fabricate a fresh position', () => {
  const original = active();
  const resumedPacket = packet({ state:'paused', serverTimeMs:serverTime+20_000, leaseUntilMs:0, point:null, pointAgeMs:null, freshness:'paused' });
  const first = received(original, resumedPacket, 21_000);
  const second = received(first, { ...resumedPacket, serverTimeMs:serverTime+40_000 }, 41_000);
  assert.equal(view(second, 42_000).phase, 'last_known');
  assert.equal(view(second, 42_000).ageMs, 46_000);
  assert.equal(second.pointClock, original.pointClock);
  const withoutPrior = received(initialState(), resumedPacket, 21_000);
  assert.equal(view(withoutPrior, 21_000).point, null);
  assert.equal(view(withoutPrior, 21_000).phase, 'paused');
});

test('V2 memory: a verified fresh response can replace the old point after reconnect', () => {
  const offline = reconnecting(failed(active()));
  const newer = fixture();
  newer.point = { ...newer.point, latitude:0,longitude:0,sampleTimeMs:serverTime+19_500 };
  newer.serverTimeMs += 20_000; newer.pointAgeMs = 500;
  const next = received(offline, validatePacket(newer, scope), 21_000);
  assert.equal(view(next, 21_000).phase, 'recent');
  assert.equal(view(next, 21_000).point.latitude, 0);
  assert.equal(view(next, 21_500).ageMs, 1_000);
});

test('V2 until-stop: lease/revalidation loss never invents a chosen expiry or Stop', () => {
  const state = active({ expiresAtMs:0,leaseUntilMs:serverTime+1_000 });
  const aged = advance(state, 86_401_000);
  assert.equal(aged.terminal, null);
  assert.equal(view(aged, 86_401_000).phase, 'last_known');
  assert.equal(view(aged, 86_401_000).untilStopped, true);
  assert.equal(view(aged, 86_401_000).remainingMs, null);
  assert.equal(view(aged, 86_401_000).ageMs, 86_405_000);
});

test('V2 terminal: known immutable chosen deadline clears during an outage, including conservative transit', () => {
  const state = failed(active({ expiresAtMs:serverTime+10_000 }, 1_000, 1_000));
  assert.equal(view(state, 9_999).phase, 'last_known');
  assert.equal(view(state, 9_999).point.latitude, 12.345678);
  const expired = advance(state, 10_000);
  assert.equal(view(expired, 10_000).phase, 'expired');
  assertWiped(expired);
});

test('V2 terminal: chosen deadline clears even while the page is hidden', () => {
  const hidden = paused(active({ expiresAtMs:serverTime+5_000 }));
  const expired = advance(hidden, 6_000);
  assert.equal(expired.terminal, 'expired');
  assertWiped(expired);
});

test('V2 terminal: confirmed Stop/expiry removes cached data, even if server incorrectly includes a point', () => {
  for (const terminal of ['stopped','expired']) {
    const result = received(failed(active()), packet({ state:terminal }), 2_000);
    assert.equal(result.terminal, terminal);
    assertWiped(result);
  }
});

test('V2 terminal: manual Clear and permanent unavailability wipe all point state and cannot resurrect', () => {
  for (const cleared of [end(active(), 'cleared'), failed(active(),'invalid',true)]) {
    assertWiped(cleared);
    assert.equal(received(cleared, packet({}), 4_000), cleared);
    assert.equal(reconnecting(cleared), cleared);
    assert.equal(paused(cleared), cleared);
  }
});

test('V2 map: helper creates only an HTTPS Google Maps coordinate URL with no capabilities', async () => {
  const { buildMapUrl } = await import('../web/view-map.js');
  const raw = fixture().point;
  const url = new URL(buildMapUrl(raw));
  assert.equal(url.protocol,'https:'); assert.equal(url.hostname,'www.google.com');
  assert.equal(url.pathname,'/maps/search/');
  assert.equal(url.username,''); assert.equal(url.password,'');
  for (const key of url.searchParams.keys()) assert.ok(['api','query'].includes(key), 'no identity or credential query fields');
  assert.equal(url.searchParams.get('api'),'1');
  const [latitude,longitude]=url.searchParams.get('query').split(',').map(Number);
  assert.equal(latitude,raw.latitude); assert.equal(longitude,raw.longitude);
  assert.equal(url.hash,'');
  for (const value of [...Object.values(secrets),scope.id,scope.generation,'Unverified address']) assert.ok(!url.href.includes(value));
  assert.ok(!/[?&#](?:id|s|g|r|token|key|generation|readToken|writeToken|revokeToken)=/i.test(url.href));
});

test('V2 map: invalid or coercible coordinates cannot produce an external destination', async () => {
  const { buildMapUrl } = await import('../web/view-map.js');
  for (const coordinates of [{latitude:91,longitude:0},{latitude:0,longitude:181},{latitude:NaN,longitude:0},{latitude:0,longitude:Infinity},{latitude:'12.3',longitude:4},{latitude:null,longitude:4},null]) {
    assert.throws(()=>buildMapUrl(coordinates));
  }
  assert.equal(new URL(buildMapUrl({latitude:0,longitude:0,url:'javascript:bad',provider:'https://attacker.invalid/'})).hostname,'www.google.com');
});

// Wiring/source checks complement the pure state tests; these do not claim real browser behavior.
test('V2 privacy wiring: receiver code contains no storage, automatic geolocation or geocoding APIs', async () => {
  const names=['view.js','view-map.js','view-state.js','view-network.js'];
  const files=await Promise.all(names.map(name=>readFile(new URL('../web/'+name,import.meta.url),'utf8')));
  const code=files.join('\n').replace(/\/\*[\s\S]*?\*\//g,'').replace(/^\s*\/\/.*$/gm,'');
  assert.ok(!/\b(?:localStorage|sessionStorage|indexedDB)\s*[.[]/.test(code));
  assert.ok(!/navigator\s*\.\s*geolocation/.test(code));
  assert.ok(!/nominatim|reverseGeocode|maps\.googleapis/i.test(code));
  assert.ok(!/fetch\s*\(\s*['"]https?:/i.test(code),'no automatic external fetch');
});


test('V2 terminal defense: revalidation cannot extend or erase an earlier known chosen deadline', () => {
  const first = active({ expiresAtMs:serverTime+10_000 });
  for (const revalidatedExpiry of [serverTime+10_000,serverTime+60_000,0]) {
    const revalidated = received(first, packet({ expiresAtMs:revalidatedExpiry, pointAgeMs:0 }), 9_000);
    assertWiped(advance(revalidated, 11_000));
    assert.equal(view(revalidated, 11_000).phase, 'expired');
  }
  const late = received(first, packet({ expiresAtMs:0,pointAgeMs:0 }), 12_000);
  assert.equal(late.terminal, 'expired'); assertWiped(late);
});

// Execute the production controller and deck with a small simulated DOM. This tests
// event/gating/data flows only; synthetic geometry is not a browser layout result.
let controllerRun = 0;
async function withController(replies, run) {
  const slots = new Map(), documentListeners = new Map(), windowListeners = new Map();
  const opens = [], requests = [], historyCalls = [], timers = new Map();
  let mono = 1_000, timerId = 0;
  class Node {
    constructor(tag='div',id='') { this.tagName=tag.toUpperCase();this.id=id;this.textContent='';this.className='';this.dataset={};this.attributes={};this.children=[];this.parentElement=null;this.disabled=false;this.hidden=false;this.listeners=new Map();this.clientWidth=400;this.clientHeight=100; }
    setAttribute(k,v){this.attributes[k]=String(v);}
    getAttribute(k){return this.attributes[k]??null;}
    addEventListener(kind,fn){const list=this.listeners.get(kind)||[];list.push(fn);this.listeners.set(kind,list);}
    dispatch(kind,force=false){if(kind==='click'&&this.disabled&&!force)return;for(const fn of this.listeners.get(kind)||[])fn({target:this,preventDefault(){}});}
    insertBefore(node,reference){node.remove();const index=reference?this.children.indexOf(reference):-1;this.children.splice(index<0?this.children.length:index,0,node);node.parentElement=this;return node;}
    append(...nodes){for(const node of nodes)this.insertBefore(node,null);}
    replaceChildren(...nodes){for(const old of [...this.children])old.remove();this.append(...nodes);}
    remove(){if(this.parentElement){const list=this.parentElement.children;const index=list.indexOf(this);if(index>=0)list.splice(index,1);this.parentElement=null;}}
    getBoundingClientRect(){return {width:400,height:40,top:0,left:0,right:400,bottom:40};}
    focus(){doc.activeElement=this;}
    contains(node){return node===this||this.children.some(child=>child.contains(node));}
    closest(){const field=new Node('div');const title=new Node('dt');title.textContent=this.id;field.querySelector=selector=>selector==='dt'?title:this;return field;}
    querySelectorAll(){return [];}
  }
  const get=id=>{if(!slots.has(id))slots.set(id,new Node('div',id));return slots.get(id);};
  const doc={visibilityState:'visible',activeElement:null,getElementById:get,createElement:tag=>new Node(tag),querySelector:selector=>selector==='.status-card'?get('status-card'):null,querySelectorAll:()=>[],addEventListener(kind,fn){const list=documentListeners.get(kind)||[];list.push(fn);documentListeners.set(kind,list);}};
  const html=await readFile(new URL('../web/index.html',import.meta.url),'utf8');
  for(const match of html.matchAll(/<([a-z][a-z0-9]*)\b([^>]*\bid="([^"]+)"[^>]*)>([^<]*)/gi)){
    const node=get(match[3]);node.tagName=match[1].toUpperCase();node.textContent=match[4];node.hidden=/\bhidden\b/.test(match[2]);node.disabled=/\bdisabled\b/.test(match[2]);
  }
  const win={addEventListener(kind,fn){const list=windowListeners.get(kind)||[];list.push(fn);windowListeners.set(kind,list);},open(url,target,features){opens.push({url,target,features});return {opener:null};}};
  const fakeFetch=(url,options={})=>{
    requests.push({url:String(url),options});const response=replies.shift()??{pending:true};
    if(response.error)return Promise.reject(new Error('Synthetic transport failure'));
    if(response.pending)return new Promise((resolve,reject)=>{options.signal?.addEventListener('abort',()=>reject(new Error('Synthetic abort')),{once:true});});
    return Promise.resolve(new Response(JSON.stringify(response.body),{status:response.status??200,headers:{'Content-Type':'application/json'}}));
  };
  const replacement={document:doc,window:win,location:{hash:`#s=${scope.id}&g=${scope.generation}&r=${secrets.readToken}`,protocol:'https:',hostname:'receiver.example.invalid',pathname:'/view',search:'',origin:'https://receiver.example.invalid'},history:{replaceState(...args){historyCalls.push(args);}},performance:{now:()=>mono},fetch:fakeFetch,ResizeObserver:class{observe(){}},getComputedStyle:()=>({paddingTop:'0',paddingBottom:'0',fontSize:'16px',lineHeight:'24px',fontFamily:'system-ui',letterSpacing:'normal'}),requestAnimationFrame:fn=>{fn();return 1;},setTimeout:(fn,delay)=>{const id=++timerId;timers.set(id,{fn,delay});return id;},clearTimeout:id=>timers.delete(id),setInterval:(fn,delay)=>{const id=++timerId;timers.set(id,{fn,delay,interval:true});return id;},clearInterval:id=>timers.delete(id)};
  const descriptors=new Map(Object.keys(replacement).map(name=>[name,Object.getOwnPropertyDescriptor(globalThis,name)]));
  const flush=async()=>{for(let i=0;i<4;i++)await new Promise(resolve=>setImmediate(resolve));};
  const api={get,doc,opens,requests,historyCalls,html,setNow:value=>{mono=value;},flush,tickIntervals:async()=>{for(const timer of [...timers.values()])if(timer.interval)timer.fn();await flush();},click:async(id,force=false)=>{get(id).dispatch('click',force);await flush();},visibility:async value=>{doc.visibilityState=value;for(const fn of documentListeners.get('visibilitychange')||[])fn();await flush();},finishDisclosure:async()=>{for(let i=0;!get('page-next').disabled&&i<40;i++){get('page-next').dispatch('click');await flush();}assert.equal(get('page-next').disabled,true,'synthetic disclosure reached its final page');}};
  try{
    for(const [name,value] of Object.entries(replacement))Object.defineProperty(globalThis,name,{configurable:true,writable:true,value});
    await import(new URL(`../web/view.js?independent-privacy-test=${++controllerRun}`,import.meta.url));await flush();
    await run(api);
  }finally{
    try{get('clear').dispatch('click',true);await flush();}catch{}
    timers.clear();
    for(const [name,descriptor] of descriptors){if(descriptor)Object.defineProperty(globalThis,name,descriptor);else delete globalThis[name];}
  }
}

function assertNoCoordinatesInVisibleNodes(api){
  assert.equal(api.get('position-card').hidden,true);
  for(const id of ['latitude','longitude','fix-age','accuracy','fix-time','checked-time'])assert.equal(api.get(id).textContent,'',id+' cleared');
  const visible=api.get('deck-content').children.map(node=>node.textContent).join('\n');
  assert.ok(!visible.includes('12.345678')&&!visible.includes('-45.678901'),'coordinate/disclosure text removed from displayed deck');
}

test('V2 simulated controller: no map request before paged disclosure and explicit final confirmation', async()=>withController([{body:fixture()}],async api=>{
  assert.equal(api.requests.length,1);assert.equal(api.opens.length,0);
  assert.ok(api.requests.every(request=>request.url.startsWith('/v1/shares/')));
  for(const request of api.requests){assert.equal(request.options.referrerPolicy,'no-referrer');assert.equal(request.options.credentials,'omit');assert.equal(request.options.redirect,'error');}
  assert.equal(api.historyCalls[0][2],'/view','fragment removed before fetch');
  await api.click('open-map');
  assert.equal(api.opens.length,0,'opening disclosure does not open a map');
  assert.equal(api.get('page-mode').textContent,'Cancel');
  assert.equal(api.get('open-map').disabled,true,'cannot confirm before the final synthetic page');
  await api.click('open-map',true);assert.equal(api.opens.length,0,'queued early confirmation is rejected');
  await api.finishDisclosure();
  assert.equal(api.get('open-map').disabled,false);
  await api.click('open-map');
  assert.equal(api.opens.length,1);
  const opened=api.opens[0],url=new URL(opened.url);
  assert.equal(url.hostname,'www.google.com');assert.equal(url.pathname,'/maps/search/');
  assert.equal(url.searchParams.get('query'),'12.345678,-45.678901');
  assert.equal(opened.target,'_blank');assert.match(opened.features,/(?:^|,)noopener(?:,|$)/);assert.match(opened.features,/(?:^|,)noreferrer(?:,|$)/);
  for(const canary of [...Object.values(secrets),scope.id,scope.generation])assert.ok(!opened.url.includes(canary));
  assert.equal(api.requests.length,1,'external map is not fetched or embedded by the page');
  assert.match(api.get('status-announcer').textContent,/opening is not confirmed/i);
}));

test('V2 simulated controller: Cancel, Clear and hidden-page transitions invalidate pending map consent', async()=>{
  for(const transition of ['cancel','clear','hidden'])await withController([{body:fixture()}],async api=>{
    await api.click('open-map');await api.finishDisclosure();
    if(transition==='cancel')await api.click('page-mode');
    else if(transition==='clear')await api.click('clear');
    else await api.visibility('hidden');
    await api.click('open-map',true);
    assert.equal(api.opens.length,0,'old disclosure cannot navigate after '+transition);
    if(transition!=='cancel')assertNoCoordinatesInVisibleNodes(api);
  });
});

test('V2 simulated controller: revalidated Stop/expiry wipe DOM and invalidate already-open consent', async()=>{
  for(const terminal of ['stopped','expired'])await withController([{body:fixture()},{body:fixture({state:terminal})}],async api=>{
    await api.click('open-map');await api.finishDisclosure();
    await api.click('refresh');
    assertNoCoordinatesInVisibleNodes(api);
    assert.equal(api.get('open-map').disabled,true);
    await api.click('open-map',true);assert.equal(api.opens.length,0);
    assert.match(api.get('status-label').textContent,terminal==='stopped'?/STOPPED/:/EXPIRED/);
  });
});

test('V2 simulated controller: offline display is last-known with age/time and no invented address', async()=>withController([{body:fixture()},{error:true}],async api=>{
  api.setNow(21_000);await api.click('refresh');
  assert.match(api.get('status-label').textContent,/LAST KNOWN.*UNVERIFIED/);
  assert.equal(api.get('latitude').textContent,'12.345678');
  assert.match(api.get('fix-age').textContent,/25 seconds.*unverified/i);
  assert.ok(api.get('fix-time').textContent.length>0);
  assert.match(api.get('street-address').textContent,/unavailable/i);
  assert.ok(!api.get('street-address').textContent.includes('Unverified address must never be displayed'));
  await api.visibility('hidden');assertNoCoordinatesInVisibleNodes(api);
  await api.visibility('visible');
  assert.match(api.get('status-label').textContent,/LAST KNOWN.*UNVERIFIED/);
  assert.equal(api.get('latitude').textContent,'12.345678');
  assert.equal(api.opens.length,0);
  assert.ok(api.requests.every(request=>request.url.startsWith('/v1/shares/')));
}));


test('V2 map: consent names the provider, snapshot age and transfer/privacy limits before navigation', async()=>{
  const { mapDisclosure }=await import('../web/view-map.js');
  const disclosure=mapDisclosure(fixture().point,{ageText:'2 hours old',fixTimeText:'synthetic UTC time'});
  const text=disclosure.map(part=>part.text).join('\n');
  assert.match(text,/Google/);assert.match(text,/device\/network/);assert.match(text,/may retain records/i);
  assert.match(text,/2 hours old/);assert.match(text,/synthetic UTC time/);assert.match(text,/not live tracking/i);
  assert.match(text,/street address unavailable/i);assert.match(text,/Cancel/);
  for(const value of [...Object.values(secrets),scope.id,scope.generation])assert.ok(!text.includes(value));
});

test('V2 actual HTTP assets: receiver and map helper are served with no-referrer/no-store and restrictive permissions', async()=>{
  const { createSafeBridgeServer }=await import('../backend/server.mjs');
  const app=createSafeBridgeServer({development:true,allowAnonymousCreation:true});
  await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+app.server.address().port;
  try{
    for(const path of ['/view','/view-map.js','/view-state.js','/view-deck.js','/view.js']){
      const response=await fetch(origin+path);assert.equal(response.status,200,path);
      assert.equal(response.headers.get('referrer-policy'),'no-referrer');assert.match(response.headers.get('cache-control'),/no-store/);
      assert.match(response.headers.get('content-security-policy'),/connect-src 'self'/);
      assert.match(response.headers.get('content-security-policy'),/frame-ancestors 'none'/);
      assert.match(response.headers.get('permissions-policy'),/geolocation=\(\)/);
      assert.equal(response.headers.get('set-cookie'),null);
      const body=await response.text();for(const value of [...Object.values(secrets),scope.id,scope.generation,'12.345678'])assert.ok(!body.includes(value),'static asset has no synthetic session/location');
      if(path==='/view'){
        assert.match(body,/<meta name="referrer" content="no-referrer">/);
        assert.ok(!/<(?:script|iframe|img|link)\b[^>]*(?:src|href)=["']https?:/i.test(body),'no automatic third-party assets');
      }
    }
  }finally{await app.close();}
});


test('V2 simulated controller: hidden chosen-expiry tick clears consent/state without polling', async()=>withController([{body:fixture({expiresAtMs:serverTime+5_000})}],async api=>{
  await api.click('open-map');await api.finishDisclosure();
  await api.visibility('hidden');assertNoCoordinatesInVisibleNodes(api);
  const before=api.requests.length;api.setNow(6_000);await api.tickIntervals();
  assertNoCoordinatesInVisibleNodes(api);assert.match(api.get('status-label').textContent,/EXPIRED/);
  await api.click('open-map',true);assert.equal(api.opens.length,0);
  assert.equal(api.requests.length,before,'hidden timer performs no network polling');
  await api.visibility('visible');assert.equal(api.requests.length,before,'expired credentials do not resume a request');
}));
