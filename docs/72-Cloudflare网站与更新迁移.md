# Cloudflare 网站、下载与更新迁移

日期：2026-10-24。

## 已部署的入口

- 网站：https://printoria.printoria-studio.workers.dev/unbox/
- 留言板：https://printoria.printoria-studio.workers.dev/unbox/#feedback
- Windows 下载：https://printoria.printoria-studio.workers.dev/unbox/download/windows
- macOS 下载：https://printoria.printoria-studio.workers.dev/unbox/download/macos
- 更新清单：https://printoria.printoria-studio.workers.dev/unbox/releases/latest.json

使用现有 Printoria Worker、R2 与 D1，Unbox 对象统一放在 `unbox/` 前缀。原 Printoria 模块保留，非 Unbox 请求继续交给原模块。网站只提供 Unbox 内部导航，没有返回 Printoria 首页的按钮或链接。未开通新的收费计划。

部署前后 Printoria 首页响应逐字一致，`/api/builds` 继续返回 200。网页深浅主题、1440px 桌面和 390px 手机布局均检查；图片和字体正常，无横向溢出，减少动态效果模式有效。

## 0.2.21 更新

客户端去除 GitHub Releases API 预检查，直接读取 Cloudflare 清单；安装包只允许使用配置域名下的版本路径，继续使用原更新公钥和签名验证。游戏运行时更新互斥、下载进度、失败恢复和 macOS 权限提示保持不变。游戏模块沿用已验证的 0.2.20 六个原生 JAR。

构建来源提交：`e4b8483`。Windows 构建工作流：`40079827162`，已成功。后续提交是网站、测试和部署文档，不改变该版本的客户端程序。

| 平台 | 大小 | SHA-256 |
| --- | ---: | --- |
| macOS ARM64 | 58,941,197 字节 | `acdb7bd630762066ef74b858a44d4fc2f8d74e7e170fe5615ff5470e61d512a9` |
| Windows x64 | 35,848,449 字节 | `47a27a7f8482c91f2e0e0af153c16db424061dc30a641d582e01bbd28be5d02a` |

验证记录：

- 本地 Rust 测试 81 通过、2 忽略；Windows 原生 Rust 测试 82 通过、3 忽略。
- 最新前端与网站完整测试 80 项通过，其中 Microsoft 登录、留言权限与网站路由专项测试 10 项通过。
- 两平台各 46 个随包资源校验通过，Windows GUI 子系统检查通过。
- 从公开下载按钮完整下载两平台安装包；SHA-256 与本地产物一致，HEAD、字节范围下载、签名和篡改拒绝通过。
- macOS 通过真实 Tauri updater 将隔离的旧应用副本安装为 0.2.21，检查签名及程序哈希，并实际启动更新后的应用。最新版检查正确返回无更新。
- Windows 实际安装更新工作流 `40081342253` 通过：隔离安装 0.2.20，经 Cloudflare 下载及签名检查更新至 0.2.21，拒绝无效签名，并启动更新后的应用。前两次辅助测试的 ICO 和 Common Controls 清单问题已修复，正式安装包未改动。

本次不代表 Windows/Mac 新增游戏实玩验证，也不代表跨家庭联机重新实测。

## 公开留言板

入口位于下载区之前，导航和页脚均可到达。发帖前须使用 Microsoft 登录；公开昵称可选，默认 Anonymous。留言公开显示，支持按时间读取和加载更多。文案为英文，深浅主题沿用网站现有字体和圆角面板。

Cloudflare D1 新建 `unbox_feedback` 表，没有修改 Printoria 原有业务表。服务端校验昵称不超过 32 字符、留言 10–1,200 字符、请求体不超过 8 KiB；限制同一微软账号或网络地址每小时 3 条，拒绝跨来源提交并加入隐藏字段拦截。网络地址仅保存散列。每条新留言同时保存稳定微软账号标识及联系邮箱，不向公开 API 返回这些字段。留言以纯文本渲染，不能执行 HTML。

已从线上表单提交临时留言，确认成功提示、表单清空及刷新后仍能读取。随后仅按记录的测试 ID 删除该条留言；未留下模拟用户反馈。真实数据库分页、频率限制与输入边界有回归测试。工作室可通过已认证的 Cloudflare D1 按留言 ID 管理内容，未开放公共删除接口。

## Microsoft 登录与邮箱

用户已在现有微软应用添加 SPA 回调 `/unbox/auth/callback`。网站使用 Authorization Code + PKCE，申请 `openid profile email`，没有 Client Secret，也不启用 implicit token。后台验证微软令牌签名、issuer、audience、有效期及 nonce；身份绑定 issuer + sub，不使用可变化的邮箱作为身份键。

登录流程有效期 10 分钟，网站会话 7 天，凭证使用 Secure / HttpOnly / SameSite=Lax Cookie；数据库只存网站会话令牌的散列。退出登录即撤销网站会话。不保存微软访问令牌或刷新令牌，不发送邮件。

每条留言的 `contact_email` 由服务端从登录会话写入，忽略前端伪造的邮箱。表单明确告知邮箱仅用于工作室回复；登录者能看到自己的联系邮箱，公众只能读取昵称、正文、时间及留言 ID。微软未返回可用邮箱时给出明确错误，不将账户标记为已独立验证邮箱。

实际微软账号登录、授权、回跳及会话建立已通过。已使用线上表单发出标记测试留言，检查 D1 留下邮箱且公开响应未暴露邮箱，随后按测试 ID 删除。专项测试覆盖错误签名、issuer、audience、nonce、过期令牌、会话撤销、未登录发帖、跨来源请求、邮箱伪造和分页频率限制。

## GitHub 过渡安排

用户明确选择“先保留仓库公开，给现有用户一段升级过渡时间”。本轮不将仓库设为私有。

旧客户端的下载地址白名单只接受 GitHub，因此过渡 Release 的 `latest.json` 必须指向 GitHub 附件；Cloudflare 清单指向 Cloudflare 安装包。GitHub `v0.2.21` 已公开发布并成为 latest，12 项附件齐全。两边安装包与签名完全相同。安装 0.2.21 后，后续更新使用 Cloudflare。

过渡期结束后再将仓库私有化。未安装过渡版本的旧客户端届时需要从网站手动安装一次。

操作说明见 `deploy/unbox/README.md`。本地验证日志与原 Worker 备份在 `.cache/cloudflare/`；发行包及签名记录在 `.cache/release-0.2.21/`。短期上传 Worker 与本地上传令牌在完成上传后删除。
