import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../deploy/hobing/worker.mjs';

test('Hobing serves Unbox at its existing prefix and redirects the root', async () => {
  const origin = 'https://unbox.example.test';
  const env = {FILES: {
    async head(key) {
      assert.equal(key, 'unbox/index.html');
      return {size: 2, httpEtag: '"test"', writeHttpMetadata(headers) { headers.set('Content-Type', 'text/html'); }};
    },
    async get(key) { assert.equal(key, 'unbox/index.html'); return {body: 'ok'}; },
  }};
  const redirect = await worker.fetch(new Request(origin + '/?theme=light'), env);
  assert.equal(redirect.status, 308);
  assert.equal(redirect.headers.get('Location'), origin + '/unbox/?theme=light');
  const page = await worker.fetch(new Request(origin + '/unbox/'), env);
  assert.equal(await page.text(), 'ok');
  assert.equal((await worker.fetch(new Request(origin + '/other'), env)).status, 404);
});

test('Canonical origin redirects aliases and preserves the internal health check', async () => {
  const env = {PUBLIC_ORIGIN: 'https://unbox.example.test', FILES: {
    async head(key) { assert.equal(key, 'unbox/index.html'); return {size: 2, httpEtag: '"test"', writeHttpMetadata() {}}; },
    async get() { return {body: 'ok'}; },
  }};
  const alias = await worker.fetch(new Request('https://alias.example.test/unbox/download/windows?x=1'), env);
  assert.equal(alias.status, 308);
  assert.equal(alias.headers.get('Location'), 'https://unbox.example.test/unbox/download/windows?x=1');
  const health = await worker.fetch(new Request('https://runtime-check.invalid/unbox/'), env);
  assert.equal(health.status, 200);
  assert.equal(await health.text(), 'ok');
  assert.equal((await worker.fetch(new Request('https://unbox.example.test/unbox/'), {...env, PUBLIC_ORIGIN: 'https://evil.test/path'})).status, 503);
});
