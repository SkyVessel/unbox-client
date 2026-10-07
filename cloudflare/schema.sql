CREATE TABLE IF NOT EXISTS manifests (
 id TEXT PRIMARY KEY,
 secret_hash TEXT NOT NULL,
 payload TEXT NOT NULL,
 expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS manifest_expiry ON manifests(expires_at);
