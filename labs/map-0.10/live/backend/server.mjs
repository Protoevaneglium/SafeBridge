import http from 'node:http';
import { trustedProxyPolicy } from './trusted-proxy.mjs';
import https from 'node:https';
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { readFile, stat, realpath } from 'node:fs/promises';
import { resolve, dirname, extname, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';
import { verifyAdmissionTicket, readAdmissionKeyFile } from './admission.mjs';
import { verifyPreparedGrant, issuePreparedAdmissionTicket, verifyPreparedAdmissionTicket } from './prepared-admission.mjs';
import { openDurableStore, readStateKeyFile } from './durable-store.mjs';
import { createNearbyRoutes } from './nearby-routes.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const ID = /^[A-Za-z0-9_-]{22}$/;
const TERMINAL = new Set(['stopped', 'expired']);
const DEFAULTS = Object.freeze({ leaseMs: 90_000, recentMs: 30_000, permitTtlMs: 60_000,
  tombstoneMs: 86_400_000, capacity: 1_000, terminalCapacity:1_000, nonceCapacity:10_000, rateCapacity:2_000, maxBodyBytes: 4096, bodyTimeoutMs: 5000,
  globalPerMinute: 12_000, ipPerMinute: 600, createsPerWindow: 20 });
const MIME = { '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8',
  '.css':'text/css; charset=utf-8', '.svg':'image/svg+xml', '.ico':'image/x-icon',
  '.png':'image/png', '.webp':'image/webp' };
const SECURITY_HEADERS = Object.freeze({
  'Cache-Control':'no-store, max-age=0', 'Pragma':'no-cache', 'Referrer-Policy':'no-referrer',
  'Content-Security-Policy':"default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; font-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'; object-src 'none'",
  'X-Content-Type-Options':'nosniff', 'X-Frame-Options':'DENY', 'X-Robots-Tag':'noindex, nofollow, noarchive',
  'Permissions-Policy':'geolocation=(), camera=(), microphone=()', 'Cross-Origin-Resource-Policy':'same-origin'
});
function random(n) { return randomBytes(n).toString('base64url'); }
function validSecret(s) { return typeof s==='string' && TOKEN.test(s) && Buffer.from(s,'base64url').length===32 && Buffer.from(s,'base64url').toString('base64url')===s; }
function validCreationKey(s) { return typeof s==='string' && /^[A-Za-z0-9_-]{22,86}$/.test(s) && Buffer.from(s,'base64url').length>=16 && Buffer.from(s,'base64url').length<=64 && Buffer.from(s,'base64url').toString('base64url')===s; }
function jsonType(value) { return /^application\/json(?:\s*;\s*charset\s*=\s*utf-8)?\s*$/i.test(value??''); }
function same(a,b) { return Buffer.isBuffer(a) && Buffer.isBuffer(b) && a.length===b.length && timingSafeEqual(a,b); }
function objectKeys(o, names) { return o && typeof o==='object' && !Array.isArray(o) && Object.keys(o).sort().join(',')===names.slice().sort().join(','); }
function isLoopback(address) { return address==='127.0.0.1' || address==='::1' || address==='::ffff:127.0.0.1'; }
function json(res,status,payload) { if(res.writableEnded || res.destroyed)return; res.writeHead(status,{'Content-Type':'application/json; charset=utf-8',...SECURITY_HEADERS});res.end(JSON.stringify(payload)); }
function failure(res,status,error) { json(res,status,{error}); }
function body(req, limit, timeoutMs) {
  return new Promise((resolveBody,reject)=>{
    let chunks=[],size=0,settled=false;
    const finish=(err,value)=>{if(settled)return;settled=true;clearTimeout(timer);chunks=[];err?reject(err):resolveBody(value);};
    const timer=setTimeout(()=>{finish({status:408,error:'request_timeout'});req.resume();},timeoutMs);timer.unref();
    req.on('data',chunk=>{if(settled)return;size+=chunk.length;if(size>limit){finish({status:413,error:'body_too_large'});req.resume();}else chunks.push(chunk);});
    req.on('end',()=>{if(settled)return;try {finish(null,JSON.parse(Buffer.concat(chunks).toString('utf8')));} catch {finish({status:400,error:'invalid_json'});}});
    req.on('error',()=>finish({status:400,error:'bad_request'}));
    req.on('aborted',()=>finish({status:400,error:'bad_request'}));
  });
}

/** Coordinate-free durable controls with RAM-only positions; no deployment or telemetry. */
export function createSafeBridgeServer(options={}) {
  const cfg={...DEFAULTS,...options};
  const development=options.development===true;
  const proxy=trustedProxyPolicy(options.trustedProxy);
  if(proxy&&(development||options.tls!==undefined))throw new Error('Trusted proxy mode cannot mix with development or TLS');
  if(!development && !proxy && (!options.tls?.key || !options.tls?.cert)) throw new Error('TLS required; explicit development:true is loopback-only');
  let approvedOrigin,approvedHost;
  if(!development){try {const u=new URL(options.approvedOrigin);if(u.protocol!=='https:'||u.username||u.password||u.pathname!=='/'||u.search||u.hash)throw new Error();approvedOrigin=u.origin;approvedHost=u.host;}catch{throw new Error('An exact HTTPS approvedOrigin is required for production');}}
  const durableStore=options.durableStore??null;
  if(!development&&!durableStore)throw new Error('Production requires explicit initialized durableStore and prepared grants');
  const loaded=durableStore?durableStore.load():null;
  const anonymous=development&&options.allowAnonymousCreation===true;
  const legacy=development&&options.allowLegacyAdmissionForTests===true;
  if((options.allowAnonymousCreation===true||options.allowLegacyAdmissionForTests===true)&&!development)throw new Error('Anonymous/legacy admission is explicit loopback test only');
  const grantRows=new Map((loaded?.grants??[]).map(g=>[g.slotId,g]));
  const prepared=grantRows.size>0;
  if(prepared&&(anonymous||legacy))throw new Error('Prepared grants cannot be mixed with anonymous or legacy creation');
  if(!development&&!prepared)throw new Error('Production requires prepared grants reserving admission slots');
  if(!prepared&&!anonymous&&!legacy)throw new Error('Prepared grants or explicit synthetic admission mode required');
  if(legacy&&(!Buffer.isBuffer(options.admissionKey)||options.admissionKey.length!==32))throw new Error('Legacy test admissionKey required');
  if(loaded&&options.capacity!==undefined&&options.capacity!==loaded.metadata.capacity)throw new Error('Durable capacity is sealed; configuration mismatch');
  if(loaded)cfg.capacity=loaded.metadata.capacity;
  if(prepared&&!development&&loaded.metadata.endpoint!==approvedOrigin)throw new Error('Prepared grants endpoint does not match approved origin');
  for(const k of Object.keys(DEFAULTS))if(!Number.isSafeInteger(cfg[k])||cfg[k]<=0)throw new Error('Invalid server configuration');
  const nearbyRoutes=createNearbyRoutes(options.nearby);
  durableStore?.claimServer();
  const meta=loaded?.metadata??null;
  const bootElapsed=performance.now(),bootWall=Math.max(Date.now(),meta?.clockFloorMs??0);
  const clock=options.clock??(()=>Math.floor(bootWall+performance.now()-bootElapsed));
  const generation=meta?.generation??random(16),key=meta?Buffer.from(meta.macKey,'base64url'):randomBytes(32),rateKey=randomBytes(32);
  const admissionKey=meta?Buffer.from(meta.admissionKey,'base64url'):Buffer.isBuffer(options.admissionKey)?Buffer.from(options.admissionKey):null;
  const grantConfig={endpoint:meta?.endpoint??null,grants:new Map([...grantRows].filter(([,g])=>!g.disabled).map(([id,g])=>[id,Buffer.from(g.verifier,'base64url')]))};
  const rows=new Map(),creations=new Map(),terminals=new Map(),spentNonces=new Map((loaded?.spent??[]).map(n=>[n.id,n.expiresAtMs])),rateEntries=new Map(),grantRates=new Map();
  let activeCount=0,dirty=false,fatal=false;
  for(const r of loaded?.sessions??[]){const row={...r,writeHash:Buffer.from(r.writeHash,'base64url'),readHash:Buffer.from(r.readHash,'base64url'),revokeHash:Buffer.from(r.revokeHash,'base64url'),point:null,updateDigest:null,leaseUntilMs:0};if(!TERMINAL.has(row.state)){row.state='paused';activeCount++;dirty=true;}else terminals.set(row.id,row);rows.set(row.id,row);creations.set(row.creationHash,row.id);}
  const counts={requests:0,created:0,updates:0,revoked:0,expired:0,paused:0,rejected:0,rateLimited:0};
  let lastClock=meta?.clockFloorMs??0,globalWindow=0,globalCount=0;
  const now=()=>{const n=clock();if(!Number.isSafeInteger(n)||n<0)throw new Error('Invalid clock');lastClock=Math.max(lastClock,n);return lastClock;};
  function persist(){if(!durableStore||!dirty)return;try{durableStore.commit({metadata:{...meta,clockFloorMs:lastClock},sessions:[...rows.values()].map(r=>({id:r.id,state:r.state,durationSeconds:r.durationSeconds,expiresAtMs:r.expiresAtMs,purgeAtMs:r.purgeAtMs,creationHash:r.creationHash,writeHash:r.writeHash.toString('base64url'),readHash:r.readHash.toString('base64url'),revokeHash:r.revokeHash.toString('base64url'),sequence:r.sequence,slotId:r.slotId??null})),spent:[...spentNonces].map(([id,expiresAtMs])=>({id,expiresAtMs})),grants:[...grantRows.values()]});dirty=false;}catch{fatal=true;for(const r of rows.values()){r.point=null;r.updateDigest=null;}throw {status:503,error:'durable_state_unavailable'};}}
  const mac=(scope,value)=>createHmac('sha256',key).update(scope).update('\0').update(value).digest();
  const tokenMac=(scope,token)=>mac('token:'+scope,typeof token==='string'?token:'');
  const verify=(row,scope,token)=>{const candidate=tokenMac(scope,token);return same(row?.[scope+'Hash'] ?? mac('dummy',scope),candidate) && validSecret(token);};
  function drop(row){dirty=true;rows.delete(row.id);terminals.delete(row.id);if(creations.get(row.creationHash)===row.id)creations.delete(row.creationHash);}
  function end(row,state,t) {if(TERMINAL.has(row.state))return;dirty=true;activeCount--;if(row.slotId){const grant=grantRows.get(row.slotId);if(!grant||grant.activeSessionId!==row.id||grant.epoch===Number.MAX_SAFE_INTEGER)throw {status:503,error:'durable_state_unavailable'};grant.activeSessionId=null;grant.activeCreationHash=null;grant.epoch++;}row.state=state;row.point=null;row.updateDigest=null;row.purgeAtMs=t+cfg.tombstoneMs;counts[state==='stopped'?'revoked':state]++;terminals.set(row.id,row);while(terminals.size>cfg.terminalCapacity)drop(terminals.values().next().value);}
  function refresh(row,t) {if(!TERMINAL.has(row.state)){if(row.expiresAtMs!==0&&t>=row.expiresAtMs)end(row,'expired',t);else if(t>=row.leaseUntilMs&&row.state!=='paused'){row.point=null;row.state='paused';dirty=true;counts.paused++;}}return row;}
  function cleanup(t=now()) {
    for(const row of rows.values())refresh(row,t);
    for(const row of terminals.values())if(t>=row.purgeAtMs)drop(row);
    for(const [nonce,expires] of spentNonces)if(t>=expires){spentNonces.delete(nonce);dirty=true;};
    for(const [digest,entry] of rateEntries)if(t>=entry.until)rateEntries.delete(digest);
  }
  function permit(t) {const payload=JSON.stringify([generation,t,t+cfg.permitTtlMs,random(16)]);const p=Buffer.from(payload).toString('base64url');return p+'.'+mac('permit',p).toString('base64url');}
  function validPermit(p,t) {
    if(typeof p!=='string'||p.length>512)return false;const [value,sig,extra]=p.split('.');if(extra!==undefined||!value||!sig||!TOKEN.test(sig))return false;
    if(!same(mac('permit',value),Buffer.from(sig,'base64url')))return false;
    try {const v=JSON.parse(Buffer.from(value,'base64url').toString('utf8'));return Array.isArray(v)&&v.length===4&&v[0]===generation&&Number.isSafeInteger(v[1])&&Number.isSafeInteger(v[2])&&v[2]-v[1]===cfg.permitTtlMs&&v[1]<=t&&t<v[2]&&ID.test(v[3]);}catch{return false;}
  }
  function limited(address,isCreate,t) {
    const minute=Math.floor(t/60_000);if(minute!==globalWindow){globalWindow=minute;globalCount=0;}globalCount++;
    const epoch=Math.floor(t/900_000);const rotation=createHmac('sha256',rateKey).update(String(epoch)).digest();
    const digest=createHmac('sha256',rotation).update(address??'unknown').digest('hex');
    let entry=rateEntries.get(digest);if(!entry){if(rateEntries.size>=cfg.rateCapacity)return true;entry={until:(epoch+1)*900_000,minute,count:0,creates:0};rateEntries.set(digest,entry);}
    if(entry.minute!==minute){entry.minute=minute;entry.count=0;}entry.count++;if(isCreate)entry.creates++;
    return globalCount>cfg.globalPerMinute||entry.count>cfg.ipPerMinute||entry.creates>cfg.createsPerWindow;
  }
  function managementReceipt(row){const value=Buffer.from(JSON.stringify([3,generation,row.id,row.expiresAtMs,row.durationSeconds,row.writeHash.toString('base64url'),row.revokeHash.toString('base64url'),row.sequence])).toString('base64url');return value+'.'+mac('management-receipt',value).toString('base64url');}
  function status(row,t,management=true) {persist();return {id:row.id,generation,state:row.state,serverTimeMs:t,expiresAtMs:row.expiresAtMs,leaseUntilMs:row.leaseUntilMs,...(management?{managementReceipt:managementReceipt(row),lastSequence:row.sequence}:{})};}
  function missingManagementStatus(id,req,scopes,t){
    if(rows.has(id))return null;const receipt=req.headers['x-safebridge-management-receipt'];if(typeof receipt!=='string'||receipt.length>1024||req.headers['x-safebridge-generation']!==generation)return null;
    const [value,sig,extra]=receipt.split('.');if(extra!==undefined||!value||!TOKEN.test(sig??'')||!same(mac('management-receipt',value),Buffer.from(sig,'base64url')))return null;
    try{const v=JSON.parse(Buffer.from(value,'base64url').toString('utf8'));if(!Array.isArray(v)||v.length!==8||v[0]!==3||v[1]!==generation||v[2]!==id||!ID.test(id)||!Number.isSafeInteger(v[3])||v[3]<0||!Number.isSafeInteger(v[4])||v[4]<0||v[4]>2147483647||!TOKEN.test(v[5])||!TOKEN.test(v[6])||!Number.isSafeInteger(v[7])||v[7]<0)return null;
      const token=(req.headers.authorization??'').startsWith('Bearer ')?req.headers.authorization.slice(7):'';
      const hits=scopes.map(scope=>same(tokenMac(scope,token),Buffer.from(v[scope==='write'?5:6],'base64url')));if(!validSecret(token)||!hits.some(Boolean))return null;
      return {id,generation,state:'unavailable',serverTimeMs:t,expiresAtMs:v[3],leaseUntilMs:0,managementReceipt:receipt,lastSequence:v[7]};
    }catch{return null;}
  }
  function authorize(id,req,scopes,t) {
    const row=rows.get(id);const token=(req.headers.authorization??'').startsWith('Bearer ')?req.headers.authorization.slice(7):'';
    // Compute every expected scope even for missing rows, with no secret-bearing error output.
    const hits=scopes.map(scope=>verify(row,scope,token));
    if(req.headers['x-safebridge-generation']!==generation||!ID.test(id)||!row||!hits.some(Boolean))return null;
    return refresh(row,t);
  }
  function create(input,token,t,res) {
    if(!objectKeys(input,['generation','createPermit','creationKey','readToken','revokeToken','durationSeconds','admissionTicket','requestedExpiresAtMs'])||!validSecret(token)||!validSecret(input.readToken)||!validSecret(input.revokeToken)||!validCreationKey(input.creationKey)||!Number.isSafeInteger(input.durationSeconds)||input.durationSeconds<0||input.durationSeconds>2147483647||!Number.isSafeInteger(input.requestedExpiresAtMs)||input.requestedExpiresAtMs<0||((input.durationSeconds===0)!==(input.requestedExpiresAtMs===0))||!(typeof input.admissionTicket==='string'&&input.admissionTicket.length>0&&input.admissionTicket.length<=1024||anonymous&&input.admissionTicket===null)||input.generation!==generation||new Set([token,input.readToken,input.revokeToken]).size!==3)return failure(res,400,'invalid_creation');
    const ch=mac('creation',input.creationKey).toString('hex');
    const existingId=creations.get(ch);const old=existingId?rows.get(existingId):null;
    if(old){if(!verify(old,'write',token)||!verify(old,'read',input.readToken)||!verify(old,'revoke',input.revokeToken)||old.durationSeconds!==input.durationSeconds||old.expiresAtMs!==input.requestedExpiresAtMs)return failure(res,404,'not_found');return json(res,200,status(refresh(old,t),t));}
    if(input.requestedExpiresAtMs!==0&&input.requestedExpiresAtMs<=t)return failure(res,400,'chosen_end_elapsed');
    if(input.requestedExpiresAtMs!==0&&input.requestedExpiresAtMs-t>2147483647*1000)return failure(res,400,'invalid_creation');
    if(!validPermit(input.createPermit,t))return failure(res,400,'invalid_create_permit');
    let ticket=null,nonceHash=null,grant=null;
    if(prepared){ticket=verifyPreparedAdmissionTicket(input.admissionTicket,{admissionKey,generation,nowMs:t,creationKey:input.creationKey});if(!ticket)return failure(res,403,'invalid_admission_ticket');grant=grantRows.get(ticket.slotId);if(!grant||grant.disabled)return failure(res,403,'invalid_admission_ticket');if(grant.epoch!==ticket.slotEpoch)return failure(res,409,'admission_epoch_changed');if(grant.activeSessionId!==null)return failure(res,409,'prepared_slot_active');}
    else if(input.admissionTicket!==null){if(!legacy)return failure(res,403,'invalid_admission_ticket');ticket=verifyAdmissionTicket(input.admissionTicket,{admissionKey,generation,nowMs:t});if(!ticket)return failure(res,403,'invalid_admission_ticket');nonceHash=mac('spent-nonce',ticket.nonce).toString('hex');if(spentNonces.has(nonceHash))return failure(res,403,'admission_ticket_spent');}
    else if(!anonymous)return failure(res,403,'admission_required');
    if(activeCount>=cfg.capacity)return failure(res,503,'capacity_reached');
    if(nonceHash&&spentNonces.size>=cfg.nonceCapacity)return failure(res,503,'admission_capacity_reached');
    const id=random(16), expiresAtMs=input.requestedExpiresAtMs;
    if(!Number.isSafeInteger(expiresAtMs))return failure(res,400,'invalid_creation');
    const row={id,state:'waiting_for_fix',durationSeconds:input.durationSeconds,expiresAtMs,leaseUntilMs:expiresAtMs===0?t+cfg.leaseMs:Math.min(expiresAtMs,t+cfg.leaseMs),purgeAtMs:null,creationHash:ch,writeHash:tokenMac('write',token),readHash:tokenMac('read',input.readToken),revokeHash:tokenMac('revoke',input.revokeToken),point:null,sequence:0,updateDigest:null,slotId:grant?.slotId??null};
    if(nonceHash)spentNonces.set(nonceHash,ticket.expiresAtMs);if(grant){grant.activeSessionId=id;grant.activeCreationHash=ch;}dirty=true;
    rows.set(id,row);activeCount++;creations.set(ch,id);counts.created++;return json(res,201,status(row,t));
  }
  function update(row,input,t,res,bodyElapsedMs=0) {
    if(TERMINAL.has(row.state))return failure(res,409,'session_ended');
    if(!objectKeys(input,['sequence','latitude','longitude','accuracyM','sampleTimeMs','sampleAgeMs','approximate','mock'])||!Number.isSafeInteger(input.sequence)||input.sequence<1||!Number.isFinite(input.latitude)||input.latitude < -90||input.latitude>90||!Number.isFinite(input.longitude)||input.longitude < -180||input.longitude>180||!Number.isFinite(input.accuracyM)||input.accuracyM<0||input.accuracyM>100_000||!Number.isSafeInteger(input.sampleTimeMs)||input.sampleTimeMs<0||!Number.isSafeInteger(input.sampleAgeMs)||input.sampleAgeMs<0||input.sampleAgeMs>30_000||typeof input.approximate!=='boolean'||typeof input.mock!=='boolean')return failure(res,400,'invalid_point');
    const canonical=JSON.stringify([input.sequence,input.latitude,input.longitude,input.accuracyM,input.sampleTimeMs,input.sampleAgeMs,input.approximate,input.mock]);
    const digest=mac('update',canonical);
    if(input.sequence===row.sequence){if(!same(row.updateDigest,digest))return failure(res,409,'sequence_conflict');return json(res,200,status(row,t));}
    if(input.sequence<row.sequence)return failure(res,409,'out_of_order');
    const effectiveAgeMs=input.sampleAgeMs+Math.max(0,bodyElapsedMs);if(effectiveAgeMs>30_000)return failure(res,400,'stale_point');
    dirty=true;row.sequence=input.sequence;row.updateDigest=digest;row.point={...input,sampleAgeMs:effectiveAgeMs,receivedAtMs:t};row.state='active';row.leaseUntilMs=row.expiresAtMs===0?t+cfg.leaseMs:Math.min(row.expiresAtMs,t+cfg.leaseMs);counts.updates++;return json(res,200,status(row,t));
  }
  async function staticFile(req,res,pathname) {
    if(req.method!=='GET'&&req.method!=='HEAD')return failure(res,405,'method_not_allowed');
    if(pathname==='/')pathname='/view';if(pathname==='/view'||pathname==='/view/')pathname='/index.html';
    let decoded;try {decoded=decodeURIComponent(pathname);}catch{return failure(res,404,'not_found');}
    if(decoded.includes('\0')||decoded.includes('\\')||decoded.split('/').includes('..'))return failure(res,404,'not_found');
    const webRoot=resolve(options.webRoot??resolve(HERE,'../web'));const path=resolve(webRoot,'.'+decoded);
    if(!path.startsWith(webRoot+sep)||!MIME[extname(path)])return failure(res,404,'not_found');
    try {const actualRoot=await realpath(webRoot),actualPath=await realpath(path);if(!actualPath.startsWith(actualRoot+sep))return failure(res,404,'not_found');const st=await stat(actualPath);if(!st.isFile()||st.size>1_048_576)return failure(res,404,'not_found');const data=await readFile(actualPath);res.writeHead(200,{'Content-Type':MIME[extname(path)],...SECURITY_HEADERS});res.end(req.method==='HEAD'?undefined:data);}catch{return failure(res,404,'not_found');}
  }
  async function handler(req,res) {
    counts.requests++;
    try {
      if(proxy&&!proxy.accepts(req))return failure(res,403,'trusted_proxy_required');
      if(fatal)return failure(res,503,'durable_state_unavailable');
      if(development&&(!isLoopback(req.socket.remoteAddress)||! /^(localhost|127\.0\.0\.1|\[::1\])(?::\d{1,5})?$/.test(req.headers.host??'')))return failure(res,403,'loopback_only');
      if(!development && req.headers.host?.toLowerCase()!==approvedHost)return failure(res,403,'origin_not_allowed');
      if(!development)res.setHeader('Strict-Transport-Security','max-age=31536000');
      const origin=development?'http://'+req.headers.host:approvedOrigin;
      if(req.headers.origin && req.headers.origin!==origin)return failure(res,403,'origin_not_allowed');
      const url=new URL(req.url,origin);if(url.search)return failure(res,400,'query_not_allowed');
      const path=url.pathname,t=now();cleanup(t);persist();
      // A shared-network/global request budget must not obstruct the first valid Stop.
      // This path is bounded to one transition per already-created session. Wrong
      // credentials and all terminal retries still pass through ordinary rate limits.
      const firstRevoke=path.match(/^\/v1\/shares\/([A-Za-z0-9_-]+)$/);
      if(req.method==='DELETE' && firstRevoke){
        const row=authorize(firstRevoke[1],req,['revoke'],t);
        if(row && !TERMINAL.has(row.state)){end(row,'stopped',t);return json(res,200,status(row,t));}
      }
      let resetInput=null;
      if(prepared&&req.headers['x-safebridge-generation']===generation&&path==='/v1/admission/current'&&req.method==='DELETE'&&jsonType(req.headers['content-type'])&&(!req.headers['content-encoding']||req.headers['content-encoding'].toLowerCase()==='identity')){
        try{resetInput=await body(req,cfg.maxBodyBytes,cfg.bodyTimeoutMs);}catch(error){if(limited(req.socket.remoteAddress,false,now()))return failure(res,429,'rate_limited');throw error;}
        const at=now();cleanup(at);persist();const token=(req.headers.authorization??'').startsWith('Bearer ')?req.headers.authorization.slice(7):'';
        if(objectKeys(resetInput,['slotId'])&&verifyPreparedGrant(grantConfig,resetInput.slotId,token)){const grant=grantRows.get(resetInput.slotId),active=grant.activeSessionId?rows.get(grant.activeSessionId):null;if(active){end(active,'stopped',at);persist();return json(res,200,{generation,slotId:grant.slotId,state:'stopped',serverTimeMs:at,slotEpoch:grant.epoch});}}
      }
      // Independent map budget: never consumes live-session update/Stop allowance.
      if(path==='/nearby'||path.startsWith('/nearby/')||path==='/api/nearby'||path.startsWith('/api/tiles/')||path==='/v1/map-config'){await nearbyRoutes.handle(req,res,path);return;}
      if(limited(req.socket.remoteAddress,path==='/v1/shares'&&req.method==='POST',t)){counts.rateLimited++;return failure(res,429,'rate_limited');}
      if(path==='/v1/config'&&req.method==='GET')return json(res,200,{protocolVersion:3,durableSessionState:!!durableStore,preparedAdmission:prepared,admissionRequired:!anonymous,generation,createPermit:permit(t),serverTimeMs:t,minDurationSeconds:1,maxDurationSeconds:2147483647,untilStopSupported:true,leaseMs:cfg.leaseMs,recentMs:cfg.recentMs});
      if(path==='/v1/shares'&&req.method==='POST'){
        if(!jsonType(req.headers['content-type']) || (req.headers['content-encoding'] && req.headers['content-encoding'].toLowerCase()!=='identity'))return failure(res,415,'unsupported_media_type');
        const input=await body(req,cfg.maxBodyBytes,cfg.bodyTimeoutMs);
        const token=(req.headers.authorization??'').startsWith('Bearer ')?req.headers.authorization.slice(7):'';
        // All state changes are synchronous after body parsing; refresh time after slow bodies.
        const at=now();cleanup(at);return create(input,token,at,res);
      }
      if((path==='/v1/admission'&&req.method==='POST')||(path==='/v1/admission/current'&&req.method==='DELETE')){
        if(!prepared||req.headers['x-safebridge-generation']!==generation)return failure(res,404,'not_found');
        if(!jsonType(req.headers['content-type'])||(req.headers['content-encoding']&&req.headers['content-encoding'].toLowerCase()!=='identity'))return failure(res,415,'unsupported_media_type');
        const input=resetInput??await body(req,cfg.maxBodyBytes,cfg.bodyTimeoutMs),at=now();cleanup(at);persist();
        const reset=req.method==='DELETE',expected=reset?['slotId']:['slotId','creationKey','expectedSlotEpoch'];
        if(!objectKeys(input,expected)||!ID.test(input.slotId??'')||(!reset&&(!validCreationKey(input.creationKey)||!(input.expectedSlotEpoch===null||Number.isSafeInteger(input.expectedSlotEpoch)&&input.expectedSlotEpoch>=0))))return failure(res,400,'invalid_admission_request');
        const token=(req.headers.authorization??'').startsWith('Bearer ')?req.headers.authorization.slice(7):'';
        if(!verifyPreparedGrant(grantConfig,input.slotId,token))return failure(res,404,'not_found');
        const grant=grantRows.get(input.slotId);let rate=grantRates.get(input.slotId);const minute=Math.floor(at/60000);if(!rate||rate.minute!==minute){rate={minute,count:0};grantRates.set(input.slotId,rate);}rate.count++;if(rate.count>30)return failure(res,429,'prepared_rate_limited');
        if(reset){const active=grant.activeSessionId?rows.get(grant.activeSessionId):null;if(active)end(active,'stopped',at);else{if(grant.epoch===Number.MAX_SAFE_INTEGER)return failure(res,409,'admission_epoch_exhausted');grant.epoch++;dirty=true;}persist();return json(res,200,{generation,slotId:grant.slotId,state:active?'stopped':'none',serverTimeMs:at,slotEpoch:grant.epoch});}
        if(input.expectedSlotEpoch!==null&&input.expectedSlotEpoch!==grant.epoch)return failure(res,409,'admission_epoch_changed');
        const hash=mac('creation',input.creationKey).toString('hex');if(grant.activeSessionId!==null&&grant.activeCreationHash!==hash)return failure(res,409,'prepared_slot_active');
        const expiresAtMs=at+120000,admissionTicket=issuePreparedAdmissionTicket({admissionKey,generation,slotId:grant.slotId,slotEpoch:grant.epoch,creationKey:input.creationKey,nowMs:at,ttlSeconds:120});return json(res,200,{generation,slotId:grant.slotId,creationKey:input.creationKey,slotEpoch:grant.epoch,admissionTicket,expiresAtMs});
      }
      const route=path.match(/^\/v1\/shares\/([A-Za-z0-9_-]+)(?:\/(latest|status))?$/);
      if(route){
        const [,id,action]=route;
        const scopes=action==='latest'&&req.method==='GET'?['read']:action==='latest'&&req.method==='PUT'?['write']:action==='status'&&req.method==='GET'?['write','revoke']:!action&&req.method==='DELETE'?['revoke']:null;
        if(!scopes)return failure(res,405,'method_not_allowed');
        let row=authorize(id,req,scopes,t);if(!row){if(action==='status'||req.method==='DELETE'){const unavailable=missingManagementStatus(id,req,scopes,t);if(unavailable)return json(res,200,unavailable);}counts.rejected++;return failure(res,404,'not_found');}
        if(req.method==='PUT'){
          if(!jsonType(req.headers['content-type']) || (req.headers['content-encoding'] && req.headers['content-encoding'].toLowerCase()!=='identity'))return failure(res,415,'unsupported_media_type');
          const input=await body(req,cfg.maxBodyBytes,cfg.bodyTimeoutMs),at=now();cleanup(at);row=authorize(id,req,scopes,at);if(!row)return failure(res,404,'not_found');return update(row,input,at,res,at-t);
        }
        if(req.method==='DELETE'){end(row,'stopped',t);return json(res,200,status(row,t));}
        if(action==='status')return json(res,200,status(row,t));
        const point=row.point?{...row.point}:null;const pointAgeMs=point?Math.max(0,t-point.receivedAtMs+point.sampleAgeMs):null;
        return json(res,200,{...status(row,t,false),point,pointAgeMs,freshness:TERMINAL.has(row.state)?'ended':row.state==='paused'?'paused':!point?'waiting':pointAgeMs<=cfg.recentMs?'recent':'stale'});
      }
      if(path.startsWith('/v1/'))return failure(res,404,'not_found');
      return await staticFile(req,res,path);
    }catch(err){failure(res,err?.status??400,err?.error??'bad_request');}
  }
  const server=(development||proxy)?http.createServer({maxHeaderSize:8192},handler):https.createServer({...options.tls,maxHeaderSize:8192},handler);
  server.requestTimeout=10_000;server.headersTimeout=5_000;server.keepAliveTimeout=1_000;server.maxRequestsPerSocket=100;
  server.on('clientError',(_err,socket)=>{if(socket.writable)socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n');});
  while(terminals.size>cfg.terminalCapacity)drop(terminals.values().next().value);
  cleanup(now());persist();
  const timer=setInterval(()=>{try{cleanup();persist();}catch{fatal=true;for(const row of rows.values())row.point=null;}},1000);timer.unref();
  return {server,generation,stats:()=>({...counts,activeSessions:activeCount,terminalSessions:terminals.size,spentTickets:spentNonces.size,rateBuckets:rateEntries.size}),close:async()=>{nearbyRoutes.close();clearInterval(timer);server.closeAllConnections();if(server.listening)await new Promise((resolveClose,reject)=>server.close(e=>e?reject(e):resolveClose()));durableStore?.close();}};
}

if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const args=process.argv.slice(2),value=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1];};
  const dev=args.includes('--dev-loopback'),port=Number(value('--port')??8787),host=value('--host')??'127.0.0.1';
  if(!Number.isInteger(port)||port<1||port>65535)throw new Error('Invalid port');
  if(dev&&host!=='127.0.0.1')throw new Error('Development HTTP binds only to 127.0.0.1');
  let tls;
  if(!dev){const cert=value('--tls-cert'),key=value('--tls-key');if(!cert||!key)throw new Error('Use --tls-cert/--tls-key, or explicit --dev-loopback for local synthetic tests');tls={cert:await readFile(cert),key:await readFile(key),minVersion:'TLSv1.2'};}
  const admissionFile=value('--admission-key-file'),admissionKey=admissionFile?await readAdmissionKeyFile(admissionFile):undefined;
  const stateDirectory=value('--state-directory'),stateKeyFile=value('--state-key-file'),durableStore=stateDirectory&&stateKeyFile?openDurableStore({directory:stateDirectory,stateKey:readStateKeyFile(stateKeyFile)}):undefined;
  const app=createSafeBridgeServer({development:dev,tls,approvedOrigin:value('--approved-origin'),admissionKey,durableStore,allowLegacyAdmissionForTests:args.includes('--allow-legacy-admission-test'),allowAnonymousCreation:args.includes('--allow-anonymous-test-creation')});
  app.server.listen(port,host,()=>process.stdout.write(dev?'Safe Bridge synthetic lab listening on loopback HTTP. Not a deployed service.\n':'Safe Bridge HTTPS listener started. Deployment safety review still required.\n'));
  for(const signal of ['SIGINT','SIGTERM'])process.once(signal,async()=>{await app.close();process.exit(0);});
}
