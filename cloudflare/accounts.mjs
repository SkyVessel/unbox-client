// Legacy email authentication is retired. Keep only hashing and expiry cleanup.
const hex=b=>Array.from(new Uint8Array(b),x=>x.toString(16).padStart(2,'0')).join('');
export const digest=async s=>hex(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)));
export async function cleanupAccounts(db,now){await db.batch([db.prepare('DELETE FROM account_sessions WHERE expires<=?').bind(now),db.prepare('DELETE FROM account_limits WHERE expires<=?').bind(now)])}
