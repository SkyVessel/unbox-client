# Unbox 邮箱账号与私密混合联机

2026-10-08。工作区新增实现；生产 Cloudflare 当前仍是上一版 Microsoft 好友服务。不要把本地测试或编译成功描述为正式邮箱注册已上线。

## 用户明确的规则（最新修订）

- 开发阶段改为邮箱 + 密码，不发邮件、不配置 Resend。邮箱去首尾空白并转小写后唯一；**未验证邮箱归属**，不能保证每个自然人一个账号。
- 注册只填两个字段，自动生成游戏昵称。账号菜单的铅笔按钮可以改昵称；昵称允许重复，3–16 个英文字母/数字/下划线是 Minecraft 当前兼容范围。
- 邮箱、固定账号 ID、固定 UUID 与昵称分开。改名不重建账号，不改变好友关系或存档身份；已存在的邮箱重复注册返回冲突，不覆盖密码。
- Minecraft 所有权与 Unbox 身份分开；Unbox 不获得 Mojang 正版认证或公共正版服访问权。
- 完全免费，不购买域名、不升级 Workers、不配置发信服务。

## 注册、登录与改名

账号菜单 → Create an Unbox account → Email + Password → Create account → 自动选中账号。再次登录输入同一邮箱和密码，返回同一个 UUID。密码 12–128 字符，支持显示/隐藏；不写入前端持久状态、本地文件或日志。D1 保存随机 32 字节盐和 PBKDF2-SHA256 哈希，30 天会话只保存 token 哈希；客户端会话令牌存 macOS Keychain。退出当前设备会撤销该会话。

密码检查前限制每邮箱每 15 分钟 10 次、每 IP 每 15 分钟 20 次、全局每天 1,000 次。错误密码与不存在邮箱返回同样的登录错误。注册最多 2,000 个服务身份。邮箱未验证，暂不提供邮件找回密码，也不标记为 Verified email。数据库保留 `email_verified=0`，将来接验证时不需换 UUID。

**上线前的密码成本审查仍需完成**：开发实现明确使用 100,000 次 PBKDF2，不会自动降级。当前 Workers 的迭代上限及提高上限的 PR 仍需跟进（[Cloudflare 源码讨论](https://github.com/cloudflare/workerd/pull/7550)）；[OWASP 推荐 PBKDF2-SHA256 为至少 600,000 次](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。不能把本次实现称为已符合该推荐；上线前需重新选定满足强度与免费运行预算的方案，并实测生产 CPU 限制。失败时不启用付费方案或明文密码。

账号菜单 → 已保存账号旁的铅笔 → Player name → Save name。允许两人都叫 SameName。正在运行游戏时要求关闭游戏再修改本机昵称；下次启动从服务刷新昵称。好友同步以云端昵称为准，不会被旧设备上传的旧名字覆盖。改名撤销尚未使用的邀请，重新邀请即可。原版按名字执行的命令对重名仍可能有歧义；账号、准入与背包权限按 UUID。

## 邮件验证以后再接

本轮不需要 Resend、发信密钥或新域名。现有 workers.dev 地址可承载账号 API。以后选择邮件验证时才需要配置发信服务和可验证的发信身份；此前的验证码接口返回明确的停用状态，不存在免验证码通道。

## 私密房间身份认证 v2

Microsoft 与已注册 Unbox 账号都可以邀请；匿名 Local play 账号不能开此类邀请。房间内玩家权限和数据使用服务端认可的 UUID，而不是昵称。

- 保留 Minecraft 的 RSA/AES 连接加密；仅对 Unbox 私密 v2 房间改用邀请认证，不修改普通正版服务器认证。
- 每个被邀请人都有 5 分钟房间凭证；通过仅收发双方可读的邀请记录交付。
- Fabric login query 验证 HMAC-SHA256 双向证明，绑定本连接随机挑战、房间、受邀身份及实际服务器公钥摘要。错误凭证、未邀请身份和远程冒用房主 UUID不能加入。
- 握手按客户端声明的 UUID 查找邀请，再验证密钥证明；声明 UUID 本身不构成认证。同名来宾不会走原版房主名字快捷分支。
- 房主在 proof 通过后赋予真实受邀 UUID，避免原版 offline UUID 按昵称生成造成的数据串号。
- 移除好友会撤销后续准入并踢出；停止共享清除邀请密钥和允许列表。已建立的会话不因 5 分钟邀请到期立刻被踢出。
- 短期房间凭证出现在本机私有桥接文件中；native/game 写入使用仅当前用户读写权限。长期账号令牌仍不进入游戏目录，短期凭证也不通过账号/好友 UI 的 IPC 返回。
- LAN 探测优先，失败才启用免费 e4mc。第三方中继仍有可用性边界。
- 新客户端遇到旧生产服务时保留原 Microsoft-only 邀请流程；只有服务明确返回 protocol 2 才启用混合认证。

## 数据库和部署

生产 v1 已存在 `social-schema.sql` 的原始表。升级前读取 `PRAGMA table_info(invites)`，缺失 `join_secret` 时执行一次 `social-v2-migration.sql`。执行幂等的 `accounts-schema.sql`。上传 Worker 时同时上传 `social.mjs` 与 `accounts.mjs`，保持 `DB` 绑定、已有 Secret 与 hourly cron。

新建数据库应用更新后的 `social-schema.sql` 和 `accounts-schema.sql`，不要重复执行 ALTER migration。账号新表已于 2026-10-08 部署；若其他开发环境先应用过旧验证码表，必须先检查列并单独迁移，不能把 CREATE IF NOT EXISTS 当成列升级。新版 `/health` 返回 version 2、accountAuth password、emailVerification false。上传 `social.mjs` 和 `accounts.mjs`，无须邮件 Secret。

2026-10-08 已通过 Cloudflare 插件迁移现有 D1 并上传 social.mjs、accounts.mjs，线上服务为 v2。沿用 Workers Free 与原 DB 绑定，无邮件服务和付费升级。线上七组 API 检查通过：注册、邮箱去重、错误密码/登录恢复、同名改名、好友请求/接受、v2 邀请、注销撤销。两个合成账号及关联测试数据已清理。

## 实测与限制

- Node：大小写邮箱重复注册、并发唯一性、密码错误与请求限制、随机盐、会话退出/过期、同名改名、跨设备重登稳定 UUID、好友关系与权限隔离。
- Rust：旧 Keychain 兼容、会话存储/到期及公开账号快照不含 token。
- Playwright：两字段注册、密码显示切换、错误密码恢复、改名入口与游戏运行错误；徽章使用裁切留白的原 SVG 和居中布局。
- `scripts/test-private-world.mjs` 使用隔离 SQLite + HTTP Worker，实际注册并重新登录两个密码账号，再将两人都改成 SameName，运行两个真正的 Minecraft 26.1 客户端与一个集成服务器，走正常 Invite → Join 流程。
- 前一版本实际完成 LAN 及真实 e4mc 公网中继测试，原始结果位于 `.cache/network-tests/private-email-v2-final.json` 等；这是之前的测试邮件流程，不能作为新密码/重名流程的证明。
- 新密码/同名流程本轮结果另记 `.cache/network-tests/private-password-samename-final.json`：主机 6 项、来宾 3 项断言通过，两个同名账号经真实 e4mc 中继共同入服，验证来宾不会被识别为房主。
- 公网测试在同一台 Mac 清空测试邀请的 LAN 候选强制走中继，不等于两处独立网络测试。真实 Microsoft + Unbox 混合入服仍未测试；生产 v2 已部署并通过上述 API 检查；实际游戏联机测试使用隔离测试服务，尚未完成生产服务的跨设备入服。

最终构建：26 项 Node/Playwright 测试、21 项 Rust 测试通过，macOS debug app 已打包；包内 JAR 与本次游戏测试使用的 JAR 校验一致。尝试额外的本地 workerd/D1 仿真时运行时启动挂起，已停止测试进程；该项不算通过，也不能代替 Workers Free 生产 CPU 验证。
