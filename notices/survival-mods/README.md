# Native client and survival integrations

These are independently loaded, unmodified upstream mods. The launcher's exact native-loader and game-version selections, recursive required dependencies, Modrinth version IDs, download URLs, sizes, and SHA-1/SHA-512 hashes are recorded in `src-tauri/resources/survival-mods-lock.json`. Optional dependencies are not installed unless explicitly selected as a feature (VeinMiner Hotkey). Fabric API remains supplied by the existing performance lock with the same version and hashes.

| Project | Published license | Source | Distribution |
| --- | --- | --- | --- |
| KotlinLangForge | `GPL-3.0-only` | [Source](https://github.com/btwonion/KotlinLangForge) | [Official downloads](https://modrinth.com/mod/kotlin-lang-forge/versions) |
| FallingTree | `LGPL-3.0-only` | [Source](https://github.com/RakambdaOrg/FallingTree) | [Official downloads](https://modrinth.com/mod/fallingtree/versions) |
| Fabric Language Kotlin | `Apache-2.0` | [Source](https://github.com/FabricMC/fabric-language-kotlin/) | [Official downloads](https://modrinth.com/mod/fabric-language-kotlin/versions) |
| Inventory Profiles Next | `AGPL-3.0-or-later` | [Source](https://codeberg.org/Inventory-Profiles-Next-Mod/IPN) | [Official downloads](https://modrinth.com/mod/inventory-profiles-next/versions) |
| VeinMiner | `AGPL-3.0-only` | [Source](https://github.com/MiraculixxT/Veinminer) | [Official downloads](https://modrinth.com/mod/veinminer/versions) |
| CreativeCore | `LGPL-3.0-only` | [Source](https://github.com/CreativeMD/CreativeCore) | [Official downloads](https://modrinth.com/mod/creativecore/versions) |
| ItemPhysic Lite | `LGPL-2.1-only` | [Source](https://github.com/CreativeMD/ItemPhysicLite) | [Official downloads](https://modrinth.com/mod/itemphysic-lite/versions) |
| Fabric API | `Apache-2.0` | [Source](https://github.com/FabricMC/fabric-api) | [Official downloads](https://modrinth.com/mod/fabric-api/versions) |
| VeinMiner Hotkey | `LGPL-3.0-only` | [Source](https://github.com/MiraculixxT/Veinminer) | [Official downloads](https://modrinth.com/mod/veinminer-client/versions) |
| libIPN | `AGPL-3.0-or-later` | [Source](https://github.com/blackd/libIPN) | [Official downloads](https://modrinth.com/mod/libipn/versions) |

License texts are retained in this directory. Upstream JARs keep their embedded notices, including nested dependencies. No upstream JAR is modified, merged into Unbox's own JAR, or checked into the repository. Development downloads stay in `.cache/survival-mods`; normal launcher preparation downloads directly from the pinned official Modrinth CDN URLs.

## Corresponding source and redistribution

The source links above identify the upstream repositories; they are not a written source offer. Before distributing or mirroring GPL/AGPL/LGPL binaries, archive the complete corresponding source for each **exact pinned release**, its build/install instructions and required scripts, and provide equivalent source access alongside the binary download as required by that release's license. A link to a moving default branch is not an exact release source archive. Preserve attribution, license, copyright and modification notices. If Unbox makes covered modifications or combines covered code, review the obligations applicable to that combined work; do not claim independent loading automatically resolves them. AGPL's network-source condition also applies to covered modified versions where required. Current integration does not assert that these future binary-release obligations have been completed.

`LGPL-2.1.txt`, `LGPL-3.0.txt`, `GPL-3.0.txt`, `AGPL-3.0.txt`, and `Apache-2.0.txt` were retrieved from GNU and Apache official license endpoints on 2026-10-15. Project-specific notices are also retained under `upstream/` when present in the downloaded artifacts.

## Behavior boundary

ItemPhysic Lite and Inventory Profiles Next are personal client features. VeinMiner and FallingTree are host gameplay rules; the Unbox bridge keeps them disabled until the local world owner enables them. Installing a client hotkey does not grant permission or enable those rules on an unknown dedicated server. The initial integration was source-only. Subsequent authorized native builds and isolated gameplay tests are recorded in `docs/32-生存模组实测与修复.md`; the NeoForge 26.1 VeinMiner exclusion below follows a real startup failure.

## NeoForge 26.1 inventory exclusion

Inventory Profiles Next / libIPN are intentionally **not installed on NeoForge 26.1**. Although Modrinth marks their releases compatible, inspected JAR metadata disagrees: IPN 2.3.8 requires NeoForge 26.1.2.94 or later; libIPN 6.9.0 requires Minecraft `[26.1.2,26.2)`. The oldest listed candidate, libIPN 6.7.2, also requires Minecraft `[26.1.2,26.2)`. No upstream metadata is patched to bypass this boundary. The lock records this target's unavailable inventory feature and rejected release IDs. IPN 2.3.8 with libIPN 6.9.0 remains selected for both Fabric targets and NeoForge 26.1.2 (whose launcher pin is NeoForge 26.1.2.116).

VeinMiner and VeinMiner Hotkey are also **not installed on NeoForge 26.1 with the pinned NeoForge 26.1.0.19-beta**. A real startup failed because VeinMiner 2.10.4 links `net.neoforged.neoforge.event.level.block.BreakBlockEvent`; this runtime only supplies `BlockEvent$BreakEvent`. Bytecode inspection of every official NeoForge release tagged for Minecraft 26.1 (2.8.0, 2.8.1, 2.8.2, 2.9.0, 2.10.0, 2.10.1, 2.10.4) found the same incompatible event reference. The lock records their rejected IDs. Its unused KotlinLangForge dependency is omitted too. No upstream binaries or loader metadata are patched. NeoForge 26.1.2 keeps its working VeinMiner/Hotkey selection. FallingTree 26.1-25 refers to the existing `BlockEvent$BreakEvent` API; a subsequent isolated NeoForge 26.1 run also passed actual enabled/disabled tree-breaking checks.
