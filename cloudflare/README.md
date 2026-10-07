# Unbox metadata service

Not deployed. Wrangler `whoami` returned unauthenticated on 2026-10-06.

This service stores seven-day, capability-protected mod manifests, not game traffic, chat, saves, access tokens or arbitrary executable code. It resolves no remote download URLs. Publishers require a separately provisioned token; never distribute that token inside the launcher. Individual accounts and friend/room authorization must replace the development publisher credential before connecting the public launcher.

After the user authenticates Wrangler, verify the account and free Workers/D1 plan, create a D1 database, add its returned binding as `DB`, apply `schema.sql`, provision `PUBLISH_TOKEN` using Wrangler's secret prompt and deploy. No database IDs, tokens, account IDs or successful deployment are assumed here. Do not enable a paid plan to work around a limit.

Quota exhaustion must fail requests; it must not automatically enable paid services. The Cloudflare dashboard and actual plan remain authoritative. This does not implement Minecraft hosting, NAT traversal, a relay, online friends, or Microsoft authentication.
