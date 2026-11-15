# unpvp 性能、连接 Feedback 与 0.2.24 交接

日期：2026-11-15。发布基线为 v0.2.23；保留原有稳定版和 Beta Profiles。仅更新 1.8.9 的核心/PvP 模块和启动器反馈上传路径，不替换其他游戏引擎。

## 本轮功能

- 名牌：按 tick 复用好友/徽章状态，按 UUID、文字、Logo 变体缓存格式化结果；最多 512 项。保留名字、三种 Logo、昵称变化、连接切换和过期行为。
- HUD：复用整数和颜色解析结果；配置值变化即失效，减少逐帧字符串分配和解析。
- 一并发布此前已验证的 Toggle Sprint、OptiFine Shader Hit Color 修复、Fullbright 和延迟 HUD 调整。Fullbright 默认关闭，不宣称它能大幅提高 FPS。
- Feedback：主菜单及 Right Shift 快捷菜单入口，Mods → Performance 也有入口；页面使用现有浅/深色主题。

## Feedback 使用与边界

每次启动默认关闭。打开页面不会开始记录，点击 **Start recording** 才开始。保持启动器运行；正常退出 Minecraft 时自动发送一次。**Stop & discard** 停止并丢弃本次记录。

记录内容为账户 UUID、服务器地址、实际 TCP 对端、IPv4/IPv6、连接类型、代理处理器是否存在、TCP_NODELAY、KeepAlive 本地处理时间、包到游戏线程的等待、帧时间、GC 通知，以及攻击/受伤/速度包数量。没有视频、聊天、密码、令牌或游戏包正文。连接事件只保留预定义阶段，不上传任意日志文字。

记录在内存中有界保存，后台序列化；最多 40 个测量窗口、40 个连接事件、48,000 字节。关闭时记录器不工作。退出时由启动器通过独立 HTTPS 请求上传，游戏连接不会因此转发到 Unbox。

- 写入中：`logs/unbox-feedback-recording.json`。
- 正常退出且仍同意上传：改名为 `logs/unbox-feedback.json`。
- 上传结果：`logs/unbox-feedback-status.json`；成功删除待发报告，失败保留本机文件。
- 下次启动隔离旧报告到 `unbox-feedback-previous.json`，不自动发送旧会话。
- 强制杀进程/断电可能没有最终报告；启动器提前关闭也无法发送。本版本没有后台常驻上传器或自动重试。

启动器校验本次随机 session、账户 UUID、consent、版本、窗口数量和大小。上传使用启动游戏时捕获的账户身份，切换账户不会误归属。

## 服务端

沿用现有好友 Worker，新增私有 POST `/v1/connection-feedback`，与网站公开反馈板分离。没有公开读取接口。使用已有账户认证；服务端绑定 person_id、账户 UUID、账户种类和名称，不信任报告自行填写的归属。

`cloudflare/connection-feedback.mjs` 严格筛选字段；同会话重复上传去重，每账户 UUID 每日最多 4 份、全局最多 2,000 行、单份最多 48 KB。保存 7 天，定时任务清理到期数据，物理删除可能晚于到期时间一个调度周期。可用 Cloudflare D1 管理工具按账户和时间查询，不能把表做成公开 API。

建表脚本：`cloudflare/connection-feedback-schema.sql`。线上部署保留原 Worker 绑定、密钥和最新版支持列表，仅加入该路由、模块与清理任务。

## 网络调查结论

源码及本地实测中，普通服务器走 Minecraft 原有直连 TCP；私密好友邀请才使用原联机路径。TCP_NODELAY 已开启，无需再安装相同用途模组。主线程暂停测试中 KeepAlive 仍可回复。未发现新增代码拦截攻击、受伤或击退速度包。

这不能确认用户 Windows 到 Hypixel 的实际线路，也未定位“同一对手没有击退”的根因。需要新 Feedback 比较实际对端、主线程等待、GC 与包计数。包计数不包含目标和负载，不能单凭数量判断一次命中是否被服务器认可。

延迟 HUD 已改用低频统计请求/响应估计，避免将服务器填入 TAB 的 1 ms 直接作为真实网络延迟；它仍包含服务器处理时间，不是 TCP RTT。KeepAlive 收包至回包的本地耗时同样不等于往返延迟。

没有强制 IPv6，也没有升级 1.8.9 的 Java 主版本。Java 8 原路径明确偏好 IPv4；实际对端与偏好值会记录，不能依据论坛猜测直接更换线路。GC 通知记录的是 GC 事件，不覆盖全部 JVM 安全点暂停。

## 性能验证

本机 Mac 固定场景：64 个玩家、48 个浮空文字；相同画质、名字、Logo 和性能模组，预热 8 秒，采样 8 秒。

| 场景 | 优化前 FPS（两次） | 优化后 FPS | 新版 p95 / p99 帧耗时 |
|---|---:|---:|---:|
| 普通人群，HUD 开 | 179.831 / 182.393 | 202.215 | 7.330 / 9.370 ms |
| 密集 Logo，HUD 开 | 159.692 / 161.948 | 185.144 | 8.563 / 9.915 ms |

此为本地合成场景，不是 Windows Hypixel 大厅实测，也不承诺消除与 Lunar 的两倍差距。

另试 Java 8 G1、50 ms 暂停目标：普通人群 202.275、密集 Logo 181.950 FPS，没有明确收益，未改变发布版默认 GC。公开依据：Oracle Java 8 G1 调优文档说明暂停目标与吞吐量存在取舍：https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/g1_gc.html 。

## 已执行验证

- Node：86 通过；Rust：100 通过、6 个原有忽略项。
- 服务端专项及社交测试：17 通过。
- 真实 1.8.9：97 项 PvP 检查、战斗/连接/Feedback 检查通过；18 项徽章检查通过。
- 最终游戏退出生成 3 窗口、3,345 字节报告，包含账户 UUID 与事件；正常退出改名流程通过。
- 默认关闭、主动开启、停止丢弃、排队写入不能复活报告、大小限制均已检查。
- 浅色和深色页面截图均检查，无裁切或文字对比问题。
- 线上临时测试身份：认证上传、错账户拒绝、匿名拒绝、重复去重、私有归属通过；只删除本次创建的测试数据。
- 稳定引擎和其他 Beta 引擎字节保持 v0.2.23；1.8.9 network/patcher 未更换。

## 代码位置与后续排查

Java：`Feedback.java`、`FeedbackScreen.java`、`ConnectionDiagnostics.java`、`NameBadges.java`、`UiState.java`；正常退出接点 `FeedbackShutdownMixin.java`。

启动器：`src-tauri/src/feedback.rs`，`unpvp.rs` 传 session 并在游戏退出上传，`social.rs` 复用账户认证。

后续请用户开启 Feedback，进入同一 Hypixel 大厅和正式对局，复现后正常退出。先核对账户和版本，再比较 route/remoteAddress、包处理等待、帧时间和 GC。不要从 ping 数字单独推断击退问题，不能把本地测试写成 Windows 实玩验证。

发布打包和渠道验证记录在本文件后续补充；构建通过不等于实际服务器对战通过。
