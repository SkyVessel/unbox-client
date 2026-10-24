CREATE TABLE IF NOT EXISTS unbox_feedback (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  message TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  actor_hash TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS unbox_feedback_created ON unbox_feedback(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS unbox_feedback_actor ON unbox_feedback(actor_hash, created_at);
