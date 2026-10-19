-- Additive migration: do not remove legacy accounts until local migrations have shipped.
CREATE TABLE IF NOT EXISTS player_accounts(uuid TEXT PRIMARY KEY,created INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','suspended')));
CREATE TABLE IF NOT EXISTS account_devices(id TEXT PRIMARY KEY,person_id TEXT NOT NULL,token_hash TEXT NOT NULL UNIQUE,created INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS asset_catalog(id TEXT PRIMARY KEY,kind TEXT NOT NULL CHECK(kind IN ('logo','cape','role')),name TEXT NOT NULL,resource TEXT,enabled INTEGER NOT NULL DEFAULT 1,features TEXT NOT NULL DEFAULT '[]',updated INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS asset_grants(id TEXT PRIMARY KEY,uuid TEXT NOT NULL,item TEXT NOT NULL,source TEXT NOT NULL,created INTEGER NOT NULL,expires INTEGER,revoked INTEGER,UNIQUE(uuid,item,source));
CREATE INDEX IF NOT EXISTS grant_owner ON asset_grants(uuid,item);
CREATE TABLE IF NOT EXISTS player_equipment(uuid TEXT NOT NULL,slot TEXT NOT NULL CHECK(slot IN ('logo','cape')),item TEXT NOT NULL,PRIMARY KEY(uuid,slot));
CREATE TABLE IF NOT EXISTS platform_audit(id TEXT PRIMARY KEY,actor TEXT NOT NULL,action TEXT NOT NULL,target TEXT NOT NULL,detail TEXT NOT NULL,created INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS news_posts(id TEXT PRIMARY KEY,title TEXT NOT NULL,summary TEXT NOT NULL,body TEXT NOT NULL,image TEXT,url TEXT,status TEXT NOT NULL DEFAULT 'draft' CHECK(status IN ('draft','published')),published INTEGER,updated INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS news_publication ON news_posts(status,published);
CREATE TABLE IF NOT EXISTS redemption_codes(id TEXT PRIMARY KEY,code_hash TEXT NOT NULL UNIQUE,items TEXT NOT NULL,created INTEGER NOT NULL,expires INTEGER,disabled INTEGER NOT NULL DEFAULT 0,claimed_by TEXT,claimed_at INTEGER,claim_request TEXT UNIQUE);
