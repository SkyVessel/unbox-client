// Private, invitation-scoped candidate exchange. No new public room/lobby identity.
export const transportVersion='voxlink-v1';
const uuid=s=>typeof s==='string'&&/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(s);
export function candidate(c){
 if(!c||!['udp','tcp','tcp-punch'].includes(c.kind)||!Number.isInteger(c.port)||c.port<1||c.port>65535||!Number.isInteger(c.slot)||c.slot<0||c.slot>1||![0,16].includes(c.range))throw Error('Invalid transport candidate');
 const h=c.host;if(typeof h!=='string'||h.length>64||!h.match(/^[0-9a-fA-F:.]+$/))throw Error('Invalid transport address');
 if(h.includes(':')){try{const normalized=new URL('http://['+h+']/').hostname.toLowerCase();if(normalized==='[::]'||normalized==='[::1]'||normalized.startsWith('[::ffff:')||/^\[(?:f[cd]|fe[89ab]|ff)/.test(normalized)||normalized.includes('.'))throw Error();}catch{throw Error('Invalid transport IPv6')}}
 else {const a=h.split('.');if(a.length!==4||a.some(n=>!/^\d{1,3}$/.test(n)||+n>255||String(+n)!==n)||+a[0]===0||+a[0]===10||+a[0]===127||+a[0]>=224||(+a[0]===169&&+a[1]===254)||(+a[0]===192&&+a[1]===168)||(+a[0]===172&&+a[1]>=16&&+a[1]<=31))throw Error('Invalid transport IPv4');}
 return {kind:c.kind,host:h,port:c.port,slot:c.slot,range:c.range};
}
export async function exchange(db,me,b,now,mutual){
 if(!uuid(b.id)||!uuid(b.attempt)||!['host','guest'].includes(b.side)||!Array.isArray(b.offer?.candidates)||b.offer.candidates.length>8)throw Error('Invalid transport offer');
 const i=await db.prepare('SELECT sender,receiver,room FROM invites WHERE id=? AND expires>?').bind(b.id,now).first();
 if(!i||(b.side==='host'?i.sender:i.receiver)!==me.id||!await mutual(b.side==='host'?i.receiver:i.sender))return {error:'Invitation expired',status:404};
 const room=JSON.parse(i.room);if(room.network!==transportVersion)return {error:'Transport unavailable',status:409};
 const offer={candidates:b.offer.candidates.map(candidate)};
 if(b.side==='guest'){
  // Repeated delivery is idempotent. A new attempt replaces both sides; stale host replies cannot revive it.
  await db.prepare("UPDATE invites SET room=CASE WHEN json_extract(room,'$.transport.attempt')=? THEN room ELSE json_set(room,'$.transport',json(?)) END WHERE id=? AND expires>?")
   .bind(b.attempt,JSON.stringify({attempt:b.attempt,created:now,guest:offer}),b.id,now).run();
 }else{
  if(room.transport?.attempt!==b.attempt||room.transport.created<now-45000)return {error:'Connection attempt expired',status:409};
  await db.prepare("UPDATE invites SET room=json_set(room,'$.transport.host',json(?)) WHERE id=? AND expires>? AND json_extract(room,'$.transport.attempt')=?")
   .bind(JSON.stringify(offer),b.id,now,b.attempt).run();
 }
 return {ok:true};
}
