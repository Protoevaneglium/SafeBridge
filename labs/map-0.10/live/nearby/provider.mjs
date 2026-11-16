import {makeQuery,normalize} from './model.mjs';
/** Operator-side adapter; endpoint MUST be deliberately configured for permitted app use.
 * No default public volunteer endpoint, retries, logs, persistent cache or credentials. */
export async function discover({origin,radius=3000,endpoint,fetchImpl=fetch,signal}) {
 const url=new URL(endpoint);if(url.protocol!=='https:'||url.username||url.password||url.hash||url.search)throw Error('Configured HTTPS provider required');
 const controller=new AbortController();const abort=()=>controller.abort();if(signal?.aborted)controller.abort();signal?.addEventListener('abort',abort,{once:true});const timer=setTimeout(abort,18000);
 try{const response=await fetchImpl(url.href,{method:'POST',redirect:'error',credentials:'omit',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({data:makeQuery(origin,radius)}),signal:controller.signal});if(!response.ok)throw Error('Nearby provider unavailable');if(!response.body)throw Error('Empty provider response');const reader=response.body.getReader();let chunks=[],length=0;try{for(;;){const {done,value}=await reader.read();if(done)break;length+=value.byteLength;if(length>4194304)throw Error('Provider response too large');chunks.push(value);}}finally{await reader.cancel();}let bytes=new Uint8Array(length),offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}return normalize(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes)),origin,radius);}finally{clearTimeout(timer);signal?.removeEventListener('abort',abort);}
}
