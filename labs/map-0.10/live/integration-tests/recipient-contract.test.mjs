import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createSafeBridgeServer } from '../backend/server.mjs';
import { readBoundedJson } from '../web/view-network.js';
import { initialState, validatePacket, received, failed, view, end } from '../web/view-state.js';

const secret=()=>randomBytes(32).toString('base64url');
async function laboratory(run){
  let clock=1_800_000_000_000;
  const app=createSafeBridgeServer({development:true,allowAnonymousCreation:true,clock:()=>clock,createsPerWindow:200});
  await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
  const origin=`http://127.0.0.1:${app.server.address().port}`;
  try{
    const config=await readBoundedJson(await fetch(origin+'/v1/config'));
    const credentials={read:secret(),write:secret(),revoke:secret()};
    const createBody={generation:config.generation,createPermit:config.createPermit,creationKey:randomBytes(16).toString('base64url'),readToken:credentials.read,revokeToken:credentials.revoke,durationSeconds:0,requestedExpiresAtMs:0,admissionTicket:null};
    const created=await readBoundedJson(await fetch(origin+'/v1/shares',{method:'POST',headers:{Authorization:`Bearer ${credentials.write}`,'Content-Type':'application/json'},body:JSON.stringify(createBody)}));
    const expected={id:created.id,generation:created.generation};
    const headers=token=>({Authorization:`Bearer ${token}`,'X-SafeBridge-Generation':created.generation});
    const read=async()=>validatePacket(await readBoundedJson(await fetch(origin+`/v1/shares/${created.id}/latest`,{headers:headers(credentials.read)})),expected);
    const put=async(sequence=1)=>fetch(origin+`/v1/shares/${created.id}/latest`,{method:'PUT',headers:{...headers(credentials.write),'Content-Type':'application/json'},body:JSON.stringify({sequence,latitude:0.125,longitude:-0.25,accuracyM:55,sampleTimeMs:clock,sampleAgeMs:2000,approximate:true,mock:true})});
    await run({app,origin,credentials,created,expected,headers,read,put,advance:ms=>clock+=ms});
  }finally{await app.close();}
}

test('actual HTTP waiting packet flows through production bounded reader and recipient state',async()=>laboratory(async({read})=>{
  const packet=await read();const state=received(initialState(),packet,1000,20);const shown=view(state,1000);
  assert.equal(shown.phase,'waiting');assert.equal(shown.point,null);
}));
test('actual upload reaches recipient with reported age, accuracy, approximation and mock warning inputs',async()=>laboratory(async({put,read})=>{
  assert.equal((await put()).status,200);const packet=await read();const state=received(initialState(),packet,1000,20);const shown=view(state,1000);
  assert.equal(shown.phase,'recent');assert.equal(shown.point.latitude,0.125);assert.equal(shown.point.longitude,-0.25);
  assert.equal(shown.point.accuracyM,55);assert.equal(shown.point.approximate,true);assert.equal(shown.point.mock,true);assert.equal(shown.ageMs,2020);
}));
test('server age and recipient transit advance stale state without inventing movement',async()=>laboratory(async({put,read,advance})=>{
  await put();advance(31000);const packet=await read();const shown=view(received(initialState(),packet,1000,100),1000);
  assert.equal(shown.phase,'stale');assert.equal(shown.ageMs,33100);assert.equal(shown.point.latitude,0.125);
}));
test('real Stop clears recipient state and rejects a late pre-Stop packet',async()=>laboratory(async({put,read,origin,created,credentials,headers})=>{
  await put();const before=await read();let state=received(initialState(),before,1000);
  const stopped=await fetch(origin+`/v1/shares/${created.id}`,{method:'DELETE',headers:headers(credentials.revoke)});assert.equal(stopped.status,200);
  state=received(state,await read(),2000);assert.equal(view(state,2000).phase,'stopped');assert.equal(view(state,2000).point,null);
  assert.equal(view(received(state,before,3000),3000).phase,'stopped');assert.equal((await put(2)).status,409);
}));
test('actual outage preserves labeled last-known point and same session resumes after data TTL',async()=>laboratory(async({put,read,advance})=>{
  await put();let state=received(initialState(),await read(),1000);
  const offline=view(failed(state),1001);assert.equal(offline.phase,'last_known');assert.equal(offline.point.latitude,0.125);assert.equal(offline.ageMs,2001);
  advance(91000);const paused=await read();assert.equal(paused.state,'paused');assert.equal(paused.point,null);state=received(failed(state),paused,92000);assert.equal(view(state,92000).phase,'last_known');assert.equal(view(state,92000).ageMs,93000);assert.equal(view(state,92000).untilStopped,true);
  assert.equal((await put(2)).status,200);state=received(state,await read(),93000);assert.equal(view(state,93000).phase,'recent');assert.equal(view(state,93000).ageMs,2000);
}));
test('actual static page/assets have restrictive headers and no app/account dependency',async()=>laboratory(async({origin})=>{
  for(const path of ['/view','/view.js','/view-deck.js','/view-state.js','/view-network.js','/view-map.js','/view.css']){
    const response=await fetch(origin+path);assert.equal(response.status,200,path);assert.match(response.headers.get('cache-control'),/no-store/);
    assert.equal(response.headers.get('referrer-policy'),'no-referrer');assert.match(response.headers.get('content-security-policy'),/frame-ancestors 'none'/);
    const source=await response.text();assert.ok(source.length>0);
  }
}));
