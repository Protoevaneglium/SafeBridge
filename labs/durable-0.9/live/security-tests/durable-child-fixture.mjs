// Independent synthetic crash fixture. No production command imports this module.
import {createSafeBridgeServer} from '../backend/server.mjs';
import {openDurableStore} from '../backend/durable-store.mjs';
let app,now=1800000000000,hold=null,watch=null;
const decode=value=>value&&typeof value==='object'?(Array.isArray(value)?value.map(decode):Object.keys(value).length===1&&typeof value.base64Bytes==='string'?Buffer.from(value.base64Bytes,'base64'):Object.fromEntries(Object.entries(value).map(([k,v])=>[k,decode(v)]))):value;
const send=value=>process.send?.(value);
process.on('message',async message=>{
  try{
    if(message.command==='boot'){
      if(app)throw new Error('Already booted');now=message.nowMs??now;
      const options=decode(message.options??{});
      const durableStore=message.durable?openDurableStore({directory:message.durable.directory,stateKey:Buffer.from(message.durable.stateKey,'base64')}):null;
      try{app=createSafeBridgeServer({...options,...(durableStore?{durableStore}:{}),clock:()=>now});}catch(error){durableStore?.close();throw error;}
      app.server.prependListener('request',(req,res)=>{
        if(watch&&watch.path===req.url){const captured=watch;watch=null;req.once('data',()=>send({kind:'body-started',marker:captured.marker}));}
        const original=res.end;
        res.end=function(...args){
          if(hold&&hold.method===req.method&&hold.path===req.url&&res.statusCode>=200&&res.statusCode<300){
            const captured=hold;hold=null;
            // The normal handler reached its acknowledgement after committing.
            // Deliberately never flush it. The parent then SIGKILLs this process.
            send({kind:'ack-held',marker:captured.marker,status:res.statusCode,body:args[0]==null?null:JSON.parse(String(args[0]))});
            return this;
          }
          return original.apply(this,args);
        };
      });
      await new Promise((resolve,reject)=>{app.server.once('error',reject);app.server.listen(0,'127.0.0.1',resolve);});
      send({kind:'ready',port:app.server.address().port,generation:app.generation});
    }else if(message.command==='hold'){
      hold={method:message.method,path:message.path,marker:message.marker};send({kind:'armed',marker:message.marker});
    }else if(message.command==='watch-body'){
      watch={path:message.path,marker:message.marker};send({kind:'armed',marker:message.marker});
    }else if(message.command==='advance'){
      now+=message.ms;send({kind:'advanced',marker:message.marker,nowMs:now});
    }else if(message.command==='close'){
      await app?.close();send({kind:'closed'});process.disconnect();
    }else throw new Error('Unknown fixture command');
  }catch(error){send({kind:'fixture-error',error:String(error?.message??error)});if(message.command==='boot')process.exitCode=1;}
});
process.on('disconnect',()=>{if(!app)process.exit(0);});
