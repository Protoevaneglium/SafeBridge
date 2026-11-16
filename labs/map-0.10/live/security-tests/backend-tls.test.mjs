import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import http from 'node:http';
import {mkdtempSync,chmodSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {createPreparedGrant,issuePreparedAdmissionTicket} from '../backend/prepared-admission.mjs';
import {initializeDurableStore} from '../backend/durable-store.mjs';
import {createSafeBridgeServer} from '../backend/server.mjs';
const DOMAIN='prepared.example.invalid',ORIGIN='https://'+DOMAIN;
const capability=()=>randomBytes(32).toString('base64url');
// Actual TLS and prepared admission, all on loopback. Disposable keys are never fixtures.
async function tlsHarness(overrides={}){
  let now=1800000000000;const directory=mkdtempSync(join(tmpdir(),'safebridge-synthetic-tls-'));chmodSync(directory,0o700);let store,app;
  try{
    const keyFile=join(directory,'test-key.pem'),certFile=join(directory,'test-cert.pem');
    execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',keyFile,'-out',certFile,'-days','1','-subj','/CN='+DOMAIN,'-addext','subjectAltName=DNS:'+DOMAIN],{stdio:'ignore'});
    const tls={key:readFileSync(keyFile),cert:readFileSync(certFile),minVersion:'TLSv1.2'},stateKey=randomBytes(32);
    const grants=[createPreparedGrant({endpoint:ORIGIN}),createPreparedGrant({endpoint:ORIGIN})];
    store=initializeDurableStore({directory,stateKey,capacity:2,nowMs:now,preparedGrants:{endpoint:ORIGIN,grants:new Map(grants.map(g=>[g.grant.slotId,Buffer.from(g.grant.verifier,'base64url')]))}});
    const metadata=store.load().metadata;
    app=createSafeBridgeServer({tls,approvedOrigin:ORIGIN,durableStore:store,clock:()=>now,...overrides});
    await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));const port=app.server.address().port;
    async function request({path='/v1/config',method='GET',body,headers={},trustTestCA=true,servername=DOMAIN}={}){
      const data=body===undefined?null:JSON.stringify(body),options={hostname:'127.0.0.1',port,path,method,servername,headers:{Host:DOMAIN,...(data===null?{}:{'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)}),...headers}};if(trustTestCA)options.ca=tls.cert;
      return new Promise((resolve,reject)=>{const req=https.request(options,res=>{let text='';res.setEncoding('utf8');const version=res.socket.getProtocol(),authorized=res.socket.authorized;res.on('data',c=>text+=c);res.on('end',()=>{try{resolve({status:res.statusCode,text,body:JSON.parse(text),headers:res.headers,version,authorized});}catch(error){reject(error);}});});req.setTimeout(3000,()=>req.destroy(new Error('synthetic TLS timeout')));req.on('error',reject);req.end(data);});
    }
    return {app,store,tls,port,request,grants,metadata,get now(){return now;},advance(ms){now+=ms;},close:async()=>{await app.close();rmSync(directory,{recursive:true,force:true});}};
  }catch(error){if(app)await app.close();else store?.close();rmSync(directory,{recursive:true,force:true});throw error;}
}
async function candidate(h,{durationSeconds=0,index=0,ticket}={}){
  const config=(await h.request()).body;assert.equal(config.protocolVersion,3);assert.equal(config.durableSessionState,true);assert.equal(config.preparedAdmission,true);const credentials={write:capability(),read:capability(),revoke:capability()},creationKey=capability();
  const grant=h.grants[index].bundle,issued=await h.request({path:'/v1/admission',method:'POST',headers:{Authorization:'Bearer '+grant.preparedToken,'X-SafeBridge-Generation':config.generation},body:{slotId:grant.slotId,creationKey,expectedSlotEpoch:null}});assert.equal(issued.status,200);
  const body={generation:config.generation,createPermit:config.createPermit,creationKey,readToken:credentials.read,revokeToken:credentials.revoke,admissionTicket:ticket===undefined?issued.body.admissionTicket:ticket,durationSeconds,requestedExpiresAtMs:durationSeconds===0?0:h.now+durationSeconds*1000};return {config,credentials,body,index,epoch:issued.body.slotEpoch};
}
const post=(h,s)=>h.request({path:'/v1/shares',method:'POST',headers:{Authorization:'Bearer '+s.credentials.write},body:s.body});
const operation=(h,s,id,method,action='',role='revoke',extra={})=>h.request({path:'/v1/shares/'+id+(action?'/'+action:''),method,headers:{Authorization:'Bearer '+s.credentials[role],'X-SafeBridge-Generation':s.config.generation,...extra}});

test('production requires TLS, exact HTTPS origin and initialized prepared durable state',()=>{assert.throws(()=>createSafeBridgeServer(),/TLS required/);assert.throws(()=>createSafeBridgeServer({tls:{key:'invalid',cert:'invalid'}}),/approvedOrigin/);assert.throws(()=>createSafeBridgeServer({tls:{key:'invalid',cert:'invalid'},approvedOrigin:'http://example.invalid'}),/approvedOrigin/);assert.throws(()=>createSafeBridgeServer({tls:{key:'invalid',cert:'invalid'},approvedOrigin:ORIGIN}),/durableStore/);});

test('actual production TLS validates authority and retains no-store/no-referrer isolation',async()=>{const h=await tlsHarness();try{const ok=await h.request();assert.equal(ok.status,200);assert.equal(ok.authorized,true);assert.ok(['TLSv1.2','TLSv1.3'].includes(ok.version));assert.equal(ok.headers['strict-transport-security'],'max-age=31536000');assert.match(ok.headers['cache-control'],/no-store/);assert.equal(ok.headers['referrer-policy'],'no-referrer');assert.match(ok.headers['content-security-policy'],/frame-ancestors 'none'/);assert.equal(ok.headers['access-control-allow-origin'],undefined);assert.equal((await h.request({headers:{Host:'unapproved.invalid'}})).status,403);assert.equal((await h.request({headers:{Origin:'https://unapproved.invalid'}})).status,403);assert.equal((await h.request({headers:{Origin:ORIGIN}})).status,200);}finally{await h.close();}});

test('default trust and hostname verification reject the disposable untrusted or mismatched server',async()=>{const h=await tlsHarness();try{await assert.rejects(()=>h.request({trustTestCA:false}),e=>['DEPTH_ZERO_SELF_SIGNED_CERT','SELF_SIGNED_CERT_IN_CHAIN','UNABLE_TO_VERIFY_LEAF_SIGNATURE'].includes(e.code));await assert.rejects(()=>h.request({servername:'wrong-host.invalid'}),e=>e.code==='ERR_TLS_CERT_ALTNAME_INVALID');}finally{await h.close();}});

test('plaintext HTTP cannot invoke the TLS prepared API',async()=>{const h=await tlsHarness();try{const result=await new Promise(resolve=>{const req=http.request({hostname:'127.0.0.1',port:h.port,path:'/v1/config',headers:{Host:DOMAIN}},res=>{res.resume();res.on('end',()=>resolve(res.statusCode));});req.setTimeout(3000,()=>req.destroy(new Error('synthetic timeout')));req.on('error',()=>resolve(null));req.end();});assert.notEqual(result,200);}finally{await h.close();}});

test('public config and forged/absent prepared tickets cannot occupy production quota',async()=>{const h=await tlsHarness();try{for(const flag of ['allowAnonymousCreation','allowLegacyAdmissionForTests'])assert.throws(()=>createSafeBridgeServer({tls:h.tls,approvedOrigin:ORIGIN,durableStore:h.store,[flag]:true}),/loopback test only/i);for(const value of [null,'synthetic-forged-ticket']){const s=await candidate(h,{ticket:value});const rejected=await post(h,s);assert.ok(rejected.status>=400&&rejected.status<500);assert.equal(h.app.stats().activeSessions,0);}const s=await candidate(h),forged=issuePreparedAdmissionTicket({admissionKey:randomBytes(32),generation:s.config.generation,slotId:h.grants[0].bundle.slotId,creationKey:s.body.creationKey,slotEpoch:s.epoch,nowMs:h.now});assert.equal((await post(h,{...s,body:{...s.body,admissionTicket:forged}})).status,403);assert.equal(h.app.stats().activeSessions,0);const accepted=await post(h,s);assert.equal(accepted.status,201);assert.equal(h.app.stats().activeSessions,1);}finally{await h.close();}});

test('prepared tickets are creation-key bound and expired tickets fail before allocation',async()=>{const h=await tlsHarness();try{const s=await candidate(h);assert.equal((await post(h,{...s,body:{...s.body,creationKey:capability()}})).status,403);h.advance(120001);s.body.createPermit=(await h.request()).body.createPermit;assert.equal((await post(h,s)).status,403);assert.equal(h.app.stats().activeSessions,0);const b=h.grants[0].bundle,issued=await h.request({path:'/v1/admission',method:'POST',headers:{Authorization:'Bearer '+b.preparedToken,'X-SafeBridge-Generation':s.config.generation},body:{slotId:b.slotId,creationKey:s.body.creationKey,expectedSlotEpoch:s.epoch}});assert.equal(issued.status,200);s.body.admissionTicket=issued.body.admissionTicket;assert.equal((await post(h,s)).status,201);}finally{await h.close();}});

test('epoch protection survives terminal eviction and a fresh intent gets a different ID',async()=>{const h=await tlsHarness({terminalCapacity:1});try{const a=await candidate(h),made=await post(h,a);assert.equal(made.status,201);await operation(h,a,made.body.id,'DELETE');const b=await candidate(h,{index:1}),madeB=await post(h,b);await operation(h,b,madeB.body.id,'DELETE');assert.equal((await operation(h,a,made.body.id,'GET','latest','read')).status,404);assert.equal((await post(h,a)).status,409);const fresh=await candidate(h),madeFresh=await post(h,fresh);assert.equal(madeFresh.status,201);assert.notEqual(madeFresh.body.id,made.body.id);}finally{await h.close();}});

test('elapsed chosen end allocates nothing and same pinned prepared intent may choose a new explicit end',async()=>{const h=await tlsHarness();try{const s=await candidate(h,{durationSeconds:60});h.advance(60001);s.body.createPermit=(await h.request()).body.createPermit;const expired=await post(h,s);assert.equal(expired.status,400);assert.equal(expired.body.error,'chosen_end_elapsed');assert.equal(h.app.stats().activeSessions,0);s.body.requestedExpiresAtMs=h.now+60000;const made=await post(h,s);assert.equal(made.status,201);assert.equal(made.body.expiresAtMs,s.body.requestedExpiresAtMs);}finally{await h.close();}});

test('recipient needs no account/grant and receives no management or admission secrets',async()=>{const h=await tlsHarness();try{const s=await candidate(h),made=await post(h,s),seen=await operation(h,s,made.body.id,'GET','latest','read');assert.equal(seen.status,200);for(const field of ['managementReceipt','lastSequence','preparedToken','admissionTicket','slotId','writeToken','revokeToken'])assert.ok(!(field in seen.body));for(const secret of [s.body.admissionTicket,h.grants[0].bundle.preparedToken,h.metadata.macKey,h.metadata.admissionKey]){assert.ok(!seen.text.includes(secret));assert.ok(!JSON.stringify(s.config).includes(secret));}assert.equal((await h.request({path:'/v1/enroll',method:'POST',body:{}})).status,404);}finally{await h.close();}});
