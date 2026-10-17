# Third-party asset notices

This development repository contains project-created assets and the following third-party materials. Their respective licenses and trademark rights remain in effect. A general license for Unbox's original code has not yet been selected; publication does not relicense third-party works.

- **Barlow / Barlow Condensed:** font files in `app/public/assets` and `client-mod/src/main/resources/assets/unbox/font`, under the SIL Open Font License. Notices are included beside the fonts.
- **Lucide:** interface icons and raster derivatives, under the ISC license. The notice is retained in `client-mod/src/main/resources/lucide-license.txt`.
- **Prism Launcher instance icons:** block thumbnails and Fabric/NeoForge marks are raster derivatives of the [upstream instance SVGs](https://github.com/PrismLauncher/PrismLauncher/tree/develop/launcher/resources/multimc/scalable/instances). Original SVGs and the upstream COPYING notice are retained in `notices/prism-icons`. Applicable per-file terms must be reviewed before binary distribution.
- **Forge mark:** sourced from the [MinecraftForge Installer logo](https://github.com/MinecraftForge/Installer/blob/master/src/main/resources/big_logo.png). It identifies a loader; no affiliation is implied.
- **Sample player assets:** the placeholder player and face originate from [skinview3d example assets](https://github.com/bs-community/skinview3d/tree/master/examples/public/img). They are demonstration imagery, not an authenticated user's skin. Replacement or rights clearance remains necessary for a production release.
- **Open-box logo:** supplied by the project owner as the FORM mark and reused for Unbox.
- **Scenery:** `valley.png`, `lake.png`, and `coast.png` were generated for this project; they are not captured Minecraft gameplay.

The performance manifest records upstream download URLs and hashes. It does not include third-party mod JARs. Minecraft binaries, collected competitor screenshots, and wallpaper reference collections are not distributed in this repository.

- **e4mc 6.2.3 (Fabric modern):** on-demand free world relay integration, [upstream source](https://github.com/vgskye/e4mc-minecraft-architectury), MIT. The original notice is retained in `notices/e4mc/LICENSE`. Its pinned binary is downloaded from Modrinth and includes its own nested-dependency notices. Unbox does not operate or guarantee the public e4mc service.

- **Native client/survival integrations:** ItemPhysic Lite, CreativeCore, Inventory Profiles Next, libIPN, Fabric Language Kotlin / Kotlin Lang Forge, Miraculixx VeinMiner and Hotkey, and FallingTree. Exact versions and checksums are pinned separately in `src-tauri/resources/survival-mods-lock.json`; licenses, source links and redistribution/source-access requirements are recorded in [notices/survival-mods/README.md](notices/survival-mods/README.md). They remain separate, unmodified upstream JARs fetched from the official distribution service.


## Minecraft 26.3 dependency selection (2026-10-16)

The 26.3 native dependencies are pinned in `src-tauri/resources/performance-lock-26.3.json`, `neoforge-performance-lock-26.3.json`, `survival-mods-lock.json`, and `wick-lock.json`. Each loader uses its own upstream artifact. Downloaded Modrinth JARs were checked against upstream SHA-1/SHA-512 digests and their embedded loader/game constraints; Wick digests were recorded from its official CurseForge downloads. Existing versions remain pinned separately.

- **Sodium:** Fabric keeps `0.9.2+mc26.3` (`bAZQdGpg`); NeoForge uses `0.9.3-beta.1+mc26.3` (`SS023RVV`). The upstream project publishes under [PolyForm Shield 1.0.0](https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md). Sources: [CaffeineMC/Sodium](https://github.com/CaffeineMC/sodium); binaries are fetched from the official Modrinth CDN, unmodified.
- **Iris:** `1.11.7+mc26.3`, separately built for Fabric (`vTN4NRGW`) and NeoForge (`Ag5S66CF`), LGPL-3.0-only; [upstream source](https://github.com/IrisShaders/Iris).
- **Wick:** `1.0.0+mc26.3`, MIT; [upstream source](https://github.com/comedy01/Wick), [Fabric file 9069972](https://www.curseforge.com/minecraft/mc-mods/wick-lights/files/9069972), [NeoForge file 9070071](https://www.curseforge.com/minecraft/mc-mods/wick-lights/files/9070071).
- **Native survival mods:** both 26.3 targets add ItemPhysic Lite `1.6.13`, Inventory Profiles Next `2.3.8`, libIPN `6.9.0`, VeinMiner/Hotkey `2.12.3`, and FallingTree `26.3.0.3`. CreativeCore is `2.14.19` on Fabric and `2.14.21` on NeoForge. Fabric API `0.163.0+26.3`, Fabric Language Kotlin `1.14.2+kotlin.2.4.21`, and KotlinLangForge `2.15.0-k2.4.21-3.1+neoforge` supply the respective native dependencies. Their licenses and source-access obligations remain recorded in [the survival notices](notices/survival-mods/README.md).

### NeoForge Sodium/Iris compatibility resolution

Iris's Modrinth metadata for `Ag5S66CF` identifies Sodium `VHtnT9Q4` (`0.9.2`) as its required version. That Sodium build fails on the pinned NeoForge `26.3.0.86`: its platform-specific `LevelExtractorMixin` targets a removed `Frustum` argument. Sodium `0.9.3-beta.1` incorporates the upstream [NeoForge compatibility fix, PR #3948](https://github.com/CaffeineMC/sodium/pull/3948), which removes that obsolete mixin after NeoForge `26.3.0.65-beta` changed the API.

Iris's embedded `neoforge.mods.toml` accepts Sodium `[0.6,)`. The local lock therefore resolves Iris's required Sodium dependency to the verified beta artifact `SS023RVV`, while retaining the upstream Iris binary. This is an explicit locally validated resolution of the upstream metadata disagreement. With Minecraft `26.3`, NeoForge `26.3.0.86`, Sodium `0.9.3-beta.1`, Iris `1.11.7`, and `earlyWindowControl=false` on this Mac, the complete performance group passed 25 home checks and exited with code 0. Evidence: `.cache/benchmarks/version263-neohome-02/logs/launch-sodium093beta-iris.log` and `home-test-conditions.json` in the same directory. This check covers startup/home behavior; shader-pack rendering and remote gameplay require their own verification.
