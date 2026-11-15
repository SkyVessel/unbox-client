# Independent Forge 1.8.9 PvP ports

These are selected, modified upstream implementations, not the complete upstream mod binaries. They use Unbox settings and no OneConfig, Essential, Kotlin or external UI runtime. Exact repositories, immutable commits and licenses are in `UPSTREAM.json`; verbatim license files ship under `src/main/resources/META-INF/unbox-pvp/`.

## Changes from upstream

- DamageTint v3.3.0: preserve four `FloatBuffer` component hooks; Unbox RGB, alpha and fade settings; guard zero maximum hurt time; also update OptiFine's separate shader hurt-color uniform (optional hook when OptiFine is absent).
- OverflowParticles v1.0.2: retain spawn filtering, per-layer count and particle scale/color hooks. Apply fixed scale/color at spawn; avoid upstream per-particle tessellator flushes. Supports density reduction and per-type visibility, not the full upstream per-particle editor/multiplication suite.
- OverflowAnimations v2.2.5: selected 1.7 block-hit, bow/item positions, fishing rod and sway hooks. Remove upstream equip reset hooks so Antimations owns that interception point.
- Antimations: equip-reset and first-person use-action controls. Configuration only is replaced.
- NoScreenShake: separate camera bobbing from hand bobbing. A render-context flag handles OptiFine's additional hand-render method overload.
- VanillaHUD v2.2.12: public scoreboard drawing logic adapted in `client-mod/src/unpvp/java/dev/unbox/unpvp/ExtraHud.java`, with actual team-specific objective, hidden-entry filtering and the vanilla 15-entry limit. Numeric Tab latency/header/footer and hotbar transform wrappers are separate toggles. No upstream consecutive-score heuristic is included.
- PolyTime v1.0.2: local `World.getWorldTime` plus consistent sky/moon reads in `getCelestialAngle`, `getMoonPhase` and `getCurrentMoonPhaseFactor`; shared `WorldInfo` is never replaced and the integrated server remains authoritative.
- PolyBlur v1.0.2: separate phosphor shader group and original shader assets; disabled by default, released when disabled, in menus or after leaving the world; size follows physical framebuffer.
- CreativeMD ItemPhysicLite: model resting/spinning orientation backported to 1.8.9; vanilla item batching and model count retained. No server physics changes. The pinned branch called `1.12` actually contains newer MatrixStack sources; this is a source adaptation, not a claim that those sources compile unchanged on 1.8.9.

Viewmodel uses an original camera-space transform inside the vanilla first-person matrix scope, composed with the existing animation hooks. Item and separate empty-hand position, rotation and scale do not affect world/entity rendering. OverflowAnimations custom-position sources were studied as a reference; its full transform replacement and configuration runtime were not copied.

Unbox's hold/toggle zoom, sprint/sneak controls, look-back, local nickname, chat/history and resource-pack browser are native implementations. BehindYou was researched but no source was copied into this derivative.

## Native lobby packet fix

`ServerScoreboardMixin` ignores only team/objective removals whose entries are already absent in the client packet handler. Normal scoreboard updates remain unchanged. This is an original Unbox fix, separate from upstream PvP adaptations. `PvpProbe` covers absent, valid and repeated removals through the actual packet handler; `LobbyProbe` provides an isolated dense-player/label diagnostic. See `docs/99-unpvp服务器大厅帧率优化.md` for evidence and rejected font-rendering experiments. No font renderer or entity-visibility change ships in this fix.

## Model-only occlusion

The Patcher-derived `ModelOcclusion` uses nonblocking GL15 queries and invokes the original renderer for names when a fully occluded living model is skipped. Armor stands/holograms keep the existing handling. `ModelCullingOwnerMixin` prevents RenderLib from preemptively hiding names for models handled here. Camera matrix changes, entity movement, stale/unavailable results, shaders and spectator mode conservatively render normally. This is separate from the earlier scoreboard-only fix. Sources and attribution accompany the core/PvP JARs; see `docs/100-unpvp-AppleSilicon与模型遮挡优化.md` for tests and limits.

## Build

From the repository root, prepare the existing official Forge toolchain and pinned performance dependencies, then run:

```sh
python3 scripts/build-unpvp.py
python3 scripts/build-unpvp-pvp.py
python3 scripts/prepare-unpvp-performance.py
```

`build-unpvp-pvp.py` compiles with Java 8 bytecode, generates Mixin SRG mappings using the same Mixin runtime bundled with RenderLib, and produces `src-tauri/resources/unpvp-pvp.jar`. The launcher copies this separately from `unpvp.jar` and the Patcher performance subset. `required: true` ensures failed hooks are visible at startup, rather than silently presenting non-working controls.

All sources, modifications and license notices must accompany any future distribution as required by their respective licenses. This task builds local test artifacts only.

Unbox `LatencyMixin` observes standard statistics request/reply timing for the HUD. It never cancels or modifies packets; this is an approximate connection response measurement, not the server-supplied player-list latency. The implementation is original; the protocol approach is also used publicly by Skytils. See `docs/104-unpvp对战与延迟排查.md` for rate limits, ambiguity and verification boundaries.

Unbox Fullbright is an original client-lightmap implementation: fixed white lightmap while enabled, one texture upload per activation, and normal lightmap rebuilding on disable. It never changes gamma, world light values, spawning rules or server packets. It is exposed through the existing Unbox catalogue and may be overridden by shader-pack lighting. The public Patcher Fullbright implementation was reviewed as a reference; its world/chunk lighting suppression was not ported.

Optional connection diagnostics observe KeepAlive handler turnaround and selected entity packets' main-thread queue time. Reports are local and bounded; network callbacks perform no disk I/O. A vanilla loopback TCP fixture verifies reply handling while the game thread is stalled.
