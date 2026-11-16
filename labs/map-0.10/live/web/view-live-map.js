import {tileLayout,tilePath} from '../nearby/tiles.mjs';
import {readBoundedJson} from './view-network.js';
/** Local recipient visualization. Only validated recipient view state enters this class.
 * No share IDs/tokens are accepted, stored, or sent to the map service. */
export class LiveMapPanel {
 constructor(doc=globalThis.document,fetchImpl=(...args)=>fetch(...args)){
  this.doc=doc;this.fetch=fetchImpl;this.nodes=Object.fromEntries(['live-map-toggle','live-map-panel','live-map-svg','live-map-status','live-map-streets'].map(id=>[id,doc.getElementById(id)]));this.open=false;this.current=null;this.config=null;this.tilesEnabled=false;this.request=null;this.version=0;this.drawKey=null;this.images=new Map();
  this.nodes['live-map-toggle']?.addEventListener('click',()=>{const current=this.current;this.open=!this.open;if(!this.open){this.clear();this.current=current;}else this.render(current);});this.nodes['live-map-streets']?.addEventListener('click',()=>this.streetAction());
 }
 clear(){this.version++;this.request?.abort();this.request=null;this.open=false;this.config=null;this.tilesEnabled=false;this.current=null;this.drawKey=null;this.images.clear();this.nodes['live-map-svg']?.replaceChildren();if(this.nodes['live-map-panel'])this.nodes['live-map-panel'].hidden=true;if(this.nodes['live-map-status'])this.nodes['live-map-status'].textContent='';if(this.nodes['live-map-toggle'])this.nodes['live-map-toggle'].textContent='Live position plot';if(this.nodes['live-map-streets'])this.nodes['live-map-streets'].textContent='Check street-map option';}
 render(current){
  if(!this.nodes['live-map-panel'])return;
  const unavailable=!current||['stopped','expired','unavailable','cleared'].includes(current.phase);if(unavailable){this.clear();this.nodes['live-map-toggle'].disabled=true;return;}
  this.current=current;this.nodes['live-map-toggle'].disabled=!current.point;
  if(!this.open||current.phase==='hidden'){this.nodes['live-map-panel'].hidden=true;this.nodes['live-map-svg'].replaceChildren();this.images.clear();this.drawKey=null;return;}
  this.nodes['live-map-panel'].hidden=false;this.nodes['live-map-toggle'].textContent='Close position plot';
  if(!current.point){this.nodes['live-map-svg'].replaceChildren();this.nodes['live-map-status'].textContent='Waiting for a verified device fix. No position invented.';return;}
  const p=current.point;const age=current.ageMs===null?'age unknown':`${Math.floor(current.ageMs/1000)} seconds old`;
  this.nodes['live-map-status'].textContent=`${['last_known','stale','hidden'].includes(current.phase)?'LAST KNOWN — current position unverified. ':''}${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)} · ${age} · estimated accuracy ${p.accuracyM} m. ${p.mock?'Synthetic/mock fix. ':''}${this.tilesEnabled?'Street-map data: '+this.config.tileAttribution+'. No safe-route guarantee.':'Coordinate plot only; not a street map. No third-party map request.'}`;
  if(this.config&&!this.tilesEnabled)this.nodes['live-map-status'].textContent+=' '+this.config.providerDisclosure+' The displayed map area will update as verified fixes arrive while this view is open. No viewing token is sent. Choose Load street tiles only if this is appropriate.';
  this.nodes['live-map-streets'].disabled=!!this.request||this.tilesEnabled;
  const key=`${p.latitude},${p.longitude},${p.accuracyM},${this.tilesEnabled}`;if(key===this.drawKey)return;this.drawKey=key;
  const svg=this.nodes['live-map-svg'];const create=(tag,attrs,text)=>{const n=this.doc.createElementNS('http://www.w3.org/2000/svg',tag);for(const[k,v]of Object.entries(attrs))n.setAttribute(k,v);if(text)n.textContent=text;svg.append(n);return n;};
  if(this.tilesEnabled){
   // Reuse unchanged tile nodes instead of requesting them every one-second age tick.
   const keep=new Set();for(const tile of tileLayout(p,15)){const id=`${tile.z}/${tile.x}/${tile.y}`;keep.add(id);let n=this.images.get(id);if(!n){n=create('image',{href:tilePath(this.config.tileTemplate,tile),width:256,height:256});const version=this.version;n.addEventListener('error',()=>{if(this.open&&version===this.version)this.nodes['live-map-status'].textContent='Some street tiles unavailable. No route or current position can be inferred from missing map data.';});this.images.set(id,n);}n.setAttribute('x',tile.left);n.setAttribute('y',tile.top);}
   for(const[id,node]of this.images)if(!keep.has(id)){node.remove();this.images.delete(id);}
   for(const n of Array.from(svg.children))if(n.getAttribute('data-overlay')==='yes')n.remove();
   const c=create('circle',{cx:300,cy:300,r:9,fill:'#9b351c','data-overlay':'yes'});c.setAttribute('aria-label','Latest verified or labeled last-known device fix');
  }else{svg.replaceChildren();this.images.clear();create('rect',{x:0,y:0,width:600,height:600,fill:'#eff8fa',stroke:'#79939a'});create('line',{x1:0,y1:300,x2:600,y2:300,stroke:'#79939a'});create('line',{x1:300,y1:0,x2:300,y2:600,stroke:'#79939a'});create('circle',{cx:(p.longitude+180)/360*600,cy:(90-p.latitude)/180*600,r:8,fill:'#9b351c'});create('text',{x:10,y:25},'Global coordinates · north up · no street data');}
 }
 async streetAction(){if(!this.open||!this.current?.point||this.doc.visibilityState!=='visible')return;
  if(this.config){this.tilesEnabled=true;this.render(this.current);return;}
  const version=++this.version,controller=new AbortController();this.request=controller;this.nodes['live-map-streets'].disabled=true;const timer=setTimeout(()=>controller.abort(),8000);
  try{const response=await this.fetch('/v1/map-config',{credentials:'omit',cache:'no-store',redirect:'error',referrerPolicy:'no-referrer',signal:controller.signal});if(!response.ok)throw Error();const c=await readBoundedJson(response);if(version!==this.version||!this.open||this.doc.visibilityState!=='visible')return;if(c.tileTemplate!=='/api/tiles/{z}/{x}/{y}.png'||typeof c.tileAttribution!=='string'||!c.tileAttribution.trim()||c.tileAttribution.length>1000||typeof c.providerDisclosure!=='string'||!c.providerDisclosure.trim()||c.providerDisclosure.length>2000)throw Error();this.config={tileTemplate:c.tileTemplate,tileAttribution:c.tileAttribution,providerDisclosure:c.providerDisclosure};this.nodes['live-map-streets'].textContent='Load street tiles';}
  catch{if(version===this.version&&this.open){this.nodes['live-map-status'].textContent='Street map is not configured or unavailable. Coordinate plot remains available; no street tiles were requested.';}}
  finally{clearTimeout(timer);if(version===this.version){this.request=null;this.nodes['live-map-streets'].disabled=false;if(this.config)this.render(this.current);}}
 }
 suspend(){this.version++;this.request?.abort();this.request=null;this.nodes['live-map-svg']?.replaceChildren();this.images.clear();this.drawKey=null;if(this.nodes['live-map-panel'])this.nodes['live-map-panel'].hidden=true;}
}
