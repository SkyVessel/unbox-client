const json=(b,s=200)=>Response.json(b,{status:s,headers:{'Cache-Control':'no-store'}});
const bytes=new TextEncoder();
const hex=b=>Array.from(new Uint8Array(b),x=>x.toString(16).padStart(2,'0')).join('');
export const digest=async s=>hex(await crypto.subtle.digest('SHA-256',bytes.encode(s)));
const random=()=>hex(crypto.getRandomValues(new Uint8Array(32)));
// Workers currently caps PBKDF2 at 100,000. Explicit development policy; no silent fallback.
// Review the work factor and Free CPU budget before public launch (docs/13).
const iterations=100000;
function email(value){const s=typeof value==='string'?value.trim().toLowerCase():'';if(s.length>254||!/^([^\s@]+)@([^\s@]+\.[^\s@]+)$/.test(s))throw Error('Invalid email address');return s}
async function passwordHash(password,salt,rounds){const key=await crypto.subtle.importKey('raw',bytes.encode(password),'PBKDF2',false,['deriveBits']);return hex(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:Uint8Array.from(salt.match(/../g),s=>parseInt(s,16)),iterations:rounds},key,256))}
function equal(a,b){if(a.length!==b.length)return false;let diff=0;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);return diff===0}
export async function accountRoute(path,b,request,env){
 if(path==='/v1/accounts/code'||path==='/v1/accounts/verify')return json({error:'Email verification is not enabled. Sign in with your password.'},503);
 if(path!=='/v1/accounts/register'&&path!=='/v1/accounts/login')return null;
 const db=env.DB,now=Date.now(),address=email(b.email),register=path.endsWith('/register');
 if(typeof b.password!=='string'||b.password.length<12||b.password.length>128||bytes.encode(b.password).length>512)return json({error:'Use a password with 12–128 characters'},400);
 // Reserve limits atomically before expensive password work. IP comes from Cloudflare, not request JSON.
 const buckets=[['auth-day:'+Math.floor(now/86400000),1000,now+86400000],['auth-ip:'+await digest(request.headers.get('CF-Connecting-IP')||'unknown')+':'+Math.floor(now/900000),20,now+900000],['auth-email:'+await digest(address)+':'+Math.floor(now/900000),10,now+900000]];
 for(const [key,limit,expires] of buckets){const r=await db.prepare('INSERT INTO account_limits(key,n,expires) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET n=n+1 WHERE n<? RETURNING n').bind(key,expires,limit).first();if(!r)return json({error:'Too many attempts. Try again in 15 minutes.'},429)}
 let person=await db.prepare('SELECT p.*,a.password_hash,a.password_salt,a.password_iterations,a.email_verified FROM unbox_accounts a JOIN people p ON p.id=a.person_id WHERE a.email=?').bind(address).first();
 if(register&&person)return json({error:'An account already uses this email. Sign in instead.'},409);
 const salt=register?random():(person?.password_salt||'00'.repeat(32));
 const derived=await passwordHash(b.password,salt,person?.password_iterations||iterations);
 if(!register&&(!person||!equal(derived,person.password_hash)))return json({error:'Incorrect email or password'},401);
 const token=random(),tokenHash=await digest(token),expires=now+30*86400000;
 if(register){
  const count=await db.prepare('SELECT COUNT(*) AS n FROM people').first();if(count.n>=2000)return json({error:'Registration capacity reached'},503);
  const id=crypto.randomUUID();person={id,name:'Unbox_'+id.replaceAll('-','').slice(0,8),uuid:crypto.randomUUID().replaceAll('-',''),kind:'unbox',email_verified:0};
  try{await db.batch([db.prepare("INSERT INTO people(id,code,token_hash,name,uuid,kind,seen) VALUES(?,?,?,?,?,'unbox',?)").bind(id,id.replaceAll('-','').slice(0,12).toUpperCase(),await digest(random()),person.name,person.uuid,now),db.prepare('INSERT INTO unbox_accounts(email,person_id,created,password_hash,password_salt,password_iterations,email_verified) VALUES(?,?,?,?,?,?,0)').bind(address,id,now,derived,salt,iterations),db.prepare('INSERT INTO account_sessions(token_hash,person_id,expires) VALUES(?,?,?)').bind(tokenHash,id,expires)])}catch{return json({error:'Could not create the account. It may already exist; try signing in.'},409)}
 }else await db.prepare('INSERT INTO account_sessions(token_hash,person_id,expires) VALUES(?,?,?)').bind(tokenHash,person.id,expires).run();
 return json({id:person.id,token,expires,account:{accountId:'unbox:'+person.id,name:person.name,uuid:person.uuid,type:'unbox',provider:'Unbox',emailVerified:person.email_verified===1}},register?201:200);
}
export async function cleanupAccounts(db,now){await db.batch([db.prepare('DELETE FROM account_sessions WHERE expires<=?').bind(now),db.prepare('DELETE FROM account_limits WHERE expires<=?').bind(now)])}
