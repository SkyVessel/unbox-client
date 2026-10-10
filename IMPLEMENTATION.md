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
