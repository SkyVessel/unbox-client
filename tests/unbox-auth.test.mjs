import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPair, SignJWT} from 'jose';
import {auth, hash, getSession, verifyMicrosoftToken, CLIENT_ID, ISSUER} from '../deploy/unbox/auth.mjs';
import {database, session} from './unbox-test-db.mjs';
const origin = 'https://printoria.printoria-studio.workers.dev';
const request = (route, body = {}, cookie = '') => new Request(origin + '/unbox/api/auth/' + route, {method: 'POST', headers: {Origin: origin, 'Content-Type': 'application/json', Cookie: cookie, 'CF-Connecting-IP': '192.0.2.1'}, body: JSON.stringify(body)});

test('Microsoft ID token verification binds issuer, audience, signature, expiry and nonce', async () => {
  const {privateKey, publicKey} = await generateKeyPair('RS256');
  const signed = (overrides = {}, key = privateKey) => new SignJWT({sub: 'stable-player-id', nonce: 'flow-nonce', email: 'player@example.com', name: 'Player', iss: ISSUER, aud: CLIENT_ID, iat: Math.floor(Date.now()/1000), exp: Math.floor(Date.now()/1000)+600, ...overrides}).setProtectedHeader({alg: 'RS256'}).sign(key);
  const user = await verifyMicrosoftToken(await signed(), 'flow-nonce', publicKey);
  assert.equal(user.account_id, ISSUER + '|stable-player-id'); assert.equal(user.email, 'player@example.com');
  for (const changed of [{iss: 'https://other.test'}, {aud: 'other-app'}, {nonce: 'other-flow'}, {exp: 1}, {sub: ''}, {email: null}]) await assert.rejects(verifyMicrosoftToken(await signed(changed), 'flow-nonce', publicKey));
  const other = await generateKeyPair('RS256');
  await assert.rejects(verifyMicrosoftToken(await signed({}, other.privateKey), 'flow-nonce', publicKey));
  assert.equal((await verifyMicrosoftToken(await signed({email: undefined, preferred_username: 'alias@example.com'}), 'flow-nonce', publicKey)).account_id, user.account_id);
});

test('PKCE flow requires the same browser cookie and state, expires and rate limits', async () => {
  const env = database();
  const started = await auth(request('start'), env); assert.equal(started.status, 200);
  const setCookie = started.headers.get('Set-Cookie');
  assert.match(setCookie, /Secure; HttpOnly; SameSite=Lax/);
  const cookie = setCookie.split(';')[0];
  const url = new URL((await started.json()).url); const state = url.searchParams.get('state');
  assert.equal(url.searchParams.get('redirect_uri'), origin + '/unbox/auth/callback');
  assert.equal(url.searchParams.get('response_type'), 'code');
  assert.equal(url.searchParams.get('scope'), 'openid profile email');
  const flow = await auth(request('flow', {state}, cookie), env); assert.equal(flow.status, 200);
  assert.equal(await hash((await flow.json()).verifier), url.searchParams.get('code_challenge'));
  assert.equal((await auth(request('flow', {state}), env)).status, 401);
  assert.equal((await auth(request('flow', {state: 'wrong'}, cookie), env)).status, 401);
  assert.equal((await auth(request('complete', {state, idToken: 'invalid'}, cookie), env)).status, 401);
  assert.equal((await auth(request('flow', null, cookie), env)).status, 401);
  for (let i = 0; i < 9; i++) assert.equal((await auth(request('start'), env)).status, 200);
  assert.equal((await auth(request('start'), env)).status, 429);
  env.db.exec('UPDATE unbox_auth_flows SET expires=0');
  assert.equal((await auth(request('flow', {state}, cookie), env)).status, 401);
});

test('Sessions expire, logout revokes them, and cross-origin mutations are rejected', async () => {
  const env = database(); const cookie = await session(env);
  assert.equal((await getSession(request('session', {}, cookie), env)).email, 'player1@example.com');
  const read = new Request(origin + '/unbox/api/auth/session', {headers: {Cookie: cookie}});
  const result = await auth(read, env); assert.equal(result.headers.get('Cache-Control'), 'no-store');
  assert.deepEqual(Object.keys((await result.json()).user).sort(), ['email','name']);
  const cross = request('logout', {}, cookie); cross.headers.set('Origin', 'https://other.test');
  assert.equal((await auth(cross, env)).status, 403);
  assert.equal((await auth(request('logout', {}, cookie), env)).status, 200);
  assert.equal(await getSession(read, env), null);
  const expired = await session(env, 2, 0);
  assert.equal(await getSession(request('session', {}, expired), env), null);
});
