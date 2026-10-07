# Unbox Client

**A Minecraft Java client focused on survival quality-of-life mods, performance optimization, and easier multiplayer with friends.**

Unbox Client combines a desktop launcher with an in-game Fabric client module for **Minecraft Java 26.1**. The goal is to make survival play smoother and help friends share a consistent modded setup without manually matching every file. The interface is English; product and research documents are Chinese.

**Status: active development, not a finished release.** Local launcher and client-mod functionality are implemented. One-click multiplayer and shared modpack synchronization are planned and not yet operational. See [implementation status](IMPLEMENTATION.md) for verification and limitations. No FPS advantage over other clients is claimed.

## Focus

- **Survival quality of life:** armor durability, coordinates and direction, configurable HUDs, zoom, auto sprint, chat controls, crosshair customization, and particle settings.
- **Performance:** a pinned optimization-mod stack, including Sodium, Lithium, ImmediatelyFast, EntityCulling, FerriteCore, and DynamicFPS, with repeatable frame-time measurements. Frame pacing still needs improvement.
- **Simple multiplayer (in development):** invite friends, prepare matching gameplay mods, and join with fewer setup steps while keeping personal HUD and visual preferences separate.
- **An integrated client:** profile management, a branded game menu, and a compact Right Shift panel with per-mod settings and draggable HUDs.

## Microsoft / Minecraft account integration

Unbox is an independent third-party project and is not affiliated with or endorsed by Mojang or Microsoft.

Microsoft sign-in is intended to let players authenticate their own Minecraft Java accounts, retrieve their player profile and skin, and launch the game with valid session credentials. Passwords are entered on Microsoft's authorization page, not collected by Unbox. The implemented flow uses Microsoft device authorization, Xbox Live/XSTS, and Minecraft Services; account tokens are stored in macOS Keychain.

Unbox's application registration and Minecraft Services access are pending. Real-account end-to-end sign-in has not yet been verified. The project does not ship another launcher's Client ID. Application registration is a developer setup task; players should only need to sign in and authorize the application. See [account setup notes](docs/09-OneClient设置对照与Shift升级.md).

## Development

Requirements: Node.js/npm, Rust and the Tauri macOS toolchain. Running Minecraft and rebuilding the game module require Java 25. The current module build also requires locally cached compile dependencies in `.cache`; a clean-machine bootstrap is not provided yet.

```sh
npm ci
npm run dev          # Browser UI preview; cannot launch Minecraft
npm run desktop      # Native development application
npm test             # UI and cloud validation tests; requires Playwright Chromium
cargo test --manifest-path src-tauri/Cargo.toml
sh scripts/build-mod.sh
npm run tauri build -- --debug
```

The macOS bundle is written to `src-tauri/target/debug/bundle/macos/Unbox Client.app`. Build `src-tauri/resources/unbox-client.jar` before native packaging; generated JARs and game dependencies are not committed. Rebuild it after Java source changes. This repository does not distribute Minecraft game binaries.

## Source map

- `app/src`: React interface and native bridge.
- `src-tauri/src`: profile persistence, verified downloads and game process management.
- `client-mod`: Fabric client module sources for 26.1.
- `src-tauri/resources/performance-lock.json`: exact upstream mod versions, download URLs and checksums.
- `cloudflare`: undeployed manifest API draft; see its README for authentication limitations.
- `design`: design records and tokens. Local Pen files, exports and third-party reference media are excluded from the public repository.
- `research/benchmarks`: recorded performance measurements; local visual exports referenced by older reports are not published here.

## Assets

The open-box logo reuses the project's supplied FORM mark. Three scenic images were generated for this project; they are not game screenshots. Barlow fonts include their OFL notice under `app/public/assets`. See [third-party asset notices](THIRD_PARTY_NOTICES.md) for asset origins and retained notices. Third-party assets retain their respective rights; a public source repository is not a cleared binary release. Collected competitor screenshots, wallpaper references, and local account data are excluded.

## In-game controls

Press Right Shift to open the compact Unbox quick menu, then choose Mods. The game also opens with an Unbox-branded title screen. Each card has its own settings and enable switch. Drag enabled, unlocked HUDs directly on the quick menu or Mods screen. Use Edit HUD for resizing, snapping and fine positioning; Done saves, Cancel restores the previous layout. Settings support live previews, cross-tab search, exact numeric entry and per-setting reset. See [the latest research and verification record](docs/10-Lunar参考对照与游戏内UI升级.md) for module settings, actual game screenshots, and measured performance boundaries.
