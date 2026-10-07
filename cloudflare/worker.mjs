// Free-tier metadata service. Never proxies game TCP traffic or stores user credentials.
const json=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
const digest=async(value)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');
export default {
 async fetch(request,env){
  const url=new URL(request.url);
  if(url.pathname==='/health'&&request.method==='GET')return json({service:'unbox-metadata',version:1,gameVersion:'26.1',configured:!!env.DB&&!!env.PUBLISH_TOKEN});
  if(!env.DB||!env.PUBLISH_TOKEN)return json({error:'Service is not configured'},503);
  if(url.pathname==='/v1/manifests'&&request.method==='POST'){
   const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';
   if(await digest(token)!==await digest(env.PUBLISH_TOKEN))return json({error:'Unauthorized'},401);
   // Bound memory even when Content-Length is absent or incorrect.
   const reader=request.body?.getReader();if(!reader)return json({error:'Missing body'},400);
   let size=0,chunks=[];while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>65536){await reader.cancel();return json({error:'Manifest too large'},413)}chunks.push(value)}
   let manifest;try{const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length}manifest=JSON.parse(new TextDecoder().decode(bytes));}catch{return json({error:'Invalid JSON'},400)}
   if(manifest.gameVersion!=='26.1'||!['fabric','vanilla'].includes(manifest.loader)||!Array.isArray(manifest.mods)||manifest.mods.length>300)return json({error:'Invalid manifest'},400);
   for(const mod of manifest.mods){if(typeof mod.projectId!=='string'||typeof mod.versionId!=='string'||typeof mod.sha512!=='string'||!/^\w{1,64}$/.test(mod.projectId)||!/^\w{1,64}$/.test(mod.versionId)||!/^[a-f0-9]{128}$/.test(mod.sha512))return json({error:'Invalid mod reference'},400)}
   const id=crypto.randomUUID(),secret=crypto.randomUUID()+crypto.randomUUID(),hash=await digest(secret),expires=Date.now()+7*86400000;
   // Strip all personal fields and arbitrary URLs. Resolve artifacts via the provider on clients.
   const payload=JSON.stringify({schemaVersion:1,gameVersion:'26.1',loader:manifest.loader,mods:manifest.mods.map(({projectId,versionId,sha512})=>({projectId,versionId,sha512}))});
   await env.DB.prepare('INSERT INTO manifests(id, secret_hash, payload, expires_at) VALUES(?, ?, ?, ?)').bind(id,hash,payload,expires).run();
   return json({id,readToken:secret,expiresAt:expires},201);
  }
  const match=url.pathname.match(/^\/v1\/manifests\/([a-f0-9-]{36})$/);
  if(match&&request.method==='GET'){
   const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';if(!token)return json({error:'Unauthorized'},401);
   const row=await env.DB.prepare('SELECT payload FROM manifests WHERE id = ? AND secret_hash = ? AND expires_at > ?').bind(match[1],await digest(token),Date.now()).first();
   return row?json(JSON.parse(row.payload)):json({error:'Not found or expired'},404);
  }
  return json({error:'Not found'},404);
 },
 async scheduled(event,env){if(env.DB)await env.DB.prepare('DELETE FROM manifests WHERE expires_at <= ?').bind(Date.now()).run();}
};
