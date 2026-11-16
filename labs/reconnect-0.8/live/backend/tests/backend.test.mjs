import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import { createSafeBridgeServer } from '../server.mjs';
import { issueAdmissionTicket, verifyAdmissionTicket } from '../admission.mjs';
const secret=(n=32)=>randomBytes(n).toString('base64url');
const point=(sequence=1)=>({sequence,latitude:0,longitude:0,accuracyM:50,sampleTimeMs:1_800_000_000_000,sampleAgeMs:0,approximate:true,mock:true});
async function fixture(t,opts={}){
  let time=1_800_000_000_000;
  const app=createSafeBridgeServer({development:true,allowAnonymousCreation:true,clock:()=>time,...opts});
  await new Promise(r=>app.server.listen(0,'127.0.0.1',r));
  t.after(()=>app.close());
  const base='http://127.0.0.1:'+app.server.address().port;
  async function request(path,{method='GET',token,generation=app.generation,body,headers={}}={}){
    const h={'X-SafeBridge-Generation':generation,...headers};if(token)h.Authorization='Bearer '+token;if(body!==undefined&&!h['Content-Type'])h['Content-Type']='application/json';
    const response=await fetch(base+path,{method,headers:h,body:body===undefined?undefined:typeof body==='string'?body:JSON.stringify(body)});
    const text=await response.text();let data;try{data=JSON.parse(text);}catch{data=text;}
    return {status:response.status,data,headers:response.headers,text};
  }
  async function creation(durationSeconds=300){
    const c=(await request('/v1/config')).data,w=secret(),r=secret(),x=secret(),creationKey=secret(16);
    const body={generation:c.generation,createPermit:c.createPermit,creationKey,readToken:r,revokeToken:x,durationSeconds,requestedExpiresAtMs:durationSeconds===0?0:time+durationSeconds*1000,admissionTicket:c.admissionRequired?issueAdmissionTicket({admissionKey:opts.admissionKey,generation:c.generation,nowMs:time,ttlSeconds:900}):null};
    const result=await request('/v1/shares',{method:'POST',token:w,body});assert.equal(result.status,201);
    return {w,r,x,body,meta:result.data,id:result.data.id};
  }
  return {app,base,request,creation,advance:ms=>{time+=ms;},now:()=>time};
}

test('requires TLS and exact HTTPS origin unless development is explicitly selected',()=>{assert.throws(()=>createSafeBridgeServer(),/TLS required/);for(const approvedOrigin of [undefined,'http://example.invalid','https://example.invalid/extra','https://user:pass@example.invalid','https://example.invalid?x=1'])assert.throws(()=>createSafeBridgeServer({tls:{key:'not-a-real-key',cert:'not-a-real-cert'},approvedOrigin}),/approvedOrigin/);});
test('config, security headers and no sensitive aggregate dimensions',async t=>{
  const f=await fixture(t),r=await f.request('/v1/config');assert.equal(r.status,200);assert.match(r.data.generation,/^[A-Za-z0-9_-]{22}$/);assert.equal(r.data.leaseMs,90000);assert.equal(r.data.maxDurationSeconds,2147483647);assert.equal(r.data.protocolVersion,2);assert.equal(r.data.admissionRequired,false);assert.equal(r.headers.get('referrer-policy'),'no-referrer');assert.match(r.headers.get('cache-control'),/no-store/);assert.equal(r.headers.get('access-control-allow-origin'),null);assert.equal(r.headers.get('set-cookie'),null);assert.match(r.headers.get('content-security-policy'),/frame-ancestors 'none'/);assert.deepEqual(Object.values(f.app.stats()).every(x=>typeof x==='number'),true);
});
test('scope/session/generation matrix has uniform 404 responses',async t=>{
  const f=await fixture(t),a=await f.creation(),b=await f.creation();
  const cases=[['GET','/latest',a.w],['GET','/latest',a.x],['PUT','/latest',a.r],['PUT','/latest',a.x],['DELETE','',a.r],['DELETE','',a.w],['GET','/status',a.r],['GET','/latest',b.r],['GET','/status',b.w]];
  for(const [method,end,token] of cases){const r=await f.request('/v1/shares/'+a.id+end,{method,token,body:method==='PUT'?point():undefined});assert.equal(r.status,404);assert.deepEqual(r.data,{error:'not_found'});}
  for(const [id,token,generation] of [[secret(16),a.r,f.app.generation],[a.id,secret(),f.app.generation],[a.id,a.r,secret(16)]]){const r=await f.request('/v1/shares/'+id+'/latest',{token,generation});assert.equal(r.status,404);assert.deepEqual(r.data,{error:'not_found'});}
});
test('latest-only point state, stale age, and duplicate update preserve deadlines',async t=>{
  const f=await fixture(t),a=await f.creation(),path='/v1/shares/'+a.id;
  let r=await f.request(path+'/latest',{token:a.r});assert.equal(r.data.freshness,'waiting');assert.equal(r.data.point,null);
  r=await f.request(path+'/latest',{method:'PUT',token:a.w,body:point()});assert.equal(r.status,200);const lease=r.data.leaseUntilMs;
  f.advance(31000);r=await f.request(path+'/latest',{token:a.r});assert.equal(r.data.freshness,'stale');assert.equal(r.data.pointAgeMs,31000);
  r=await f.request(path+'/latest',{method:'PUT',token:a.w,body:point()});assert.equal(r.status,200);assert.equal(r.data.leaseUntilMs,lease);
  r=await f.request(path+'/latest',{method:'PUT',token:a.w,body:{...point(),latitude:1}});assert.equal(r.status,409);assert.equal(r.data.error,'sequence_conflict');
  r=await f.request(path+'/latest',{method:'PUT',token:a.w,body:point(2)});assert.equal(r.status,200);assert.ok(r.data.leaseUntilMs>lease);
  r=await f.request(path+'/latest',{method:'PUT',token:a.w,body:point(1)});assert.equal(r.status,409);assert.equal(r.data.error,'out_of_order');
  r=await f.request(path+'/latest',{token:a.r});assert.equal(r.data.point.sequence,2);assert.equal(r.data.freshness,'recent');assert.equal('history' in r.data,false);
});
test('Stop is scoped, clears latest, is idempotent and defeats delayed PUT',async t=>{
  const f=await fixture(t),a=await f.creation(),path='/v1/shares/'+a.id;
  await f.request(path+'/latest',{method:'PUT',token:a.w,body:point()});
  const text=JSON.stringify(point(2));
  let resolveDelayed;
  const delayed=new Promise(r=>resolveDelayed=r);
  const req=http.request(f.base+path+'/latest',{method:'PUT',headers:{Authorization:'Bearer '+a.w,'X-SafeBridge-Generation':f.app.generation,'Content-Type':'application/json'}},res=>{let data='';res.on('data',x=>data+=x);res.on('end',()=>resolveDelayed({status:res.statusCode,data:JSON.parse(data)}));});
  req.write(text.slice(0,10));await new Promise(r=>setTimeout(r,25));
  let r=await f.request(path,{method:'DELETE',token:a.x});assert.equal(r.status,200);assert.equal(r.data.state,'stopped');assert.equal('point' in r.data,false);
  req.end(text.slice(10));r=await delayed;assert.equal(r.status,409);assert.equal(r.data.error,'session_ended');
  r=await f.request(path+'/latest',{token:a.r});assert.equal(r.data.state,'stopped');assert.equal(r.data.point,null);assert.equal(r.data.freshness,'ended');
  r=await f.request(path,{method:'DELETE',token:a.x});assert.equal(r.status,200);assert.equal(r.data.state,'stopped');
});
test('lease lapse pauses and resumes; hard expiry is terminal',async t=>{
  const f=await fixture(t),a=await f.creation(300),b=await f.creation(60);
  f.advance(60000);let r=await f.request('/v1/shares/'+b.id+'/latest',{token:b.r});assert.equal(r.data.state,'expired');assert.equal(r.data.point,null);
  f.advance(30000);r=await f.request('/v1/shares/'+a.id+'/latest',{token:a.r});assert.equal(r.data.state,'paused');assert.equal(r.data.point,null);assert.equal(r.data.freshness,'paused');
  r=await f.request('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:point()});assert.equal(r.status,200);assert.equal(r.data.state,'active');
});
test('creation replay immutable, terminal, and valid after permit expiry while retained',async t=>{
  const f=await fixture(t),a=await f.creation(300),expiry=a.meta.expiresAtMs;
  f.advance(61000);let r=await f.request('/v1/shares',{method:'POST',token:a.w,body:a.body});assert.equal(r.status,200);assert.equal(r.data.id,a.id);assert.equal(r.data.expiresAtMs,expiry);
  await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});r=await f.request('/v1/shares',{method:'POST',token:a.w,body:a.body});assert.equal(r.data.state,'stopped');assert.equal(r.data.expiresAtMs,expiry);
  r=await f.request('/v1/shares',{method:'POST',token:a.w,body:{...a.body,durationSeconds:600}});assert.equal(r.status,404);
});
test('purge and server restart reject old creation requests',async t=>{
  const f=await fixture(t,{tombstoneMs:1000}),a=await f.creation(60);f.advance(60000);await f.request('/v1/shares/'+a.id+'/status',{token:a.x});f.advance(1001);
  let r=await f.request('/v1/shares/'+a.id+'/status',{token:a.x});assert.equal(r.status,404);
  r=await f.request('/v1/shares',{method:'POST',token:a.w,body:a.body});assert.equal(r.status,400);assert.equal(r.data.error,'chosen_end_elapsed');
  const g=await fixture(t);r=await g.request('/v1/shares',{method:'POST',token:a.w,body:a.body});assert.equal(r.status,400);
});
test('strict point shape and bounded body/media without echoing data',async t=>{
  const f=await fixture(t),a=await f.creation(),path='/v1/shares/'+a.id+'/latest';
  for(const bad of [null,[],{...point(),extra:'CANARY_SECRET'},{...point(),latitude:91},{...point(),longitude:-181},{...point(),sampleAgeMs:30001},{...point(),sampleAgeMs:-1},{...point(),accuracyM:null},{...point(),sequence:1.2},{...point(),approximate:'yes'}]){const r=await f.request(path,{method:'PUT',token:a.w,body:bad});assert.equal(r.status,400);assert.equal(r.text.includes('CANARY_SECRET'),false);}
  let r=await f.request(path,{method:'PUT',token:a.w,body:'{"CANARY_SECRET":'});assert.equal(r.status,400);assert.equal(r.text.includes('CANARY_SECRET'),false);
  r=await f.request(path,{method:'PUT',token:a.w,body:'x'.repeat(4097)});assert.equal(r.status,413);
  for(const headers of [{'Content-Type':'application/jsonjunk'},{'Content-Encoding':'gzip'}]){r=await f.request(path,{method:'PUT',token:a.w,body:point(),headers});assert.equal(r.status,415);}
});
test('independent credentials, forged permits, duration bounds and capacity',async t=>{
  const f=await fixture(t,{capacity:1,tombstoneMs:1000}),a=await f.creation(60),c=(await f.request('/v1/config')).data;
  let r=await f.request('/v1/shares',{method:'POST',token:a.w,body:{...a.body,creationKey:secret(16),readToken:a.w}});assert.equal(r.status,400);
  r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{...a.body,creationKey:secret(16),readToken:secret(),revokeToken:secret(),createPermit:'forged'}});assert.equal(r.status,400);
  r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{generation:c.generation,createPermit:c.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:60,requestedExpiresAtMs:f.now()+60000,admissionTicket:null}});assert.equal(r.status,503);
  f.advance(61001);const b=await f.creation(60);assert.notEqual(b.id,a.id);
});
test('Host, Origin, query tokens and aggregate rate limiting',async t=>{
  const f=await fixture(t);let r=await new Promise((resolveResult,reject)=>{const request=http.get(f.base+'/v1/config',{headers:{Host:'evil.invalid'}},response=>{response.resume();response.on('end',()=>resolveResult({status:response.statusCode}));});request.on('error',reject);});assert.equal(r.status,403);
  r=await f.request('/v1/config',{headers:{Origin:'https://evil.invalid'}});assert.equal(r.status,403);
  r=await f.request('/v1/config?token=CANARY_SECRET');assert.equal(r.status,400);assert.equal(r.text.includes('CANARY_SECRET'),false);
  const g=await fixture(t,{ipPerMinute:2});assert.equal((await g.request('/v1/config')).status,200);assert.equal((await g.request('/v1/config')).status,200);assert.equal((await g.request('/v1/config')).status,429);assert.equal(g.app.stats().rateLimited,1);
});

test('anonymous creation requires explicit loopback test option',async t=>{
  assert.throws(()=>createSafeBridgeServer({development:true}),/admissionKey/);
  const f=await fixture(t,{allowAnonymousCreation:false,admissionKey:randomBytes(32)});
  const c=(await f.request('/v1/config')).data;assert.equal(c.admissionRequired,true);assert.equal(c.protocolVersion,2);
  const r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{generation:c.generation,createPermit:c.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:0,requestedExpiresAtMs:0,admissionTicket:null}});
  assert.equal(r.status,400);assert.equal(f.app.stats().activeSessions,0);assert.equal(f.app.stats().spentTickets,0);
});
test('duration zero stays open, positive one expires, and paused sessions resume same ID',async t=>{
  const f=await fixture(t),a=await f.creation(0),b=await f.creation(1),c=await f.creation(2147483647);
  assert.equal(a.meta.expiresAtMs,0);assert.equal(b.meta.expiresAtMs,f.now()+1000);assert.equal(c.meta.expiresAtMs,f.now()+2147483647000);
  await f.request('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:point()});f.advance(90001);
  let r=await f.request('/v1/shares/'+a.id+'/latest',{token:a.r});assert.equal(r.data.state,'paused');assert.equal(r.data.point,null);assert.equal(r.data.expiresAtMs,0);
  r=await f.request('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:point()});assert.equal(r.status,200);assert.equal(r.data.state,'paused');
  r=await f.request('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:point(2)});assert.equal(r.status,200);assert.equal(r.data.state,'active');assert.equal(r.data.id,a.id);
  r=await f.request('/v1/shares/'+b.id+'/latest',{token:b.r});assert.equal(r.data.state,'expired');
  await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});r=await f.request('/v1/shares/'+a.id+'/latest',{method:'PUT',token:a.w,body:point(3)});assert.equal(r.status,409);
});
test('terminal cache cannot lock active capacity for 24 hours',async t=>{
  const f=await fixture(t,{capacity:1,terminalCapacity:2,createsPerWindow:100});
  for(let i=0;i<8;i++){const a=await f.creation(0);assert.equal(f.app.stats().activeSessions,1);const r=await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});assert.equal(r.status,200);assert.equal(f.app.stats().activeSessions,0);assert.ok(f.app.stats().terminalSessions<=2);}
  const current=await f.creation(0);assert.equal(current.meta.state,'waiting_for_fix');assert.equal(f.app.stats().terminalSessions,2);
});
test('admission validates signature, generation and time before occupying active capacity',async t=>{
  const key=randomBytes(32),f=await fixture(t,{allowAnonymousCreation:false,admissionKey:key});
  const c=(await f.request('/v1/config')).data;
  for(const ticket of ['forged',issueAdmissionTicket({admissionKey:randomBytes(32),generation:c.generation,nowMs:f.now(),ttlSeconds:60}),issueAdmissionTicket({admissionKey:key,generation:secret(16),nowMs:f.now(),ttlSeconds:60}),issueAdmissionTicket({admissionKey:key,generation:c.generation,nowMs:f.now()+1,ttlSeconds:60}),issueAdmissionTicket({admissionKey:key,generation:c.generation,nowMs:f.now()-1001,ttlSeconds:1})]){
    const r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{generation:c.generation,createPermit:c.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:0,requestedExpiresAtMs:0,admissionTicket:ticket}});assert.equal(r.status,403);assert.equal(f.app.stats().activeSessions,0);assert.equal(f.app.stats().spentTickets,0);
  }
  assert.throws(()=>issueAdmissionTicket({admissionKey:key,generation:c.generation,nowMs:f.now(),ttlSeconds:3601}));
  const a=await f.creation(0);assert.equal(a.meta.state,'waiting_for_fix');assert.equal(f.app.stats().spentTickets,1);
});
test('spent ticket survives terminal eviction; retained identical retry survives ticket expiry',async t=>{
  const f=await fixture(t,{allowAnonymousCreation:false,admissionKey:randomBytes(32),terminalCapacity:1});
  const a=await f.creation(0);await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});
  const b=await f.creation(0);await f.request('/v1/shares/'+b.id,{method:'DELETE',token:b.x});
  const config=(await f.request('/v1/config')).data;
  let r=await f.request('/v1/shares',{method:'POST',token:a.w,body:{...a.body,createPermit:config.createPermit}});assert.equal(r.status,403);assert.equal(r.data.error,'admission_ticket_spent');
  f.advance(900001);r=await f.request('/v1/shares',{method:'POST',token:b.w,body:b.body});assert.equal(r.status,200);assert.equal(r.data.id,b.id);assert.equal(r.data.state,'stopped');
});
test('capacity failure does not spend admission; nonce guard fails closed then expires',async t=>{
  const key=randomBytes(32),f=await fixture(t,{allowAnonymousCreation:false,admissionKey:key,capacity:1,nonceCapacity:2});
  const a=await f.creation(0),c=(await f.request('/v1/config')).data,w=secret();
  const b={generation:c.generation,createPermit:c.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:0,requestedExpiresAtMs:0,admissionTicket:issueAdmissionTicket({admissionKey:key,generation:c.generation,nowMs:f.now(),ttlSeconds:1})};
  let r=await f.request('/v1/shares',{method:'POST',token:w,body:b});assert.equal(r.status,503);assert.equal(f.app.stats().spentTickets,1);
  await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});r=await f.request('/v1/shares',{method:'POST',token:w,body:b});assert.equal(r.status,201);assert.equal(f.app.stats().spentTickets,2);
  await f.request('/v1/shares/'+r.data.id,{method:'DELETE',token:b.revokeToken});
  const cfg=(await f.request('/v1/config')).data,newW=secret();const next={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:0,requestedExpiresAtMs:0,admissionTicket:issueAdmissionTicket({admissionKey:key,generation:cfg.generation,nowMs:f.now(),ttlSeconds:60})};
  r=await f.request('/v1/shares',{method:'POST',token:newW,body:next});assert.equal(r.status,503);assert.equal(r.data.error,'admission_capacity_reached');
  f.advance(1001);r=await f.request('/v1/shares',{method:'POST',token:newW,body:next});assert.equal(r.status,201);
});
test('management receipts reconcile evicted terminal sessions without leaking to readers',async t=>{
  const f=await fixture(t,{terminalCapacity:1}),a=await f.creation(0);assert.equal(typeof a.meta.managementReceipt,'string');
  let r=await f.request('/v1/shares/'+a.id+'/latest',{token:a.r});assert.equal('managementReceipt' in r.data,false);
  await f.request('/v1/shares/'+a.id,{method:'DELETE',token:a.x});const b=await f.creation(0);await f.request('/v1/shares/'+b.id,{method:'DELETE',token:b.x});
  const receiptHeader={'X-SafeBridge-Management-Receipt':a.meta.managementReceipt};
  for(const [method,path,token] of [['GET','/status',a.w],['GET','/status',a.x],['DELETE','',a.x]]){r=await f.request('/v1/shares/'+a.id+path,{method,token,headers:receiptHeader});assert.equal(r.status,200);assert.equal(r.data.state,'unavailable');assert.equal(r.data.expiresAtMs,0);assert.equal(r.data.leaseUntilMs,0);assert.equal('point' in r.data,false);}
  for(const [method,path,token] of [['GET','/status',a.r],['DELETE','',a.w],['GET','/latest',a.r],['PUT','/latest',a.w]]){r=await f.request('/v1/shares/'+a.id+path,{method,token,headers:receiptHeader,body:method==='PUT'?point(2):undefined});assert.equal(r.status,404);}
  r=await f.request('/v1/shares/'+a.id+'/status',{token:a.w,headers:{'X-SafeBridge-Management-Receipt':a.meta.managementReceipt.slice(1)}});assert.equal(r.status,404);
});

test('offline operator issuer reads only a private key file and outputs a bounded ticket',async t=>{
  const {mkdtemp,writeFile,chmod,rm}=await import('node:fs/promises');const {tmpdir}=await import('node:os');const {join}=await import('node:path');const {spawnSync}=await import('node:child_process');
  const dir=await mkdtemp(join(tmpdir(),'safe-bridge-synthetic-admission-'));t.after(()=>rm(dir,{recursive:true,force:true}));
  const path=join(dir,'private-test-key'),key=randomBytes(32),generation=secret(16);await writeFile(path,key.toString('base64url')+'\n',{mode:0o600});
  const cli=new URL('../issue-ticket.mjs',import.meta.url).pathname;
  let run=spawnSync(process.execPath,[cli,'--key-file',path,'--generation',generation,'--ttl-seconds','60'],{encoding:'utf8'});assert.equal(run.status,0);assert.equal(run.stderr,'');assert.ok(verifyAdmissionTicket(run.stdout.trim(),{admissionKey:key,generation,nowMs:Date.now()}));assert.ok(!run.stdout.includes(key.toString('base64url')));
  await chmod(path,0o644);run=spawnSync(process.execPath,[cli,'--key-file',path,'--generation',generation],{encoding:'utf8'});assert.notEqual(run.status,0);assert.equal(run.stdout,'');assert.ok(!run.stderr.includes(key.toString('base64url')));
});

test('delayed creation honors exact original chosen end and immutable retry identity',async t=>{
  const f=await fixture(t),config=(await f.request('/v1/config')).data,w=secret(),end=f.now()+60000;
  const body={generation:config.generation,createPermit:config.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:60,requestedExpiresAtMs:end,admissionTicket:null};
  f.advance(40000);let r=await f.request('/v1/shares',{method:'POST',token:w,body});assert.equal(r.status,201);assert.equal(r.data.expiresAtMs,end);assert.equal(r.data.expiresAtMs-r.data.serverTimeMs,20000);const id=r.data.id;
  f.advance(20001);r=await f.request('/v1/shares',{method:'POST',token:w,body});assert.equal(r.status,200);assert.equal(r.data.id,id);assert.equal(r.data.expiresAtMs,end);assert.equal(r.data.state,'expired');
  r=await f.request('/v1/shares',{method:'POST',token:w,body:{...body,requestedExpiresAtMs:end+1}});assert.equal(r.status,404);assert.equal(f.app.stats().created,1);
});
test('new creation after chosen end allocates nothing and leaves admission ticket unspent',async t=>{
  const key=randomBytes(32),f=await fixture(t,{allowAnonymousCreation:false,admissionKey:key});const config=(await f.request('/v1/config')).data,w=secret();
  const body={generation:config.generation,createPermit:config.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:1,requestedExpiresAtMs:f.now()+1000,admissionTicket:issueAdmissionTicket({admissionKey:key,generation:config.generation,nowMs:f.now(),ttlSeconds:60})};
  f.advance(1001);let r=await f.request('/v1/shares',{method:'POST',token:w,body});assert.equal(r.status,400);assert.equal(r.data.error,'chosen_end_elapsed');assert.equal(f.app.stats().activeSessions,0);assert.equal(f.app.stats().created,0);assert.equal(f.app.stats().spentTickets,0);
  // A separate explicit test choice can still use the unspent ticket; no prior mapping exists.
  r=await f.request('/v1/shares',{method:'POST',token:w,body:{...body,creationKey:secret(16),requestedExpiresAtMs:f.now()+5000}});assert.equal(r.status,201);assert.equal(r.data.expiresAtMs,f.now()+5000);assert.equal(f.app.stats().spentTickets,1);
});
test('requested end is mandatory, safely bounded and consistent with until-Stop',async t=>{
  const f=await fixture(t),config=(await f.request('/v1/config')).data;
  const base={generation:config.generation,createPermit:config.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:1,requestedExpiresAtMs:f.now()+1000,admissionTicket:null};
  for(const end of [-1,null,'1800000001000',1.5,0,f.now()+2147483647001]){const r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{...base,creationKey:secret(16),requestedExpiresAtMs:end}});assert.equal(r.status,400);}
  const missing={...base};delete missing.requestedExpiresAtMs;assert.equal((await f.request('/v1/shares',{method:'POST',token:secret(),body:missing})).status,400);
  assert.equal((await f.request('/v1/shares',{method:'POST',token:secret(),body:{...base,durationSeconds:0}})).status,400);assert.equal(f.app.stats().activeSessions,0);
  // Metadata does not reset or derive an independently chosen absolute deadline.
  const requested=f.now()+120000,r=await f.request('/v1/shares',{method:'POST',token:secret(),body:{...base,requestedExpiresAtMs:requested}});assert.equal(r.status,201);assert.equal(r.data.expiresAtMs,requested);
});
test('a creation body finishing after its chosen end cannot allocate or spend admission',async t=>{
  const key=randomBytes(32),f=await fixture(t,{allowAnonymousCreation:false,admissionKey:key}),config=(await f.request('/v1/config')).data,w=secret();
  const body={generation:config.generation,createPermit:config.createPermit,creationKey:secret(16),readToken:secret(),revokeToken:secret(),durationSeconds:1,requestedExpiresAtMs:f.now()+1000,admissionTicket:issueAdmissionTicket({admissionKey:key,generation:config.generation,nowMs:f.now(),ttlSeconds:60})};const text=JSON.stringify(body);
  let finish;const result=new Promise(r=>finish=r);const req=http.request(f.base+'/v1/shares',{method:'POST',headers:{Authorization:'Bearer '+w,'Content-Type':'application/json'}},res=>{let text='';res.on('data',x=>text+=x);res.on('end',()=>finish({status:res.statusCode,body:JSON.parse(text)}));});
  req.write(text.slice(0,10));await new Promise(r=>setTimeout(r,25));f.advance(1001);req.end(text.slice(10));const r=await result;assert.equal(r.status,400);assert.equal(r.body.error,'chosen_end_elapsed');assert.equal(f.app.stats().activeSessions,0);assert.equal(f.app.stats().spentTickets,0);
});
