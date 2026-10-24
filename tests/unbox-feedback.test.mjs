import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {feedback, validateFeedback} from '../deploy/unbox/feedback.mjs';

const origin = 'https://printoria.printoria-studio.workers.dev';
function database() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../deploy/unbox/feedback.sql', import.meta.url), 'utf8'));
  return {DB: {prepare(sql) {
    let params = [];
    return {bind(...args) { params = args; return this; }, async all() { return {results: db.prepare(sql).all(...params)}; }, async run() { const result = db.prepare(sql).run(...params); return {meta: {changes: result.changes}}; }};
  }}};
}
const post = (message, options = {}) => new Request(origin + '/unbox/api/feedback', {
  method: 'POST', headers: {'Content-Type': 'application/json', Origin: origin, 'CF-Connecting-IP': '192.0.2.1', ...options.headers},
  body: JSON.stringify({name: 'Player', message, ...options.fields}),
});

test('Feedback validation bounds input without interpreting HTML', () => {
  assert.equal(validateFeedback({message: 'short'}), null);
  assert.equal(validateFeedback({message: 'x'.repeat(1201)}), null);
  assert.equal(validateFeedback({name: 'x'.repeat(33), message: 'Useful feedback.'}), null);
  assert.equal(validateFeedback({message: 'Useful feedback.', website: 'spam'}), null);
  assert.equal(validateFeedback({message: 'Useful\u0000feedback.'}), null);
  assert.deepEqual(validateFeedback({message: '  Add clearer map labels.  '}), {name: 'Anonymous', message: 'Add clearer map labels.'});
  assert.equal(validateFeedback({message: '<img src=x onerror=alert(1)>'}).message, '<img src=x onerror=alert(1)>');
});

test('Public posts persist, paginate, hide actor hashes and enforce atomic rate limits', async () => {
  const env = database();
  for (let i = 0; i < 3; i++) assert.equal((await feedback(post('A useful suggestion ' + i), env)).status, 201);
  const limited = await feedback(post('One more suggestion'), env);
  assert.equal(limited.status, 429);
  assert.equal(limited.headers.get('Retry-After'), '3600');
  for (let i = 2; i < 15; i++) assert.equal((await feedback(post('Another suggestion ' + i, {headers: {'CF-Connecting-IP': '192.0.2.' + i}}), env)).status, 201);
  const first = await (await feedback(new Request(origin + '/unbox/api/feedback'), env)).json();
  assert.equal(first.posts.length, 12); assert.ok(first.next);
  assert.ok(first.posts.every(p => !('actor_hash' in p)));
  const second = await (await feedback(new Request(origin + '/unbox/api/feedback?before=' + first.next), env)).json();
  assert.equal(second.posts.length, 4); assert.equal(second.next, null);
  assert.equal(new Set([...first.posts, ...second.posts].map(p => p.id)).size, 16);
});

test('Cross-origin, oversized and malformed requests cannot write posts', async () => {
  const env = database();
  assert.equal((await feedback(post('A useful suggestion', {headers: {Origin: 'https://other.test'}}), env)).status, 403);
  assert.equal((await feedback(post('x'.repeat(9000)), env)).status, 413);
  assert.equal((await feedback(post('A useful suggestion', {headers: {'Content-Type': 'text/plain'}}), env)).status, 415);
  assert.equal((await feedback(new Request(origin + '/unbox/api/feedback?before=invalid'), env)).status, 400);
  const result = await (await feedback(new Request(origin + '/unbox/api/feedback'), env)).json();
  assert.deepEqual(result.posts, []);
});
