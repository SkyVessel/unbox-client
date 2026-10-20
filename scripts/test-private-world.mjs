// Local Worker harness, real Minecraft ownership proof, and two isolated game processes.
import {createServer} from 'node:http';
import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir,access} from 'node:fs/promises';
import {resolve} from 'node:path';
import {database} from '../tests/social-database.mjs';
import worker from '../cloudflare/social.mjs';
const relay=process.argv.includes('--relay'),syncMods=process.argv.includes('--sync-mods'),coldJoin=process.argv.includes('--cold-join');if(coldJoin&&!syncMods)throw Error('--cold-join requires --sync-mods');
const source=process.argv[2],run=process.argv[3]||('private-'+Date.now());
if(!source||!/^[-a-z0-9]+$/.test(run))throw Error('Usage: node scripts/test-private-world.mjs WORLD_DIRECTORY RUN_NAME');
const db=database(),env={DB:db};
const http=createServer(async(req,res)=>{try{let chunks=[];for await(const b of req)chunks.push(b);const r=await worker.fetch(new Request('http://localhost'+req.url,{method:req.method,headers:req.headers,body:Buffer.concat(chunks)}),env);res.writeHead(r.status,{'content-type':'application/json'});res.end(await r.text())}catch{res.writeHead(500);res.end('{}')}});
await new Promise(r=>http.listen(0,'127.0.0.1',r));const url='http://127.0.0.1:'+http.address().port;
async function call(path,b,token){const r=await fetch(url+'/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify(b)});const v=await r.json();if(!r.ok)throw Error(v.error||path);return v}
const children=[],publicChecks=[],paths={};let stopping=false;
const fixtureRoot=resolve('.cache/network-tests',run),ids={host:crypto.randomUUID(),guest:crypto.randomUUID()};let guestProcess,restarted=false,firstDownload;
function launch(role,p,resume=false){const child=spawn('python3',['scripts/benchmark.py','private-'+role,source,run+'-'+role],{env:{...process.env,UNBOX_TEST_PROFILE:JSON.stringify(p.account),...(syncMods?{UNBOX_SYNC_FIXTURE:'1',UNBOX_BENCH_GAME_DIR:paths[role],UNBOX_SHARED_CACHE:fixtureRoot+'/shared-cache',UNBOX_BENCH_RESUME:resume?'1':'0',...(coldJoin&&role==='guest'?{UNBOX_COLD_JOIN:'1'}:{})}:{})},detached:true,stdio:['ignore','pipe','pipe']});children.push(child);child.stdout.on('data',b=>process.stdout.write(b));child.stderr.on('data',b=>process.stderr.write(b));child.on('exit',code=>{child.result=code});if(role==='guest')guestProcess=child;return child;}
async function register(kind){
 const id=crypto.randomUUID(),token=crypto.randomUUID().replaceAll('-','')+crypto.randomUUID().replaceAll('-','');let account={name:'SameName',uuid:crypto.randomUUID().replaceAll('-',''),type:kind},proof;
 if(kind==='microsoft'){
  const challenge=await call('challenge',{id},token);await mkdir(fixtureRoot,{recursive:true});const input=fixtureRoot+'/proof-request.json';await save(input,{id,token,nonce:challenge.challenge});
  const native=spawn(resolve('src-tauri/target/debug/unbox-client'),['--private-proof-fixture',input],{stdio:['ignore','pipe','inherit'],timeout:60000});let output='';native.stdout.on('data',b=>output+=b);const code=await new Promise(r=>native.on('exit',r));if(code!==0)throw Error('Real Microsoft ownership proof unavailable');const v=JSON.parse(output);account=v.account;proof=v.proof;
 }
 await call('register',{id,kind,name:account.name,uuid:account.uuid,proof},token);return {id,token,account};
}
async function load(path){try{return JSON.parse(await readFile(path,'utf8'))}catch{return null}}
async function save(path,v){await writeFile(path,JSON.stringify(v))}
try{
 const reverse=process.argv.includes('--microsoft-guest');const host=await register(reverse||process.argv.includes('--local-host')?'local':'microsoft'),guest=await register(reverse?'microsoft':'local');if(reverse)host.account.name=guest.account.name;else guest.account.name=host.account.name;publicChecks.push('Real Microsoft certificate verification (when selected) and a local identity, without email accounts');
 await call('friends/request',{code:guest.id.replaceAll('-','').slice(0,12).toUpperCase()},host.token);await call('friends/accept',{id:host.id},guest.token);
 for(const[role,p]of[['host',host],['guest',guest]]){
  paths[role]=syncMods?fixtureRoot+'/profiles/'+ids[role]:resolve('.cache/benchmarks',run+'-'+role);if(!coldJoin||role==='host')launch(role,p);
 }
 if(coldJoin){await mkdir(fixtureRoot,{recursive:true});await save(fixtureRoot+'/state.json',{profiles:[],selected:null});}
 let coldStarted=false;
 let seen=new Set(),rooms=new Map(),started=Date.now(),hostDone,guestDone;
 while(Date.now()-started<360000){
  for(const[role,p]of[['host',host],['guest',guest]]){
   const dir=paths[role];
   if(coldJoin&&role==='guest'&&!coldStarted){
    const view=await call('sync',{name:p.account.name,uuid:p.account.uuid,kind:p.account.type,state:'Online'},p.token);view.connected=true;
    const invite=view.invites.find(i=>i.receiver===view.self.id);if(!invite)continue;
    const viewPath=fixtureRoot+'/cold-invite.json';await save(viewPath,view);
    const native=spawn(resolve('src-tauri/target/debug/unbox-client'),['--prepare-invite-fixture',fixtureRoot,viewPath,invite.id,p.account.uuid],{stdio:['ignore','pipe','inherit']});let output='';native.stdout.on('data',b=>output+=b);const code=await new Promise(r=>native.on('exit',r));if(code!==0)throw Error('Cold invitation preparation failed');const profile=JSON.parse(output.trim());
    if(profile.loader!==(process.env.UNBOX_TEST_LOADER||'fabric'))throw Error('Wrong native loader selected');
    paths.guest=fixtureRoot+'/profiles/'+profile.id;ids.guest=profile.id;await save(paths.guest+'/unbox-social-view.json',{...view,updated:Date.now()});coldStarted=true;launch('guest',guest,true);
    publicChecks.push('Guest had no game or profiles; accepted invitation creates matching native-loader friend profile and auto-joins from title screen');continue;
   }
   try{await access(dir)}catch{continue}
   const world=await load(dir+'/unbox-world.json'),command=await load(dir+'/unbox-social-command.json');
   if(command&&!seen.has(command.requestId)){seen.add(command.requestId);if(command.op==='send')await call('invites/send',{id:command.id,room:world.room,joinSecret:world.grants[command.id]},p.token);else if(command.op==='relay'){if(!relay)throw Error('LAN probe failed; this test must use direct LAN');await call('invites/relay',{id:command.id},p.token);}await save(dir+'/unbox-social-ack.json',{requestId:command.requestId,ok:true,at:Date.now()});}
   if(world?.room&&rooms.get(role)!==JSON.stringify(world.room)){await call('room',{room:world.room},p.token);rooms.set(role,JSON.stringify(world.room));}
   const v=await call('sync',{name:p.account.name,uuid:p.account.uuid,kind:p.account.type,state:world?.room?'Hosting':'Playing'},p.token);if(relay&&role==='guest')for(const i of v.invites)i.room.lan=[];v.connected=true;v.updated=Date.now();await save(dir+'/unbox-social-view.json',v);
  }
  if(syncMods&&!restarted&&guestProcess?.result!=null){
   const request=await load(paths.guest+'/unbox-sync-request.json');if(request){
    firstDownload=await load(paths.guest+'/logs/environment-sync.json');if(!firstDownload?.restart||firstDownload.downloadedBytes<=0)throw Error('Guest did not transfer the host gameplay JAR');
    try{await access(paths.guest+'/mods/shared-fixture.jar');throw Error('Guest was preloaded with host fixture')}catch(e){if(e.code!=='ENOENT')throw e;}
    publicChecks.push('Guest started without the host gameplay mod; first join downloaded missing bytes and requested restart');
    const sourceFile=paths.guest+'/profile.json';await save(sourceFile,{...await load(sourceFile),id:ids.guest,loader:process.env.UNBOX_TEST_LOADER||'fabric',version:process.env.UNBOX_MINECRAFT_VERSION||'26.1'});
    const native=spawn(resolve('src-tauri/target/debug/unbox-client'),['--apply-sync-fixture',fixtureRoot,sourceFile,paths.guest+'/unbox-sync-request.json'],{stdio:['ignore','pipe','inherit']});let output='';native.stdout.on('data',b=>output+=b);const code=await new Promise(r=>native.on('exit',r));if(code!==0)throw Error('Native environment preparation failed');const profile=JSON.parse(output.trim());
    const manifest=request.manifest;for(const f of manifest.files)await access(fixtureRoot+'/shared-cache/'+f.hash+'.jar');publicChecks.push('Gameplay JAR downloaded through encrypted game login and verified in native shared cache');
    paths.guest=fixtureRoot+'/profiles/'+profile.id;await save(paths.guest+'/unbox-rejoin.json',{id:request.inviteId,at:Date.now(),accountUuid:guest.account.uuid});restarted=true;guestProcess.result=0;launch('guest',guest,true);
   }
  }
  guestDone=await load(paths.guest+'/logs/private-world-smoke.json');if(guestDone)await save(paths.host+'/logs/guest-complete.json',{});
  hostDone=await load(paths.host+'/logs/private-world-smoke.json');if(hostDone&&guestDone)break;
  if(children.some(c=>c.result!=null&&c.result!==0)&&!(syncMods&&!restarted))throw Error('Game exited before completing the test');
  await new Promise(r=>setTimeout(r,500));
 }
 if(!hostDone||!guestDone)throw Error('Two-player test did not complete');
 const result={publicChecks,host:hostDone,guest:guestDone,transport:relay?'Real e4mc public relay':'Direct LAN',...(syncMods?{sync:{firstDownload,rejoin:await load(paths.guest+'/logs/environment-sync.json')}}:{}),scope:'Two real clients on one Mac. Local Worker service; real Microsoft certificate proof for the Microsoft identity when selected. Public relay mode is not a two-household test.'};
 await mkdir('.cache/network-tests',{recursive:true});await save('.cache/network-tests/'+run+'.json',result);console.log(JSON.stringify(result));
}finally{stopping=true;for(const c of children)if(c.result==null)try{process.kill(-c.pid,'SIGTERM')}catch{};http.closeAllConnections();http.close();db.sql.close()}
