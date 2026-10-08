import {test} from 'node:test';
import assert from 'node:assert/strict';
import {database} from './social-database.mjs';
import worker from '../cloudflare/social.mjs';
const password='a long test passphrase';
function fixture(){const db=database(),env={DB:db};
 const send=async(path,b={},token,ip='192.0.2.1')=>{const r=await worker.fetch(new Request('https://test/v1/'+path,{method:'POST',headers:{...(token?{Authorization:'Bearer '+token}:{}),'CF-Connecting-IP':ip},body:JSON.stringify(b)}),env);return {status:r.status,...await r.json()}};
 return{db,env,send}}
test('password registration needs no mail, rejects duplicate email, and restores stable identity on login',async()=>{const f=fixture();try{
 const a=await f.send('accounts/register',{email:' Test@Example.com ',password});assert.equal(a.status,201);assert.equal(a.account.type,'unbox');assert.equal(a.account.emailVerified,false);assert.match(a.account.name,/^Unbox_[a-f0-9]{8}$/);assert.match(a.token,/^[a-f0-9]{64}$/);
 const duplicate=await f.send('accounts/register',{email:'test@example.com',password:'different password'});assert.equal(duplicate.status,409);assert.equal('token' in duplicate,false);
 const b=await f.send('accounts/login',{email:'TEST@example.com',password});assert.equal(b.status,200);assert.equal(b.id,a.id);assert.equal(b.account.uuid,a.account.uuid);assert.equal(b.account.name,a.account.name);assert.notEqual(a.token,b.token);
 const profile={name:a.account.name,uuid:a.account.uuid,kind:'unbox'};assert.equal((await f.send('sync',profile,a.token)).status,200);const stale=await f.send('sync',{...profile,name:'Impostor'},a.token);assert.equal(stale.status,200);assert.equal(stale.self.name,a.account.name);
 await f.send('accounts/logout',{},a.token);assert.equal((await f.send('sync',profile,a.token)).status,401);assert.equal((await f.send('sync',profile,b.token)).status,200);
 f.db.sql.exec('UPDATE account_sessions SET expires=0');assert.equal((await f.send('sync',profile,b.token)).status,401);
 }finally{f.db.sql.close()}});
test('passwords are salted, invalid credentials fail uniformly, and secrets never enter social views',async()=>{const f=fixture();try{
 const a=await f.send('accounts/register',{email:'a@example.com',password}),b=await f.send('accounts/register',{email:'b@example.com',password});assert.equal(a.status,201);assert.equal(b.status,201);
 const rows=f.db.sql.prepare('SELECT * FROM unbox_accounts').all();assert.notEqual(rows[0].password_salt,rows[1].password_salt);assert.notEqual(rows[0].password_hash,rows[1].password_hash);for(const r of rows){assert.match(r.password_hash,/^[a-f0-9]{64}$/);assert.equal(r.email_verified,0)}assert.equal(JSON.stringify(rows).includes(password),false);
 const bad=await f.send('accounts/login',{email:'a@example.com',password:'wrong password'}),missing=await f.send('accounts/login',{email:'missing@example.com',password});assert.equal(bad.status,401);assert.deepEqual(bad,missing);
 const v=await f.send('sync',{name:a.account.name,uuid:a.account.uuid,kind:'unbox'},a.token);for(const secret of [password,a.token,'a@example.com',rows[0].password_hash,rows[0].password_salt])assert.equal(JSON.stringify(v).includes(secret),false);
 assert.equal((await f.send('accounts/code',{email:'a@example.com',mode:'register'})).status,503);assert.equal((await f.send('accounts/verify',{id:a.id,code:'000000'})).status,503);
 }finally{f.db.sql.close()}});
test('validation and attempt limits stop requests without overwriting accounts',async()=>{const f=fixture();try{
 for(const b of [{email:'bad',password},{email:'ok@example.com',password:'short'},{email:'ok@example.com',password:'a'.repeat(129)},{email:'ok@example.com',password:123}])assert.equal((await f.send('accounts/register',b)).status,400);
 assert.equal(f.db.sql.prepare('SELECT COUNT(*) n FROM people').get().n,0);
 const a=await f.send('accounts/register',{email:'limit@example.com',password});for(let i=0;i<9;i++)assert.equal((await f.send('accounts/login',{email:'limit@example.com',password:'wrong password'})).status,401);
 assert.equal((await f.send('accounts/login',{email:'limit@example.com',password})).status,429);
 assert.equal((await f.send('accounts/login',{email:'limit@example.com',password},null,'192.0.2.2')).status,429);
 assert.equal(f.db.sql.prepare('SELECT COUNT(*) n FROM people').get().n,1);assert.equal(f.db.sql.prepare('SELECT id FROM people').get().id,a.id);
 }finally{f.db.sql.close()}});
test('concurrent duplicate registration creates exactly one identity and preserves the original password',async()=>{const f=fixture();try{
 const results=await Promise.all([f.send('accounts/register',{email:'race@example.com',password}),f.send('accounts/register',{email:'RACE@example.com',password:'the other password'})]);assert.deepEqual(results.map(r=>r.status).sort(),[201,409]);assert.equal(f.db.sql.prepare('SELECT COUNT(*) n FROM people').get().n,1);assert.equal(f.db.sql.prepare('SELECT COUNT(*) n FROM account_sessions').get().n,1);
 }finally{f.db.sql.close()}});
test('nicknames can be duplicated and changed without changing email, UUID, friends or ownership',async()=>{const f=fixture();try{
 const a=await f.send('accounts/register',{email:'first@example.com',password}),b=await f.send('accounts/register',{email:'second@example.com',password});
 await f.send('friends/request',{code:b.id.replaceAll('-','').slice(0,12).toUpperCase()},a.token);await f.send('friends/accept',{id:a.id},b.token);
 for(const p of [a,b]){const changed=await f.send('accounts/profile',{name:'SameName'},p.token);assert.equal(changed.status,200);assert.equal(changed.account.name,'SameName');assert.equal(changed.account.uuid,p.account.uuid)}
 const sync=await f.send('sync',{name:a.account.name,uuid:a.account.uuid,kind:'unbox'},a.token);assert.equal(sync.status,200);assert.equal(sync.self.name,'SameName');assert.equal(sync.friends[0].name,'SameName');assert.equal(sync.friends[0].id,b.id);assert.equal(sync.friends[0].accepted,1);
 const renamed=await f.send('accounts/profile',{name:'NewName',id:b.id},a.token);assert.equal(renamed.account.name,'NewName');assert.equal((await f.send('accounts/profile',{},b.token)).account.name,'SameName');
 const login=await f.send('accounts/login',{email:'first@example.com',password});assert.equal(login.account.name,'NewName');assert.equal(login.account.uuid,a.account.uuid);assert.equal((await f.send('accounts/register',{email:'FIRST@example.com',password})).status,409);
 assert.equal((await f.send('accounts/profile',{name:'bad name'},a.token)).status,400);assert.equal((await f.send('accounts/profile',{name:'Spoof'})).status,401);
 }finally{f.db.sql.close()}});
