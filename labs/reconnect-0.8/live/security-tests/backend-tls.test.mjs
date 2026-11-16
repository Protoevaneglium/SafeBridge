import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import http from 'node:http';
import {mkdtempSync,chmodSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {issueAdmissionTicket} from '../backend/admission.mjs';
import {createSafeBridgeServer} from '../backend/server.mjs';

// Ephemeral self-signed material is generated in an OS-private temp directory,
// used as a test-only CA, and deleted. No keys/certificates are project fixtures.
async function tlsHarness(overrides={}){
  let now=1800000000000;
  const directory=mkdtempSync(join(tmpdir(),'safebridge-synthetic-tls-'));chmodSync(directory,0o700);
  try{
    const keyFile=join(directory,'test-key.pem'),certFile=join(directory,'test-cert.pem');
    execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',keyFile,'-out',certFile,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],{stdio:'ignore'});
    const tls={key:readFileSync(keyFile),cert:readFileSync(certFile),minVersion:'TLSv1.2'};
    const admissionKey=randomBytes(32);
    const app=createSafeBridgeServer({tls,approvedOrigin:'https://localhost',admissionKey,clock:()=>now,...overrides});
    await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
    const port=app.server.address().port;
    async function request({path='/v1/config',method='GET',body,headers={},trustTestCA=true,servername='localhost'}={}){
      const data=body===undefined?null:JSON.stringify(body);
      const options={hostname:'127.0.0.1',port,path,method,servername,headers:{Host:'localhost',...(data===null?{}:{'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)}),...headers}};
      if(trustTestCA)options.ca=tls.cert;
      return await new Promise((resolve,reject)=>{
        const req=https.request(options,res=>{let text='';res.setEncoding('utf8');const version=res.socket.getProtocol(),authorized=res.socket.authorized;res.on('data',c=>text+=c);res.on('end',()=>resolve({status:res.statusCode,text,body:JSON.parse(text),headers:res.headers,version,authorized}));});
        req.setTimeout(3000,()=>req.destroy(new Error('synthetic TLS timeout')));req.on('error',reject);req.end(data);
      });
    }
    return {app,tls,port,request,admissionKey,get now(){return now;},advance(ms){now+=ms;},close:async()=>{await app.close();rmSync(directory,{recursive:true,force:true});}};
  }catch(error){rmSync(directory,{recursive:true,force:true});throw error;}
}

test('production constructor requires TLS and a configured HTTPS origin',()=>{
  assert.throws(()=>createSafeBridgeServer(),/TLS required/);
  assert.throws(()=>createSafeBridgeServer({tls:{key:'invalid',cert:'invalid'}}),/approvedOrigin/);
  assert.throws(()=>createSafeBridgeServer({tls:{key:'invalid',cert:'invalid'},approvedOrigin:'http://example.invalid'}),/approvedOrigin/);
});

test('actual TLS listener validates authority and retains privacy headers',async()=>{
  const h=await tlsHarness();try{
    const ok=await h.request();assert.equal(ok.status,200);assert.equal(ok.authorized,true);assert.ok(['TLSv1.2','TLSv1.3'].includes(ok.version));
    assert.equal(ok.headers['strict-transport-security'],'max-age=31536000');assert.match(ok.headers['cache-control'],/no-store/);assert.equal(ok.headers['referrer-policy'],'no-referrer');assert.match(ok.headers['content-security-policy'],/frame-ancestors 'none'/);assert.equal(ok.headers['access-control-allow-origin'],undefined);
    const badHost=await h.request({headers:{Host:'unapproved.invalid'}});assert.ok(badHost.status>=400&&badHost.status<500);
    const badOrigin=await h.request({headers:{Origin:'https://unapproved.invalid'}});assert.equal(badOrigin.status,403);
    const matching=await h.request({headers:{Origin:'https://localhost'}});assert.equal(matching.status,200);
  }finally{await h.close();}
});

test('default trust and hostname verification reject an untrusted/mismatched test server',async()=>{
  const h=await tlsHarness();try{
    await assert.rejects(()=>h.request({trustTestCA:false}),error=>['DEPTH_ZERO_SELF_SIGNED_CERT','SELF_SIGNED_CERT_IN_CHAIN','UNABLE_TO_VERIFY_LEAF_SIGNATURE'].includes(error.code));
    await assert.rejects(()=>h.request({servername:'wrong-host.invalid'}),error=>error.code==='ERR_TLS_CERT_ALTNAME_INVALID');
  }finally{await h.close();}
});

test('plaintext HTTP cannot invoke the TLS API',async()=>{
  const h=await tlsHarness();try{
    const result=await new Promise(resolve=>{
      const req=http.request({hostname:'127.0.0.1',port:h.port,path:'/v1/config',headers:{Host:'localhost'}},res=>{res.resume();res.on('end',()=>resolve(res.statusCode));});
      req.setTimeout(3000,()=>req.destroy(new Error('synthetic timeout')));req.on('error',()=>resolve(null));req.end();
    });
    assert.notEqual(result,200);
  }finally{await h.close();}
});

const capability=()=>randomBytes(32).toString('base64url');
async function candidate(h,{durationSeconds=0,ticket}={}){
  const config=(await h.request()).body;assert.equal(config.protocolVersion,2);assert.equal(config.admissionRequired,true);
  const credentials={write:capability(),read:capability(),revoke:capability()};
  const admissionTicket=ticket===undefined?issueAdmissionTicket({admissionKey:h.admissionKey,generation:config.generation,nowMs:h.now,ttlSeconds:3600}):ticket;
  const body={generation:config.generation,createPermit:config.createPermit,creationKey:randomBytes(24).toString('base64url'),readToken:credentials.read,revokeToken:credentials.revoke,admissionTicket,durationSeconds,requestedExpiresAtMs:durationSeconds===0?0:config.serverTimeMs+durationSeconds*1000};
  return {config,credentials,body};
}
const post=(h,s)=>h.request({path:'/v1/shares',method:'POST',headers:{Authorization:`Bearer ${s.credentials.write}`},body:s.body});
const operation=(h,s,id,method,action='',role='revoke',extra={})=>h.request({path:`/v1/shares/${id}${action?'/'+action:''}`,method,headers:{Authorization:`Bearer ${s.credentials[role]}`,'X-SafeBridge-Generation':s.config.generation,...extra}});

test('production admission cannot be bypassed with public config, absent or forged tickets',async()=>{
  const h=await tlsHarness({capacity:1});try{
    assert.throws(()=>createSafeBridgeServer({development:true}),/admissionKey/);
    assert.throws(()=>createSafeBridgeServer({tls:h.tls,approvedOrigin:'https://localhost',allowAnonymousCreation:true,admissionKey:h.admissionKey}),/Anonymous/);
    assert.throws(()=>createSafeBridgeServer({tls:h.tls,approvedOrigin:'https://localhost'}),/admissionKey/);
    for(const ticket of [null,'synthetic-forged-ticket',issueAdmissionTicket({admissionKey:randomBytes(32),generation:h.app.generation,nowMs:h.now,ttlSeconds:3600})]){
      const unauthorized=await candidate(h,{ticket});const r=await post(h,unauthorized);assert.ok(r.status>=400&&r.status<500);assert.equal(h.app.stats().activeSessions,0);assert.equal(h.app.stats().spentTickets,0);
    }
    const admitted=await candidate(h);const r=await post(h,admitted);assert.equal(r.status,201);assert.equal(h.app.stats().activeSessions,1);
    const recipient=await operation(h,admitted,r.body.id,'GET','latest','read');assert.equal(recipient.status,200);assert.ok(!('admissionTicket' in recipient.body));assert.ok(!('managementReceipt' in recipient.body));
    assert.ok(!recipient.text.includes(admitted.body.admissionTicket));assert.ok(!JSON.stringify(admitted.config).includes(h.admissionKey.toString('base64url')));
  }finally{await h.close();}
});

test('a spent ticket cannot allocate new quota after its terminal row is evicted',async()=>{
  const h=await tlsHarness({capacity:1,terminalCapacity:1});try{
    const a=await candidate(h),madeA=await post(h,a);assert.equal(madeA.status,201);
    assert.equal((await operation(h,a,madeA.body.id,'DELETE')).body.state,'stopped');
    const b=await candidate(h),madeB=await post(h,b);assert.equal(madeB.status,201);await operation(h,b,madeB.body.id,'DELETE');
    assert.equal((await operation(h,a,madeA.body.id,'GET','latest','read')).status,404);
    assert.equal((await post(h,a)).status,403);assert.equal(h.app.stats().activeSessions,0);
    const stolenAgain=await candidate(h,{ticket:a.body.admissionTicket});assert.equal((await post(h,stolenAgain)).status,403);assert.equal(h.app.stats().activeSessions,0);
    const c=await candidate(h);assert.equal((await post(h,c)).status,201);assert.equal(h.app.stats().activeSessions,1);assert.ok(h.app.stats().terminalSessions<=1);
  }finally{await h.close();}
});

test('nonce guard overflow fails closed and does not spend a rejected ticket',async()=>{
  const h=await tlsHarness({capacity:2,terminalCapacity:1,nonceCapacity:1});try{
    const firstTicket=issueAdmissionTicket({admissionKey:h.admissionKey,generation:h.app.generation,nowMs:h.now,ttlSeconds:1});
    const a=await candidate(h,{ticket:firstTicket}),madeA=await post(h,a);assert.equal(madeA.status,201);await operation(h,a,madeA.body.id,'DELETE');
    const b=await candidate(h);const denied=await post(h,b);assert.equal(denied.status,503);assert.equal(h.app.stats().activeSessions,0);assert.equal(h.app.stats().spentTickets,1);
    h.advance(1001);const retried=await post(h,b);assert.equal(retried.status,201);assert.equal(h.app.stats().activeSessions,1);assert.equal(h.app.stats().spentTickets,1);
    const originalRetry=await post(h,a);assert.equal(originalRetry.status,200);assert.equal(originalRetry.body.state,'stopped');assert.equal(originalRetry.body.id,madeA.body.id);assert.equal(h.app.stats().activeSessions,1);
    const expiredReuse=await candidate(h,{ticket:firstTicket});assert.equal((await post(h,expiredReuse)).status,403); // A new creation cannot reuse an expired ticket.
  }finally{await h.close();}
});

test('expired and old-generation signed tickets cannot create a production session',async()=>{
  const first=await tlsHarness();let old;
  try{
    const ticket=issueAdmissionTicket({admissionKey:first.admissionKey,generation:first.app.generation,nowMs:first.now,ttlSeconds:1});
    const expired=await candidate(first,{ticket});first.advance(1000);assert.equal((await post(first,expired)).status,403);assert.equal(first.app.stats().activeSessions,0);
    old=await candidate(first);old.admissionKey=first.admissionKey;
  }finally{await first.close();}
  const second=await tlsHarness({admissionKey:old.admissionKey});try{
    assert.notEqual(second.app.generation,old.config.generation);
    const otherGeneration=await candidate(second,{ticket:old.body.admissionTicket});assert.equal((await post(second,otherGeneration)).status,403);assert.equal(second.app.stats().activeSessions,0);
  }finally{await second.close();}
});

test('elapsed chosen end does not consume its signed admission ticket',async()=>{
  const h=await tlsHarness({capacity:1,nonceCapacity:1});try{
    const ticket=issueAdmissionTicket({admissionKey:h.admissionKey,generation:h.app.generation,nowMs:h.now,ttlSeconds:3600});
    const old=await candidate(h,{durationSeconds:60,ticket}),target=old.body.requestedExpiresAtMs;
    h.advance(60001);old.body.createPermit=(await h.request()).body.createPermit;
    const rejected=await post(h,old);assert.equal(rejected.status,400);assert.equal(rejected.body.error,'chosen_end_elapsed');assert.equal(h.app.stats().activeSessions,0);assert.equal(h.app.stats().spentTickets,0);
    const corrected=await candidate(h,{durationSeconds:60,ticket});const accepted=await post(h,corrected);assert.equal(accepted.status,201);assert.equal(h.app.stats().spentTickets,1);assert.ok(accepted.body.expiresAtMs>target);
  }finally{await h.close();}
});
