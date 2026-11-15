# Patcher · Unbox performance subset

This is a modified, limited selection from [Sk1erLLC/Patcher](https://github.com/Sk1erLLC/Patcher), not the complete upstream Patcher distribution. The source revision and exact nine source files are recorded in `UPSTREAM.json`; copied mixin bodies are unchanged.

The subset includes model batching, reduced BlockPos allocations, cached NBT strings, game-rule parsing, capability lookup removal of obsolete Twitch work from the frame loop and suppression of explicit full garbage collections during world switching. It deliberately omits Essential, upstream settings/social interfaces, duplicate entity culling and unrelated gameplay changes. Unbox supplies a minimal Forge entry point, a batching setting and the Mixin configuration/build script. RenderLib supplies Mixin at runtime.

Upstream authors retain attribution. See `LICENSE` (CC BY-NC-SA 4.0). This local test integration is not a public release or a grant of commercial redistribution rights. Preserve this license, source and modification notices with any permitted redistribution; do not describe the subset as the complete Patcher mod.

Build from the repository root:

```sh
python3 scripts/build-unpvp.py
python3 scripts/prepare-unpvp-performance.py
python3 scripts/build-unpvp-patcher.py
python3 scripts/prepare-unpvp-performance.py
```

The output contains `LICENSE-Patcher.txt` and `Patcher-UPSTREAM.json`. No Essential or OneConfig classes are bundled.
