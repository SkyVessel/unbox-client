import {createRemoteJWKSet, jwtVerify} from 'jose';

export const CLIENT_ID = '0136aeab-bc7a-41b1-988e-08bf665fd53c';
export const ISSUER = 'https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0';
const microsoftKeys = createRemoteJWKSet(new URL('https://login.microsoftonline.com/consumers/discovery/v2.0/keys'));
const SCOPE = 'openid profile email';
const SESSION = '__Secure-unbox_session';
const FLOW = '__Secure-unbox_flow';
const base64url = bytes => btoa(String.fromCharCode(...bytes)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
const random = () => base64url(crypto.getRandomValues(new Uint8Array(32)));
export const hash = async text => base64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))));
const cookie = (name, value, seconds) => `${name}=${value}; Path=/unbox/; Secure; HttpOnly; SameSite=Lax; Max-Age=${seconds}`;
const getCookie = (request, name) => (request.headers.get('Cookie') || '').split(';').map(s => s.trim()).find(s => s.startsWith(name + '='))?.slice(name.length + 1);
const json = (data, status = 200, cookies = []) => {
  const headers = new Headers({'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'});
  for (const value of cookies) headers.append('Set-Cookie', value);
  return new Response(JSON.stringify(data), {status, headers});
};

export async function readJson(request, limit = 32768) {
  if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw new Error('Expected JSON.');
  const reader = request.body?.getReader();
  if (!reader) throw new Error('Missing request.');
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read(); if (done) break;
    size += value.byteLength;
    if (size > limit) { await reader.cancel(); throw new Error('Request too large.'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return JSON.parse(new TextDecoder().decode(bytes));
}

export async function verifyMicrosoftToken(token, nonce, keys = microsoftKeys) {
  const {payload} = await jwtVerify(token, keys, {issuer: ISSUER, audience: CLIENT_ID, algorithms: ['RS256'], requiredClaims: ['sub', 'exp', 'iat', 'nonce'], clockTolerance: 5});
  if (payload.nonce !== nonce || typeof payload.sub !== 'string' || !payload.sub || payload.sub.length > 256) throw new Error('Invalid sign-in session.');
  const email = [payload.email, payload.preferred_username].find(value => typeof value === 'string' && value.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value));
  if (!email) throw new Error('Microsoft did not share an email address. Use a Microsoft account with an email address.');
  return {account_id: `${ISSUER}|${payload.sub}`, email, name: typeof payload.name === 'string' ? payload.name.slice(0, 100) : 'Microsoft account'};
}

export async function getSession(request, env) {
  const token = getCookie(request, SESSION);
  if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return null;
  return env.DB.prepare('SELECT account_id,email,name FROM unbox_sessions WHERE id=? AND expires>?').bind(await hash(token), Date.now()).first();
}

export async function auth(request, env) {
  const url = new URL(request.url); const route = url.pathname.split('/').pop();
  if (route === 'session' && request.method === 'GET') {
    const user = await getSession(request, env);
    return json({user: user ? {name: user.name, email: user.email} : null});
  }
  if (request.method !== 'POST') return json({error: 'Method not allowed.'}, 405);
  if (request.headers.get('Origin') !== url.origin) return json({error: 'Please sign in from the Unbox website.'}, 403);
  if (route === 'logout') {
    const token = getCookie(request, SESSION);
    if (token) await env.DB.prepare('DELETE FROM unbox_sessions WHERE id=?').bind(await hash(token)).run();
    return json({ok: true}, 200, [cookie(SESSION, '', 0), cookie(FLOW, '', 0)]);
  }
  if (route === 'start') {
    const now = Date.now();
    await env.DB.batch([env.DB.prepare('DELETE FROM unbox_auth_flows WHERE expires<?').bind(now), env.DB.prepare('DELETE FROM unbox_sessions WHERE expires<?').bind(now)]);
    const token = random(), state = random(), nonce = random(), verifier = random();
    const actor = await hash(request.headers.get('CF-Connecting-IP') || 'unknown');
    const result = await env.DB.prepare('INSERT INTO unbox_auth_flows (id,state,nonce,verifier,actor_hash,expires) SELECT ?,?,?,?,?,? WHERE (SELECT COUNT(*) FROM unbox_auth_flows WHERE actor_hash=?)<10').bind(await hash(token), state, nonce, verifier, actor, now + 600000, actor).run();
    if (!result.meta.changes) return json({error: 'Too many sign-in attempts. Please try again in a few minutes.'}, 429);
    const authorization = new URL('https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize');
    authorization.search = new URLSearchParams({client_id: CLIENT_ID, response_type: 'code', redirect_uri: url.origin + '/unbox/auth/callback', response_mode: 'query', scope: SCOPE, state, nonce, code_challenge: await hash(verifier), code_challenge_method: 'S256', prompt: 'select_account'});
    return json({url: authorization.href}, 200, [cookie(FLOW, token, 600)]);
  }
  if (!['flow', 'complete'].includes(route)) return json({error: 'Not found.'}, 404);
  let body; try { body = await readJson(request); } catch { return json({error: 'Invalid sign-in request.'}, 400); }
  const token = getCookie(request, FLOW);
  if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return json({error: 'Sign-in expired. Please try again.'}, 401);
  const id = await hash(token);
  const flow = await env.DB.prepare('SELECT state,nonce,verifier FROM unbox_auth_flows WHERE id=? AND expires>?').bind(id, Date.now()).first();
  if (!flow || !body || typeof body.state !== 'string' || body.state !== flow.state) return json({error: 'Sign-in expired or did not start in this browser. Please try again.'}, 401);
  if (route === 'flow') return json({verifier: flow.verifier, clientId: CLIENT_ID, scope: SCOPE, redirectUri: url.origin + '/unbox/auth/callback'});
  if (typeof body.idToken !== 'string') return json({error: 'Microsoft did not return a sign-in token.'}, 400);
  let user;
  try { user = await verifyMicrosoftToken(body.idToken, flow.nonce); }
  catch (error) { return json({error: error.message.startsWith('Microsoft did not share') ? error.message : 'Microsoft sign-in could not be verified. Please try again.'}, 401); }
  const consumed = await env.DB.prepare('DELETE FROM unbox_auth_flows WHERE id=? AND expires>? RETURNING id').bind(id, Date.now()).first();
  if (!consumed) return json({error: 'This sign-in has already been used. Please try again.'}, 401);
  const session = random();
  await env.DB.prepare('INSERT INTO unbox_sessions (id,account_id,email,name,expires) VALUES (?,?,?,?,?)').bind(await hash(session), user.account_id, user.email, user.name, Date.now() + 604800000).run();
  return json({user: {name: user.name, email: user.email}}, 200, [cookie(SESSION, session, 604800), cookie(FLOW, '', 0)]);
}
