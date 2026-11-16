import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createSafeBridgeServer} from '../server.mjs';
import {initializeDurableStore,openDurableStore,installPreparedGrants} from '../durable-store.mjs';
import {createPreparedGrant,validatePreparedGrantConfig} from '../prepared-admission.mjs';
const secret=(n=32)=>randomBytes(n).toString('base64url');
async function fixture(t,opts={}){
 const directory=await mkdtemp(join(tmpdir(),'safe-bridge-durable-backend-')),stateDirectory=join(directory,'state'),stateKey=randomBytes(32),endpoint='https://synthetic.invalid';
 const prepared=[createPreparedGrant({endpoint}),createPreparedGrant({endpoint})],config=validatePreparedGrantConfig({v:1,endpoint,grants:prepared.map(p=>p.grant)},{endpoint,capacity:2});let clock=1800000000000;
 let store=initializeDurableStore({directory:stateDirectory,stateKey,capacity:2,preparedGrants:config,nowMs:clock}),app,origin;
 async function listen(){app=createSafeBridgeServer({development:true,durableStore:store,clock:()=>clock,...opts});await new Promise(r=>app.server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+app.server.address().port;}
 await listen();t.after(async()=>{await app.close();await rm(directory,{recursive:true,force:true});});
 async function call(path,{method='GET',token,generation=app.generation,body,headers={}}={}){const h={'X-SafeBridge-Generation':generation,...headers};if(token)h.Authorization='Bearer '+token;if(body!==undefined)h['Content-Type']='application/json';const r=await fetch(origin+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});return{status:r.status,body:await r.json()};}
 async function admit(which,key=secret(16),epoch=null){const p=prepared[which].bundle,r=await call('/v1/admission',{method:'POST',token:p.preparedToken,body:{slotId:p.slotId,creationKey:key,expectedSlotEpoch:epoch}});return{key,...r};}
 async function create(which=0,duration=0){const cfg=(await call('/v1/config')).body,a=await admit(which);assert.equal(a.status,200);const w=secret(),r=secret(),x=secret(),body={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:a.key,readToken:r,revokeToken:x,durationSeconds:duration,requestedExpiresAtMs:duration===0?0:clock+duration*1000,admissionTicket:a.body.admissionTicket};const result=await call('/v1/shares',{method:'POST',token:w,body});assert.equal(result.status,201);return{w,r,x,body,admission:a.body,metadata:result.body,id:result.body.id};}
 return{directory,stateDirectory,stateKey,prepared,config,call,admit,create,get app(){return app;},get store(){return store;},get now(){return clock;},advance:n=>{clock+=n;},restart:async()=>{await app.close();store=openDurableStore({directory:stateDirectory,stateKey});await listen();}};
}
const sample=sequence=>({sequence,latitude:0,longitude:0,accuracyM:25,sampleTimeMs:1800000000000,sampleAgeMs:0,approximate:true,mock:true});
test('durable restart preserves generation, ID, consent and sequence but no point',async t=>{
 const f=await fixture(t),a=await f.create(),generation=f.app.generation;let r=await f.call('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:sample(7)});assert.equal(r.status,200);assert.equal(r.body.lastSequence,7);
 const saved=f.store.load();assert.equal(saved.sessions[0].sequence,7);assert.equal('point'in saved.sessions[0],false);assert.equal('updateDigest'in saved.sessions[0],false);
 await f.restart();assert.equal(f.app.generation,generation);r=await f.call('/v1/shares/'+a.id+'/latest',{token:a.r});assert.equal(r.body.state,'paused');assert.equal(r.body.point,null);assert.equal('lastSequence'in r.body,false);assert.equal('managementReceipt'in r.body,false);
 r=await f.call('/v1/shares/'+a.id+'/status',{token:a.w});assert.equal(r.body.lastSequence,7);assert.equal(r.body.expiresAtMs,0);
 r=await f.call('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:sample(8)});assert.equal(r.status,200);assert.equal(r.body.state,'active');
});
test('durable Stop survives restart and invalidates old slot ticket/epoch',async t=>{
 const f=await fixture(t),a=await f.create();let r=await f.call('/v1/shares/'+a.id,{method:'DELETE',token:a.x});assert.equal(r.body.state,'stopped');await f.restart();
 r=await f.call('/v1/shares/'+a.id+'/latest',{token:a.r});assert.equal(r.body.state,'stopped');r=await f.call('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:sample(1)});assert.equal(r.status,409);
 r=await f.admit(0,a.body.creationKey,a.admission.slotEpoch);assert.equal(r.status,409);assert.equal(r.body.error,'admission_epoch_changed');
 r=await f.call('/v1/shares',{method:'POST',token:a.w,body:a.body});assert.equal(r.status,200);assert.equal(r.body.id,a.id);assert.equal(r.body.state,'stopped');
});
test('paused orphan consumes only its own reserved slot; owner reset is isolated',async t=>{
 const f=await fixture(t),a=await f.create(0);await f.restart();f.advance(86400000*2);
 let r=await f.admit(0);assert.equal(r.status,409);assert.equal(r.body.error,'prepared_slot_active');const b=await f.create(1);assert.equal(b.metadata.state,'waiting_for_fix');
 const p=f.prepared[0].bundle;r=await f.call('/v1/admission/current',{method:'DELETE',token:p.preparedToken,body:{slotId:p.slotId}});assert.equal(r.status,200);assert.equal(r.body.state,'stopped');assert.equal('id'in r.body,false);
 r=await f.call('/v1/shares/'+b.id+'/latest',{token:b.r});assert.equal(r.body.state,'waiting_for_fix');const fresh=await f.create(0);assert.notEqual(fresh.id,a.id);
});
test('prepared admission/reset require exact generation, secret and pinned epoch',async t=>{
 const f=await fixture(t),a=await f.create(),p=f.prepared[0].bundle;
 for(const path of ['/v1/admission','/v1/admission/current']){const reset=path.endsWith('current'),body=reset?{slotId:p.slotId}:{slotId:p.slotId,creationKey:secret(16),expectedSlotEpoch:null};const r=await f.call(path,{method:reset?'DELETE':'POST',token:p.preparedToken,generation:secret(16),body});assert.equal(r.status,404);}
 let r=await f.call('/v1/admission/current',{method:'DELETE',token:f.prepared[1].bundle.preparedToken,body:{slotId:p.slotId}});assert.equal(r.status,404);assert.equal((await f.call('/v1/shares/'+a.id+'/status',{token:a.w})).body.state,'waiting_for_fix');
});
test('prepared history churn does not spend shared legacy nonce capacity',async t=>{
 const f=await fixture(t,{nonceCapacity:1,terminalCapacity:1,createsPerWindow:100});for(let i=0;i<5;i++){const a=await f.create(0);await f.call('/v1/shares/'+a.id,{method:'DELETE',token:a.x});}assert.equal(f.app.stats().spentTickets,0);const other=await f.create(1);assert.equal(other.metadata.state,'waiting_for_fix');
});
test('private add-only grant installation rejects live use and preserves existing state offline',async t=>{
 const f=await fixture(t),a=await f.create();const generation=f.app.generation,original=f.store.load();
 assert.throws(()=>installPreparedGrants(f.store,f.config),/offline/);assert.throws(()=>createSafeBridgeServer({development:true,durableStore:f.store}),/authoritative/);
 await f.app.close();const offline=openDurableStore({directory:f.stateDirectory,stateKey:f.stateKey});
 try{assert.throws(()=>installPreparedGrants(offline,{endpoint:f.config.endpoint,grants:new Map([[f.prepared[0].grant.slotId,Buffer.from(f.prepared[0].grant.verifier,'base64url')]])}),/remove/);
 const changed=new Map(f.config.grants);changed.set(f.prepared[0].grant.slotId,randomBytes(32));assert.throws(()=>installPreparedGrants(offline,{endpoint:f.config.endpoint,grants:changed}),/replace/);
 installPreparedGrants(offline,f.config);assert.equal(offline.load().metadata.generation,generation);assert.equal(offline.load().grants[0].epoch,original.grants[0].epoch);assert.equal(offline.load().sessions[0].id,a.id);}finally{offline.close();}
 const bytes=await readFile(join(f.stateDirectory,'control.sqlite'));for(const value of [a.w,a.r,a.x,a.body.creationKey,f.prepared[0].bundle.preparedToken])assert.equal(bytes.includes(Buffer.from(value)),false);
});
