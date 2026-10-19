import {digest} from './accounts.mjs';
const json=(v,status=200)=>Response.json(v,{status,headers:{'Cache-Control':'no-store'}});
const normalize=v=>typeof v==='string'?v.toUpperCase().replace(/[\s-]/g,''):'';
export async function issueCode(db,b,actor,now){
 if(!Array.isArray(b.items)||!b.items.length||b.items.length>32||new Set(b.items).size!==b.items.length||!b.items.every(s=>typeof s==='string'&&/^[a-z0-9][a-z0-9_-]{0,63}$/.test(s))||!(b.expires==null||Number.isSafeInteger(b.expires)&&b.expires>now))return json({error:'Choose available items and a future expiry'},400);
 for(const item of b.items)if(!await db.prepare('SELECT id FROM asset_catalog WHERE id=? AND enabled=1').bind(item).first())return json({error:'Every item must be available'},400);
 const id=crypto.randomUUID(),raw=crypto.randomUUID().replaceAll('-','').toUpperCase()+crypto.randomUUID().replaceAll('-','').slice(0,8).toUpperCase(),code=raw.match(/.{5}/g).join('-');
 await db.batch([db.prepare('INSERT INTO redemption_codes(id,code_hash,items,created,expires) VALUES(?,?,?,?,?)').bind(id,await digest(raw),JSON.stringify(b.items),now,b.expires??null),db.prepare('INSERT INTO platform_audit VALUES(?,?,?,?,?,?)').bind(crypto.randomUUID(),actor,'issue-code',id,JSON.stringify({items:b.items,expires:b.expires??null}),now)]);
 return json({id,code,items:b.items,expires:b.expires??null});
}
export async function redeemCode(db,me,b,now,ownership){
 // Only the authenticated, certificate-verified identity supplies the owner UUID.
 if(me?.kind!=='microsoft'||!await db.prepare("SELECT uuid FROM player_accounts WHERE uuid=? AND status='active'").bind(me.uuid).first())return json({error:'An active verified Minecraft account is required'},403);
 const limit=await db.prepare('INSERT INTO account_limits VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET n=CASE WHEN expires<=? THEN 1 ELSE n+1 END,expires=CASE WHEN expires<=? THEN excluded.expires ELSE expires END RETURNING n').bind('redeem:'+me.uuid,now+60000,now,now).first();
 if(limit.n>10)return json({error:'Too many attempts. Try again in one minute.'},429);
 const raw=normalize(b.code);if(!/^[A-F0-9]{40}$/.test(raw))return json({error:'Invalid or unavailable redemption code'},400);
 const hash=await digest(raw),claim=crypto.randomUUID();
 // D1 batch is transactional. The claim predicate and grants execute under the same write transaction.
 await db.batch([
  db.prepare("UPDATE redemption_codes SET claimed_by=?,claimed_at=?,claim_request=? WHERE code_hash=? AND claimed_by IS NULL AND disabled=0 AND (expires IS NULL OR expires>?) AND EXISTS(SELECT 1 FROM player_accounts WHERE uuid=? AND status='active') AND NOT EXISTS(SELECT 1 FROM json_each(redemption_codes.items) j LEFT JOIN asset_catalog c ON c.id=j.value WHERE c.id IS NULL OR c.enabled<>1)").bind(me.uuid,now,claim,hash,now,me.uuid),
  db.prepare("INSERT INTO asset_grants(id,uuid,item,source,created,expires,revoked) SELECT lower(hex(randomblob(16))),r.claimed_by,j.value,'redeem:'||r.id,?,NULL,NULL FROM redemption_codes r,json_each(r.items) j WHERE r.claim_request=?").bind(now,claim),
  db.prepare("INSERT INTO platform_audit SELECT ?,?,'redeem-code',id,?,? FROM redemption_codes WHERE claim_request=?").bind(crypto.randomUUID(),me.uuid,JSON.stringify({uuid:me.uuid}),now,claim)
 ]);
 const result=await db.prepare('SELECT claimed_by,claim_request FROM redemption_codes WHERE code_hash=?').bind(hash).first();
 if(result?.claimed_by!==me.uuid)return json({error:'Invalid, expired, already used or unavailable redemption code'},400);
 return json({...await ownership(db,me.uuid,now),duplicate:result.claim_request!==claim});
}
