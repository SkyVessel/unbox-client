# Unbox Client

**A Minecraft Java client focused on survival quality-of-life mods, performance optimization, and easier multiplayer with friends.**

Unbox Client combines a desktop launcher with an in-game Fabric client module for **Minecraft Java 26.1**. The goal is to make survival play smoother and help friends share a consistent modded setup without manually matching every file. The interface is English; product and research documents are Chinese.

**Status: early testing release, under active development.** Local launcher and client-mod functionality are implemented. Friend accounts and invitations are deployed on Cloudflare Free. LAN-first joining with e4mc fallback passed a two-instance test on one Mac; Fabric gameplay-JAR synchronization now caches differences in separate friend profiles. Independent-network testing and full modpack/configuration synchronization remain outstanding. See [implementation status](IMPLEMENTATION.md) for verification and limitations. No FPS advantage over other clients is claimed.

## Focus

- **Survival quality of life:** armor durability, coordinates and direction, configurable HUDs, zoom, auto sprint, chat controls, crosshair customization, and particle settings.
- **Performance:** a pinned optimization-mod stack, including Sodium, Lithium, ImmediatelyFast, EntityCulling, FerriteCore, and DynamicFPS, with repeatable frame-time measurements. Frame pacing still needs improvement.
- **Simple multiplayer (in development):** invite friends, prepare matching gameplay mods, and join with fewer setup steps while keeping personal HUD and visual preferences separate.
- **An integrated client:** profile management, a branded game menu, and a compact Right Shift panel with per-mod settings and draggable HUDs.

## Microsoft / Minecraft account integration

Unbox is an independent third-party project and is not affiliated with or endorsed by Mojang or Microsoft.

Microsoft sign-in is intended to let players authenticate their own Minecraft Java accounts, retrieve their player profile and skin, and launch the game with valid session credentials. Passwords are entered on Microsoft's authorization page, not collected by Unbox. The implemented flow uses Microsoft device authorization, Xbox Live/XSTS, and Minecraft Services; account tokens are stored in macOS Keychain.

Microsoft sign-in uses Unbox's built-in public application ID in both debug and release builds. Players do not enter a Client ID or Client Secret. Previous DevLogin accounts require fresh Microsoft authorization; they remain saved until the same Minecraft identity signs in successfully through Unbox. Tokens stay in OS-protected storage. Microsoft's device-code endpoint accepts the registered ID, but a real-account end-to-end login, Minecraft Services access and restart/renewal still require verification with the new authorization. See [Unbox Microsoft application integration](docs/22-Unbox微软应用接入.md).

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
- `cloudflare`: deployed friends/invite Worker and D1 schema, plus a separate undeployed manifest API draft.
- `design`: design records and tokens. Local Pen files, exports and third-party reference media are excluded from the public repository.
- `research/benchmarks`: recorded performance measurements; local visual exports referenced by older reports are not published here.

## Assets

The open-box logo reuses the project's supplied FORM mark. Three scenic images were generated for this project; they are not game screenshots. Barlow fonts include their OFL notice under `app/public/assets`. See [third-party asset notices](THIRD_PARTY_NOTICES.md) for asset origins and retained notices. Third-party assets retain their respective rights; this client does not bundle Minecraft game binaries. Collected competitor screenshots, wallpaper references, and local account data are excluded.

## In-game controls

Press Right Shift to open the compact Unbox quick menu, then choose Mods. The game also opens with an Unbox-branded title screen. Each card has its own settings and enable switch. Drag enabled, unlocked HUDs directly on the quick menu or Mods screen. Use Edit HUD for resizing, snapping and fine positioning; Done saves, Cancel restores the previous layout. Settings support live previews, cross-tab search, exact numeric entry and per-setting reset. See [the latest research and verification record](docs/10-Lunar参考对照与游戏内UI升级.md) for module settings, actual game screenshots, and measured performance boundaries.

### Friends and direct content import

Drop `.jar` mods or `.zip` packs into the selected profile list; the borderless `+` opens its folder. The friends/room service is in `cloudflare/social.mjs`, with LAN-first and on-demand [e4mc](https://e4mc.link) transport in the Fabric module. The friends service is deployed on Workers Free with D1 and bundled in the macOS app; live API checks passed, while two real accounts joining across networks remain unverified. Current private world invites require Microsoft or Unbox accounts and running Unbox Fabric profiles. Fabric gameplay JARs synchronize into separate cached friend profiles; complete modpack configuration and independent-network validation remain outstanding. See [implementation and deployment notes](docs/12-文件导入与好友联机.md). Only free Workers/D1 plans are authorized; game traffic does not pass through Cloudflare.

Email/password Unbox registration, editable non-unique nicknames and mixed-identity private worlds are implemented. Email verification is deferred during development; production rollout remains pending. See [account implementation and verification](docs/13-Unbox邮箱账号与混合联机.md).

## Downloads and updates

[Download Unbox Client](https://github.com/SkyVessel/unbox-client/releases/latest) for **macOS Apple Silicon (arm64)** and **Windows x64**. Existing testers can use Settings → Updates. On macOS, extract the app archive and move Unbox Client to Applications; on Windows, run the setup executable. Testing builds have updater signatures, but no Apple notarization or Windows publisher certificate. Intel Macs and Linux packages are not included.

Use the borderless download icon at the bottom left, or **Settings → Updates → Check for updates**. Installation is manual and waits until Minecraft is closed. Profiles and worlds remain in the app data directory. The current source embeds Unbox's registered Microsoft Client ID; older published builds retain their original login configuration.
