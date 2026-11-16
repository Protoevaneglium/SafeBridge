import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
const ID=/^[A-Za-z0-9_-]{22}$/;
const TOKEN=/^[A-Za-z0-9_-]{43}$/;
const DOMAIN='SafeBridge admission v1\0';
function validKey(key){return Buffer.isBuffer(key)&&key.length===32;}
function signature(key,payload){return createHmac('sha256',key).update(DOMAIN).update(payload).digest();}
function canonicalId(value){return typeof value==='string'&&ID.test(value)&&Buffer.from(value,'base64url').toString('base64url')===value;}
/** Offline operator helper; never expose the admission key to the Android app/viewer. */
export function issueAdmissionTicket({admissionKey,generation,nowMs,ttlSeconds=900,nonce=randomBytes(16).toString('base64url')}){
  if(!validKey(admissionKey)||!canonicalId(generation)||!canonicalId(nonce)||!Number.isSafeInteger(nowMs)||nowMs<0||!Number.isInteger(ttlSeconds)||ttlSeconds<1||ttlSeconds>3600||!Number.isSafeInteger(nowMs+ttlSeconds*1000))throw new Error('Invalid admission ticket parameters');
  const payload=Buffer.from(JSON.stringify({v:1,generation,nonce,issuedAtMs:nowMs,expiresAtMs:nowMs+ttlSeconds*1000})).toString('base64url');
  return payload+'.'+signature(admissionKey,payload).toString('base64url');
}
export function verifyAdmissionTicket(ticket,{admissionKey,generation,nowMs}){
  if(!validKey(admissionKey)||typeof ticket!=='string'||ticket.length>1024)return null;
  const [payload,sig,extra]=ticket.split('.');if(extra!==undefined||!payload||!TOKEN.test(sig??''))return null;
  const decoded=Buffer.from(sig,'base64url'),expected=signature(admissionKey,payload);if(decoded.length!==expected.length||decoded.toString('base64url')!==sig||Buffer.from(payload,'base64url').toString('base64url')!==payload||!timingSafeEqual(decoded,expected))return null;
  try{const v=JSON.parse(Buffer.from(payload,'base64url').toString('utf8'));
    if(!v||typeof v!=='object'||Array.isArray(v)||Object.keys(v).sort().join(',')!=='expiresAtMs,generation,issuedAtMs,nonce,v'||v.v!==1||v.generation!==generation||!canonicalId(v.nonce)||!Number.isSafeInteger(v.issuedAtMs)||v.issuedAtMs<0||!Number.isSafeInteger(v.expiresAtMs)||v.expiresAtMs-v.issuedAtMs<1000||v.expiresAtMs-v.issuedAtMs>3600000||v.issuedAtMs>nowMs||v.expiresAtMs<=nowMs)return null;
    return {nonce:v.nonce,expiresAtMs:v.expiresAtMs};
  }catch{return null;}
}
export async function readAdmissionKeyFile(path){
  const info=await stat(path);if(!info.isFile()||info.size>128||(info.mode&0o077)!==0)throw new Error('Admission key file must be a private regular file (mode 0600 or stricter)');
  const value=(await readFile(path,'utf8')).trim();if(!TOKEN.test(value)||Buffer.from(value,'base64url').length!==32||Buffer.from(value,'base64url').toString('base64url')!==value)throw new Error('Admission key file must contain one canonical 32-byte base64url secret');
  return Buffer.from(value,'base64url');
}
