// Independent fault injection for a synthetic offline CLI child only.
// Never imported by production commands. Stops immediately before the first
// SQLite write transaction so the parent can SIGKILL after private publication.
import {DatabaseSync} from 'node:sqlite';
const original=DatabaseSync.prototype.exec;
let stopped=false;
DatabaseSync.prototype.exec=function(sql){
  if(!stopped&&/\bBEGIN\s+IMMEDIATE\b/i.test(String(sql))){
    stopped=true;
    process.send?.({kind:'before-durable-transaction'});
    process.kill(process.pid,'SIGSTOP');
  }
  return original.call(this,sql);
};
