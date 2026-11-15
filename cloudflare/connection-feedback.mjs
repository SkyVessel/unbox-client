// Authenticated private diagnostics. Never exposed through the public feedback board.
const json=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
const number=(x)=>typeof x==='number'&&Number.isFinite(x)&&x>=0&&x<=1e15;
const text=(x,n=128)=>typeof x==='string'&&x.length<=n&&!/[\x00-\x1f\x7f]/.test(x);
const id=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
function stats(v){if(!v||!Number.isInteger(v.samples)||v.samples<0||v.samples>100000)return null;const o={samples:v.samples};for(const k of ['p50Ms','p95Ms','maxMs'])if(v[k]!==undefined){if(!number(v[k]))return null;o[k]=v[k]}return o}
function validateSync(v){
 if(!v||v.schema!==2||v.kind!=='mod-sync'||v.consent!==true||!id.test(v.session)||!number(v.startedAt)||v.minecraft!=='26.3'||!['fabric','neoforge'].includes(v.loader)||!text(v.clientVersion,32)||!Array.isArray(v.events)||v.events.length<1||v.events.length>120||JSON.stringify(v).length>48000)return null;
 const events=[];
 for(const e of v.events){
  if(!e||!number(e.at)||e.at<v.startedAt||!number(e.bytes)||!number(e.total))return null;
  const clean={at:e.at,bytes:e.bytes,total:e.total};
  for(const k of ['stage','role','fileId','errorType','errorCode']){if(e[k]!==undefined){if(!text(e[k],64)||!/^[A-Za-z0-9_-]{0,64}$/.test(e[k]))return null;clean[k]=e[k]}}
  if(!clean.stage||!clean.role)return null;events.push(clean);
 }
 return {schema:2,kind:'mod-sync',session:v.session,startedAt:v.startedAt,minecraft:v.minecraft,loader:v.loader,clientVersion:v.clientVersion,events};
}
export function validateReport(v){
 if(v?.schema===2)return validateSync(v);
 if(!v||v.schema!==1||v.consent!==true||!id.test(v.session)||!number(v.startedAt)||v.minecraft!=='1.8.9'||!text(v.clientVersion,32)||!Array.isArray(v.windows)||v.windows.length<1||v.windows.length>40||JSON.stringify(v).length>48000)return null;
 const windows=[];
 for(const w of v.windows){
  if(!w||!w.connection||!number(w.at)||!number(w.durationMs)||w.at<v.startedAt)return null;
  const o={at:w.at,durationMs:w.durationMs},c={};
  for(const k of ['java','vm','os','arch','preferIPv4Stack','preferIPv6Addresses','channel','inspectionError','route','addressFamily'])if(w.connection[k]!==undefined){if(!text(w.connection[k]))return null;c[k]=w.connection[k]}
  for(const k of ['requestedAddress','remoteEndpoint'])if(w.connection[k]!==undefined){const x=w.connection[k];if(!text(x,300)||!/^[-A-Za-z0-9_.:/\[\]]{1,300}$/.test(x))return null;c[k]=x}
  for(const k of ['socksProxyPropertyPresent','localTransport','tcpNoDelay'])if(w.connection[k]!==undefined){if(typeof w.connection[k]!=='boolean')return null;c[k]=w.connection[k]}
  if(w.connection.maxHeapMiB!==undefined){if(!number(w.connection.maxHeapMiB))return null;c.maxHeapMiB=w.connection.maxHeapMiB}
  for(const k of ['collectors','pipeline'])if(w.connection[k]!==undefined){const a=w.connection[k];if(!Array.isArray(a)||a.length>32||!a.every(x=>text(x)))return null;c[k]=a}
  o.connection=c;
  for(const k of ['gcCollections','gcCollectionTimeMs','responseEstimateMs','loadedEntities','serverReportedPingMs','droppedSamples','unhandledSamples','attackPackets','velocityPackets','hurtPackets'])if(w[k]!==undefined){if(!number(w[k]))return null;o[k]=w[k]}
  for(const k of ['fullbright','playerSprinting'])if(w[k]!==undefined){if(typeof w[k]!=='boolean')return null;o[k]=w[k]}
  if(w.screen!==undefined){if(!text(w.screen,64))return null;o.screen=w.screen}
  for(const k of ['keepaliveHandler','entityMainThreadQueue','frames','gcEvents']){const s=stats(w[k]);if(!s)return null;o[k]=s}
  windows.push(o);
 }
 const events=[];if(v.events!==undefined){if(!Array.isArray(v.events)||v.events.length>40)return null;for(const e of v.events){if(!e||!number(e.at)||!text(e.stage,64)||!text(e.route,64))return null;events.push({at:e.at,stage:e.stage,route:e.route})}}
 return {events,schema:1,session:v.session,startedAt:v.startedAt,minecraft:v.minecraft,clientVersion:v.clientVersion,windows};
}
export async function connectionFeedback(request,db,me,body,now){
 if(request.method!=='POST')return json({error:'Method not allowed'},405);
 if(body.accountUuid!==me.uuid)return json({error:'Report account does not match'},403);
 const report=validateReport(body);if(!report)return json({error:'Invalid connection report'},400);
 const existing=await db.prepare('SELECT person_id FROM connection_feedback WHERE id=?').bind(report.session).first();
 if(existing)return existing.person_id===me.id?json({ok:true,duplicate:true}):json({error:'Report ID already exists'},409);
 // Expire before capacity checks; indexed by received time. No raw log, chat or credential fields survive validation.
 await db.prepare('DELETE FROM connection_feedback WHERE received_at<?').bind(now-7*86400000).run();
 const result=await db.prepare('INSERT INTO connection_feedback(id,person_id,account_uuid,account_kind,account_name,received_at,report) SELECT ?,?,?,?,?,?,? WHERE (SELECT COUNT(*) FROM connection_feedback WHERE account_uuid=? AND received_at>?)<4 AND (SELECT COUNT(*) FROM connection_feedback)<2000').bind(report.session,me.id,me.uuid,me.kind,me.name,now,JSON.stringify(report),me.uuid,now-86400000).run();
 if(!(result.meta?.changes??result.changes))return json({error:'Feedback capacity reached. Report stays on this device.'},429);
 return json({ok:true,id:report.session},201);
}
