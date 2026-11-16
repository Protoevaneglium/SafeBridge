import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtemp, chmod, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createSafeBridgeServer } from '../backend/server.mjs';
import { initializeDurableStore, openDurableStore } from '../backend/durable-store.mjs';
import { createPreparedGrant, validatePreparedGrantConfig } from '../backend/prepared-admission.mjs';
import { readBoundedJson } from '../web/view-network.js';
import { initialState, validatePacket, received, failed, reconnecting, view } from '../web/view-state.js';

// Independent actual-loopback HTTP tests. All keys, grants, coordinates and clocks
// below are synthetic; no hosted service, physical device or browser is involved.
const secret=()=>randomBytes(32).toString('base64url');
const endpoint='https://recipient-v3-fixture.invalid';
const latitude=12.345678,longitude=-45.678901;

async function laboratory(run){
  const directory=await mkdtemp(join(tmpdir(),'sb09-recipient-private-'));
  await chmod(directory,0o700);
  const stateKey=randomBytes(32);
  const installations=[createPreparedGrant({endpoint}),createPreparedGrant({endpoint})];
  const preparedGrants=validatePreparedGrantConfig({v:1,endpoint,grants:installations.map(x=>x.grant)},{endpoint,capacity:2});
  let now=1_900_000_000_000,store,app,port,origin;
  async function start(initial){
    store=initial?initializeDurableStore({directory,stateKey,capacity:2,preparedGrants,nowMs:now}):openDurableStore({directory,stateKey});
    app=createSafeBridgeServer({development:true,durableStore:store,clock:()=>now});
    await new Promise((resolve,reject)=>{app.server.once('error',reject);app.server.listen(port??0,'127.0.0.1',resolve);});
    port=app.server.address().port;origin='http://127.0.0.1:'+port;
  }
  async function call(path,{method='GET',token,generation,body}={}){
    const headers={Accept:'application/json'};
    if(token)headers.Authorization='Bearer '+token;
    if(generation)headers['X-SafeBridge-Generation']=generation;
    if(body!==undefined)headers['Content-Type']='application/json';
    const response=await fetch(origin+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),redirect:'error'});
    const value=await readBoundedJson(response);
    return {status:response.status,value};
  }
  function expect(reply,status,operation){assert.equal(reply.status,status,operation+' HTTP status');return reply.value;}
  async function create(installation=installations[0],durationSeconds=0){
    const config=expect(await call('/v1/config'),200,'config');
    assert.equal(config.protocolVersion,3);assert.equal(config.durableSessionState,true);assert.equal(config.preparedAdmission,true);
    const creationKey=randomBytes(16).toString('base64url'),credentials={write:secret(),read:secret(),revoke:secret()};
    const ticket=expect(await call('/v1/admission',{method:'POST',token:installation.bundle.preparedToken,generation:config.generation,body:{slotId:installation.bundle.slotId,creationKey,expectedSlotEpoch:null}}),200,'prepared ticket');
    const requestedExpiresAtMs=durationSeconds===0?0:config.serverTimeMs+durationSeconds*1000;
    const body={generation:config.generation,createPermit:config.createPermit,creationKey,readToken:credentials.read,revokeToken:credentials.revoke,durationSeconds,admissionTicket:ticket.admissionTicket,requestedExpiresAtMs};
    const reply=await call('/v1/shares',{method:'POST',token:credentials.write,body});
    assert.ok([200,201].includes(reply.status),'create admitted prepared session');
    const made=reply.value;
    return {id:made.id,generation:made.generation,credentials,installation,created:made,readLink:origin+`/view#s=${made.id}&g=${made.generation}&r=${credentials.read}`};
  }
  const headers=(session,role)=>({token:session.credentials[role],generation:session.generation});
  async function read(session){
    const packet=expect(await call(`/v1/shares/${session.id}/latest`,headers(session,'read')),200,'read');
    return {raw:packet,normalized:validatePacket(packet,session)};
  }
  async function put(session,sequence,changes={}){
    return call(`/v1/shares/${session.id}/latest`,{method:'PUT',...headers(session,'write'),body:{sequence,latitude,longitude,accuracyM:125,sampleTimeMs:now,sampleAgeMs:1000,approximate:true,mock:true,...changes}});
  }
  async function restart(){await app.close();app=null;await start(false);}
  try{await start(true);await run({create,read,put,call,headers,restart,installations,directory,get origin(){return origin;},get now(){return now;},advance(ms){now+=ms;},snapshot(){return store.load();},expect});}
  finally{if(app)await app.close();else store?.close();stateKey.fill(0);await rm(directory,{recursive:true,force:true});}
}

function checked(state,packet,mono){return received(state,packet.normalized,mono,0);}
function assertWiped(state){assert.equal(state.point,null);assert.equal(state.pointClock,null);assert.equal(state.clock,null);}

test('V3 restart: same read link resumes paused, tab RAM remains unverified, then a fresh upload updates the same link',async()=>laboratory(async h=>{
  const session=await h.create();h.expect(await h.put(session,1),200,'initial fix');
  const before=await h.read(session);let state=checked(initialState(),before,1000);
  assert.equal(view(state,1000).phase,'recent');
  state=failed(state);const link=session.readLink;
  h.advance(1200);await h.restart();
  const config=h.expect(await h.call('/v1/config'),200,'restarted config');assert.equal(config.generation,session.generation);
  assert.equal(h.origin+`/view#s=${session.id}&g=${session.generation}&r=${session.credentials.read}`,link);
  const paused=await h.read(session);assert.equal(paused.raw.state,'paused');assert.equal(paused.raw.point,null);
  assert.equal(paused.raw.id,session.id);assert.equal(paused.raw.generation,session.generation);
  state=checked(reconnecting(state),paused,2200);
  assert.equal(view(state,2200).phase,'last_known');assert.equal(view(state,2200).ageMs,2200);
  assert.equal(view(state,2200).point.latitude,latitude);assert.equal(view(state,2200).point.mock,true);
  h.expect(await h.put(session,2,{latitude:0,longitude:0}),200,'fresh resumed fix');
  state=checked(state,await h.read(session),2300);
  assert.equal(view(state,2300).phase,'recent');assert.equal(view(state,2300).point.latitude,0);assert.equal(view(state,2300).point.longitude,0);
}));

test('V3 restart: a new recipient tab cannot recover the old RAM-only point from durable storage',async()=>laboratory(async h=>{
  const session=await h.create();h.expect(await h.put(session,1),200,'initial fix');
  await h.restart();const packet=await h.read(session),state=checked(initialState(),packet,5000);
  assert.equal(view(state,5000).phase,'paused');assert.equal(state.point,null);assert.equal(state.pointClock,null);
  assert.equal(view(state,5000).untilStopped,true);
  assert.equal('lastSequence' in packet.raw,false,'management sequence is not exposed in recipient packet');
  for(const key of ['slotId','preparedToken','writeToken','revokeToken'])assert.equal(key in packet.raw,false);
}));

test('V3 durable metadata: accepted coordinates and raw recipient/sender capabilities do not enter the semantic DB snapshot',async()=>laboratory(async h=>{
  const session=await h.create();h.expect(await h.put(session,1),200,'initial fix');
  const snapshot=h.snapshot(),json=JSON.stringify(snapshot);
  const forbidden=new Set(['latitude','longitude','accuracyM','point','sampleTimeMs','sampleAgeMs','receivedAtMs']);
  const walk=value=>{if(value&&typeof value==='object')for(const [key,child]of Object.entries(value)){assert.ok(!forbidden.has(key),'no coordinate payload in durable schema');walk(child);}};
  walk(snapshot);
  for(const canary of [String(latitude),String(longitude),...Object.values(session.credentials),session.installation.bundle.preparedToken])assert.ok(!json.includes(canary),'raw position/capability absent from snapshot');
  const bytes=await readFile(join(h.directory,'control.sqlite'));
  for(const canary of [String(latitude),String(longitude),...Object.values(session.credentials)])assert.equal(bytes.includes(Buffer.from(canary)),false,'plaintext canary absent from DB');
  await h.restart();assert.equal((await h.read(session)).raw.point,null);
}));

test('V3 until-stop restart: prolonged downtime does not secretly expire a reserved link',async()=>laboratory(async h=>{
  const session=await h.create();h.expect(await h.put(session,1),200,'initial fix');let state=checked(initialState(),await h.read(session),1000);
  const elapsed=7*24*60*60*1000;h.advance(elapsed);await h.restart();
  const packet=await h.read(session);assert.equal(packet.raw.state,'paused');assert.equal(packet.raw.expiresAtMs,0);
  state=checked(failed(state),packet,1000+elapsed);
  assert.equal(state.terminal,null);assert.equal(view(state,1000+elapsed).phase,'last_known');assert.equal(view(state,1000+elapsed).untilStopped,true);
  assert.ok(view(state,1000+elapsed).ageMs>=elapsed);
}));

test('V3 Stop durability: confirmed Stop survives restart and clears the recipient prior RAM point',async()=>laboratory(async h=>{
  const session=await h.create();h.expect(await h.put(session,1),200,'initial fix');const before=await h.read(session);let state=checked(initialState(),before,1000);
  h.expect(await h.call(`/v1/shares/${session.id}`,{method:'DELETE',...h.headers(session,'revoke')}),200,'Stop');
  await h.restart();state=checked(state,await h.read(session),2000);
  assert.equal(view(state,2000).phase,'stopped');assertWiped(state);
  assert.equal(checked(state,before,3000),state,'late old active response cannot resurrect view');
  assert.equal((await h.put(session,2)).status,409,'terminal session rejects further fixes');
}));

test('V3 chosen expiry: restart preserves the selected end and does not create a new viewing window',async()=>laboratory(async h=>{
  const session=await h.create(undefined,5);h.expect(await h.put(session,1),200,'initial fix');let state=checked(initialState(),await h.read(session),1000);
  const end=session.created.expiresAtMs;h.advance(5000);await h.restart();
  const packet=await h.read(session);assert.equal(packet.raw.state,'expired');assert.equal(packet.raw.expiresAtMs,end);
  state=checked(state,packet,6000);assert.equal(view(state,6000).phase,'expired');assertWiped(state);
}));

test('V3 own-slot reset: only that old link ends; another prepared installation survives restart paused',async()=>laboratory(async h=>{
  const first=await h.create(h.installations[0]),second=await h.create(h.installations[1]);
  h.expect(await h.put(first,1),200,'first fix');h.expect(await h.put(second,1,{latitude:-1,longitude:2}),200,'second fix');
  let firstState=checked(initialState(),await h.read(first),1000),secondState=checked(initialState(),await h.read(second),1000);
  const reset=h.expect(await h.call('/v1/admission/current',{method:'DELETE',token:first.installation.bundle.preparedToken,generation:first.generation,body:{slotId:first.installation.bundle.slotId}}),200,'own-slot reset');
  assert.equal(reset.state,'stopped');await h.restart();
  firstState=checked(firstState,await h.read(first),2000);assert.equal(view(firstState,2000).phase,'stopped');assertWiped(firstState);
  const other=await h.read(second);assert.equal(other.raw.state,'paused');secondState=checked(secondState,other,2000);
  assert.equal(view(secondState,2000).phase,'last_known');assert.equal(view(secondState,2000).point.latitude,-1);assert.equal(secondState.terminal,null);
}));
