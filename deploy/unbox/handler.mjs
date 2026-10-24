import {feedback} from './feedback.mjs';
import {auth} from './auth.mjs';
// Mounted before Printoria's existing handler. Every object is confined to unbox/.
export async function unbox(request, env) {
  const url = new URL(request.url);
  if (url.pathname === '/unbox') return Response.redirect(url.origin + '/unbox/' + url.search, 308);
  if (!url.pathname.startsWith('/unbox/')) return null;
  if (url.pathname.startsWith('/unbox/api/auth/')) {
    try { return await auth(request, env); }
    catch { return new Response(JSON.stringify({error: 'Sign-in is temporarily unavailable. Please try again.'}), {status: 503, headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}}); }
  }
  if (url.pathname === '/unbox/api/feedback') {
    try { return await feedback(request, env); }
    catch (error) { console.error('Unbox feedback request failed', error); return new Response(JSON.stringify({error: 'Could not load feedback. Please try again.'}), {status: 503, headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}}); }
  }
  if (!['GET', 'HEAD'].includes(request.method)) return new Response('Method not allowed', {status: 405, headers: {Allow: 'GET, HEAD'}});
  let path;
  try { path = decodeURIComponent(url.pathname.slice(7)); } catch { return new Response('Not found', {status: 404}); }
  if (path.includes('\\') || path.split('/').some(p => p === '..' || p === '.') || !/^[a-zA-Z0-9_./-]*$/.test(path)) return new Response('Not found', {status: 404});
  if (path === 'download/windows' || path === 'download/macos') {
    const object = await env.FILES.get('unbox/releases/latest.json');
    if (!object) return new Response('Release unavailable', {status: 503});
    const manifest = await object.json();
    const target = manifest.platforms[path.endsWith('windows') ? 'windows-x86_64' : 'darwin-aarch64'].url;
    if (!target.startsWith(url.origin + '/unbox/releases/')) return new Response('Release unavailable', {status: 503});
    return new Response(null, {status: 302, headers: {Location: target, 'Cache-Control': 'no-store'}});
  }
  const key = 'unbox/' + (path === 'auth/callback' ? 'auth-callback.html' : path || 'index.html');
  const head = await env.FILES.head(key);
  if (!head) return new Response('Not found', {status: 404});
  const headers = new Headers({'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'strict-origin-when-cross-origin', 'Accept-Ranges': 'bytes', ETag: head.httpEtag});
  head.writeHttpMetadata(headers);
  const versioned = /^releases\/v\d+\.\d+\.\d+\//.test(path);
  headers.set('Cache-Control', versioned ? 'public, max-age=31536000, immutable' : 'no-cache');
  if (path === 'auth/callback') {
    headers.set('Cache-Control', 'no-store');
    headers.set('Referrer-Policy', 'no-referrer');
    headers.set('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' https://login.microsoftonline.com; base-uri 'none'; frame-ancestors 'none'");
  }
  if (versioned && /\.(exe|gz)$/.test(path)) headers.set('Content-Disposition', `attachment; filename="${path.split('/').pop()}"`);
  if (request.headers.get('If-None-Match') === head.httpEtag) return new Response(null, {status: 304, headers});
  let range;
  const value = request.headers.get('Range');
  if (value && (!request.headers.get('If-Range') || request.headers.get('If-Range') === head.httpEtag)) {
    const match = /^bytes=(\d*)-(\d*)$/.exec(value);
    if (!match || (!match[1] && !match[2])) return new Response(null, {status: 416, headers: {'Content-Range': `bytes */${head.size}`}});
    const start = match[1] ? Number(match[1]) : Math.max(0, head.size - Number(match[2]));
    const end = match[1] && match[2] ? Math.min(Number(match[2]), head.size - 1) : head.size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= head.size) return new Response(null, {status: 416, headers: {'Content-Range': `bytes */${head.size}`}});
    range = {offset: start, length: end - start + 1};
    headers.set('Content-Range', `bytes ${start}-${end}/${head.size}`);
  }
  headers.set('Content-Length', String(range ? range.length : head.size));
  if (request.method === 'HEAD') return new Response(null, {status: range ? 206 : 200, headers});
  const object = await env.FILES.get(key, range ? {range} : undefined);
  if (!object) return new Response('Not found', {status: 404});
  return new Response(object.body, {status: range ? 206 : 200, headers});
}
