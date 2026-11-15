# unpvp 连接路径与 Fullbright 实验

日期：2026-11-14。分支：`codex/unpvp-ui`。承接 [103 大厅性能](103-unpvp-Windows-Hypixel大厅性能调查.md) 与 [104 对战/延迟](104-unpvp对战与延迟排查.md)。

## 结论与范围

用户明确反馈 Windows 正式对局存在真实命中/击退差异，并要求继续查是否有 Unbox 中转、检查 Claude 的排查建议、加入 Fullbright 后测试及查阅社区案例。本轮没有将异常归为体感，没有给 Windows 的根因做未经验证的归因。

1. 已检查的 Unbox 公共服务器连接代码未走好友 P2P/e4mc 或 Unbox 后端。原版 TCP 工厂的本地实际连接亦未出现代理 handler，TCP_NODELAY=true。
2. 1.8.9 KeepAlive 不经游戏主线程排队。本地 TCP 夹具主动暂停游戏线程 300 ms，回复仍完成；此结果不代表 Windows / Hypixel 线路已经验证。
3. Fullbright 已加入现有面板，默认关。夜间视觉开关有效，可恢复原亮度设置；两组本地 FPS 对照没有稳定提升。
4. 新增可关闭的本地连接诊断，供 Windows 复现时确认实际 TCP 对端、内部回复和实体包排队时间。不能用本机回环结果替代用户电脑数据。

## 连接链路核对

| 项目 | 核对结果 |
| --- | --- |
| 普通服务器入口 | `MultiplayerScreen.connectToSelected()` 的普通条目调用原版父类；`PrivateConnectingMixin` 只处理取消操作，没有替换目标地址 |
| 好友中继入口 | `SocialBridge.join()` 才执行 LAN → VoxLink → e4mc 回退。公共 Hypixel 连接不经过这个入口 |
| 私密握手 | `PrivateLogin.client()` 只处理已跟踪的邀请连接；没有邀请 grant 时走正常 Microsoft 登录；端口 claim 不修改 TCP 连接目标 |
| 启动器代理 | `network::builder()` 用于 HTTP 下载等请求。`unpvp.rs` 生成 Java 启动命令未注入 SOCKS、HTTP 代理或隧道参数；没有将游戏下载代理转换成游戏 Netty 代理 |
| TCP | 原版 `NetworkManager$5.initChannel` 设置 TCP_NODELAY=true。本地真实 NioSocketChannel 也读取到 true |
| Forge | `NetworkDispatcher` 的正常已连接路径转发普通包；`FMLProxyPacket` 是模组自定义消息封装，不代表互联网代理。未发现该检查路径同步 HTTP/文件写入来处理普通 KeepAlive |
| IPv4 | 原版 `net.minecraft.client.main.Main.main` 开头把 `java.net.preferIPv4Stack` 设置为 true，本地运行也为 true。这是原版行为；Lunar 当前实现未取得，不能声称双方必然解析到同一 IP |
| 外部路径 | 操作系统网络过滤、第三方注入、DNS 结果、不同接入节点及 Hypixel 防护/内部路由仍需 Windows 对端与线路记录；“未走 Unbox 中继”不代表整个互联网没有中间节点 |

字节码检查记录在 `.cache/unpvp-combat/{net-handler-bytecode,forge-dispatcher-bytecode}.txt`。主代码：`src-tauri/src/{unpvp,network,process}.rs`、`MultiplayerScreen.java`、`SocialBridge.java`、`PrivateLogin.java`。

## 对截图中建议的核查

- **Java/GC 值得查，但目前没有证据把它列为最可能根因。** Windows 固定使用 Temurin HotSpot 8u512 x64；默认应用内存选项 4096 MiB，实际最大堆跟随用户配置且旧版限制在 512–8192 MiB，最小堆 256 MiB。不是完全未设置堆大小。未显式指定收集器；新诊断记录实际收集器和堆上限。本机测试是 Zulu ARM64 8u512、PS Scavenge/PS MarkSweep，不能代替 Windows 记录。
- **KeepAlive 不需要等主线程。** 检查实际 1.8.9 `NetHandlerPlayClient.handleKeepAlive` 只构造回复并调用发送，没有 `PacketThreadUtil.checkThreadAndEnqueue`。GC 停顿、网络线程拥堵仍可延迟它；普通实体/击退消息确实有主线程处理阶段，已分别加入计时。
- **TCP_NODELAY 已有。** [TcpNoDelayMod 作者 README](https://github.com/prplz/TcpNoDelayMod) 明确表示 1.8.1 及以后不需要该模组。本轮没有重复叠加或改写 Nagle 设置。
- **Java 8 的日志参数要用对应版本。** 截图的 `-Xlog:gc` 是较新 JVM 语法。Java 8 可使用 `-Xloggc:<file>`、`-XX:+PrintGCDetails`、`-XX:+PrintGCDateStamps` 等，参考 [Oracle Java 8 文档](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html)。本轮没有盲目更换 GC 或堆参数；诊断先记录 MXBean 聚合数据，若指向 GC，再采详细暂停日志。
- **抓包有边界。** 正版登录后的 Minecraft 游戏流量加密；Wireshark 可看 TCP 重传和 ACK 等，但普通抓包不能直接读出加密后的 KeepAlive 消息内容。内部计时与 TCP 记录应结合，不能声称随手抓包就能读出全部游戏事件。
- **“超过 1–2 ms 就必定客户端有问题”不成立为通用阈值。** 应区分内部回复处理、主线程排队、GC、回环/公网往返及负载条件。本地完整 TCP 往返与内部处理计时本身也不是同一个量。

## Fullbright 实现与实测

入口：Right Shift → Mods → **Fullbright**；沿用现有卡片、快捷键覆盖规则和矢量太阳图标。目录现为 30 项；新增为 Unbox 自制实现，没有安装 OneConfig、Essential 或未知第三方二进制。

`FullbrightMixin` 将客户端 16×16 lightmap 设为白色，启用时上传一次，之后跳过重复重建。关闭时强制原版重建，恢复当时世界照明。没有改 gamma 存档、世界真实光照、怪物生成规则、区块光照传播或服务器包。光影包可能使用自己的照明，因此不承诺任意光影均表现一致。

公开参考：[Lunar Lighting 官方文档](https://lunarclient.dev/apollo/developers/mods/lighting) 确实说配置/关闭光照效果可能增加 FPS，列出 Fullbright 和 Brightness Boost，Fullbright 默认 true。它没有给出单项 FPS 增幅或内部算法。[Patcher README](https://github.com/Sk1erLLC/Patcher/blob/master/README.md) 也列出 Fullbright；本轮没有移植它对 world/chunk 光照计算的抑制，只实现客户端 lightmap，不能声称与其全部优化完全相同。

测试目录：`.cache/unpvp-tests/fullbright-lobby-01/`。Apple Silicon、Java 8、完整性能包、1600×900 请求窗口及 Retina 输出、视距 8、VSync 关、不限帧；固定夜间平地场景，64 个测试玩家及 48 条悬浮文字。每段先等待 8 秒，再采约 8 秒；按关→开→关→开执行，没有并行运行另一个游戏或构建任务。

| 阶段 | 平均 FPS | P50 帧时间 ms | P95 ms | P99 ms |
| --- | ---: | ---: | ---: | ---: |
| 关闭 A | 208.353 | 4.185 | 8.172 | 10.312 |
| 开启 A | 174.810 | 4.347 | 10.269 | 24.569 |
| 关闭 B | 201.817 | 4.280 | 7.240 | 9.440 |
| 开启 B | 201.668 | 4.315 | 7.214 | 9.130 |

第一组存在明显负向波动，第二组基本相同。短测不足以确定负向波动的原因，但足以说明**本轮没有测到稳定的 FPS 增益，更没有两倍提升**。保留所有数据，不仅挑选瞬间 HUD 数字。空场景平均 760.692 FPS 只用于测试预热背景，不与多人场景直接算作 Fullbright 增益。

已查看 `lobby-probe/screenshots/fullbright-{off,on}-a.png`：夜间地面和玩家提亮有效，UI、人数及悬浮文字仍保留。关闭后的 lightmap 数据及 gamma 恢复检查通过。

## 连接诊断怎么用

新构建中：Right Shift → Mods → **Performance → Connection diagnostics: On**。在大厅与正式对局各停留约一分钟，然后关闭。每 10 秒写入一次，最多保留 60 个采样窗口，文件在当前 Profile 的 `unpvp-ui/logs/unbox-connection-diagnostics.json`。

本地报告包含：目标地址/实际 TCP 对端、通道与管线名称、TCP_NODELAY、Java/堆/GC 信息、服务器下发的旧 Ping 字段、独立响应估计、KeepAlive 接收后到调用 sendPacket 的时间、部分实体消息解码后到主线程开始处理的时间、帧时间和 GC 聚合耗时。没有账号令牌、聊天内容、邀请凭证或数据包正文；不自动上传。对端地址是诊断需要的本地记录。

边界：KeepAlive 计时不包含 sendPacket 之后的编码/写出、系统/公网等待；实体计时从解码之后开始；GC 聚合耗时不是精确 stop-the-world 时间。10 秒窗口用于定位方向，不能单凭相关性宣布因果。报告不会测出客户端收到消息前的所有服务端和线路延迟。

默认关闭；启用时网络回调只更新有上限的内存样本。排序与 JSON 汇总在游戏线程，文件写入在后台；不在网络回调做磁盘 I/O。关闭时导出剩余窗口。帧率正式对比应关闭诊断，避免采样本身影响成绩。

## 已找到的社区先例与处理方式

- [Hypixel：Lunar 250 ms，Forge 320–400 ms](https://hypixel.net/threads/getting-high-ping-unstable-ping-on-the-hypixel-server.5127190/)：2022 年楼主报告同类差距，试过有线、清 DNS、重启路由器。回帖提出 Java、图形负载、ISP/Cloudflare；楼主将其归因于防护线路，但没有公布逐客户端 TCP 对端或性能剖析，因此不能把帖子里的归因当作本项目已验证答案。
- [Forge 卡顿，原版/Lunar 正常的用户报告](https://www.reddit.com/r/Minecraft/comments/m58uhd/forge_is_causing_lag_spikes/)：说明类似反馈存在；本次取得页面没有足以复现和证实的通用修复结论。
- [Hypixel：特定粒子场景 Forge 延迟、Lunar 正常](https://hypixel.net/threads/fire-veil-ping-lag.5485415/)：涉及特定 SkyBlock 场景，作者尝试逐个移除模组仍有问题；不等于我们的 PvP 场景同因。
- [Hypixel 官方排查](https://support.hypixel.net/hc/en-us/articles/360019671939-Solving-Lag-and-Connection-Issues-on-Hypixel)：建议移除模组做对照、检查 DNS/后台流量，持续异常时做 MTR 并提交支持。原版/Forge/Unbox 对照和实际线路记录比直接套入网友 JVM 参数更能缩小范围。
- [Lunar 官方 FPS 指南](https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client)：1.8 Turbo Nametags、HUD Caching、实体/方块实体距离及其他渲染设置是明确差异。没有把 Fullbright 描述为大厅两倍差距的通用解决方案。

## 验证与交付

`ConnectionProbe` 使用真实原版 TCP 工厂与实际 `NetHandlerPlayClient`，连接本机临时 TCP 夹具，不登录公共服务器。测试主动阻塞游戏主线程 300 ms，服务端仍收到正确 KeepAlive 回复；`connection-bright-03` 完整回环往返 3.1915 ms，内部接收→发送入口 0.025875 ms。实体消息人工等待约 20 ms 后交给实际处理器，诊断记录 23.457208 ms，验证计时路径。不是 Windows、公网、加密登录或 Hypixel 对战成绩。

`connection-bright-03` 的 23 项专项检查与 97 项既有 PvP 回归通过，退出码 0；`fullbright-lobby-01` 五阶段执行完成，退出码 0。最终构建/回归与应用打包结果在 `.cache/unpvp-combat/`。本轮未发布 GitHub Release、未部署后端、未构建或验证新的 Windows 安装包。

后续应先收集 Windows 异常时的报告，比较双方实际远端 IP、Unbox 内部 KeepAlive/实体队列耗时及 GC/帧时间，再针对已出现的瓶颈修改。未取得这些数据前，不改服务器击退强度、攻击距离、KeepAlive 频率或发送额外疾跑包。

最终验证：`connection-bright-final` 再次通过 23 项专项及 97 项 PvP 回归，正常退出；最新核心/PvP 与另两个既有 JAR 已核对和 macOS 调试应用内文件完全一致，见 `.cache/unpvp-combat/bundle-audit.json`。应用已更新到 `src-tauri/target/debug/bundle/macos/Unbox Client.app`（115.29 MiB）。重新启动该应用后启动 1.8.9 才会安装新组件；正在运行的旧游戏不会热更新。Windows 安装包仍未更新。
