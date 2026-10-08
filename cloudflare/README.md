> 2026-10-08 最新修订：开发阶段使用邮箱 + 密码，无邮件验证；昵称可改且允许重名，邮箱唯一。Resend 暂停接入。下面验证码相关描述为历史记录，最新状态见 docs/13。

# Unbox cloud services


Deployed v2 adds email/password Unbox accounts, editable non-unique nicknames and encrypted mixed-identity invitations. Email verification is deferred by user request; Resend is not required. Production was upgraded through the Cloudflare plugin on 2026-10-08. Seven groups of live password-account, rename, friendship, v2 invitation and session-revocation checks passed; temporary accounts were removed. See `docs/13-Unbox邮箱账号与混合联机.md`.
## Friends and invitations (deployed)

`social.mjs` is deployed as `unbox-client-friends` on Workers Free with the `unbox-friends` D1 database. Configuration and its real binding are in `social-wrangler.jsonc`; the schema is `social-schema.sql`. The launcher uses `https://unbox-client-friends.printoria-studio.workers.dev`. `/health` returns `configured: true`.

Deployment was completed using the Cloudflare plugin on 2026-10-08, without browser access or paid upgrades. Free-plan default CPU limits apply; do not add custom CPU limits (API error 100328). Hourly cleanup is enabled, preview URLs and persistent Worker logs are disabled. Existing unrelated resources were not modified.

Live API checks passed 36 assertions, including friendship lifecycle and invitation authorization using temporary synthetic identities. All fixtures were removed. This does not establish real Microsoft login or cross-device gameplay success. Game traffic uses LAN or the free e4mc public relay, never Cloudflare. See [verification notes](../docs/12-文件导入与好友联机.md).

## Shared manifests (undeployed draft)

This service stores seven-day, capability-protected mod manifests, not game traffic, chat, saves, access tokens or arbitrary executable code. It resolves no remote download URLs. Publishers require a separately provisioned token; never distribute that token inside the launcher. Individual accounts and friend/room authorization must replace the development publisher credential before connecting the public launcher.

After the user authenticates Wrangler, verify the account and free Workers/D1 plan, create a D1 database, add its returned binding as `DB`, apply `schema.sql`, provision `PUBLISH_TOKEN` using Wrangler's secret prompt and deploy. No database IDs, tokens, account IDs or successful deployment are assumed here. Do not enable a paid plan to work around a limit.

Quota exhaustion must fail requests; it must not automatically enable paid services. The Cloudflare dashboard and actual plan remain authoritative. This does not implement Minecraft hosting, NAT traversal, a relay, online friends, or Microsoft authentication.
