# Unbox Client — implementation status

Updated 2026-10-07. This is a local development build, not a finished public release.

## Verified

- Native macOS Tauri application builds and opens, with a rounded application icon.
- Four-step profile creation validates the name, retains drafts on Back, offers exact Minecraft 26.1, and persists only after final confirmation.
- Fabric and Vanilla profile paths exist. Fabric 0.19.5 with Java 25 was launched successfully on this Mac. Logs confirm Unbox, Sodium, Lithium, ImmediatelyFast, EntityCulling, FerriteCore and DynamicFPS initialization. Vanilla has not received the same end-to-end launch verification.
- Official game assets and pinned mods are downloaded with checksum checks. Profiles have separate directories. Existing unrelated mod files are preserved.
- Local account selection, module preferences and launcher settings persist. Local identities do not authenticate to Microsoft or online-mode servers.
- Home layout follows viewport size, including live resizing from 1280×800 to 1920×1080 and 3840×2160 and back. Side rail and margins use bounded responsive values; the launch area takes approximately 43% of the inner panel. News and content lists use the remaining area. The compact 1000×720 layout is also tested.
- Nine Node/Playwright tests cover profile flow, persistence, responsive layout, reduced motion and the undeployed cloud API's validation, plus mocked Microsoft sign-in/cancellation and account-skin UI. Ten Rust unit tests cover profile paths, launch argument rules, offline UUIDs, safe managed-mod upgrades and separation of client files from gameplay mods, plus sanitized authentication errors and skin-origin restrictions.

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

- Cloudflare CLI reports unauthenticated. No cloud resources were deployed and no paid plan was enabled. The `cloudflare` directory contains a bounded manifest API draft, not a functioning friends, identity or relay service. User login is required before deployment.
- Microsoft device authorization, Xbox/XSTS, Minecraft ownership/profile checks, Keychain storage, token refresh and launch credentials are implemented. A registered Unbox Client ID and Minecraft Services approval are still missing, so real-account end-to-end sign-in remains unverified. No borrowed third-party client ID is used.
- Forge and NeoForge are explicitly marked integration pending; this does not mean upstream 26.1 releases are unavailable.
- Authenticated account skin retrieval, face avatars and Classic/Slim 3D preview are implemented and covered with a simulated account fixture; real account verification awaits app registration. Skin uploads, cape selection, infinite thumbnail lists, friends, one-click multiplayer and shared environment synchronization remain unfinished. Local accounts retain a labelled sample preview.
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
