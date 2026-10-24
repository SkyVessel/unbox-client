import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {hash} from '../deploy/unbox/auth.mjs';
export function database() {
  const db = new DatabaseSync(':memory:');
  for (const file of ['feedback.sql', 'auth.sql']) db.exec(readFileSync(new URL('../deploy/unbox/' + file, import.meta.url), 'utf8'));
  const DB = {prepare(sql) {
    let params = [];
    return {bind(...args) { params = args; return this; }, async first() { return db.prepare(sql).get(...params) || null; }, async all() { return {results: db.prepare(sql).all(...params)}; }, async run() { const result = db.prepare(sql).run(...params); return {meta: {changes: result.changes}}; }};
  }, async batch(statements) { return Promise.all(statements.map(s => s.run())); }};
  return {DB, db};
}
export async function session(env, index = 1, expires = Date.now() + 60000) {
  const token = String(index).padStart(43, 'a');
  await env.DB.prepare('INSERT INTO unbox_sessions VALUES (?,?,?,?,?)').bind(await hash(token), 'account-' + index, `player${index}@example.com`, 'Test player', expires).run();
  return '__Secure-unbox_session=' + token;
}
