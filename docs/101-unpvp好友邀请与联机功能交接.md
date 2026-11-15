# unpvp 1.8.9 好友与联机功能交接

日期：2026-11-14。分支：`codex/unpvp-ui`。本轮为本地调试交付，没有发布 GitHub Release，也没有部署新的好友服务。

## 范围与依据

对照现代版 `SocialBridge`、`SocialLogin`、`VoxLinkNetwork/Transport`、`SocialNotifications`、`FriendsScreen/Overlay`、`MultiplayerHubScreen`、`JoinDiagnostics`、`LocalSkin/SkinLibraryScreen` 实现已有能力。没有引入语音、生存/建造功能、OneConfig 或 expvp 代码。既有矢量 UI、世界画质、Apple Silicon 运行时和 PvP 模组保持原有路径。

用户操作：保持启动器运行并选好账号；在游戏标题左上角或游戏内 Right Shift 菜单进入好友区域，使用好友码添加好友。进入单人世界后邀请在线好友；收到邀请后可在通知、Worlds 区域或 Multiplayer 列表加入。

## 已接入的功能

| 现代版已有能力 | 1.8.9 接入情况 |
| --- | --- |
| 好友列表、在线状态、头像 | 读取原启动器好友服务，断开后显示离线缓存 |
| 好友码、添加、接受、拒绝、取消请求、删除 | 使用原有命令和确认回执；等待中的命令不会被后续点击覆盖 |
| 世界邀请、通知、隐藏/取消邀请 | 快捷菜单和原版服务器列表均可进入邀请世界；通知不抢游戏鼠标 |
| 加入前离开当前世界确认 | 保留确认流程，先退出当前世界再连接 |
| 局域网 / 点对点 / 中继 | 局域网探测 → VoxLink 直连 → e4mc 回退 |
| 取消、超时、失败重试、停止共享 | 清理连接、候选交换、UDP 探测和中继；停止共享移除访客，保留房主 |
| 连接状态和报告 | Connection 页面；本地报告 `logs/unbox-network.json`，不写地址、邀请凭证或世界名 |
| 跨版本邀请 | 确认后交给原启动器选择对应好友 Profile 并恢复邀请；保留自己的按键/HUD |
| 账号与皮肤 | 沿用启动器账号；接入已保存皮肤选择和 Classic / Slim；本地皮肤按账号、SHA-256 和图片尺寸验证 |
| 头顶名字与 Logo | 名字前显示基础开盒标志、Creator Crown 或 Fluttering Cash；Appearance → Logos 切换账号已拥有的变体 |
| 已有兑换入口 | 接入原有兑换命令，沿用 Microsoft 账号限制；没有用真实兑换码测试 |
| 公共服务器身份限制 | 本地账号只能进入受邀私密世界；Microsoft 账号保留普通服务器入口 |

本轮重点为好友和联机，不能据此把现代版所有独立功能都标为 1.8.9 已移植。现代版整合包玩法 JAR/config/脚本同步、资产商城等不属于本轮新增内容；1.8.9 继续使用已经锁定的 PvP 包。原有 OptiFine 披风功能未替换。

## 关键实现

### 启动器与游戏目录

1.8.9 真正的游戏目录为 `profiles/<id>/unpvp-ui`，此前好友服务写入 Profile 外层，游戏读不到。新增 `game_dir` 统一解析，修正好友通信、皮肤库和邀请恢复文件的位置。

好友独立 Profile 从同为 1.8.9 的个人 Profile 继承 `options.txt`、`optionsof.txt`、`config/unpvp-ui.properties`，只在首次创建时复制。已有好友 Profile 的个人修改不会被覆盖。现代版和旧版的选项文件不混拷。

### 连接与私密身份

1.8.9 没有现代版的登录查询包，新增 `PrivateLogin`，保持固定 UUID 和邀请绑定。使用随机邀请密钥、每次连接的新 nonce、房间/用户/服务器公钥绑定的双向 HMAC，随后沿用 Minecraft RSA/AES 登录加密。未邀请、过期、移除好友和停止共享都不能继续获得准入。

邀请路由提示放在握手的“声明端口”字段，实际连接地址、TCP 端口和域名保持原值。它只是从当前授权表中选出待验证邀请的 16 位提示，**不是访问凭证**；提示从密钥的 SHA-256 导出，生成时避免当前授权表内冲突，仍必须通过完整的双向证明。这样不会影响 e4mc 对域名的解析，也不依赖 Forge 会截断的自定义域名后缀。公共服务器的握手和官方登录路径不变。

房主名字不能由访客冒用，这是 1.8.9 单人世界按名字判断部分房主行为的兼容保护。真实玩家身份依旧绑定 UUID。正在连接时取消会关闭登录连接；未完成私密验证的 LoginSuccess 会被拒绝。

### 仍使用 VoxLink 和 e4mc

- VoxLink：复用现代版传输源码，构建时转换为 Java 8 可用的实现；保留相同信令和可靠 UDP/TCP 逻辑。现代版源码没有修改。
- e4mc：从 [e4mc Retro](https://github.com/xhyrom/e4mc-retro) 固定版本提取重定位后的网络库和原生库，适配其 QUIC 会话。没有加载它的 Forge 入口、UI、命令或随包 Mixin。
- e4mc 上游服务严格规范化握手域名，参考 [quiclime 的握手解析](https://github.com/vgskye/e4mc-quiclime/blob/6e97f68f75e81128f8e192027412a996673503b8/src/netty.rs)。本轮通过真实中继连接验证了兼容性。
- 来源、提交、SHA-512、许可证在 `ports/unpvp-network/UPSTREAM.json` 和包内 `META-INF/unbox-network/`。VoxLink 原有来源/许可及 Java 8 适配源码同样随包保留。
- 中继 QUIC 层沿用上游证书策略，不把它描述为额外的严格证书认证；实际私密游戏连接仍执行前述邀请证明及 Minecraft 端到端加密。

### UI 与性能

好友区域继续使用现有矢量布局，静态背景沿用缓存。好友内容变化才重建控件；通知、好友状态和通信轮询有间隔。文件读取、网络操作、皮肤解码放到后台，上传纹理留在游戏线程。没有修改世界清晰度、抗锯齿、模糊半径或既有 HUD。

### 头顶名字与 Logo 变体

补查现代版 `NameBadges`、`NameBadgeMixin`、`LocalNicknameNameTagMixin`、`LogoResources` 和 `SkinLibraryScreen` 后，确认当前已有基础开盒、皇冠、飘钞三款。1.8.9 现已接入相同账号装备数据与 `equip-logo` 命令，沿用 Microsoft 账号装备限制，不生成虚构的已拥有资产。Appearance 新增 Skins / Logos 切换；替换静态示意卡片为实际资产列表。

- F5 显示自己的昵称及 Logo，第一人称、F1、打开界面时隐藏自己的头顶标签；其他玩家继续遵循游戏原有标签显示规则。昵称替换按实体/UUID 匹配，避免同名玩家被误改。
- 公共服务器按当前账号有效好友服务视图识别 Unbox 玩家；私密世界增加授权玩家 UUID 名单，两个 1.8.9 客户端通过游戏连接互相识别基础标志。装备变体仍由原账号服务提供。
- 过期数据、其他账号的数据和未知变体回退基础 Logo；普通玩家不会自动加上 Unbox 标志。
- 使用现代版随包品牌图，纹理缓存并生成多级缩小图，避免每帧解码；品牌图不受材质包替换。OptiFine 的浮点字宽接口已单独适配，名字居中计算包含 Logo。字体挂钩仅在名字渲染期间生效。
- 保留名字的模型遮挡剔除仍调用同一名字渲染路径，所以 Logo 随名字一起保留。未新增针对该功能的 FPS 提升承诺。

## 验证证据

所有游戏验证均使用 `.cache/unpvp-tests` 下的新建测试目录与虚构身份，没有向用户真实好友发送邀请，也没有改动用户存档。

| 验证 | 结果 / 证据 |
| --- | --- |
| 两个 Java 8 ARM64 游戏进程，经局域网加入 | `social-lan-final`：加密入服、访客 UUID 保持不变、停止共享踢出访客且保留房主 |
| 两个游戏进程，经 VoxLink 连接 | `social-p2p-final`：同上；测试禁用 LAN 并交换真实候选，点对点候选限同机测试地址 |
| 两个游戏进程，经公网 e4mc 中继 | `social-relay-06`：真实分配中继地址并完成加密入服、停止共享；两端仍在同一台 Mac |
| 未邀请 / 冒用房主名字 | 网络客户端实际发送登录请求，被拒绝 |
| 错误邀请密钥 | `social-auth-negative-02`：刻意使用相同 16 位路由提示但不同密钥，双向证明仍拒绝入服 |
| UI / 通信回执 / 过期缓存 | `social-ui-01`：10 项检查通过，好友管理/通知/Multiplayer/连接/皮肤页截图 |
| 原有 PvP 功能 | `social-pvp-regression` 与新增名字挂钩后的 `social-badges-pvp`：各 97 项检查通过 |
| Logo / 昵称 / 装备入口 | `badges-06`：18 项通过（同时复查资产列表无静态示意卡片重叠），实际世界及 F5 截图确认皇冠、飘钞；测试使用虚构资产视图，未对真实账号执行装备操作 |
| 私密世界标志识别 | `social-badges-pair`：两个游戏进程互相识别房主/访客 Unbox 标志能力，加入与撤销仍通过 |
| 传输回归 | `.cache/unpvp-social/transport-test.log`：17 项，通过 UDP/TCP 双向 2 MiB 数据、丢包/乱序/重复、1200 字节 MTU、空闲保活与取消；测试驱动运行在构建 JDK，传输类编译为 Java 8，实际游戏测试运行 Java 8 |
| Rust | 91 项通过、5 项既有忽略；随后增加好友目录/个人设置检查，相关 sharing 8 项通过 |
| 好友服务逻辑 | `tests/social.test.mjs`：15 项通过 |
| 本地应用打包 | `npm run tauri -- build --debug --bundles app` 成功；没有发布线上更新 |

尚未完成两台异地电脑、Windows 实玩或两个真实账号的端到端云端好友操作验证；不能把同机双客户端/公网中继测试描述为这些测试已通过。跨版本自动重启通过代码接入与启动器回归检查，尚未完整跑一遍现代版与旧版实际切换。此轮没有重新宣称 Hypixel FPS 改善。

## 代码位置与交付

- 游戏功能：`client-mod/src/unpvp/java/dev/unbox/unpvp/` 中的 `SocialBridge`、`PrivateLogin`、`SocialPanel`、`SocialNotifications`、`MultiplayerScreen`、`JoinScreen`、`ConnectionScreen`、`JoinDiagnostics`、`VoxLinkNetwork`、`LocalSkin`、`NameBadges`、`BadgeTextures`、`SkinsScreen`。
- 1.8.9 接口挂钩：`ports/pvp-independent/src/main/java/dev/unbox/pvp/mixin/Private*`、`FriendServerListMixin`、`LocalSkinMixin`、`NameBadgeMixin`、`BadgeFontMixin`、`BadgeRosterMixin`。
- 构建：`scripts/build-unpvp-network.py`、`scripts/prepare-unpvp-voxlink.py`，由原 `build-unpvp.py` 调用。
- 游戏回归：`scripts/test-unpvp-social.py`、`SocialProbe`。必须使用独立测试目录，不能用真实账号邀请替代测试数据。
- 产物：`src-tauri/target/debug/bundle/macos/Unbox Client.app`。重启此启动器，再启动 1.8.9，才能载入新游戏 JAR；已运行游戏不会热替换网络代码。
