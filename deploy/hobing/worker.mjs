import {unbox} from '../unbox/handler.mjs';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (env.PUBLIC_ORIGIN) {
      let canonical;
      try {
        canonical = new URL(env.PUBLIC_ORIGIN);
        if (canonical.protocol !== 'https:' || canonical.username || canonical.password || canonical.pathname !== '/' || canonical.search || canonical.hash || canonical.origin !== env.PUBLIC_ORIGIN) throw new Error();
      } catch { return new Response('Invalid site configuration', {status: 503}); }
      if (url.origin !== canonical.origin) {
        const target = canonical.origin + url.pathname + url.search;
        if (url.hostname === 'runtime-check.invalid') return await unbox(new Request(target, request), env) || new Response('Not found', {status: 404});
        return Response.redirect(target, 308);
      }
    }
    if (url.pathname === '/') return Response.redirect(url.origin + '/unbox/' + url.search, 308);
    return await unbox(request, env) || new Response('Not found', {status: 404});
  },
};
