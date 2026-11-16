import { issueAdmissionTicket, readAdmissionKeyFile } from './admission.mjs';
const args=process.argv.slice(2),value=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1];};
const keyFile=value('--key-file'),generation=value('--generation'),ttlSeconds=Number(value('--ttl-seconds')??900);
if(!keyFile||!generation)throw new Error('Usage: node issue-ticket.mjs --key-file PRIVATE_FILE --generation CURRENT_GENERATION [--ttl-seconds 900]');
const admissionKey=await readAdmissionKeyFile(keyFile);
// Intentionally outputs the operator-requested short-lived ticket only, never the master key.
process.stdout.write(issueAdmissionTicket({admissionKey,generation,nowMs:Date.now(),ttlSeconds})+'\n');
admissionKey.fill(0);
