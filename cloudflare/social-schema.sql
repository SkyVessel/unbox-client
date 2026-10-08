CREATE TABLE IF NOT EXISTS people(id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, token_hash TEXT NOT NULL, name TEXT NOT NULL, uuid TEXT, kind TEXT NOT NULL, face TEXT, seen INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'Launcher');
CREATE TABLE IF NOT EXISTS friendships(sender TEXT NOT NULL, receiver TEXT NOT NULL, accepted INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, PRIMARY KEY(sender,receiver));
CREATE INDEX IF NOT EXISTS friend_receiver ON friendships(receiver,accepted);
CREATE TABLE IF NOT EXISTS invites(id TEXT PRIMARY KEY, sender TEXT NOT NULL, receiver TEXT NOT NULL, room TEXT NOT NULL, expires INTEGER NOT NULL, relay_requested INTEGER NOT NULL DEFAULT 0, accepted INTEGER NOT NULL DEFAULT 0, join_secret TEXT);
CREATE INDEX IF NOT EXISTS invite_receiver ON invites(receiver,expires);
CREATE INDEX IF NOT EXISTS invite_sender ON invites(sender,expires);
CREATE UNIQUE INDEX IF NOT EXISTS people_token ON people(token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS friendship_pair ON friendships(min(sender,receiver),max(sender,receiver));
CREATE TABLE IF NOT EXISTS challenges(id TEXT PRIMARY KEY,token_hash TEXT NOT NULL,nonce TEXT NOT NULL,expires INTEGER NOT NULL);
