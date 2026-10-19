# Unbox Client — implementation status


Working-tree v2 adds email/password Unbox accounts, editable non-unique nicknames and encrypted mixed-identity invitations. Email verification is deferred by user request; Resend is not required. Production remains v1 pending deployment. See `docs/13-Unbox邮箱账号与混合联机.md`.
Updated 2026-10-07. This is a local development build, not a finished public release.

## Verified

- Native macOS Tauri application builds and opens, with a rounded application icon.
- Four-step profile creation validates the name, retains drafts on Back, offers exact Minecraft 26.1, and persists only after final confirmation.
- Fabric and Vanilla profile paths exist. Fabric 0.19.5 with Java 25 was launched successfully on this Mac. Logs confirm Unbox, Sodium, Lithium, ImmediatelyFast, EntityCulling, FerriteCore and DynamicFPS initialization. Vanilla has not received the same end-to-end launch verification.
- Official game assets and pinned mods are downloaded with checksum checks. Profiles have separate directories. Existing unrelated mod files are preserved.
- Local account selection, module preferences and launcher settings persist. Local identities do not authenticate to Microsoft or online-mode servers.
- Home layout follows viewport size, including live resizing from 1280×800 to 1920×1080 and 3840×2160 and back. Side rail and margins use bounded responsive values; the launch area takes approximately 43% of the inner panel. News and content lists use the remaining area. The compact 1000×720 layout is also tested.
- Thirteen Node/Playwright tests cover profile flow, persistence, responsive layout, reduced motion, cloud API validation, simulated Microsoft authorization/cancellation, multiple accounts, skin preview/upload failures and startup error recovery. Seventeen Rust tests cover launcher rules, account isolation/serialization, sanitized errors, PNG decoding and actual HTTP requests against a local mock for OAuth refresh, Xbox/XSTS, Minecraft ownership/profile and skin upload. These tests do not authenticate a real Microsoft account.

## Implemented, further game testing required

Unbox includes FPS/CPS, coordinates and direction, armor durability, custom crosshair shapes/colors, particle density and family filters, zoom (hold/toggle and smooth transition), auto sprint, chat layout/background controls, and optional OptiFine capes/Freelook.

The in-game panel now follows original Feather's icon-card layout with independent toggles/settings, favorites, search, categories, typed sliders/segmented choices/color picker, per-module reset/undo, and actual keybind recording with conflict rejection. FPS/CPS/Coordinates/Armor HUDs have independent position, scale, colors and visibility settings. FPS is always background-free; CPS uses two transparent, square white outlines with live press feedback. HUDs can be dragged directly on the Mods screen. The HUD editor also supports drag, corner resize, snapping, keyboard nudges, save and cancellation. Right Shift opens a compact quick menu, followed by Mods; C is the default zoom key. This is configuration reload, not arbitrary JAR hot loading.

The preceding v2 run passed 42 assertions across menu actions, keybind conflicts, color cancellation, reset/undo, HUD dragging/resizing/cancellation, settings persistence, all 12 module pages, actual Sodium settings navigation, GUI scales 2/4/6, chat option isolation, and real particle filtering. Actual render exports are in `design/exports/in-game-v2/`; evidence and remaining boundaries are in `docs/08-游戏内模组面板研究与实现.md`.

The final v3 in-game regression passed 60 assertions using the packaged JAR, including direct FPS/CPS/armor dragging and locking, the crosshair canvas clipboard flow, per-family particle settings and real particle lifetime, dropdown keyboard selection, and GUI scales 2/4/6. Inspected screenshots and the test report are in `design/exports/in-game-v3/`. Rendering now uses filtered high-resolution glyphs and rounded masks; persistent HUD labels are batched as complete text runs with a bounded layout cache.

The v3 settings pass researches actual PolyCrosshair, OverflowParticles, EvergreenHUD, Chatting and PolySprint configuration. It adds a custom crosshair canvas and sharing, dependent settings, per-family particle parameters, equipment-slot selection and expanded chat layout controls. See `docs/09-OneClient设置对照与Shift升级.md` for implementation boundaries and final v3 verification.

The v4 pass adds a branded in-game title screen, a compact Right Shift quick menu, and a black/white/gray palette with blue and red accents. The launcher uses the same accent direction. Settings add cross-tab search, exact numeric entry, individual reset, HSV/Hex color selection and larger live previews. Crosshair has eight presets, custom dot/outline sizing and its existing paint canvas. Particles have 14 family controls with registry-based tagging and real single-quad render opacity. Armor adds names/counts, spacing and more durability formats; Keystrokes is the thirteenth module. Freelook adds inverted axes/smoothing, Zoom adds scroll/adaptive sensitivity, and Coordinates adds biome/dimension conversion.

The final v4 in-world test passed 101 assertions; the separate startup/title test passed 20, including actual startup replacement, vanilla world/server/settings navigation and small-window/GUI scaling. A pre-world armor preview crash was found and fixed. The UI-tested and packaged JARs differ only in the title-test assertion class, verified by comparing ZIP entries; production class contents match. See `design/exports/in-game-v4/build-verification.json`. Native packaging and all nine Node/Playwright tests pass. Research, scope and limitations are in `docs/10-Lunar参考对照与游戏内UI升级.md`. Vanilla world selection, server lists and game/skin settings screens are still used; not every game screen is reskinned.

OptiFine cape ownership/network behavior and Freelook on multiplayer servers still require corresponding environment tests. Not every module option has a gameplay assertion; rendering a settings page is recorded separately from validating an effect. OneClient/Feather/Lunar were studied via official screenshots, documentation and (for OneConfig) source, not run locally.

World-entry regression passes with Sodium 0.8.9. Performance results for the prior version are in `docs/07-崩溃修复与性能验证.md`, and the new panel/HUD results are in document 08. No superiority over other clients is claimed. Final v3 repeated static-scene tests improved average FPS (308–311 versus 222–231), but regressed 1% low (91–92 versus 109–110). Frame pacing remains unresolved; full raw evidence and intermediate experiments are in `research/benchmarks/2026-10-06-shift-v3/README.md`.

Final v4 static-scene repetitions (old → new → new → old) measured 333–335 average FPS / 131–133 1% low, versus 340–341 / 138–139 on the prior v3 build. This is a small measured regression, not a performance improvement; frame pacing remains unresolved. Raw measurements and build hashes are in `research/benchmarks/2026-10-07-lunar-v4/README.md`.

The v4.1 visual fix scales in-game menus to 85% of their previous size without changing saved HUD layouts. It replaces dark logo face fills with true transparency, exports the game logo at 768px, adds a 200ms fade/rise entrance respecting zero Screen Effects, and changes the macOS app icon to a black rounded tile. The final JAR passes 23 title checks and 101 world UI checks; captures and hashes are in `design/exports/in-game-v4.1/`. This pass does not include a new FPS benchmark.

## Not connected or not yet implemented

- The Cloudflare friends service is deployed through the plugin on Workers Free with D1 at `https://unbox-client-friends.printoria-studio.workers.dev`. Health and 36 live API assertions passed; all synthetic fixtures were removed. The rebuilt macOS app includes this endpoint. No paid plan was enabled. Two real Microsoft accounts joining across networks remain unverified. See `docs/12-文件导入与好友联机.md`.
- Microsoft device authorization, Xbox/XSTS, Minecraft ownership/profile checks, Keychain storage, token refresh and launch credentials are implemented. A registered Unbox Client ID and Minecraft Services approval are still missing, so real-account end-to-end sign-in remains unverified. Debug builds now provide an opt-in DevLogin development provider; release builds reject that provider. Multiple accounts, provider isolation, secure refresh rotation, selection/removal and startup restoration are implemented. See `docs/11-账号与皮肤闭环.md`.
- Forge and NeoForge are explicitly marked integration pending; this does not mean upstream 26.1 releases are unavailable.
- Authenticated account skin retrieval, face avatars, Classic/Slim 3D preview, PNG upload and profile refresh are implemented and covered with simulated account fixtures/local HTTP tests. Live skin changes and authenticated server entry still require real-account verification. Cape selection, infinite thumbnail lists and shared environment synchronization remain unfinished. Friend and world-invitation code is implemented and the cloud service is deployed; a live two-account, two-network gameplay test remains outstanding. Local accounts retain a labelled sample preview.
- AI/MCP game control and controlled extension hot loading remain unfinished.
- Java 25 must already be installed. Automatic Java installation, download cancellation and a complete mod dependency/conflict resolver are not implemented.
- No release signing/notarization, clean-machine build verification, or Windows/Linux validation has been completed. Third-party distribution notices need a release audit.

## Local artifacts

- App: `src-tauri/target/debug/bundle/macos/Unbox Client.app`
- Research: `docs/06-交互审查与26.1实现依据.md`
- Updated Pen profile boards: `12 · Create Profile — Name` and `15 · Profile creation — Four steps`
- Screenshots: `design/exports/unbox-responsive-1000.png`, `unbox-responsive-1280.png`, `unbox-responsive-1920.png`
- App data: `~/Library/Application Support/dev.unbox.client`

The verification profile is named `Unbox Verification` and uses the test local identity `UnboxTest`. These are test data, not a Microsoft account.

## File drops and friends (2026-10-08)

Mods/Packs/Shaders accept native drag-drop events, copy valid files without overwriting, and have icon-only folder buttons. Friends support request/accept/remove and invitations; the game quick menu includes a borderless right rail. Authenticated invite-only worlds probe LAN first and request the pinned e4mc relay only on failure. Invitations now support Microsoft and Unbox identities with UUID-bound encrypted admission. Same-name Unbox guests were tested in two real game instances through e4mc on one Mac; a real Microsoft + Unbox pair and two independent networks remain unverified. Gameplay JAR synchronization is implemented for Fabric 26.1 with isolated friend profiles and a content-addressed cache; configuration and full modpack synchronization remain unfinished. Build and validation details, free-service boundaries and deployment blocker: `docs/12-文件导入与好友联机.md`.

## Account deployment and manual updates (2026-10-08)

Cloudflare v2 is deployed on the existing Workers Free/D1 service. Seven groups of live API checks cover password registration, normalized-email uniqueness, authentication, same-name renaming, friendships, per-friend v2 invitations and session revocation. Synthetic accounts and related data were removed. No email verification or Resend is enabled. See document 13 for authentication limits and game-test scope.

The launcher adds a borderless update shortcut and Settings → Updates. Checks read stable GitHub Releases; installation uses the Tauri updater signature verifier and only assets from SkyVessel/unbox-client. Checking is allowed while playing; installation reserves the same job lock as game launch and requires the game to be closed. No release is built or published in this pass, so end-to-end self-replacement with a published package remains untested. Future signing/manifest instructions: docs/14-客户端更新.md.

The upper-left logo uses the original cropped vector artwork at 44px, without the previous SVG viewport padding. Account badges remain separately sized.

## Shared gameplay mods and logo rendering (2026-10-08)

Authenticated login now exchanges a bounded SHA-256 manifest and streams missing gameplay JARs over the existing encrypted LAN/e4mc connection. Native preparation uses a separate friend profile and resumes joining after a necessary restart. Two real game processes loaded a transferred test JAR and joined successfully; subsequent matching downloaded zero bytes. Production-cloud, two-location and large-modpack validation remain outstanding. See docs/15-共享模组与性能复查.md for protocol, cache, timeout and testing limits.

The launcher header now draws the six original logo paths directly, removing the SVG mask. WebKit 2× rendering was inspected. No mouse smoothing or input filtering was added; the reported physical-mouse stutter remains unlocalized.

## 0.2.4 local testing work (2026-10-08)

The current pass adds account-isolated offline friend snapshots, fixes the in-game face rectangle coordinates, and restores signed skin properties after private invitation authentication. Key conflicts now transfer the binding, and launcher options are generated from the game catalogue. Fabric adds draggable minimap/latency HUDs, M-map controls, server-sourced teammate death markers and permission-checked vanilla teleport commands. NeoForge 26.1 installation and five compatible optimization mods have entered an actual test world; Unbox custom in-game modules/private invites are still Fabric-only. See `docs/20-好友修复与地图及NeoForge.md` for measured results and remaining manual verification. This section supersedes older claims above that NeoForge cannot be selected or that key conflicts are rejected.


## 2026-10-09 · 0.2.5 NeoForge native modules

NeoForge 26.1 now has a separately compiled Unbox module using native key/HUD/events and a direct login transport. Shared UI, settings, HUD, terrain map, invitation proof and transfer code remain common source. No Fabric API or Fabric Loader references are allowed in the NeoForge artifact. Two-client LAN, public e4mc relay, and gameplay-JAR download/restart/rejoin checks passed. See `docs/21-NeoForge原生适配.md` for current verification and limits. This supersedes the previous Fabric-only module/invitation status. Local testing build only; no Release published.

## 2026-10-10 · Unbox Microsoft 应用

- 内置自有 Client ID，调试/发布统一，忽略旧应用配置，移除 DevLogin 和玩家填写 ID 的入口。
- 旧凭证要求重新授权；同 UUID 新授权成功后替换旧应用凭证，保留其他账号。
- 设备码真实接口 HTTP 200；32 项 Rust、37 项启动器测试通过。真实账号授权、Minecraft 权益与重启续期尚待用户登录验证，详见 `docs/22-Unbox微软应用接入.md`。

## 2026-10-10 标题返回修复与玩法模组同步复测

修复无世界时 `setScreen(null)` 在内部新建原版标题页、绕过品牌替换的问题。Fabric/NeoForge 各 25 项标题页检查通过。两种加载器分别完成仅房主安装独立玩法测试 JAR、访客下载、原生共享 Profile 准备、重启加入、双方实际使用新物品并由服务器同步奖励的双进程实测；再次加入下载量均为 0，无再次重启。本地 macOS 调试包已重建并校验，未发布 Release。测试为同机 LAN 与本地好友服务，不是异地或真实微软双账号实测。证据与限制见 `docs/23-标题返回修复与玩法模组同步实测.md`。

## 2026-10-10 0.2.6 已公开发布

用户授权后已发布 GitHub v0.2.6，并设为 latest。Windows x64 NSIS 与 macOS ARM64 更新归档均可公开下载，沿用原公钥；匿名下载、远端哈希、签名验证及篡改拒绝通过。Node 37 / macOS Rust 32 / Windows Rust 34 项检查通过。最新版源码和限制见 `docs/24-0.2.6测试更新发布.md`，此次发布覆盖此前仅本地构建的功能。真实测试者电脑上的安装与 Windows 游戏实玩尚待反馈。

## 2026-10-13 · 邀请、26.1.2 与共享资源

新增 26.1.2 的 Fabric/NeoForge 原生模块和独立依赖锁，保留 26.1。补齐文件/Profile 可撤销删除、目录入口、紧凑邀请通知、游戏内好友管理和未启动游戏时的自动加入。共享清单覆盖玩法 JAR、ZIP 材质包/光影包及启用选择；两加载器均完成真实双客户端首次下载、启用与零字节重入。名字徽章按可选原生通道识别，不给原版玩家加图标；未修改的 Mojang 客户端分别通过普通 LAN 加入两种加载器的 Unbox 世界。Node 40 / Rust 35 / 两加载器游戏 UI 各 130 项通过。真实微软双账号与异地/Windows 游戏测试未覆盖。完整证据与限制见 `docs/25-邀请与26.1.2共享环境.md`。本机 Cloudflare MCP 已部署房间版本支持；本轮尚未发布 GitHub Release。

## 2026-10-13 · 0.2.7 已公开发布

用户授权后已推送源码 main 并发布 GitHub v0.2.7/latest，包含 macOS ARM64、Windows x64 安装/更新包及四份原生游戏模块。Node 40 / macOS Rust 35 / Windows Rust 37 项通过；两平台公开匿名下载、哈希、现有公钥签名和篡改拒绝检查通过。测试者可通过 Settings → Updates 获取；发布记录和实测边界见 `docs/26-0.2.7测试更新发布.md`。这覆盖前一条“本轮尚未发布”的历史状态。

## 2026-10-14 生存功能与悬浮 UI 本地测试版

本轮范围、实际游戏验证和未验证场景见 [生存客户端开发验收](docs/29-生存客户端开发验收.md)。支持 26.1 / 26.1.2 的 Fabric 与原生 NeoForge；本轮只打包 macOS 本地调试应用。

## 2026-10-14 用户修订

启动器原 News/Profile 布局恢复、主页皮肤头像快捷切换，以及 Freecam/本地昵称与 Unbox 标志修复见 `docs/30-启动器还原与Freecam昵称修复.md`。共享世界是底层数据目录，不改变 Profile 创建方式。

## 2026-10-15 · 生存模组源码接入（尚未构建/测试）

按用户上课期间不启动、不测试的要求，本轮仅修改源码、依赖锁、设置和文档。ItemPhysic Lite、IPN、VeinMiner/Hotkey、FallingTree 接入原生安装及 Right Shift 设置；自研持久地形地图、石头墓碑与房主状态同步。NeoForge 26.1 因上游真实依赖限制不提供 IPN，其他目标已锁定对应版本。详情、接口静态核对和待验收清单见 [生存模组集成与地图墓碑](docs/31-生存模组集成与地图墓碑.md)。没有启动游戏/启动器/预览、没有运行编译或测试、没有更换现用应用、没有发布更新；历史实测结果不覆盖本轮新实现。

## 2026-10-15 · 用户授权后的生存模组实测与修复

用户下课后授权测试，已完成四目标构建与隔离游戏验收、双客户端同步/普通服务器拒绝、两个 JVM 的墓碑保存重启取回。修复地图头像纹理路径、断线清理线程、世界身份包与死亡点时序，排除会使旧 NeoForge 26.1 启动崩溃的 VeinMiner，并保护已有用户文件。前端 42 项、Rust 59 项、Right Shift UI 140 项检查通过；NeoForge 26.1 明确不提供 IPN/VeinMiner。已构建 macOS 本地调试应用，未发布更新。详细证据、截图、版本矩阵和实测边界见 [生存模组实测与修复](docs/32-生存模组实测与修复.md)。


## 2026-10-16 图标、挖矿按键与地图优化

已生成本地调试包，细节及实测截图见 [地图与生存交互优化](docs/33-地图与生存交互优化.md)。保留原图标造型并提高纹理清晰度，统一挖矿/砍树图标；删除 Smooth Step；VeinMiner 改用原生切换按键；地图采用世界区域纹理、逐帧定位、方块更新优先采样和地形明暗。ItemPhysic Lite 版本、配置与运行逻辑保持不变。

四组版本/加载器实际游戏检查通过（19/19/18/19），游戏 UI 139 项、启动器 42 项、原生切换键四组及地图缓存两项通过；测试产物哈希与本地应用一致。未发布新 Release；打开新包前需保存退出游戏并完全退出旧启动器。

## 2026-10-16 墓碑模型与 Freecam 飞行

墓碑改为石质底座、竖碑及刻痕，支持直接选中碑身、旧墓碑转换、模型持久化和回收清理。Freecam 改用独立 LocalPlayer 的原生飞行输入/移动，支持观察者惯性、冲刺及滚轮调速，保留本体与原视角恢复。四目标实际视觉/交互回归各 74 项通过；墓碑另做两个 JVM 保存/重启/挖掘取回验证，启动器 42 项测试通过。实现、截图与验证边界见 [墓碑模型与原版飞行修复](docs/34-墓碑模型与原版飞行修复.md)。已更新本地 macOS 调试包，未发布新 Release。

## 2026-10-16 简洁 R.I.P 墓碑修订

按后续要求删除墓碑底座与碑顶，改为宽、高各一格、厚四分之一格的石头纹理矩形，两面固定显示 R.I.P。选区、碰撞和石头挖掘速度同步调整，两代旧模型保留内容自动转换。四目标构建、Fabric 26.1 / NeoForge 26.1.2 双 JVM 保存重启回收检查通过；本地 macOS 调试包已更新，未发布。详见 [简洁 R.I.P 墓碑](docs/35-简洁RIP墓碑.md)。

同轮追加草地生成修复：允许替换脚边的短草/高草和蕨，保持泥土、石头、箱子与液体保护。旧逻辑回归失败已复现，修复后 Fabric 26.1 / NeoForge 26.1.2 完成植物定位与双 JVM 保存回收检查；本地测试包再次更新，实机截图见上述文档。

## 2026-10-16 Minecraft 26.3 原生适配

新增 26.3 Fabric/NeoForge，保留 26.1/26.1.2，共六个原生构建。两种 26.3 加载器完成同机双客户端首次模组/材质/光影同步、重启进入、实际玩法使用及零字节重连。生存各 23 项、视觉各 74 项、主菜单各 25 项、Fabric UI 139 项通过；旧版重新编译及 26.1 生存 23 项回归通过。Rust 65 / Node 43 / 按键迁移 13 项通过。本地 macOS 调试应用已更新并验证六份模块哈希；线上好友 Worker 仅更新 26.3 版本允许列表，未发布 GitHub Release，未进行 Windows/异地实玩。依赖兼容修复、完整证据和边界见 [26.3 原生适配与联机同步](docs/36-26.3原生适配与联机同步.md)。

## 2026-10-17 0.2.8 已公开发布

用户授权后已推送源码 main 并发布 GitHub v0.2.8/latest，包含本轮生存客户端修订及 26.3 支持。macOS ARM64 / Windows x64 更新包、六份原生模块与原公钥签名均已上传。Node 43 / macOS Rust 65 / Windows Rust 65 项通过；最终两种 26.3 模块标题页各 25 项通过。两平台包内容、公开匿名下载、哈希、签名及篡改拒绝均完成验证。详情与实测边界见 [0.2.8 测试更新发布](docs/37-0.2.8测试更新发布.md)。

## 2026-10-18 全版本模组与联机优化（本地测试包）

26.1 / 26.1.2 / 26.3 × Fabric / NeoForge 原生集成九项新功能及对应 Right Shift 设置；补全旧版 NeoForge 模组缺口。建筑与同材质形态切换仅创造模式，房主玩法开关按世界保存并同步。兼容环境复用当前游戏进程，增加加入准备/取消/重试，并修复加入计时竞态和内嵌 NeoForge 依赖扫描。标题显示运行时版本，移除重复按钮提示。

六组实际游戏各 47 项通过；旧版 NeoForge 生存回归 23 项，两种加载器的完整邀请同步与零下载、不重启重连通过。Node 43 / Rust 67 项通过。移植源码、许可和构建说明随包分发，Settings → About 可打开。已更新本地 macOS 调试应用并核对内嵌资源哈希，未发布新 Release，未做 Windows 游戏实测。完整范围、操作与证据见 [全版本模组集成与联机优化](docs/40-全版本模组集成与联机优化.md)。

## 2026-10-18 Spatial GUI 联动、形态选框与乐魂召唤

Spatial GUI 改成双向配置同步，增加人称选项和原生高级设置入口，返回时同步刷新控件。同材质切换增加原版风格浮动选框，按住预览、滚轮选择、松键提交。Happy Ghast 增加按钮/默认未绑定快捷键召唤，房主限制边长最多 15 格的立方体范围，调用原生飞行控制；到达后默认等候 4 秒，可设 1–30 秒，交互/骑乘/超时后恢复原生行为。

六种组合各通过 68 项实际游戏检查，覆盖真实第一人称渲染、两端配置往返、物品松键提交、服务器范围限制、乐魂飞行与四秒等待。更新本地 macOS 调试应用，未发布 GitHub 更新，未做 Windows 实玩。截图和联机验证见 [本轮修复说明](docs/41-Spatial设置与形态选择和乐魂召唤.md)。

## 2026-10-19 OptiFine 披风修复（未运行测试）

调整披风图片下载入口，增加失败重试和无披风结果过期，移除 64 人查询上限并清理闲置/旧世界纹理；合并本地皮肤与披风注入，避免缓存旧披风和回调互相覆盖。保留官方披风优先规则，补充 PNG 校验、读取超时及失败日志。

26.1 / 26.1.2 / 26.3 的 Fabric / NeoForge 六份模块已重新编译并打入本地 macOS 调试应用。遵照用户要求，未运行测试、未启动游戏、未进行网络复测；不声称实际披风展示已验证。未发布 GitHub 更新。详见 `docs/42-OptiFine披风链路检查.md` 和 `.cache/cape-fix/` 构建日志。

## 2026-10-19 0.2.9 已公开发布

已发布 GitHub v0.2.9/latest，覆盖 macOS ARM64 / Windows x64，包含全版本模组、联机体验、Spatial GUI/形态选择/乐魂召唤、披风修复和已完成的代码清理。六份游戏模块与移植源码随包分发，使用原 updater 公钥。Windows 原生检查 67 项通过；两平台安装包内容、签名、12 个公开附件的匿名下载和哈希均已核对。披风实际显示仍未实测，未进行 Windows 游戏或异地网络验证。详见 [0.2.9 更新发布](docs/45-0.2.9更新发布.md)。
