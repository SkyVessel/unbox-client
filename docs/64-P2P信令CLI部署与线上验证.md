# P2P 信令 CLI 部署与线上验证

2026-10-22 UTC（本机时区 2026-10-21）。用户要求使用本地 CLI 重新登录和部署。

## 账号与部署

- 使用本地 Wrangler 4.151.3 的设备码 OAuth 登录。未使用 Cloudflare 插件，也未手工读取或输出令牌。
- `whoami` 确认账号 ID 为 `53ce1c2f645a0cd3fc4fc747535a4bb6`，与既有项目配置一致。
- Worker：`unbox-client-friends`；D1：`unbox-friends`；地址：https://unbox-client-friends.printoria-studio.workers.dev。
- 生效版本：`5cb2efc4-ba4d-456d-81ad-4c3865fac4a3`。绑定原数据库，保留每小时清理任务，不执行数据库结构迁移。
- 首次 CLI 部署提示预览地址默认开启，随即在配置中显式设置 `preview_urls:false` 并重新部署，保持原有关闭设置。
- 新接口 `POST /v1/invites/transport` 与 `voxlink-v1` 能力已在线。0.2.18 客户端可直接使用，无需新二进制版本。

## 验证

- 本地服务端 15 项测试和独立传输 TCP/UDP、丢包/乱序、错误密钥、超时、取消测试通过。
- 生产 HTTPS 信令 30 项通过：邀请、好友权限、角色权限、候选交换、重复投递、房间更新保留状态、新 attempt 替换、旧回复拒绝、私网地址拒绝、中继请求和撤销好友。
- 两份静默 Minecraft 26.1 Fabric 实例经**生产信令**完成 VoxLink RUDP 入服，房主 6 项、访客 5 项游戏检查通过；数据流使用同机回环 NAT 夹具。
- 故障注入使直连候选不可用后，自动转到**真实 e4mc 公网中继**并入服，房主 6 项、访客 5 项检查通过。
- 本轮实际游戏身份均为临时本地身份，验证稳定 UUID、同名隔离、错误密钥拒绝和禁止冒充房主；没有重做真实 Microsoft 账号认证。
- 3 个 API 测试身份及两组各 2 个游戏测试身份，均只按生成的 UUID 精确清理；D1 确认剩余为零。
- 初次 Python HTTP 预检被边缘层以 1010 拒绝（默认 Python User-Agent）；使用声明用途的 Unbox 验证客户端 User-Agent 后通过。游戏与 CLI 请求正常，没有修改 Cloudflare 防护设置。

## 边界

同机测试不能给出异地 NAT 穿透成功率、跨家庭游戏延迟或 Windows 游戏验证结论。正式异地测试应使用两个不同公网网络，记录实际 route（voxlink_udp / tcp / ipv6 / relay）、成功率、连接耗时和游戏延迟。

证据：`research/verification/2026-10-22-p2p-deployment/`。全部游戏隐藏运行，未抢占桌面焦点。此前 docs/62、63 的 Worker 待部署项已在本次解决。
