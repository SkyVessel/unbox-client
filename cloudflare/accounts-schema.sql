CREATE TABLE IF NOT EXISTS unbox_accounts(email TEXT PRIMARY KEY, person_id TEXT NOT NULL UNIQUE, created INTEGER NOT NULL, password_hash TEXT NOT NULL, password_salt TEXT NOT NULL, password_iterations INTEGER NOT NULL, email_verified INTEGER NOT NULL DEFAULT 0 CHECK(email_verified IN (0,1)));
CREATE TABLE IF NOT EXISTS account_sessions(token_hash TEXT PRIMARY KEY,person_id TEXT NOT NULL,expires INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS account_session_person ON account_sessions(person_id);
CREATE TABLE IF NOT EXISTS account_limits(key TEXT PRIMARY KEY,n INTEGER NOT NULL,expires INTEGER NOT NULL);

-- Current live v1 has no unbox_accounts table. Apply social-v2-migration.sql once as well.
