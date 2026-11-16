// HOST TEST ONLY: deterministic clock and private parent/child control pipe.
// Tickets pass only over the captured operator test pipe; never application logs.
import { randomBytes } from 'node:crypto';
import { createInterface } from 'node:readline';
import { createSafeBridgeServer } from '../../live/backend/server.mjs';
import { issueAdmissionTicket } from '../../live/backend/admission.mjs';
const ticketed=process.argv[2]==='ticketed',port=Number(process.argv[3]??18790);
let now=1800000000000;
const admissionKey=randomBytes(32);
const app=createSafeBridgeServer({development:true,allowAnonymousCreation:!ticketed,admissionKey,
  clock:()=>now,createsPerWindow:10000,ipPerMinute:100000,globalPerMinute:100000,
  terminalCapacity:1000,capacity:1000});
await new Promise(resolve=>app.server.listen(port,'127.0.0.1',resolve));
process.stdout.write('{"ready":true}\n');
const lines=createInterface({input:process.stdin});
lines.on('line',async line=>{
  if(/^advance [0-9]+$/.test(line)){now+=Number(line.slice(8));process.stdout.write('{"advanced":true}\n');}
  else if((line==='ticket'||line==='ticketlong')&&ticketed){const ticket=issueAdmissionTicket({admissionKey,generation:app.generation,nowMs:now,ttlSeconds:line==='ticketlong'?900:1});process.stdout.write(JSON.stringify({ticket})+'\n');}
  else if(line==='close'){lines.close();await app.close();process.exit(0);}
  else process.stdout.write('{"error":"invalid_test_command"}\n');
});
lines.on('close',()=>{app.close();});
