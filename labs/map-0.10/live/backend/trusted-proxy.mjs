import {isIP} from 'node:net';
/** Programmatic opt-in only. Literal socket IPs, never forwarded client identities. */
export function trustedProxyPolicy(option){
 if(option===undefined)return null;
 if(!option||typeof option!=='object'||Array.isArray(option)||Object.keys(option).join(',')!=='addresses'||!Array.isArray(option.addresses)||option.addresses.length<1||option.addresses.length>32)throw Error('Trusted proxy requires 1–32 exact IP addresses');
 const addresses=new Set();
 for(const address of option.addresses){if(typeof address!=='string'||address.includes('%')||!isIP(address)||addresses.has(address))throw Error('Invalid or duplicate trusted proxy IP');addresses.add(address);}
 return Object.freeze({accepts(req){
  if(!addresses.has(req.socket.remoteAddress))return false;
  let protoCount=0,hostCount=0;
  for(let i=0;i<req.rawHeaders.length;i+=2){const name=req.rawHeaders[i].toLowerCase();if(name==='host')hostCount++;if(name==='x-forwarded-proto'){protoCount++;if(req.rawHeaders[i+1]!=='https')return false;}}
  return protoCount===1&&hostCount===1;
 }});
}
