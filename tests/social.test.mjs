import {test} from 'node:test';
import assert from 'node:assert/strict';
import {database} from './social-database.mjs';
import worker,{room} from '../cloudflare/social.mjs';

const player=(name,kind='local')=>({id:crypto.randomUUID(),token:(crypto.randomUUID()+crypto.randomUUID()).replaceAll('-',''),name,uuid:crypto.randomUUID().replaceAll('-',''),kind});
const sampleRoom=()=>({id:crypto.randomUUID(),name:'Friends world',port:25565,probePort:25566,probeToken:'a'.repeat(64),lan:['192.168.1.5'],relay:null,protocol:2});
async function send(db,p,path,b={}){const r=await worker.fetch(new Request('https://friends.test/v1/'+path,{method:'POST',headers:{Authorization:'Bearer '+p.token},body:JSON.stringify(b)}),{DB:db});return {status:r.status,...await r.json()}}
async function register(db,p){assert.equal((await send(db,p,'register',p)).status,201)}
async function befriend(db,a,b){assert.equal((await send(db,a,'friends/request',{code:b.id.replaceAll('-','').slice(0,12).toUpperCase()})).status,200);assert.equal((await send(db,b,'friends/accept',{id:a.id})).status,200)}
test('friends require receiver acceptance, expose only own graph, and removal is bilateral',async()=>{const db=database(),a=player('Alice'),b=player('Bob'),c=player('Carol');try{for(const p of[a,b,c])await register(db,p);await send(db,a,'friends/request',{code:b.id.replaceAll('-','').slice(0,12).toUpperCase()});await send(db,a,'friends/accept',{id:b.id});assert.equal((await send(db,a,'sync',a)).friends[0].accepted,0);assert.equal((await send(db,b,'sync',b)).friends[0].direction,'incoming');assert.equal((await send(db,c,'sync',c)).friends.length,0);await send(db,b,'friends/accept',{id:a.id});assert.equal((await send(db,a,'sync',a)).friends[0].accepted,1);await send(db,b,'friends/remove',{id:a.id});assert.equal((await send(db,a,'sync',a)).friends.length,0)}finally{db.sql.close()}});
test('invites gate authenticated friendship, relay requests, expiry and sender room updates',async()=>{const db=database(),a=player('Alice'),b=player('Bob'),c=player('Carol');try{for(const p of[a,b,c])await register(db,p);await befriend(db,a,b);assert.equal((await send(db,a,'invites/send',{id:b.id,room:sampleRoom()})).status,400);for(const p of[a,b,c]){p.kind='microsoft';db.sql.prepare('UPDATE people SET kind=? WHERE id=?').run(p.kind,p.id)}const r=sampleRoom();assert.equal((await send(db,a,'invites/send',{id:c.id,room:r,joinSecret:'b'.repeat(64)})).status,403);assert.equal((await send(db,a,'invites/send',{id:b.id,room:r,joinSecret:'b'.repeat(64)})).status,200);let invite=(await send(db,b,'sync',b)).invites[0];assert.equal((await send(db,c,'invites/relay',{id:invite.id})).status,404);assert.equal((await send(db,b,'invites/relay',{id:invite.id})).status,200);assert.equal((await send(db,a,'sync',a)).invites[0].relay_requested,1);await send(db,c,'room',{room:{...r,relay:'evil.e4mc.link'}});assert.equal((await send(db,b,'sync',b)).invites[0].room.relay,null);await send(db,a,'room',{room:{...r,relay:'host.e4mc.link'}});assert.equal((await send(db,b,'sync',b)).invites[0].room.relay,'host.e4mc.link');db.sql.prepare('UPDATE invites SET expires=0').run();assert.equal((await send(db,b,'invites/relay',{id:invite.id})).status,404);assert.equal((await send(db,b,'sync',b)).invites.length,0);await worker.scheduled({}, {DB:db});assert.equal(db.sql.prepare('SELECT COUNT(*) AS n FROM invites').get().n,0)}finally{db.sql.close()}});
test('Mojang verification is required, cannot replace another social identity or change UUID',async()=>{const db=database(),p=player('Alice','microsoft'),original=globalThis.fetch;try{assert.equal((await send(db,p,'register',p)).status,401);await send(db,p,'challenge',{id:p.id});globalThis.fetch=async()=>Response.json({id:'f'.repeat(32)});assert.equal((await send(db,p,'register',p)).status,401);globalThis.fetch=async()=>Response.json({id:p.uuid});await register(db,p);assert.equal((await send(db,{...p,token:'b'.repeat(64)},'register',p)).status,409);assert.equal((await send(db,p,'sync',{...p,uuid:'a'.repeat(32)})).status,403)}finally{globalThis.fetch=original;db.sql.close()}});
test('invalid addresses and missing service fail closed',async()=>{assert.throws(()=>room({...sampleRoom(),lan:['8.8.8.8']}));assert.throws(()=>room({...sampleRoom(),relay:'evil.example.com'}));assert.throws(()=>room({...sampleRoom(),port:70000}));assert.equal((await worker.fetch(new Request('https://friends.test/v1/sync',{method:'POST'}),{})).status,503)});
test('Microsoft and registered Unbox accounts can add each other and exchange private-world invitations',async()=>{
 const db=database(),original=globalThis.fetch;
 try{
  const microsoft=player('MicrosoftPlayer','microsoft');
  await send(db,microsoft,'challenge',{id:microsoft.id});globalThis.fetch=async()=>Response.json({id:microsoft.uuid});await register(db,microsoft);globalThis.fetch=original;
  const registered=await send(db,player('request'),'accounts/register',{email:'mixed-friend@example.com',password:'testing mixed friendship'});
  assert.equal(registered.status,201);const unbox={id:registered.id,token:registered.token,name:registered.account.name,uuid:registered.account.uuid,kind:'unbox'};
  for(const [from,to] of [[microsoft,unbox],[unbox,microsoft]]){
   const fromView=await send(db,from,'sync',from),toView=await send(db,to,'sync',to);assert.match(fromView.self.code,/^[A-F0-9]{12}$/);assert.match(toView.self.code,/^[A-F0-9]{12}$/);
   assert.equal((await send(db,from,'friends/request',{code:toView.self.code})).status,200);
   const pending=await send(db,to,'sync',to);assert.equal(pending.friends[0].direction,'incoming');assert.equal(pending.friends[0].kind,from.kind);
   assert.equal((await send(db,to,'friends/accept',{id:from.id})).status,200);
   assert.equal((await send(db,from,'sync',from)).friends[0].accepted,1);
   assert.equal((await send(db,from,'invites/send',{id:to.id,room:sampleRoom(),joinSecret:'b'.repeat(64)})).status,200);
   assert.equal((await send(db,to,'sync',to)).invites[0].sender,from.id);
   assert.equal((await send(db,from,'friends/remove',{id:to.id})).status,200);
  }
 }finally{globalThis.fetch=original;db.sql.close()}
});
test('Minecraft verification uses Workers-compatible manual redirects and rejects redirected responses',async()=>{
 const db=database(),p=player('RedirectTest','microsoft'),original=globalThis.fetch;
 try{
  await send(db,p,'challenge',{id:p.id});
  let calls=0;globalThis.fetch=async(url,options)=>{calls++;assert.equal(options.redirect,'manual');assert.equal(new URL(url).hostname,'sessionserver.mojang.com');return new Response(null,{status:302,headers:{Location:'https://untrusted.example'}})};
  assert.equal((await send(db,p,'register',p)).status,401);assert.equal(calls,1);assert.equal(db.sql.prepare('SELECT COUNT(*) n FROM people').get().n,0);
  globalThis.fetch=async(_url,options)=>{assert.equal(options.redirect,'manual');return Response.json({id:p.uuid})};
  await register(db,p);assert.equal((await send(db,p,'sync',p)).self.kind,'microsoft');
 }finally{globalThis.fetch=original;db.sql.close()}
});
