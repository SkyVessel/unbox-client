import {digest} from './accounts.mjs';
import {adminHtml} from './admin.mjs';
const json=(v,status=200)=>Response.json(v,{status,headers:{'Cache-Control':'no-store'}});
const identifier=v=>typeof v==='string'&&/^[a-z0-9][a-z0-9_-]{0,63}$/.test(v);
const uuid=v=>typeof v==='string'&&/^[a-f0-9]{32}$/.test(v);
const text=(v,max)=>typeof v==='string'&&v.trim().length>0&&v.length<=max;
const https=v=>v==null||v===''||(typeof v==='string'&&v.length<=2048&&/^https:\/\//.test(v)&&(()=>{try{const u=new URL(v);return !u.username&&!u.password}catch{return false}})());
const audit=(db,actor,action,target,detail,now)=>db.prepare('INSERT INTO platform_audit VALUES(?,?,?,?,?,?)').bind(crypto.randomUUID(),actor,action,target,JSON.stringify(detail),now);
export async function ownership(db,id,now=Date.now()){
 const account=await db.prepare('SELECT status FROM player_accounts WHERE uuid=?').bind(id).first();
 if(!account||account.status!=='active')return {items:[],features:[],equipment:[]};
 const items=(await db.prepare('SELECT DISTINCT c.id,c.kind,c.name,c.resource,c.features FROM asset_grants g JOIN asset_catalog c ON c.id=g.item WHERE g.uuid=? AND g.revoked IS NULL AND (g.expires IS NULL OR g.expires>?) AND c.enabled=1').bind(id,now).all()).results;
 const features=[...new Set(items.filter(i=>i.kind==='role').flatMap(i=>JSON.parse(i.features)))].sort();
 const equipped=(await db.prepare('SELECT slot,item FROM player_equipment WHERE uuid=?').bind(id).all()).results;
 return {items:items.map(({features,...item})=>item),features,equipment:equipped.filter(e=>items.some(i=>i.id===e.item&&i.kind===e.slot))};
}
export async function hasCapability(db,me,feature,now=Date.now()){
 if(!identifier(feature)||me?.kind!=='microsoft')return false;
 return (await ownership(db,me.uuid,now)).features.includes(feature);
}
export async function platformRoute(request,env,path,b,me=null){
 const db=env.DB,now=Date.now(),method=request.method;
 if(path==='/admin'&&method==='GET')return new Response(adminHtml,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src https:; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",'X-Content-Type-Options':'nosniff'}});
 if(path==='/v1/news'&&method==='GET')return json({posts:(await db.prepare("SELECT id,title,summary,body,image,url,published FROM news_posts WHERE status='published' AND published<=? ORDER BY published DESC,id LIMIT 30").bind(now).all()).results});
 if(path.startsWith('/v1/admin/')){
  const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';
  // Dedicated operational credentials are never embedded in the launcher or persisted by the UI.
  const write=!!env.ADMIN_WRITE_TOKEN&&token.length>=32&&await digest(token)===await digest(env.ADMIN_WRITE_TOKEN);
  const read=write||!!env.ADMIN_READ_TOKEN&&token.length>=32&&await digest(token)===await digest(env.ADMIN_READ_TOKEN);
  if(!read)return json({error:'Administrator authorization required'},401);
  if(method!=='GET'&&!write)return json({error:'Read-only administrator'},403);
  const actor=write?'administrator':'support';
  if(method==='GET'){
   if(path==='/v1/admin/catalog')return json({items:(await db.prepare('SELECT * FROM asset_catalog ORDER BY kind,name').all()).results});
   if(path==='/v1/admin/news')return json({posts:(await db.prepare('SELECT * FROM news_posts ORDER BY updated DESC LIMIT 100').all()).results});
   if(path==='/v1/admin/audit')return json({events:(await db.prepare('SELECT * FROM platform_audit ORDER BY created DESC LIMIT 200').all()).results});
   if(path==='/v1/admin/player'){
    const id=new URL(request.url).searchParams.get('uuid');if(!uuid(id))return json({error:'Enter the Minecraft UUID (32 hexadecimal characters)'},400);
    return json({account:await db.prepare('SELECT * FROM player_accounts WHERE uuid=?').bind(id).first(),...await ownership(db,id),grants:(await db.prepare('SELECT * FROM asset_grants WHERE uuid=? ORDER BY created DESC').bind(id).all()).results});
   }
  }
  if(method!=='POST')return json({error:'Not found'},404);
  if(path==='/v1/admin/catalog'){
   if(!identifier(b.id)||!['logo','cape','role'].includes(b.kind)||!text(b.name,80)||!https(b.resource)||typeof b.enabled!=='boolean'||!Array.isArray(b.features)||b.features.length>64||!b.features.every(identifier)||b.kind!=='role'&&b.features.length)return json({error:'Invalid item definition'},400);
   const old=await db.prepare('SELECT kind FROM asset_catalog WHERE id=?').bind(b.id).first();if(old&&old.kind!==b.kind)return json({error:'An item type cannot be changed after creation'},409);
   await db.batch([db.prepare('INSERT INTO asset_catalog VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,resource=excluded.resource,enabled=excluded.enabled,features=excluded.features,updated=excluded.updated').bind(b.id,b.kind,b.name,b.resource||null,+b.enabled,JSON.stringify(b.features),now),audit(db,actor,'catalog',b.id,b,now)]);return json({ok:true});
  }
  if(path==='/v1/admin/grant'){
   if(!uuid(b.uuid)||!identifier(b.item)||!text(b.source,100)||!(b.expires==null||Number.isSafeInteger(b.expires)&&b.expires>now))return json({error:'Invalid grant'},400);
   const owner=await db.prepare("SELECT uuid FROM player_accounts WHERE uuid=? AND status='active'").bind(b.uuid).first(),item=await db.prepare('SELECT id FROM asset_catalog WHERE id=? AND enabled=1').bind(b.item).first();
   if(!owner||!item)return json({error:'An active verified player and available item are required'},400);
   const existing=await db.prepare('SELECT * FROM asset_grants WHERE uuid=? AND item=? AND source=?').bind(b.uuid,b.item,b.source).first();if(existing)return json({ok:true,duplicate:true,grant:existing});
   const id=crypto.randomUUID();await db.batch([db.prepare('INSERT OR IGNORE INTO asset_grants VALUES(?,?,?,?,?,?,NULL)').bind(id,b.uuid,b.item,b.source,now,b.expires??null),audit(db,actor,'grant',b.uuid,{id,item:b.item,source:b.source},now)]);return json({ok:true});
  }
  if(path==='/v1/admin/revoke'){
   if(!text(b.id,64)||!text(b.reason,500))return json({error:'A grant and reason are required'},400);
   if(!await db.prepare('SELECT id FROM asset_grants WHERE id=?').bind(b.id).first())return json({error:'Grant not found'},404);
   await db.batch([db.prepare('UPDATE asset_grants SET revoked=? WHERE id=? AND revoked IS NULL').bind(now,b.id),audit(db,actor,'revoke',b.id,{reason:b.reason},now)]);return json({ok:true});
  }
  if(path==='/v1/admin/player'){
   if(!uuid(b.uuid)||!['active','suspended'].includes(b.status)||!text(b.reason,500))return json({error:'A player, status and reason are required'},400);
   if(!await db.prepare('SELECT uuid FROM player_accounts WHERE uuid=?').bind(b.uuid).first())return json({error:'Player not found'},404);
   await db.batch([db.prepare('UPDATE player_accounts SET status=? WHERE uuid=?').bind(b.status,b.uuid),audit(db,actor,'account-status',b.uuid,{status:b.status,reason:b.reason},now)]);return json({ok:true});
  }
  if(path==='/v1/admin/news'){
   if(!identifier(b.id)||!text(b.title,120)||!text(b.summary,300)||typeof b.body!=='string'||b.body.length>20000||!https(b.image)||!https(b.url)||!['draft','published'].includes(b.status)||!(b.published==null||Number.isSafeInteger(b.published)&&b.published>0))return json({error:'Invalid news post'},400);
   await db.batch([db.prepare('INSERT INTO news_posts VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,summary=excluded.summary,body=excluded.body,image=excluded.image,url=excluded.url,status=excluded.status,published=excluded.published,updated=excluded.updated').bind(b.id,b.title,b.summary,b.body,b.image||null,b.url||null,b.status,b.published??now,now),audit(db,actor,'news',b.id,{status:b.status,title:b.title},now)]);return json({ok:true});
  }
  return json({error:'Not found'},404);
 }
 if(!path.startsWith('/v1/assets'))return null;
 if(!me||me.kind!=='microsoft')return json({error:'A verified Minecraft account is required'},403);
 const value=await ownership(db,me.uuid,now);
 if(path==='/v1/assets'&&method==='POST')return json(value);
 if(path==='/v1/assets/equip'&&method==='POST'){
  if(!['logo','cape'].includes(b.slot)||!(b.item===null||value.items.some(i=>i.id===b.item&&i.kind===b.slot)))return json({error:'This item is not available to your account'},403);
  if(b.item===null)await db.prepare('DELETE FROM player_equipment WHERE uuid=? AND slot=?').bind(me.uuid,b.slot).run();else await db.prepare('INSERT INTO player_equipment VALUES(?,?,?) ON CONFLICT(uuid,slot) DO UPDATE SET item=excluded.item').bind(me.uuid,b.slot,b.item).run();return json(await ownership(db,me.uuid,now));
 }
 return json({error:'Not found'},404);
}
