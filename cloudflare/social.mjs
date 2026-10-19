import {platformRoute,ownership} from './platform.mjs';
import {cleanupAccounts} from './accounts.mjs';
import {verifyPlayerProof} from './player-proof.mjs';
const reply=(b,s=200)=>Response.json(b,{status:s,headers:{'Cache-Control':'no-store'}});
export const hash=async s=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,'0')).join('');
const uuid=s=>typeof s==='string'&&/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(s);
async function body(request){const reader=request.body?.getReader();if(!reader)return {};let size=0,parts=[];while(true){const{done,value}=await reader.read();if(done)break;if((size+=value.length)>98304){await reader.cancel();throw Error('Request too large')}parts.push(value)}const b=new Uint8Array(size);let n=0;for(const p of parts){b.set(p,n);n+=p.length}return JSON.parse(new TextDecoder().decode(b))}
function profile(b){if(!/^[A-Za-z0-9_]{1,16}$/.test(b.name||'')||!['microsoft','local','unbox'].includes(b.kind)||!(/^[a-f0-9]{32}$/.test(b.uuid||'')||b.uuid===''))throw Error('Invalid player profile');if(b.face&&!/^data:image\/png;base64,[A-Za-z0-9+/=]{1,4096}$/.test(b.face))throw Error('Invalid face');return [b.name,b.uuid,b.kind,b.face||null]}
async function activeBadges(db,players,now){
 if(players===undefined||players===null)return [];
 if(!Array.isArray(players)||players.length>2048)return null;
 const ids=[...new Set(players.map(id=>typeof id==='string'?id.toLowerCase().replaceAll('-',''):'').filter(id=>/^[a-f0-9]{32}$/.test(id)))],found=[];
 for(let offset=0;offset<ids.length;offset+=80){const chunk=ids.slice(offset,offset+80),marks=chunk.map(()=>'?').join(',');const rows=await db.prepare(`SELECT uuid FROM people WHERE kind='microsoft' AND seen>? AND state IN ('Playing','Hosting') AND uuid IN (${marks})`).bind(now-90000,...chunk).all();for(const row of rows.results)found.push(row.uuid)}
 return [...new Set(found)].sort();
}
export function room(b){if(!b||!uuid(b.id)||typeof b.name!=='string'||b.name.length>80||!Number.isInteger(b.port)||b.port<1||b.port>65535||!Number.isInteger(b.probePort)||b.probePort<1||b.probePort>65535||!/^[a-f0-9]{64}$/.test(b.probeToken||''))throw Error('Invalid world invitation');if(!Array.isArray(b.lan)||b.lan.length>8||!b.lan.every(ip=>/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)&&ip.split('.').every(x=>+x<256)&&(ip.startsWith('10.')||ip.startsWith('192.168.')||(/^172\.(1[6-9]|2\d|3[01])\./.test(ip)))))throw Error('Invalid LAN addresses');if(b.relay&&!/^[a-z0-9.-]{1,200}\.e4mc\.link$/.test(b.relay))throw Error('Invalid relay');if(b.loader!==undefined&&(!['fabric','neoforge'].includes(b.loader)||!['26.1','26.1.2','26.3'].includes(b.minecraft)))throw Error('Unsupported world environment');return {...(b.loader?{loader:b.loader,minecraft:b.minecraft}:{}),id:b.id,name:b.name,port:b.port,probePort:b.probePort,probeToken:b.probeToken,lan:b.lan,relay:b.relay||null,protocol:b.protocol===2?2:1}}
export default {
 async fetch(request,env){
  if(new URL(request.url).pathname==='/health')return reply({service:'unbox-friends',configured:!!env.DB,version:2,accountAuth:"minecraft",emailVerification:false});
  if(!env.DB)return reply({error:'Friends service is not connected'},503);
  try{
   const path=new URL(request.url).pathname;const b=await body(request);if(path.startsWith('/v1/accounts/'))return reply({error:'Unbox email accounts have retired. Update Unbox to migrate this identity to local play.'},410);if(path==='/admin'||path==='/v1/news'||path.startsWith('/v1/admin/')){const response=await platformRoute(request,env,path,b);if(response)return response;}const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';if(!/^[a-f0-9]{64}$/.test(token))return reply({error:'Unauthorized'},401);
   const tokenHash=await hash(token);const db=env.DB,now=Date.now();
   if(path==='/v1/challenge'&&request.method==='POST'){
    if(!uuid(b.id))return reply({error:'Invalid identity'},400);const total=await db.prepare('SELECT COUNT(*) AS n FROM challenges').first();if(total.n>=2000)return reply({error:'Verification capacity reached. Try later.'},429);const nonce=crypto.randomUUID().replaceAll('-','');await db.prepare('INSERT INTO challenges(id,token_hash,nonce,expires) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET token_hash=excluded.token_hash,nonce=excluded.nonce,expires=excluded.expires').bind(b.id,tokenHash,nonce,now+120000).run();return reply({challenge:nonce,verification:"player-certificate-v2"});
   }
   if(path==='/v1/register'&&request.method==='POST'){
    if(!uuid(b.id)||!['microsoft','local'].includes(b.kind))return reply({error:'Invalid identity'},400);const values=profile(b);if(b.kind==='local'){values[0]='OfflinePlayer';values[3]=null;}
    const existing=await db.prepare('SELECT token_hash FROM people WHERE id=?').bind(b.id).first();if(existing)return existing.token_hash===tokenHash?reply({id:b.id}):reply({error:'Identity already exists'},409);
    if(b.kind==='local'&&(!/^[a-f0-9]{32}$/.test(b.uuid)||await db.prepare("SELECT 1 FROM people WHERE uuid=? AND kind IN ('microsoft','local') UNION SELECT 1 FROM player_accounts WHERE uuid=? LIMIT 1").bind(b.uuid,b.uuid).first()))return reply({error:'Local identity is already in use'},409);
    if(b.kind==='microsoft'){
     const c=await db.prepare('SELECT nonce FROM challenges WHERE id=? AND token_hash=? AND expires>?').bind(b.id,tokenHash,now).first();if(!c)return reply({error:'Account verification expired'},401);
     if(b.proof){
      if(!await verifyPlayerProof(b.proof,{id:b.id,tokenHash,nonce:c.nonce,uuid:b.uuid,now}))return reply({error:'Minecraft player proof was not verified'},401);
     }else{
     const check=await fetch('https://sessionserver.mojang.com/session/minecraft/hasJoined?'+new URLSearchParams({username:b.name,serverId:c.nonce}),{redirect:'manual'});if(check.status===204)return reply({error:'Minecraft session was not verified'},401);if(!check.ok)return reply({error:'Update Unbox to use player certificate verification. Mojang is rejecting cloud session checks.'},503);const p=await check.json();if(p.id!==b.uuid)return reply({error:'Minecraft account does not match'},401);
     }
    }
    if(b.kind==='microsoft'){
     await db.prepare("INSERT OR IGNORE INTO player_accounts(uuid,created) VALUES(?,?)").bind(b.uuid,now).run();
     const canonical=await db.prepare("SELECT id FROM people WHERE kind='microsoft' AND uuid=? ORDER BY seen DESC LIMIT 1").bind(b.uuid).first();
     if(canonical){await db.batch([db.prepare('INSERT INTO account_devices VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET token_hash=excluded.token_hash,created=excluded.created').bind(b.id,canonical.id,tokenHash,now),db.prepare('DELETE FROM challenges WHERE id=?').bind(b.id)]);return reply({id:canonical.id},201);}
    }
    const count=await db.prepare('SELECT COUNT(*) AS n FROM people').first();if(count.n>=2000)return reply({error:'Free service registration limit reached'},503);
    await db.prepare('INSERT INTO people(id,code,token_hash,name,uuid,kind,face,seen) VALUES(?,?,?,?,?,?,?,?)').bind(b.id,b.id.replaceAll('-','').slice(0,12).toUpperCase(),tokenHash,...values,now).run();if(b.kind==='microsoft')await db.prepare('DELETE FROM challenges WHERE id=?').bind(b.id).run();return reply({id:b.id},201);
   }
   const me=await db.prepare("SELECT id,code,uuid,kind,name FROM people WHERE (token_hash=? AND kind IN ('microsoft','local')) OR id IN (SELECT person_id FROM account_devices WHERE token_hash=? AND created>?)").bind(tokenHash,tokenHash,now-30*86400000).first();if(!me)return reply({error:'Sign in to Unbox friends again'},401);
   if(me.kind==='microsoft'){
    await db.prepare('INSERT OR IGNORE INTO player_accounts(uuid,created) VALUES(?,?)').bind(me.uuid,now).run();
    const state=await db.prepare('SELECT status FROM player_accounts WHERE uuid=?').bind(me.uuid).first();
    if(state?.status==='suspended')return reply({error:'This platform account is suspended'},403);
   }
   if(path.startsWith('/v1/assets'))return await platformRoute(request,env,path,b,me);
   const mutual=async id=>!!await db.prepare('SELECT 1 FROM friendships WHERE accepted=1 AND ((sender=? AND receiver=?) OR (sender=? AND receiver=?))').bind(me.id,id,id,me.id).first();
   if(path==='/v1/sync'&&request.method==='POST'){
    const values=profile(b);if(me.kind==='local'){values[0]='OfflinePlayer';values[3]=null;}if(b.uuid!==me.uuid||b.kind!==me.kind)return reply({error:'Account identity changed'},403);await db.prepare('UPDATE people SET name=?,uuid=?,kind=?,face=?,seen=?,state=? WHERE id=?').bind(...values,now,['Launcher','Playing','Hosting'].includes(b.state)?b.state:'Launcher',me.id).run();
    const badges=await activeBadges(db,b.players,now);if(badges===null)return reply({error:'Invalid player list'},400);
    const friends=await db.prepare('SELECT p.id,p.code,p.name,p.uuid,p.kind,p.face,p.seen,p.state,f.sender,f.accepted FROM friendships f JOIN people p ON p.id=CASE WHEN f.sender=? THEN f.receiver ELSE f.sender END WHERE f.sender=? OR f.receiver=? ORDER BY p.seen DESC LIMIT 100').bind(me.id,me.id,me.id).all();
    const invites=await db.prepare('SELECT i.*,p.name AS senderName FROM invites i JOIN people p ON p.id=i.sender WHERE (i.receiver=? OR i.sender=?) AND i.expires>? ORDER BY i.expires DESC LIMIT 100').bind(me.id,me.id,now).all();
    return reply({protocol:2,self:me,assets:me.kind==='microsoft'?await ownership(db,me.uuid,now):{items:[],features:[],equipment:[]},badges,friends:friends.results.map(f=>({...f,online:now-f.seen<90000,direction:f.sender===me.id?'outgoing':'incoming'})),invites:invites.results.map(i=>({...i,room:JSON.parse(i.room)}))});
   }
   if(path==='/v1/friends/request'&&request.method==='POST'){
    if(!/^[A-F0-9]{12}$/.test(b.code||''))return reply({error:'Enter the 12-character friend code'},400);
    const peer=await db.prepare('SELECT id FROM people WHERE code=?').bind(b.code).first();if(!peer||peer.id===me.id)return reply({error:peer?'This is your own code':'Friend code not found'},400);
    const count=await db.prepare('SELECT COUNT(*) AS n FROM friendships WHERE sender=? OR receiver=?').bind(me.id,me.id).first();if(count.n>=100)return reply({error:'Friend list is full'},409);
    const capacity=await db.prepare('SELECT COUNT(*) AS n FROM friendships WHERE sender=? OR receiver=?').bind(peer.id,peer.id).first();if(capacity.n>=100)return reply({error:'This friend list is full'},409);
    const reverse=await db.prepare('SELECT accepted FROM friendships WHERE sender=? AND receiver=?').bind(peer.id,me.id).first();if(reverse)return reply({error:'A request from this player already exists. Accept it in your list.'},409);
    await db.prepare('INSERT OR IGNORE INTO friendships(sender,receiver,created) VALUES(?,?,?)').bind(me.id,peer.id,now).run();return reply({ok:true});
   }
   if(path==='/v1/friends/accept'&&request.method==='POST'){
    await db.prepare('UPDATE friendships SET accepted=1 WHERE sender=? AND receiver=?').bind(b.id,me.id).run();return reply({ok:true});
   }
   if(path==='/v1/friends/remove'&&request.method==='POST'){
    await db.batch([db.prepare('DELETE FROM friendships WHERE (sender=? AND receiver=?) OR (sender=? AND receiver=?)').bind(me.id,b.id,b.id,me.id),db.prepare('DELETE FROM invites WHERE (sender=? AND receiver=?) OR (sender=? AND receiver=?)').bind(me.id,b.id,b.id,me.id)]);return reply({ok:true});
   }
   if(path==='/v1/invites/send'&&request.method==='POST'){
    if(!await mutual(b.id))return reply({error:'Only accepted friends can be invited'},403);const r=room(b.room);
    const sender=await db.prepare('SELECT kind FROM people WHERE id=?').bind(me.id).first();const peer=await db.prepare('SELECT kind FROM people WHERE id=?').bind(b.id).first();if(!['microsoft','local'].includes(sender.kind)||!['microsoft','local'].includes(peer.kind))return reply({error:'Choose Microsoft or local play'},400);if(r.protocol!==2||!/^[a-f0-9]{64}$/.test(b.joinSecret||''))return reply({error:'Update Unbox to use verified private invitations'},400);
    await db.batch([db.prepare('DELETE FROM invites WHERE sender=? AND receiver=?').bind(me.id,b.id),db.prepare('INSERT INTO invites(id,sender,receiver,room,expires,join_secret) VALUES(?,?,?,?,?,?)').bind(crypto.randomUUID(),me.id,b.id,JSON.stringify(r),now+300000,b.joinSecret)]);return reply({ok:true});
   }
   if(path==='/v1/invites/relay'&&request.method==='POST'){
    const i=await db.prepare('SELECT sender FROM invites WHERE id=? AND receiver=? AND expires>?').bind(b.id,me.id,now).first();if(!i||!await mutual(i.sender))return reply({error:'Invitation expired'},404);
    await db.prepare('UPDATE invites SET relay_requested=1 WHERE id=?').bind(b.id).run();return reply({ok:true});
   }
   if(path==='/v1/room'&&request.method==='POST'){
    if(!b.room){await db.prepare('DELETE FROM invites WHERE sender=?').bind(me.id).run();return reply({ok:true})}const r=room(b.room);await db.prepare('UPDATE invites SET room=? WHERE sender=? AND expires>? AND json_extract(room,\'$.id\')=?').bind(JSON.stringify(r),me.id,now,r.id).run();return reply({ok:true});
   }
   if(path==='/v1/invites/dismiss'&&request.method==='POST'){await db.prepare('DELETE FROM invites WHERE id=? AND receiver=?').bind(b.id,me.id).run();return reply({ok:true});}
   return reply({error:'Not found'},404);
  }catch(e){return reply({error:e instanceof SyntaxError?'Invalid request':/^(Invalid|Request)/.test(e.message)?e.message:'Friends service unavailable; retry shortly'},400)}
 },
 async scheduled(event,env){if(env.DB){await cleanupAccounts(env.DB,Date.now());const cutoff=Date.now()-86400000;await env.DB.batch([env.DB.prepare("DELETE FROM friendships WHERE sender IN (SELECT id FROM people WHERE kind='local' AND seen<?) OR receiver IN (SELECT id FROM people WHERE kind='local' AND seen<?)").bind(cutoff,cutoff),env.DB.prepare("DELETE FROM invites WHERE sender IN (SELECT id FROM people WHERE kind='local' AND seen<?) OR receiver IN (SELECT id FROM people WHERE kind='local' AND seen<?)").bind(cutoff,cutoff),env.DB.prepare("DELETE FROM people WHERE kind='local' AND seen<?").bind(cutoff),env.DB.prepare('DELETE FROM account_devices WHERE created<?').bind(Date.now()-30*86400000)]);await env.DB.batch([env.DB.prepare('DELETE FROM invites WHERE expires<=?').bind(Date.now()),env.DB.prepare('DELETE FROM challenges WHERE expires<=?').bind(Date.now())]);}}
};
