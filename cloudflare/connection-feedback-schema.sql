CREATE TABLE IF NOT EXISTS connection_feedback (
 id TEXT PRIMARY KEY,
 person_id TEXT NOT NULL,
 account_uuid TEXT NOT NULL,
 account_kind TEXT NOT NULL,
 account_name TEXT NOT NULL,
 received_at INTEGER NOT NULL,
 report TEXT NOT NULL CHECK(length(report)<=48000)
);
CREATE INDEX IF NOT EXISTS connection_feedback_time ON connection_feedback(received_at);
CREATE INDEX IF NOT EXISTS connection_feedback_account_time ON connection_feedback(account_uuid,received_at);
