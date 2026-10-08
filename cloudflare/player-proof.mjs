import mojangKeys from './mojang-player-keys.mjs';

function bytes(value,max){
 if(typeof value!=='string'||value.length>max||!value.length||!/^[A-Za-z0-9+/]+={0,2}$/.test(value))throw Error('Invalid player proof');
 return Uint8Array.from(atob(value),c=>c.charCodeAt(0));
}
export function proofMessage({id,tokenHash,nonce,uuid}){
 return new TextEncoder().encode(['Unbox friends certificate proof v1',id,tokenHash,nonce,uuid].join('\n'));
}
// Matches Minecraft's v2 certificate: UUID (16 bytes), expiry (i64 BE), public SPKI.
// The player separately signs our domain-separated, short-lived challenge.
export async function verifyPlayerProof(proof,context,roots=mojangKeys){
 try{
  if(proof?.version!==1||!/^[a-f0-9]{32}$/.test(context.uuid))return false;
  const expires=Date.parse(proof.expiresAt);
  if(!Number.isSafeInteger(expires)||expires<=context.now)return false;
  const publicKey=bytes(proof.publicKey,2048),certificateSignature=bytes(proof.certificateSignature,2048),signature=bytes(proof.signature,2048);
  const certificate=new Uint8Array(24+publicKey.length);
  certificate.set(context.uuid.match(/../g).map(x=>parseInt(x,16)));
  new DataView(certificate.buffer).setBigInt64(16,BigInt(expires));certificate.set(publicKey,24);
  let trusted=false;
  for(const root of roots){
   const key=await crypto.subtle.importKey('spki',bytes(root,4096),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-1'},false,['verify']);
   if(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,certificateSignature,certificate)){trusted=true;break;}
  }
  if(!trusted)return false;
  const key=await crypto.subtle.importKey('spki',publicKey,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  return await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,signature,proofMessage(context));
 }catch{return false;}
}
