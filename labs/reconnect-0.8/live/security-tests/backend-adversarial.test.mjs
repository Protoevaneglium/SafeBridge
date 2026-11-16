import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {randomBytes} from 'node:crypto';
import {createSafeBridgeServer} from '../backend/server.mjs';

// Independent black-box security tests. All locations, credentials and times are synthetic.
const secret = () => randomBytes(32).toString('base64url');
const key = () => randomBytes(24).toString('base64url');
async function harness(overrides={}) {
  let now=1_800_000_000_000;
  const app=createSafeBridgeServer({development:true,allowAnonymousCreation:true,clock:()=>now,...overrides});
  await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
  const port=app.server.address().port;
  const origin=`http://127.0.0.1:${port}`;
  async function call(path,{method='GET',token,generation,body,raw,headers={}}={}) {
    const data=raw!==undefined?raw:body===undefined?null:JSON.stringify(body);
    const requestHeaders={...headers};
    if(token!==undefined)requestHeaders.Authorization=`Bearer ${token}`;
    if(generation!==undefined)requestHeaders['X-SafeBridge-Generation']=generation;
    if(data!==null){if(!Object.keys(requestHeaders).some(x=>x.toLowerCase()==='content-type'))requestHeaders['Content-Type']='application/json';requestHeaders['Content-Length']=Buffer.byteLength(data);}
    return await new Promise((resolve,reject)=>{
      const req=http.request({hostname:'127.0.0.1',port,path,method,headers:requestHeaders},res=>{
        let text='';res.setEncoding('utf8');res.on('data',chunk=>text+=chunk);res.on('end',()=>{let value;try{value=JSON.parse(text);}catch{value=text;}resolve({status:res.statusCode,headers:res.headers,body:value,text});});
      });
      req.setTimeout(3000,()=>req.destroy(new Error('synthetic request timeout')));req.on('error',reject);if(data!==null)req.write(data);req.end();
    });
  }
  async function create(durationSeconds=900) {
    const cfg=await call('/v1/config');assert.equal(cfg.status,200);
    const credentials={write:secret(),read:secret(),revoke:secret()};
    const payload={generation:cfg.body.generation,createPermit:cfg.body.createPermit,creationKey:key(),readToken:credentials.read,revokeToken:credentials.revoke,admissionTicket:null,durationSeconds,requestedExpiresAtMs:durationSeconds===0?0:cfg.body.serverTimeMs+durationSeconds*1000};
    const made=await call('/v1/shares',{method:'POST',token:credentials.write,body:payload});
    assert.ok([200,201].includes(made.status),`create status ${made.status}`);
    return {cfg:cfg.body,credentials,payload,session:made.body,id:made.body.id,generation:made.body.generation};
  }
  const point=(sequence=1,changes={})=>({sequence,latitude:12.345678,longitude:-23.456789,accuracyM:15,sampleTimeMs:now,sampleAgeMs:0,approximate:false,mock:false,...changes});
  return {app,origin,call,create,point,get now(){return now;},advance(ms){now+=ms;},setTime(ms){now=ms;},close:async()=>{if(app.close)await app.close();else await new Promise(resolve=>app.server.close(resolve));}};
}
const path=(s,end='latest')=>`/v1/shares/${s.id}/${end}`;
const auth=(s,role='read')=>({token:s.credentials[role],generation:s.generation});
const reject4xx=r=>assert.ok(r.status>=400&&r.status<500,`expected 4xx, got ${r.status}`);

// This suite intentionally doesn't depend on backend internal state, only HTTP responses.
test('capabilities isolate read, write, revocation and status authority',async()=>{
  const h=await harness();try{
    const s=await h.create();
    for(const role of ['write','revoke'])assert.equal((await h.call(path(s),auth(s,role))).status,404);
    for(const role of ['read','revoke'])assert.equal((await h.call(path(s),{...auth(s,role),method:'PUT',body:h.point()})).status,404);
    for(const role of ['read','write'])assert.equal((await h.call(`/v1/shares/${s.id}`,{...auth(s,role),method:'DELETE'})).status,404);
    assert.equal((await h.call(path(s,'status'),auth(s,'read'))).status,404);
    assert.equal((await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()})).status,200);
    const read=await h.call(path(s),auth(s));assert.equal(read.status,200);assert.equal(read.body.point.latitude,12.345678);
    for(const role of ['write','revoke']){const status=await h.call(path(s,'status'),auth(s,role));assert.equal(status.status,200);for(const forbidden of ['point','latitude','longitude','readToken','writeToken','revokeToken','readHash','writeHash','revokeHash'])assert.ok(!(forbidden in status.body));}
  }finally{await h.close();}
});

test('unknown, wrong scope, wrong session and wrong generation use generic errors',async()=>{
  const h=await harness();try{
    const a=await h.create(),b=await h.create();
    const errors=await Promise.all([
      h.call(path(a),{token:secret(),generation:a.generation}),
      h.call(path(a),auth(a,'write')),
      h.call(path(a),{token:b.credentials.read,generation:a.generation}),
      h.call('/v1/shares/'+key()+'/latest',auth(a)),
      h.call(path(a),{token:a.credentials.read,generation:key()})
    ]);
    for(const r of errors){assert.equal(r.status,404);assert.equal(r.text,errors[0].text);assert.ok(!r.text.includes(a.id));}
  }finally{await h.close();}
});

test('latest-only updates replace old point without exposing private session state',async()=>{
  const h=await harness();try{
    const s=await h.create();await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()});h.advance(1000);
    await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2,{latitude:45.123456})});
    const r=await h.call(path(s),auth(s));assert.equal(r.body.point.latitude,45.123456);assert.ok(!r.text.includes('12.345678'));assert.equal(r.body.point.sequence,2);
    for(const credential of Object.values(s.credentials))assert.ok(!r.text.includes(credential));
    assert.ok(!('history' in r.body));assert.ok(!('points' in r.body));
    const stats=JSON.stringify(h.app.stats?.()??{});for(const value of [...Object.values(s.credentials),s.id,'45.123456'])assert.ok(!stats.includes(value));
  }finally{await h.close();}
});

test('same-sequence retries are idempotent and never renew the lease',async()=>{
  const h=await harness();try{
    const s=await h.create(),p=h.point();const first=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:p});assert.equal(first.status,200);
    const lease=first.body.leaseUntilMs;h.advance(20000);
    const duplicate=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:p});assert.equal(duplicate.status,200);assert.equal(duplicate.body.leaseUntilMs,lease);
    reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:{...p,latitude:40}}));
    reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:{...p,sequence:0}}));
  }finally{await h.close();}
});

test('explicit stop is terminal; concurrent or later writes cannot revive it',async()=>{
  const h=await harness();try{
    const s=await h.create();await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()});
    await Promise.all([h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2)}),h.call(`/v1/shares/${s.id}`,{...auth(s,'revoke'),method:'DELETE'})]);
    const stopped=await h.call(path(s),auth(s));assert.equal(stopped.body.state,'stopped');assert.equal(stopped.body.point,null);assert.ok(!stopped.text.includes('12.345678'));
    reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(3)}));
    const twice=await h.call(`/v1/shares/${s.id}`,{...auth(s,'revoke'),method:'DELETE'});assert.equal(twice.status,200);assert.equal(twice.body.state,'stopped');assert.ok(!('point' in twice.body)||twice.body.point===null);
    const retry=await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:s.payload});assert.ok([200,201].includes(retry.status));assert.equal(retry.body.id,s.id);assert.equal(retry.body.state,'stopped');assert.equal(retry.body.expiresAtMs,s.session.expiresAtMs);
  }finally{await h.close();}
});

test('point lease lapse purges coordinates but consent resumes with the same ID and link',async()=>{
  const h=await harness();try{
    const s=await h.create();await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()});h.advance(90001);
    const r=await h.call(path(s),auth(s));assert.equal(r.body.state,'paused');assert.equal(r.body.point,null);
    const resumed=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2,{latitude:45.123456})});assert.equal(resumed.status,200);assert.equal(resumed.body.id,s.id);assert.equal(resumed.body.generation,s.generation);
    const after=await h.call(path(s),auth(s));assert.equal(after.body.state,'active');assert.equal(after.body.point.latitude,45.123456);assert.equal(after.body.id,s.id);
  }finally{await h.close();}
});

test('hard expiry wins over lease and never returns old coordinates',async()=>{
  const h=await harness();try{
    const s=await h.create(60);await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()});h.advance(60000);
    const r=await h.call(path(s),auth(s));assert.equal(r.body.state,'expired');assert.equal(r.body.point,null);
    reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2)}));
    const retry=await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:s.payload});assert.equal(retry.body.id,s.id);assert.equal(retry.body.state,'expired');assert.equal(retry.body.expiresAtMs,s.session.expiresAtMs);
  }finally{await h.close();}
});

test('creation response loss can be retried without creating a new session or extending expiry',async()=>{
  const h=await harness();try{
    const s=await h.create();h.advance(10000);
    const retried=await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:s.payload});assert.ok([200,201].includes(retried.status));assert.equal(retried.body.id,s.id);assert.equal(retried.body.expiresAtMs,s.session.expiresAtMs);assert.equal(retried.body.leaseUntilMs,s.session.leaseUntilMs);
    reject4xx(await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:{...s.payload,durationSeconds:1800}}));
    reject4xx(await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:{...s.payload,readToken:secret()}}));
  }finally{await h.close();}
});

test('expired creation permits cannot make a fresh session',async()=>{
  const h=await harness({permitTtlMs:100});try{
    const cfg=(await h.call('/v1/config')).body;h.advance(101);
    const p={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:key(),readToken:secret(),revokeToken:secret(),admissionTicket:null,durationSeconds:900,requestedExpiresAtMs:cfg.serverTimeMs+900000};
    reject4xx(await h.call('/v1/shares',{method:'POST',token:secret(),body:p}));
  }finally{await h.close();}
});

test('old creation retry does not resurrect a purged session',async()=>{
  const h=await harness({permitTtlMs:100,tombstoneMs:100});try{
    const s=await h.create(60);h.advance(61001);
    await h.call(path(s),auth(s)); // Forces expiry and cleanup independently of timer scheduling.
    h.advance(101);await h.call('/v1/config');
    reject4xx(await h.call('/v1/shares',{method:'POST',token:s.credentials.write,body:s.payload}));
  }finally{await h.close();}
});

test('new server generation rejects old creation and access credentials',async()=>{
  const first=await harness();const s=await first.create();await first.close();
  const second=await harness();try{
    const cfg=(await second.call('/v1/config')).body;assert.notEqual(cfg.generation,s.generation);
    reject4xx(await second.call('/v1/shares',{method:'POST',token:s.credentials.write,body:s.payload}));
    assert.equal((await second.call(path(s),auth(s))).status,404);
  }finally{await second.close();}
});

test('freshness ages on server elapsed time and device future time cannot extend expiry',async()=>{
  const h=await harness();try{
    const s=await h.create();const r=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(1,{sampleTimeMs:h.now+365*86400000})});assert.equal(r.status,200);const expiry=r.body.expiresAtMs;
    h.advance(30001);const stale=await h.call(path(s),auth(s));assert.equal(stale.body.freshness,'stale');assert.ok(stale.body.pointAgeMs>=30001);assert.equal(stale.body.expiresAtMs,expiry);
    h.advance(60000);const ended=await h.call(path(s),auth(s));assert.equal(ended.body.state,'paused');assert.equal(ended.body.point,null);
  }finally{await h.close();}
});

test('invalid coordinate, timing, sequence and metadata values are rejected without reflecting data',async()=>{
  const h=await harness();try{
    const s=await h.create();const invalid=[{latitude:91},{longitude:-181},{accuracyM:-1},{sampleAgeMs:-1},{sampleAgeMs:30001},{sequence:1.5},{sequence:0},{sequence:Number.MAX_SAFE_INTEGER+1},{approximate:'false'},{mock:null},{latitude:'synthetic-person-canary'},{sampleTimeMs:1e99},{extra:'synthetic-person-canary'}];
    for(const change of invalid){const r=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(1,change)});reject4xx(r);assert.ok(!r.text.includes('synthetic-person-canary'));}
    const raw=JSON.stringify(h.point()).replace('12.345678','1e999');reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',raw}));
    const latest=await h.call(path(s),auth(s));assert.equal(latest.body.point,null);
  }finally{await h.close();}
});

test('malformed, oversized and non-JSON bodies fail without echoing request data',async()=>{
  const h=await harness();try{
    const s=await h.create();
    const malformed=await h.call(path(s),{...auth(s,'write'),method:'PUT',raw:'{"secret":"synthetic-person-canary",'});reject4xx(malformed);assert.ok(!malformed.text.includes('synthetic-person-canary'));
    const oversized=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:{...h.point(),extra:'x'.repeat(65536)}});assert.equal(oversized.status,413);
    const plain=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(),headers:{'Content-Type':'text/plain'}});reject4xx(plain);
    const encoded=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(),headers:{'Content-Encoding':'gzip'}});reject4xx(encoded);
  }finally{await h.close();}
});

test('query credentials, hostile hosts and cross-origin browser requests are rejected',async()=>{
  const h=await harness();try{
    const s=await h.create();reject4xx(await h.call(path(s)+'?token='+s.credentials.read,auth(s)));
    reject4xx(await h.call('/v1/config',{headers:{Host:'attacker.invalid'}}));
    reject4xx(await h.call(path(s),{...auth(s),headers:{Origin:'https://attacker.invalid'}}));
    const cors=await h.call(path(s),{method:'OPTIONS',headers:{Origin:'https://attacker.invalid','Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'authorization,x-safebridge-generation'}});reject4xx(cors);assert.equal(cors.headers['access-control-allow-origin'],undefined);
  }finally{await h.close();}
});

test('API and viewer have no-store, no-referrer, isolation and no third-party script policy',async()=>{
  const h=await harness();try{
    for(const target of ['/v1/config','/view']){const r=await h.call(target);assert.equal(r.status,200);assert.match(r.headers['cache-control']??'',/no-store/);assert.equal(r.headers['referrer-policy'],'no-referrer');assert.equal(r.headers['x-content-type-options'],'nosniff');assert.match(r.headers['content-security-policy']??'',/frame-ancestors 'none'/);assert.match(r.headers['permissions-policy']??'',/geolocation=\(\)/);assert.equal(r.headers['access-control-allow-origin'],undefined);assert.equal(r.headers['set-cookie'],undefined);}
    const html=(await h.call('/view')).text;assert.ok(!/<script[^>]+src=["']https?:/i.test(html));assert.ok(!/<iframe/i.test(html));assert.ok(!/localStorage|sessionStorage|navigator\.geolocation/.test(html));
    for(const target of ['/%2e%2e/backend/server.mjs','/backend/server.mjs','/.env','/package.json'])assert.equal((await h.call(target)).status,404);
  }finally{await h.close();}
});

test('global capacity rejects additional sessions before accepting their secrets',async()=>{
  const h=await harness({capacity:1});try{
    await h.create();const cfg=(await h.call('/v1/config')).body;
    const r=await h.call('/v1/shares',{method:'POST',token:secret(),body:{generation:cfg.generation,createPermit:cfg.createPermit,creationKey:key(),readToken:secret(),revokeToken:secret(),admissionTicket:null,durationSeconds:900,requestedExpiresAtMs:cfg.serverTimeMs+900000}});
    assert.ok([429,503].includes(r.status));
  }finally{await h.close();}
});

test('a slow old body cannot be presented as fresh after an outage',async()=>{
  const h=await harness();try{
    const s=await h.create(),data=JSON.stringify(h.point());const half=Math.floor(data.length/2);
    let resolveReply,rejectReply;
    const reply=new Promise((resolve,reject)=>{resolveReply=resolve;rejectReply=reject;});
    const req=http.request({hostname:'127.0.0.1',port:new URL(h.origin).port,path:path(s),method:'PUT',headers:{Authorization:`Bearer ${s.credentials.write}`,'X-SafeBridge-Generation':s.generation,'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)}},res=>{let text='';res.setEncoding('utf8');res.on('data',c=>text+=c);res.on('end',()=>resolveReply({status:res.statusCode,text}));});
    req.setTimeout(3000,()=>req.destroy(new Error('synthetic slow-body timeout')));req.on('error',rejectReply);
    const connected=new Promise(resolve=>req.once('socket',socket=>socket.connecting?socket.once('connect',resolve):resolve()));
    req.flushHeaders();req.write(data.slice(0,half));await connected;
    assert.equal((await h.call(path(s,'status'),auth(s,'write'))).status,200);
    h.advance(90001);req.end(data.slice(half));
    const r=await reply;reject4xx(r);
    const latest=await h.call(path(s),auth(s));assert.equal(latest.body.state,'paused');assert.equal(latest.body.point,null);
    const resumed=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2)});assert.equal(resumed.status,200);assert.equal(resumed.body.id,s.id);
  }finally{await h.close();}
});

test('request and creation rate limits recover only at their configured windows',async()=>{
  for(const option of [{ipPerMinute:2},{globalPerMinute:2}]){
    const h=await harness(option);try{assert.equal((await h.call('/v1/config')).status,200);assert.equal((await h.call('/v1/config')).status,200);assert.equal((await h.call('/v1/config')).status,429);h.advance(60001);assert.equal((await h.call('/v1/config')).status,200);}finally{await h.close();}
  }
  const h=await harness({createsPerWindow:1});try{
    await h.create();const cfg=(await h.call('/v1/config')).body;
    const p={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:key(),readToken:secret(),revokeToken:secret(),admissionTicket:null,durationSeconds:900,requestedExpiresAtMs:cfg.serverTimeMs+900000};
    assert.equal((await h.call('/v1/shares',{method:'POST',token:secret(),body:p})).status,429);
  }finally{await h.close();}
});

test('new sessions require distinct canonical capabilities rather than aliased secrets',async()=>{
  const h=await harness();try{
    const cfg=(await h.call('/v1/config')).body,write=secret();
    const base={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:key(),readToken:secret(),revokeToken:secret(),admissionTicket:null,durationSeconds:900,requestedExpiresAtMs:cfg.serverTimeMs+900000};
    reject4xx(await h.call('/v1/shares',{method:'POST',token:write,body:{...base,readToken:write}}));
    reject4xx(await h.call('/v1/shares',{method:'POST',token:write,body:{...base,revokeToken:base.readToken}}));
    const noncanonical='A'.repeat(42)+'B';
    reject4xx(await h.call('/v1/shares',{method:'POST',token:write,body:{...base,readToken:noncanonical}}));
  }finally{await h.close();}
});

test('first authenticated Stop is not denied by an exhausted ordinary request budget',async()=>{
  const h=await harness({ipPerMinute:3});try{
    const s=await h.create();assert.equal((await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()})).status,200);
    const stopped=await h.call(`/v1/shares/${s.id}`,{...auth(s,'revoke'),method:'DELETE'});
    assert.equal(stopped.status,200);assert.equal(stopped.body.state,'stopped');
  }finally{await h.close();}
});

test('until-Stop duration has no hidden hour limit and resumes after a long outage',async()=>{
  const h=await harness();try{
    const s=await h.create(0);assert.equal(s.session.expiresAtMs,0);
    assert.equal((await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()})).status,200);
    h.advance(8*3600000);const paused=await h.call(path(s),auth(s));assert.equal(paused.body.state,'paused');assert.equal(paused.body.point,null);assert.equal(paused.body.expiresAtMs,0);
    const resumed=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2,{latitude:46.123456})});assert.equal(resumed.status,200);assert.equal(resumed.body.id,s.id);assert.equal(resumed.body.expiresAtMs,0);
    h.advance(24*3600000);const pausedAgain=await h.call(path(s),auth(s));assert.equal(pausedAgain.body.state,'paused');assert.equal(pausedAgain.body.id,s.id);
    const ended=await h.call(`/v1/shares/${s.id}`,{...auth(s,'revoke'),method:'DELETE'});assert.equal(ended.body.state,'stopped');
    reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(3)}));
  }finally{await h.close();}
});

test('chosen duration accepts one second and values beyond an hour while expiry is terminal',async()=>{
  const h=await harness();try{
    const short=await h.create(1);assert.equal(short.session.expiresAtMs,h.now+1000);h.advance(1000);
    assert.equal((await h.call(path(short),auth(short))).body.state,'expired');reject4xx(await h.call(path(short),{...auth(short,'write'),method:'PUT',body:h.point()}));
    const long=await h.create(2147483647);assert.equal(long.session.expiresAtMs,h.now+2147483647000);h.advance(3600001);
    assert.equal((await h.call(path(long),auth(long))).body.state,'paused');assert.equal((await h.call(path(long),{...auth(long,'write'),method:'PUT',body:h.point()})).status,200);
  }finally{await h.close();}
});

test('paused duplicate payload cannot restore the purged point or renew its lease',async()=>{
  const h=await harness();try{
    const s=await h.create(0),p=h.point();const sent=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:p});h.advance(90001);
    assert.equal((await h.call(path(s),auth(s))).body.state,'paused');
    const duplicate=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:p});assert.equal(duplicate.status,200);assert.equal(duplicate.body.state,'paused');assert.equal(duplicate.body.leaseUntilMs,sent.body.leaseUntilMs);
    const still=await h.call(path(s),auth(s));assert.equal(still.body.point,null);assert.equal(still.body.state,'paused');
    const fresh=await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2)});assert.equal(fresh.body.state,'active');assert.equal(fresh.body.id,s.id);
  }finally{await h.close();}
});

test('Stop during a paused outage is terminal and releases the active quota',async()=>{
  const h=await harness({capacity:1,terminalCapacity:2});try{
    for(let index=0;index<6;index++){
      const s=await h.create(0);await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()});h.advance(90001);
      assert.equal((await h.call(path(s),auth(s))).body.state,'paused');
      assert.equal((await h.call(`/v1/shares/${s.id}`,{...auth(s,'revoke'),method:'DELETE'})).body.state,'stopped');
      reject4xx(await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point(2)}));
      assert.equal(h.app.stats().activeSessions,0);assert.ok(h.app.stats().terminalSessions<=2);
    }
    const next=await h.create(0);assert.equal(next.session.state,'waiting_for_fix');assert.equal(h.app.stats().activeSessions,1);
  }finally{await h.close();}
});

test('stateless management receipt resolves evicted terminals without granting read/write or revival',async()=>{
  const h=await harness({capacity:1,terminalCapacity:1});try{
    const a=await h.create(0),receipt=a.session.managementReceipt;assert.equal(typeof receipt,'string');
    await h.call(path(a),{...auth(a,'write'),method:'PUT',body:h.point()});
    const seen=await h.call(path(a),auth(a));assert.ok(!('managementReceipt' in seen.body));assert.ok(!seen.text.includes(receipt));
    await h.call(`/v1/shares/${a.id}`,{...auth(a,'revoke'),method:'DELETE'});
    const b=await h.create(0);await h.call(`/v1/shares/${b.id}`,{...auth(b,'revoke'),method:'DELETE'});
    assert.equal((await h.call(path(a),auth(a))).status,404);
    const receiptHeader={'X-SafeBridge-Management-Receipt':receipt};
    const status=await h.call(path(a,'status'),{...auth(a,'write'),headers:receiptHeader});assert.equal(status.status,200);assert.equal(status.body.state,'unavailable');assert.ok(!('point' in status.body));
    const stopped=await h.call(`/v1/shares/${a.id}`,{...auth(a,'revoke'),method:'DELETE',headers:receiptHeader});assert.equal(stopped.body.state,'unavailable');
    assert.equal((await h.call(path(a,'status'),{...auth(a,'read'),headers:receiptHeader})).status,404);
    assert.equal((await h.call(`/v1/shares/${a.id}`,{...auth(a,'write'),method:'DELETE',headers:receiptHeader})).status,404);
    assert.equal((await h.call(path(a),{...auth(a,'write'),method:'PUT',headers:receiptHeader,body:h.point(2)})).status,404);
    assert.equal((await h.call(`/v1/shares/${key()}/status`,{...auth(a,'write'),headers:receiptHeader})).status,404);
    const [encoded,signature]=receipt.split('.');const forged=encoded+'.'+(signature.startsWith('A')?'B':'A')+signature.slice(1);
    assert.equal((await h.call(path(a,'status'),{...auth(a,'write'),headers:{'X-SafeBridge-Management-Receipt':forged}})).status,404);
    assert.equal(h.app.stats().activeSessions,0);
  }finally{await h.close();}
});

test('late creation keeps the original chosen expiry rather than adding a full duration again',async()=>{
  const h=await harness();try{
    const config=(await h.call('/v1/config')).body,target=h.now+120000;
    const credentials={write:secret(),read:secret(),revoke:secret()};
    const payload={generation:config.generation,createPermit:config.createPermit,creationKey:key(),readToken:credentials.read,revokeToken:credentials.revoke,admissionTicket:null,durationSeconds:120,requestedExpiresAtMs:target};
    h.advance(90000);payload.createPermit=(await h.call('/v1/config')).body.createPermit;
    const made=await h.call('/v1/shares',{method:'POST',token:credentials.write,body:payload});assert.equal(made.status,201);assert.equal(made.body.expiresAtMs,target);assert.equal(made.body.expiresAtMs-h.now,30000);
    const s={id:made.body.id,generation:made.body.generation,credentials};assert.equal((await h.call(path(s),{...auth(s,'write'),method:'PUT',body:h.point()})).status,200);
    h.advance(30000);const expired=await h.call(path(s),auth(s));assert.equal(expired.body.state,'expired');assert.equal(expired.body.point,null);
    const duplicate=await h.call('/v1/shares',{method:'POST',token:credentials.write,body:payload});assert.equal(duplicate.status,200);assert.equal(duplicate.body.id,s.id);assert.equal(duplicate.body.expiresAtMs,target);
    reject4xx(await h.call('/v1/shares',{method:'POST',token:credentials.write,body:{...payload,requestedExpiresAtMs:target+60000}}));
  }finally{await h.close();}
});

test('a newly elapsed chosen end cannot allocate, and duration/expiry sentinel pairs are strict',async()=>{
  const h=await harness();try{
    const cfg=(await h.call('/v1/config')).body;
    const payload={generation:cfg.generation,createPermit:cfg.createPermit,creationKey:key(),readToken:secret(),revokeToken:secret(),admissionTicket:null,durationSeconds:60,requestedExpiresAtMs:h.now+60000};
    h.advance(60001);payload.createPermit=(await h.call('/v1/config')).body.createPermit;
    const elapsed=await h.call('/v1/shares',{method:'POST',token:secret(),body:payload});assert.equal(elapsed.status,400);assert.equal(elapsed.body.error,'chosen_end_elapsed');assert.equal(h.app.stats().activeSessions,0);
    for(const values of [{durationSeconds:0,requestedExpiresAtMs:h.now+1000},{durationSeconds:60,requestedExpiresAtMs:0},{durationSeconds:60,requestedExpiresAtMs:Number.MAX_SAFE_INTEGER+1}])reject4xx(await h.call('/v1/shares',{method:'POST',token:secret(),body:{...payload,...values}}));
    assert.equal(h.app.stats().activeSessions,0);
  }finally{await h.close();}
});
