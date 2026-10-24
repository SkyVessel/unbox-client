CREATE TABLE IF NOT EXISTS unbox_auth_flows (
  id TEXT PRIMARY KEY, state TEXT NOT NULL, nonce TEXT NOT NULL,
  verifier TEXT NOT NULL, actor_hash TEXT NOT NULL, expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS unbox_auth_flows_expiry ON unbox_auth_flows(expires);
CREATE TABLE IF NOT EXISTS unbox_sessions (
  id TEXT PRIMARY KEY, account_id TEXT NOT NULL, email TEXT NOT NULL,
  name TEXT NOT NULL, expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS unbox_sessions_expiry ON unbox_sessions(expires);
