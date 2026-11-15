# Unbox unpvp 1.8.9 版本总交接

日期：2026-11-14。工作区：`/Users/ty/Documents/ChatGPT/启动器`。分支：`codex/unpvp-ui`。

本文件是当前 1.8.9 版本的接手入口，汇总 UI、PvP、性能、好友联机和玩家标志。此次只整理文档，没有修改程序、重新运行游戏测试或发布线上更新；下述结果引用已完成的测试及本次核对的产物。

## 1. 当前交付状态

unpvp 是 Unbox 的第二版 Minecraft Java 1.8.9 / Forge 客户端。它采用 Unbox 自己的界面和交互，将公开模组的选定功能、自制功能与性能组件统一接入。旧版 expvp 已废弃，不作为代码或架构来源；只沿用用户允许复用的 SVG 图标资产。

当前交付为 **本地 macOS 调试应用**。已加入好友管理、世界邀请/加入、连接状态、皮肤库、玩家名字及 Logo 变体。没有为本轮发布 GitHub Release，也没有部署新的好友后端。代码和产物仍处于当前工作区，不能假定已提交、合并或推送。

应用路径：`src-tauri/target/debug/bundle/macos/Unbox Client.app`。

加载方式：退出旧游戏，重新打开上述启动器，选择 Forge 1.8.9 Profile 后启动。启动器准备时将新 JAR 放入实际游戏目录；已经运行的游戏不会热替换 Java 代码。好友状态、皮肤和装备通信需要保持启动器运行。

产品范围：只包含 PvP 相关功能及已有通用账号/联机功能。不加入语音、生存/建造、Replay、Freecam、OneConfig 或 Essential；Look Back 是回头视角。完整现代版玩法整合包、config 和脚本同步不属于此次 1.8.9 交付。

## 2. 用户能使用的功能

### UI、交互和 HUD

- 主菜单、Right Shift 居中快捷菜单、Client Mods、设置及 HUD 编辑器沿用现代版设计体系，界面保持英文。
- 模组目录顶部只保留 PvP；卡片收藏和收藏优先排序仍在。
- 标题原版 Panorama 加均匀高斯模糊，Logo 和控件单独绘制。游戏 Motion Blur 是独立功能，默认关闭。
- 标题淡入、页面交叉淡化和列表内容过渡为 120ms；目标页就绪后衔接，避免先消失再等待。支持减少动态效果。
- 矢量图形按实际像素尺寸生成，后台准备页面，缓存静态图层；滚动不重新生成整张列表。首次未缓存页面仍有准备成本，不能承诺所有页面首次打开都零延迟。
- 设置中的文字、数字、颜色和快捷键输入位置有灰阶实心底框。新快捷键覆盖冲突旧绑定；Escape 取消、Delete 清除，支持鼠标键。
- HUD 灰色背景默认关闭，保留用户显式保存的设置。药水 HUD 已显示原版图标、效果及时间。
- 已修复 Time Changer 显示时间与服务器时间不同时的天空闪烁，以及退出菜单后 Toggle Sprint 意图丢失的问题。

详细参数、缓存预算及历史对比见 [98：UI 动画与 PvP](98-unpvp-UI动画与PvP模组交接.md)、[97：药水图标与缓存](97-unpvp药水图标与界面缓存优化.md)。没有宣称与现代版逐像素 100% 一致，也没有将短淡化动画等同于无准备耗时。

### PvP 功能与来源

当前模组目录共 **29 个入口**：11 项公开项目源码适配功能、17 项 Unbox 自制/自身移植功能、1 项 Performance 管理入口。入口数不等于独立 JAR 数。

| 来源 | 当前功能 |
| --- | --- |
| DamageTint | Hit Color，修改玩家/生物受伤叠色 |
| OverflowParticles | 粒子类型、密度、上限、大小、颜色 |
| OverflowAnimationsV2 | Classic Animations，选定旧版动作 |
| Antimations | Hand Animations，手部动作控制 |
| VanillaHUD | Scoreboard、Player List、Hotbar |
| NoScreenShake | View Bobbing，分离世界与手部晃动 |
| PolyTime | Time Changer，本地视觉时间 |
| PolyBlur | Motion Blur，独立残影着色器 |
| ItemPhysicLite | Item Physics，掉落物姿态及旋转 |

这些是选定功能的适配，不包含上游整套客户端 UI。固定提交、许可证与修改范围以 `ports/pvp-independent/UPSTREAM.json` 和 README 为准。

Unbox 自制或自身移植的 17 项：FPS、CPS、Keystrokes、Latency、Coordinates、Armor Status、Potion Effects、Crosshair、Toggle Sprint、Toggle Sneak、Zoom、Look Back、Nickname、Chat、Hurt Camera、Resource Packs、Viewmodel。详细实现分类见 98，不能按目录名将所有 Mixin 都归为外部代码。

Viewmodel 可调第一人称手臂/手持物位置、旋转及 25%–150% 大小，空手可单独设置；不改变第三人称模型、攻击判定或交互距离。Hit Color 是受伤变色，不是手上颜色。

材质包浏览器为 Unbox 自制，**没有安装 Resourcify**。路径：Right Shift → Client Mods → Resource Packs → Browse → Download → Installed → Enable。查询兼容 1.8.9 的资源包，下载后仍需启用。

### 好友、邀请和网络

沿用现代版启动器好友服务、固定账号身份和已有交互：好友列表/在线状态、好友码、添加/接受/拒绝/取消/删除、世界邀请、邀请通知、加入确认、取消连接、重试、停止共享及连接报告。

连接顺序是 **局域网 → VoxLink 点对点 → e4mc 中继**。VoxLink 使用现代版传输源码的 Java 8 适配；e4mc 使用固定的 e4mc Retro 网络依赖，没有加载其客户端界面或入口。没有更换好友服务，也没有增加语音。

私密房间验证与邀请、房间和固定 UUID 绑定，使用双向证明及 Minecraft 登录加密。握手声明端口内的 16 位值仅用于选择待验证邀请，不是访问凭证；不要改为自定义域名后缀，e4mc 会规范化域名。公共服务器维持 Microsoft 登录路径；本地账号仅提供受邀私密世界入口。

停止共享会撤销准入并移除访客，房主保留。邀请其他版本会交给启动器选择对应好友 Profile 并恢复加入；这不意味着不同 Minecraft 版本能直接进入同一服务器，也不意味着跨加载器转换模组。

实现、身份验证、网络适配和测试边界见 [101：好友邀请与联机](101-unpvp好友邀请与联机功能交接.md)。

### 皮肤、名字与 Logo

- Appearance 接入启动器已保存的皮肤库、Classic / Slim，以及原兑换入口；原 OptiFine 披风功能保留。
- 玩家头顶名字前支持三款已有标志：基础开盒、Creator Crown、Fluttering Cash。
- Appearance → Logos 显示账号已拥有的款式，使用原 `equip-logo` 命令，沿用 Microsoft 账号装备限制；没有自动授予付费或未拥有资产。
- F5 下显示自己的昵称与标志，第一人称、F1 和打开界面时隐藏自己的头顶标签。其他玩家继续遵循游戏名字可见性规则。
- 公共服务器按有效账号服务视图识别 Unbox 玩家；1.8.9 私密房间另同步授权玩家 UUID 名单。普通玩家不会自动加标志。
- 过期账号数据、其他账号数据或未知变体回退基础图标。昵称替换按实体/UUID 判断，显示名不改变真实身份。
- 图标使用现代版随包品牌资源，缓存纹理并生成缩小采样层；品牌图不受材质包覆盖。OptiFine 字宽计算已适配，Logo 参与名字居中计算。
- 保留名字的模型遮挡剔除继续调用名字绘制，因此名字与 Logo 一起保留。

## 3. 性能与 Mac 显示

六项性能组件：OptiFine HD U M5、Nothirium 0.4.9-beta-2、Entity Culling 6.5.0、Phosphor Legacy 7、FoamFix 0.6.3a、Unbox Patcher 性能子集。另有 RenderLib 1.4.5.5 和 ConfigurationsBackport 1.1 作为依赖。准确安装清单见 `src-tauri/resources/unpvp-performance.json`，不要使用含 OneConfig 的历史候选清单替代。

Apple Silicon 已使用 ARM64 Zulu Java 8u512 和独立 LWJGL 本地库。当前固定为 **MinecraftMachina mmachina.1**。mmachina.2 曾造成报告尺寸与真实窗口表面不一致，标题画面放大裁剪；改回支持正确 HighDPI 表面的 .1 后，实际窗口呈现对照通过，用户确认主菜单完整且大小正常。不要用降低 Retina 分辨率或强行缩小 UI 掩盖这个问题。

新增模型遮挡查询只跳过完全被挡住的模型，保留名字。查询异步读取，移动、投影变化、数据过期时保守正常绘制；光影、旁观和受伤晃动时停用新增路径。有查询开销，可见实体多而遮挡少时可能降低 FPS。

| 已完成的固定本地实验 | 结果 | 使用限制 |
| --- | --- | --- |
| 相同 Retina 输出，密集测试玩家/文字，关闭新增剔除 | Intel/Rosetta 139.031 → ARM64 211.471 平均 FPS | 短时模拟场景，不是 Hypixel 实测 |
| ARM64 固定墙后场景，新增剔除关闭/开启 | 310.222 → 391.682 平均 FPS | 只说明该遮挡场景，不可与架构增幅相乘 |
| 可见且无墙场景，剔除关闭/开启 | 232.844 → 220.304 平均 FPS | 保留负向数据，不宣称全场景提升 |

启动优化保留校验缓存、避免重复复制、图片批量读取及世界切换中减少显式全量 GC。保留世界保存等待，只在服务器线程确实终止后跳过无效的重复退出任务。ARM 交付日志曾记录 JVM 到标题首帧约 11.3 秒；不是点击启动按钮到可玩的总耗时。原启动器重开等待原因尚未定位。

详细证据见 [100：Apple Silicon 与遮挡](100-unpvp-AppleSilicon与模型遮挡优化.md)、[91：启动与加载](91-unpvp启动与世界加载优化.md)、[99：大厅帧率调查](99-unpvp服务器大厅帧率优化.md)。用户报告的 Hypixel Mac 大厅约 89 FPS 问题仍需真实服务器复测；本轮好友/Logo 功能没有新增世界 FPS 对照。

## 4. 代码、数据与构建

| 路径 | 作用 |
| --- | --- |
| `client-mod/src/unpvp/java/dev/unbox/unpvp/` | UI、HUD、社交桥、网络控制、皮肤、Logo、遮挡等核心 |
| `client-mod/src/unpvp/resources/` | 1.8.9 UI 场景、配置等资源 |
| `ports/pvp-independent/` | PvP 及社交/皮肤/名字接入 Mixin、来源许可 |
| `ports/patcher-performance/` | 独立 Patcher 性能子集 |
| `ports/unpvp-network/` | e4mc 适配及网络依赖来源 |
| `scripts/unpvp_catalog.py` | 29 项功能及默认设置 |
| `src-tauri/src/unpvp.rs` | 准备资源、固定依赖、选择运行时、启动游戏 |
| `src-tauri/src/main.rs`、`social.rs`、`sharing.rs`、`skin_library.rs` | 游戏目录解析及启动器通信 |

实际游戏目录是 **`profiles/<id>/unpvp-ui`**。社交状态、皮肤库和邀请恢复文件必须写入这里，不能写到 Profile 外层。配置在 `config/unpvp-ui.properties`，实际按键在 `options.txt`。新好友 Profile 仅首次从同为 1.8.9 的个人 Profile 继承选项；不覆盖已有个人配置，不混拷现代版选项。

主要运行文件：

- `unpvp-forge-1.8.9.jar`：核心，复制到游戏目录后名为 `unpvp.jar`。
- `unpvp-pvp.jar`：功能挂钩。
- `unpvp-patcher-performance.jar`：性能子集。
- `unpvp-network.jar`：网络依赖。
- `unpvp-apple-silicon.json`：ARM 运行时清单；外部性能 JAR 由启动器按清单准备。

已有工具链的本工作区构建顺序：

```sh
cd '/Users/ty/Documents/ChatGPT/启动器'
python3 scripts/build-unpvp.py
python3 scripts/build-unpvp-pvp.py
python3 scripts/prepare-unpvp-performance.py
npm run tauri -- build --debug --bundles app
```

核心构建会调用网络构建；修改 Patcher 源码时，先运行 `python3 scripts/build-unpvp-patcher.py`。不要只更新核心而漏掉独立 PvP JAR。首次环境需要原 Forge/Java 构建工具链、下载依赖及启动器准备的游戏资源，以上不是空白机器的一键安装说明。

最新打包记录 `.cache/unpvp-social/bundle.log`，产物约 115.27 MiB；审计 `.cache/unpvp-social/bundle-audit.json` 已核对四个 JAR、ARM 清单、三款品牌资源和应用内对应文件。交接时核心 SHA-256：`ac8233ae7d71a3f8b67775668af4c45d43d88a883d588b610ae16ec15879409f`。后续重新构建应重新核对，不将此摘要当作永久版本号。

## 5. 验证结果与复现

游戏测试使用 `.cache/unpvp-tests/` 下的隔离身份和世界，没有向用户真实好友发消息，没有修改用户存档。

| 证据 | 已验证内容 |
| --- | --- |
| `social-badges-pvp` | 新增名字挂钩后，97 项既有 PvP 检查通过 |
| `badges-06` | 18 项名字/Logo/账号数据/装备命令/GL 检查通过；截图确认头顶、F5 和资产列表无示意卡片重叠；游戏退出 0 |
| `social-ui-01` | 10 项好友界面、回执、过期缓存检查 |
| `social-lan-final`、`social-p2p-final`、`social-relay-06` | 两个真实游戏进程分别经 LAN、VoxLink、真实公网 e4mc 加密加入，撤销后访客退出、房主保留；两端在同一 Mac |
| `social-badges-pair` | 房主、访客互相识别 Unbox 标志能力，加入及撤销仍通过 |
| `social-auth-negative-02` | 错误密钥即使有相同 16 位路由提示仍被拒绝；另有未邀请、冒用房主名字的实际登录拒绝检查 |
| `.cache/unpvp-social/transport-test.log` | 17 项传输测试：数据传送、丢包、乱序、重复、MTU、保活及取消 |
| Rust / 好友服务 | 原 Rust 91 项通过、5 项忽略，新增后 sharing 相关 8 项通过；好友服务 15 项通过 |
| `native-presentation-fixed`、`native-resize` | 实际窗口呈现对照、缩放/尺寸/全屏检查；未将未聚焦窗口的点击算作通过 |
| `native-occlusion-final` | 名字像素一致、模型可见性、相机变化失效、移除墙及查询清理，无 GL 错误 |

`badges-06` 当时游戏正常退出且完整 PASS，但外层测试脚本尚未识别新 Badge PASS 标记，曾返回失败。随后修正了脚本的成功标记选择；没有为此重新跑一遍相同游戏。区分测试包装脚本问题与游戏断言结果，保留原日志。

可复用命令如下，每次换一个新测试名，不覆盖旧证据。测试需要本机现有运行时与游戏资源：

```sh
python3 scripts/test-unpvp.py handoff-pvp-01 --pvp --mods "$PWD/.cache/unpvp-performance/jars"
python3 scripts/test-unpvp.py handoff-badges-01 --badges --mods "$PWD/.cache/unpvp-performance/jars"
python3 scripts/test-unpvp-social.py handoff-lan-01
python3 scripts/test-unpvp-social.py handoff-p2p-01 --p2p
python3 scripts/test-unpvp-social.py handoff-relay-01 --relay
python3 scripts/test-unpvp-social.py handoff-auth-01 --badsecret
```

双客户端脚本当前依赖 `.cache/unpvp-tests/native-retina/mods` 测试模组夹。迁移到新机器时需准备等价的固定性能包，并检查测试脚本路径；不要让测试使用玩家的真实 Profile。中继测试会访问公共 e4mc，测试好友状态由本机夹具提供，不证明真实云端账号流程已通过。

## 6. 下一位接手者优先处理什么

1. **真实账号、异地联机**：两台电脑通过原好友服务添加、邀请、加入、重连、取消、移除好友、停止共享；验证不同 NAT 环境下的 VoxLink / e4mc 回退。
2. **真实资产装备**：已拥有皇冠/飘钞的 Microsoft 账号在游戏装备，启动器及另一客户端能看到同步结果。现有测试用的是虚构资产视图，没有真实兑换或装备操作。
3. **跨版本加入**：实际完成现代版与 1.8.9 Profile 的切换、游戏重启和邀请恢复；已接入代码和启动器回归，不等于完整交互实测。
4. **Windows 与长期稳定性**：Windows 游戏、网络、字体和输入实玩；Mac 长时间游玩、反复全屏/窗口切换。历史 Intel Java/LWJGL 曾发生原生窗口崩溃，不能据短期 ARM 测试宣称彻底解决。
5. **真实 Hypixel 帧时间**：同地点、人数、方向、画质、分辨率下记录平均 FPS 与 P95/P99，观察视角移动和遮挡少时的查询开销。不要降低画质或隐藏玩家名字来制造性能提升。
6. **正式发布前**：核对固定依赖、许可证/来源、平台构建、更新渠道与本轮发布授权；本地构建通过不代表已经上线。

修改时保持现有设计和交互；新增设置同时检查实际运行逻辑、预览、默认值和保存。旧 expvp 目录、旧候选锁文件和早期文档不能覆盖当前安装路径。工作区有大量其他任务的未提交修改，不要清理、回滚或覆盖无关文件。

专题查阅顺序：本文件 → [101 联机及 Logo](101-unpvp好友邀请与联机功能交接.md) → [100 原生运行及遮挡](100-unpvp-AppleSilicon与模型遮挡优化.md) → [98 UI/PvP 来源清单](98-unpvp-UI动画与PvP模组交接.md)。历史数值只在原测试条件下有效；新的实现与实测证据应补入对应专题，再更新本总交接。
