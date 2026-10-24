const json = (body, status = 200, extra = {}) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', ...extra}});

export function validateFeedback(body) {
  if (!body || typeof body !== 'object' || typeof body.message !== 'string' || (body.name !== undefined && typeof body.name !== 'string') || body.website) return null;
  const name = (body.name || '').trim().replace(/\s+/g, ' ') || 'Anonymous';
  const message = body.message.trim().replace(/\r\n/g, '\n');
  if (name.length > 32 || message.length < 10 || message.length > 1200 || /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(name + message)) return null;
  return {name, message};
}

export async function feedback(request, env) {
  const url = new URL(request.url);
  if (request.method === 'GET') {
    const before = url.searchParams.get('before');
    let statement;
    if (before) {
      const match = /^(\d{13}):([a-f0-9-]{36})$/.exec(before);
      if (!match) return json({error: 'Invalid page.'}, 400);
      statement = env.DB.prepare('SELECT id,name,message,created_at FROM unbox_feedback WHERE created_at < ? OR (created_at = ? AND id < ?) ORDER BY created_at DESC,id DESC LIMIT 13').bind(Number(match[1]), Number(match[1]), match[2]);
    } else statement = env.DB.prepare('SELECT id,name,message,created_at FROM unbox_feedback ORDER BY created_at DESC,id DESC LIMIT 13');
    const {results} = await statement.all();
    const more = results.length > 12;
    const posts = results.slice(0, 12);
    const last = posts.at(-1);
    return json({posts, next: more ? `${last.created_at}:${last.id}` : null});
  }
  if (request.method !== 'POST') return json({error: 'Method not allowed.'}, 405, {Allow: 'GET, POST'});
  if (request.headers.get('Origin') !== url.origin) return json({error: 'Please post from the Unbox website.'}, 403);
  if (!request.headers.get('Content-Type')?.startsWith('application/json')) return json({error: 'Expected a message.'}, 415);
  // Bound actual bytes, including chunked bodies, before parsing user input.
  const reader = request.body?.getReader();
  if (!reader) return json({error: 'Enter a message.'}, 400);
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > 8192) { await reader.cancel(); return json({error: 'Your message is too long.'}, 413); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  let body;
  try { body = JSON.parse(new TextDecoder().decode(bytes)); } catch { return json({error: 'Enter a valid message.'}, 400); }
  const fields = validateFeedback(body);
  if (!fields) return json({error: 'Use a name up to 32 characters and a message from 10 to 1,200 characters.'}, 400);
  const address = request.headers.get('CF-Connecting-IP');
  if (!address) return json({error: 'Could not verify this request. Please retry.'}, 400);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode('unbox-feedback:' + address));
  const actor = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  const now = Date.now(); const id = crypto.randomUUID();
  // One statement makes the rate check and insertion atomic.
  const result = await env.DB.prepare('INSERT INTO unbox_feedback (id,name,message,created_at,actor_hash) SELECT ?,?,?,?,? WHERE (SELECT COUNT(*) FROM unbox_feedback WHERE actor_hash=? AND created_at>?) < 3').bind(id, fields.name, fields.message, now, actor, actor, now - 3600000).run();
  if (!result.meta.changes) return json({error: 'You have posted 3 messages this hour. Please try again later.'}, 429, {'Retry-After': '3600'});
  return json({post: {id, ...fields, created_at: now}}, 201);
}
