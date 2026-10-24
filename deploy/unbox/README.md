# Unbox on Cloudflare

Public page: https://printoria.printoria-studio.workers.dev/unbox/

- Worker: `printoria`, account `53ce1c2f645a0cd3fc4fc747535a4bb6`.
- Existing R2 binding: `FILES` → `printoria-files`.
- All Unbox objects live under `unbox/`; Printoria's existing module, assets and D1 binding are preserved.
- `/unbox/download/windows` and `/unbox/download/macos` resolve the current release from `unbox/releases/latest.json`.
- The client reads `https://printoria.printoria-studio.workers.dev/unbox/releases/latest.json` directly. Both package URLs must point to versioned paths beneath `/unbox/releases/`.
- The original Tauri updater public key is retained. Signing keys are never shipped to the Worker or client.

## Website deployment

`python3 scripts/build-website.py` builds an explicit public-file set in `.cache/website-public`. Upload only that directory to the R2 `unbox/` prefix. Never publish the repository root, research directories or private configuration.

`worker.mjs` wraps the existing Printoria module. Before updating it, download the current Worker source and settings through the Cloudflare connector and save a complete snapshot locally. The imported `printoria-original.mjs` belongs to the existing Printoria deployment and is intentionally not included in this repository. Bundle the authentication module first with `node_modules/.bin/esbuild deploy/unbox/auth.mjs --bundle --minify --format=esm --platform=browser --target=es2022 --outfile=.cache/cloudflare/auth-bundled.mjs`. Deploy that output as `auth.mjs`, together with the wrapper, handler, feedback module and original module, preserving existing bindings and assets (`keep_assets: true`). Run the Worker before static assets so `/unbox/` reaches the wrapper.

For the initial deployment, the original source and settings are in `.cache/cloudflare/`. The wrapper forwards every non-Unbox request to the original handler. To roll back, restore the saved original module and bindings with the asset set retained.

## Release order

1. Build both platforms; verify bundled resources and sign packages with the existing local updater key.
2. Upload packages and signatures to `unbox/releases/vVERSION/`. Never overwrite a released versioned package.
3. Verify public downloads, SHA-256, Tauri signatures, signature rejection and range responses.
4. Upload the immutable versioned manifest and checksums. Replace `unbox/releases/latest.json` only after both platform packages exist.
5. Run the updater installation harness against a disposable app. The Windows workflow's `verify_update` input runs the same transport/signature/install checks in an isolated Windows installation.

The initial upload uses a short-lived authenticated `unbox-release-upload` Worker with the existing R2 binding, because local Wrangler OAuth lacks direct R2 write scope. Its random credential stays only in local cache. Delete this Worker after uploads; it is not part of the public site's permanent API. Future uploads can use a newly created short-lived Worker or an authorized R2 client.

## GitHub transition

The user selected a public transition period on 2026-10-24. Keep the repository public until that period is ended by the user. Older clients require a separate GitHub `latest.json` with GitHub package URLs because their URL allowlist rejects Cloudflare downloads. The 0.2.21 bridge release contains the same signed packages as Cloudflare, with a GitHub-specific manifest. Once upgraded, the client no longer calls the GitHub Releases API for updates.

Do not make the repository private during the transition. After it becomes private, clients that never installed the bridge will need to install once from the website. Private CI can continue using GitHub's authenticated artifact access; public download/update traffic uses Cloudflare.

## Feedback board

`GET /unbox/api/feedback` returns 12 public posts and a cursor. `POST` requires a Microsoft session and accepts same-origin JSON with an optional name (32 characters), a message (10–1,200 characters), and an empty honeypot. Messages are rendered as plain text, never HTML. The raw request body is bounded at 8 KiB, and a single SQL statement enforces at most 3 posts per hour per Microsoft account or hashed network address. Network hashes are stored privately and never returned by the API. Anonymous names are public display labels. Every new post also retains the stable Microsoft account subject and an email snapshot in private `account_id` / `contact_email` columns; public GET and POST responses omit both.

Apply `feedback.sql` once to the existing `DB` binding before deploying the feedback module. It creates only the namespaced `unbox_feedback` table and its indexes. The initial database ID is `329e4f9f-e1c6-4da7-a982-210746284cb3`. Existing Printoria tables are untouched. Moderation is available through the authenticated Cloudflare D1 console or connector using an exact post ID; no public delete endpoint exists.

Before changing storage or removing posts, preserve genuine user feedback. Deployment tests use a clearly marked temporary message and remove only its recorded ID after checking persistence.

## Microsoft sign-in

The existing Microsoft application `0136aeab-bc7a-41b1-988e-08bf665fd53c` has a **Single-page application** redirect URI:
`https://printoria.printoria-studio.workers.dev/unbox/auth/callback`.
Use authorization code + PKCE with `openid profile email`. Leave implicit token grants off and front-channel logout empty. No client secret is used; the launcher's existing device-code configuration is preserved.

Apply `auth.sql` to create the namespaced login-flow and session tables. For databases created before Microsoft sign-in, migrate `unbox_feedback` once:

```sql
ALTER TABLE unbox_feedback ADD COLUMN account_id TEXT;
ALTER TABLE unbox_feedback ADD COLUMN contact_email TEXT;
CREATE INDEX IF NOT EXISTS unbox_feedback_account ON unbox_feedback(account_id,created_at);
```

The browser exchanges the short-lived code directly with Microsoft. The Worker verifies Microsoft's RS256 signature, issuer, audience, expiry and nonce before creating a random, hashed session. Flow cookies expire in 10 minutes; session cookies expire in 7 days and use Secure, HttpOnly and SameSite=Lax. Logout revokes the server session. Microsoft access/refresh tokens are not stored. The email claim is a contact address, not an identity key or an independently verified mailbox; accounts without a usable email receive an explicit error. Public display names remain user-selected, defaulting to Anonymous.

The signed-in player can see their own reply email. The form states that messages are public and email is private for studio replies. Studio access is through authenticated Cloudflare D1 only; no public email lookup or export exists. To retrieve a particular player's contact for a follow-up, query `SELECT contact_email FROM unbox_feedback WHERE id=?` with that exact post ID. Storing the address does not send mail.

Live verification on 2026-10-24: real Microsoft account login and callback succeeded, then a marked form submission retained the account and email in D1 while public responses omitted them. The temporary test post was removed by its exact ID. Test logs are in `.cache/cloudflare/website-auth-tests.log`.
