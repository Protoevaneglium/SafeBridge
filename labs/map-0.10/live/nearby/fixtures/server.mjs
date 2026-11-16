/** Synthetic loopback fixture ONLY. No external provider, account, real geolocation or deployment. */
import http from 'node:http';import {readFile} from 'node:fs/promises';import {fileURLToPath} from 'node:url';import {resolve} from 'node:path';
import {normalize} from '../model.mjs';
const ROOT=fileURLToPath(new URL('../',import.meta.url));
const files=new Set(['index.html','map.css','map.mjs','model.mjs','tiles.mjs']);
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",'Permissions-Policy':'geolocation=(), camera=(), microphone=()'};
export const fixtureConfig=Object.freeze({endpoint:'/api/nearby',providerDisclosure:'SYNTHETIC LOOPBACK TEST ONLY. No real places or provider.',tileTemplate:'/api/tiles/{z}/{x}/{y}.png',tileAttribution:'Synthetic checker tile. NOT real street data.'});
const PNG=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAALUlEQVR4nGO48+z1f2S85fBZFExInmEYGECqBnT54WDAwMfCwBsw8LEw4AYAANNmd64nVNiBAAAAAElFTkSuQmCC','base64');
const fixtureElements=[['police',.001,0],['hospital',-.001,0],['telephone',0,.001],['wifi',0,-.001]].map(([kind,lat,lon],i)=>({type:'node',id:i+1,lat,lon,tags:{name:`SYNTHETIC ${kind}`,...(kind==='wifi'?{internet_access:'wlan'}:{amenity:kind})}}));
export function createFixture({fault='none'}={}){
 if(!['none','error','redirect','slow','oversized'].includes(fault))throw Error('Unknown fixture fault');
 let base=null;const sockets=new Set();const timers=new Set();const counts={nearby:0,tiles:0};
 function send(res,status,type,data){if(res.destroyed)return;res.writeHead(status,{'Content-Type':type,...headers});res.end(data);}
 const server=http.createServer(async(req,res)=>{try{
  if(req.headers.host!==new URL(base).host){send(res,421,'text/plain','Wrong host');return;}
  if(req.headers.origin&&req.headers.origin!==base){send(res,403,'text/plain','Wrong origin');return;}
  const path=new URL(req.url,base).pathname;
  if(path==='/api/nearby'){
   if(req.method!=='POST'){send(res,405,'text/plain','POST only');return;}if(req.headers['content-type']!=='application/json'){send(res,415,'text/plain','JSON only');return;}
   let size=0,chunks=[];for await(const chunk of req){size+=chunk.length;if(size>2048){send(res,413,'text/plain','Too large');return;}chunks.push(chunk);}let body;try{body=JSON.parse(Buffer.concat(chunks).toString());}catch{send(res,400,'text/plain','Invalid JSON');return;}
   if(body?.latitude!==0||body?.longitude!==0||body?.radius!==3000||Object.keys(body).sort().join(',')!=='latitude,longitude,radius'){send(res,400,'text/plain','Fixture only accepts synthetic center 0,0 and radius 3000');return;}
   counts.nearby++;
   if(fault==='error'){send(res,503,'text/plain','Synthetic outage');return;}
   if(fault==='redirect'){res.writeHead(302,{Location:'https://example.invalid/no-request-should-follow',...headers});res.end();return;}
   if(fault==='oversized'){send(res,200,'application/json','x'.repeat(1048577));return;}
   const reply=()=>send(res,200,'application/json',JSON.stringify(normalize({elements:fixtureElements},{latitude:0,longitude:0})));
   if(fault==='slow'){const timer=setTimeout(()=>{timers.delete(timer);reply();},200);timers.add(timer);return;}reply();return;
  }
  if(req.method!=='GET'){send(res,405,'text/plain','GET only');return;}
  if(path==='/nearby/config.mjs'){send(res,200,'text/javascript',`export const config=Object.freeze(${JSON.stringify(fixtureConfig)});`);return;}
  const tile=path.match(/^\/api\/tiles\/(\d+)\/(\d+)\/(\d+)\.png$/);if(tile){const[z,x,y]=tile.slice(1).map(Number);if(z<3||z>18||x>=2**z||y>=2**z){send(res,400,'text/plain','Invalid tile');return;}counts.tiles++;send(res,200,'image/png',PNG);return;}
  const file=path.startsWith('/nearby/')?path.slice(8):'';if(!files.has(file)){send(res,404,'text/plain','Not found');return;}
  let data=await readFile(resolve(ROOT,file));if(file==='map.mjs')data=Buffer.from("const fixtureGeolocation={getCurrentPosition(ok){ok({coords:{latitude:0,longitude:0,accuracy:10}});}};\n"+data.toString().replaceAll('navigator.geolocation','fixtureGeolocation'));if(file==='index.html')data=Buffer.from(data.toString().replace('<h1>','<h1>SYNTHETIC FIXTURE — NOT REAL PLACES: '));send(res,200,file.endsWith('.mjs')?'text/javascript':file.endsWith('.css')?'text/css':'text/html',data);
 }catch{send(res,400,'text/plain','Fixture request failed');}});
 server.requestTimeout=5000;server.headersTimeout=5000;server.on('connection',s=>{sockets.add(s);s.on('close',()=>sockets.delete(s));});
 return {counts,start:()=>new Promise((ok,fail)=>{server.once('error',fail);server.listen(0,'127.0.0.1',()=>{base=`http://127.0.0.1:${server.address().port}`;ok(base);});}),close:()=>new Promise(ok=>{for(const t of timers)clearTimeout(t);for(const s of sockets)s.destroy();server.close(()=>ok());})};
}
