# VoxLink 联机与原版多人列表

日期：2026-10-21。分支：`codex/voxlink-multiplayer`，独立 worktree，基于 `9c6508b1b31eb62134c9b869c45794a66e705f62`。本轮为本地实现与验证，未合并主分支、未发布更新、未部署线上 Worker。

## 网络路径

保留原有“打开世界 → 邀请好友 → 接受 → 自动准备共享环境 → 加入”。没有新增房间号、网络模式选择或手工端口配置。

1. 先使用原来的带令牌 LAN 探测；同网段验证成功仍直接连接游戏端口。
2. LAN 不通时，通过现有私密邀请交换 VoxLink 候选地址。每次加入使用独立 attempt ID，双方用邀请密钥派生传输认证密钥。
3. 尝试公开 IPv6 TCP、临时 UPnP TCP 映射、双向 TCP 连接与 TCP 同时打开，以及两个 UDP 源端口的双向打洞。STUN 使用 Cloudflare 与 Google；映射变化时尝试有限端口预测，范围最多 ±16。
4. UDP 成功后采用 VoxLink ReliableUdpTransport 提供可靠字节流；TCP 成功后直接转发字节流。认证通过并确认房主游戏端口可连接，才向访客游戏暴露本机桥接端口。原有 Minecraft 加密登录、稳定 UUID 权限与共享环境协议继续运行在该字节流上。
5. 直连协商预算为 25 秒；没有成功路径时自动请求原有 e4mc 公网中继。取消、超时和关闭世界会停止无用探测，清理监听与映射。已加入玩家不会仅因邀请到期被中断。

UPnP、TCP 同时打开与端口预测均依赖网络条件，不承诺穿透所有 NAT。TCP 同时打开采用约定端口，适合允许该方式的 NAT；非端口保持映射仍可能失败并转中继。

复用 [VoxLink](https://github.com/AUGUHDAR/VoxLink/tree/b80595db5485f455db31c5454d897a960e664393) 的 UDP/TCP 打洞、可靠 UDP、STUN、UPnP 核心，固定提交 `b80595db5485f455db31c5454d897a960e664393`。源码位于 `ports/voxlink`，保留 LGPL-3.0 许可、原文件哈希及修改说明；构建的每个 Unbox JAR 包含对应源码 ZIP 和许可文件。没有接入 VoxLink 自身的账号、大厅、模组同步、日志上传、Terracotta 二进制下载或 TURN 账号体系；中继继续使用现有 e4mc。

## 保留的功能与界面改动

- `EnvironmentSync.java`、`EnvironmentManifest.java`、`src-tauri/src/sharing.rs` 未修改。独立好友 Profile、JAR/ZIP 哈希校验、差异传输、材质包顺序、光影选择、确认重启及再次加入沿用原实现。
- 启动器前端 `src/` 无改动。Rust 增加本地游戏候选信令的转发，并在提供给前端的公开状态中去除传输候选。
- 游戏 Multiplayer 使用原版 `JoinMultiplayerScreen` 和服务器列表。受邀世界以原生列表项显示好友头像、世界名和房主，支持原版选择、双击、键盘加入、滚动、Refresh。
- 删除原先独立的好友世界卡片页，以及右 Shift 好友栏中的重复邀请条目；保留好友管理、邀请、停止共享、重试中继和 Worlds 入口。
- 邀请弹窗及连接进度、差异确认、失败提示文案保持原实现。弹窗时长选项移到游戏 Friends 页面。Connection 诊断按实际路径记录 `lan`、`voxlink_udp`、`voxlink_tcp`、`voxlink_tcp_punch`、`voxlink_ipv6` 或 `relay`，修正原先“公网 P2P 未启用”的诊断说明。
- 原有本地账号／微软多人权限边界保留。私密邀请没有成为开放服务器，也不写入 `servers.dat`。

## 信令服务与交付边界

`cloudflare/transport.mjs` 增加邀请内的候选校验与交换，入口为 `POST /v1/invites/transport`。房主与访客只能写自己的角色，要求有效邀请和双方好友关系；旧 attempt 的房主回复被拒绝。更新房间或中继地址时保留该邀请的协商状态。候选仅允许公网 IP 字面量，拒绝内网、回环、链路本地及 IPv4 映射回环地址。没有数据库结构迁移。

正式启用需要一起交付新游戏模块、新启动器信令桥接和新 Worker。旧 Worker 会丢弃新的网络能力字段，客户端继续使用旧 LAN／中继路径。本轮未更新线上服务，不能将本地 Worker 测试描述为线上已启用。

## 验证结果

测试平台为 macOS ARM64。所有游戏实例使用独立测试世界副本、静音和隐藏窗口，测试代理在 GLFW / SDL 创建窗口前禁用显示与焦点操作；未调用桌面激活、切前台或自动点击。26.3 使用 SDL，26.1 使用 GLFW，两条隐藏路径均已运行验证。

| 检查 | 结果 |
| --- | --- |
| 26.1 / 26.1.2 / 26.3 × Fabric / NeoForge | 六组原生 Java 模块编译通过；每个 JAR 检查传输类、UI mixin、许可及对应源码 |
| Rust | 70 项通过，包含共享环境、缓存、Profile 和前端状态去除私密字段 |
| 服务端与启动器邀请交互 | 15 项服务端测试 + 2 项无界面浏览器回归通过；包含角色权限、重复信令、过期、撤销好友、旧版本回退、地址校验 |
| 独立传输测试 | 13 项断言通过；UDP / TCP 各传输 2 MiB；512 KiB 数据在丢包、延迟乱序、重复包条件下保持一致；错误密钥、超时与取消正确处理 |
| 静默测试代理 | GLFW / SDL 测试替身确认隐藏创建，并拦截显示、聚焦；实际游戏日志确认对应类已插桩 |
| 游戏内多人界面 | Fabric 26.1、Fabric 26.3、NeoForge 26.3，各 31 项检查通过；验证头像、原生条目、Refresh、键盘加入、重复列表移除、原确认流程和大 GUI 比例 |
| 双实例 VoxLink 游戏连接 | 两个测试身份通过 RUDP 桥接入服，错误登录密钥和冒充房主被拒绝，同名玩家身份隔离，世界数据与死亡标记传输通过 |
| Fabric 26.1 共享同步 | 经 VoxLink 下载 2,101,824 字节，原生准备并重启，模组物品使用、服务端奖励、材质包与光影启用成功；重进下载 0 字节、无需重启 |
| NeoForge 26.1 共享同步 | 经 VoxLink 下载 2,101,943 字节，完成相同流程；重进下载 0 字节、无需重启 |
| 自动中继回退 | 故意让双方候选不可用，诊断出现 `direct_exhausted` 后经真实 e4mc 公网中继加入并完成游戏检查；无手工选择路由 |

双实例直连使用明确启用的回环 NAT 夹具，验证真实 VoxLink 字节流、邀请交换和游戏端到端整合；不代表两个家庭网络的 NAT 穿透成功率。中继测试使用生产 STUN 探测（获得映射）和真实 e4mc 中继，但两个游戏仍在同一台 Mac。尚未完成异地家庭网络、Windows 游戏运行，以及真实公网 IPv6／UPnP／TCP 同时打开成功场景的测试。没有从这些测试推导 FPS、异地延迟或穿透率承诺。

## 复现与本地证据

独立传输、服务端与静默代理：`sh scripts/test-voxlink.sh`。依赖已准备的 Java 25、游戏库及 ASM。

游戏测试先运行 `python3 scripts/build-silent-agent.py`，然后始终设置 `UNBOX_SILENT_TEST=1`。`scripts/test-private-world.mjs WORLD_DIRECTORY RUN_NAME --local-host --voxlink --sync-mods` 测回环夹具上的共享同步；`--local-host --relay` 测故障注入后的自动公网中继。NeoForge 追加环境变量 `UNBOX_TEST_LOADER=neoforge`。所有路径都创建世界副本。

本机证据保存在 `.cache/`，不提交包含临时邀请密钥的完整游戏目录：

- `.cache/voxlink-final-tests.log`、`.cache/voxlink-rust-tests.log`、`.cache/voxlink-node-tests.log`。
- `.cache/voxlink-build-{fabric,neoforge}-{26.1,26.1.2,26.3}.log`。
- `.cache/network-tests/voxlink-game-udp.json`、`voxlink-fallback-relay.json`、`voxlink-sync-udp.json`、`voxlink-sync-neo.json`。
- `.cache/benchmarks/voxlink-ui-verified261`、`voxlink-ui-fabric263`、`voxlink-ui-neo263` 下的 `logs/feature-smoke.json` 和 `screenshots/`。
