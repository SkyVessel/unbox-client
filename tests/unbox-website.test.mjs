import test from 'node:test';
import assert from 'node:assert/strict';
import {unbox} from '../deploy/unbox/handler.mjs';

const origin = 'https://printoria.printoria-studio.workers.dev';
const url = origin + '/unbox/';
const seen = [];
const data = new TextEncoder().encode('0123456789');
const env = {FILES: {
  async head(key) {
    seen.push(key);
    if (key.endsWith('missing')) return null;
    return {size: data.length, httpEtag: '"etag"', writeHttpMetadata(h) { h.set('Content-Type', 'application/octet-stream'); }};
  },
  async get(key, options) {
    seen.push(key);
    if (key.endsWith('latest.json')) return {json: async () => ({platforms: {'windows-x86_64': {url: origin + '/unbox/releases/v0.2.21/setup.exe'}}})};
    const range = options?.range;
    return {body: range ? data.slice(range.offset, range.offset + range.length) : data};
  },
}};

test('Unbox routing leaves Printoria routes untouched and confines R2 reads', async () => {
  assert.equal(await unbox(new Request(origin + '/marketplace'), env), null);
  assert.equal(await unbox(new Request(origin + '/api/builds'), env), null);
  assert.equal((await unbox(new Request(url + '%2F..%2Fprivate'), env)).status, 404);
  assert.equal((await unbox(new Request(url + 'missing'), env)).status, 404);
  assert.equal((await unbox(new Request(url, {method: 'POST'}), env)).status, 405);
  const redirect = await unbox(new Request(origin + '/unbox?theme=light'), env);
  assert.equal(redirect.headers.get('Location'), url + '?theme=light');
  assert.ok(seen.every(key => key.startsWith('unbox/')));
});

test('Downloads support complete, resumed and suffix downloads with immutable version URLs', async () => {
  const path = url + 'releases/v0.2.21/setup.exe';
  const full = await unbox(new Request(path), env);
  assert.equal(await full.text(), '0123456789');
  assert.match(full.headers.get('Content-Disposition'), /attachment/);
  assert.match(full.headers.get('Cache-Control'), /immutable/);
  for (const [range, expected, span] of [['bytes=2-5', '2345', '2-5'], ['bytes=8-', '89', '8-9'], ['bytes=-3', '789', '7-9']]) {
    const response = await unbox(new Request(path, {headers: {Range: range}}), env);
    assert.equal(response.status, 206);
    assert.equal(response.headers.get('Content-Range'), `bytes ${span}/10`);
    assert.equal(await response.text(), expected);
  }
  assert.equal((await unbox(new Request(path, {headers: {Range: 'bytes=20-'}}), env)).status, 416);
  const head = await unbox(new Request(path, {method: 'HEAD'}), env);
  assert.equal(await head.text(), '');
  assert.equal(head.headers.get('Content-Length'), '10');
});

test('Mutable release metadata is revalidated and download links stay on Unbox', async () => {
  const response = await unbox(new Request(url + 'releases/latest.json'), env);
  assert.equal(response.headers.get('Cache-Control'), 'no-cache');
  const download = await unbox(new Request(url + 'download/windows'), env);
  assert.equal(download.status, 302);
  assert.equal(download.headers.get('Location'), url + 'releases/v0.2.21/setup.exe');
  assert.equal(download.headers.get('Cache-Control'), 'no-store');
});
