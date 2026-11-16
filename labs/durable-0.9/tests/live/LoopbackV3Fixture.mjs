// Synthetic HOST TEST ONLY. Private captured operator IPC; no application capability logging.
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createInterface } from 'node:readline';
import { createSafeBridgeServer } from '../../live/backend/server.mjs';
import { initializeDurableStore, openDurableStore } from '../../live/backend/durable-store.mjs';
import { createPreparedGrant, validatePreparedGrantConfig } from '../../live/backend/prepared-admission.mjs';
process.umask(0o077);
const directory=process.argv[2],port=Number(process.argv[3]),keyFile=join(directory,'state.key'),stateDirectory=join(directory,'state');
const origin='https://durable.example.invalid';let now=1800000000000,stateKey,store,accesses;
if(!existsSync(keyFile)){
  stateKey=randomBytes(32);writeFileSync(keyFile,stateKey.toString('base64url'),{mode:0o600,flag:'wx'});
  const a=createPreparedGrant({endpoint:origin}),b=createPreparedGrant({endpoint:origin});
  const config=validatePreparedGrantConfig({v:1,endpoint:origin,grants:[a.grant,b.grant]},{endpoint:origin,capacity:2});
  store=initializeDurableStore({directory:stateDirectory,stateKey,capacity:2,preparedGrants:config,nowMs:now});
  accesses=[a.bundle,b.bundle];
}else{
  stateKey=Buffer.from(readFileSync(keyFile,'utf8'),'base64url');store=openDurableStore({directory:stateDirectory,stateKey});now=store.load().metadata.clockFloorMs;
}
const app=createSafeBridgeServer({development:true,durableStore:store,clock:()=>now,terminalCapacity:2,
  createsPerWindow:10000,ipPerMinute:100000,globalPerMinute:100000});
await new Promise(resolve=>app.server.listen(port,'127.0.0.1',resolve));
process.stdout.write(JSON.stringify({ready:true,...(accesses?{accesses}:{})})+'\n');
const lines=createInterface({input:process.stdin});let closing=false;
async function close(){if(closing)return;closing=true;await app.close();stateKey.fill(0);process.exit(0);}
lines.on('line',async line=>{
  if(/^advance [0-9]+$/.test(line)){now+=Number(line.slice(8));process.stdout.write('{"advanced":true}\n');}
  else if(line==='inspect'){
    const snapshot=store.load(),text=JSON.stringify(snapshot);
    const coordinateFree=!/"(?:latitude|longitude|point|accuracyM|sampleTimeMs|sampleAgeMs|receivedAtMs)"/.test(text);
    process.stdout.write(JSON.stringify({coordinateFree,sessions:snapshot.sessions.length,...app.stats()})+'\n');
  }else if(line==='close')await close();else process.stdout.write('{"error":"invalid_test_command"}\n');
});
process.on('SIGTERM',close);process.on('SIGINT',close);
