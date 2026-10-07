# Shift v3 performance evidence — 2026-10-06

Same Mac, isolated copies of the same saved world, Minecraft 26.1 + the pinned optimization stack, 2560×1440 framebuffer, render distance 12, simulation distance 8, fixed camera, VSync off, FPS/CPS/coordinates/armor enabled. DynamicFPS is excluded for foreground benchmarking. Each run warms up 30 seconds and records 45 seconds; no other game runs concurrently. The source world is never modified. Renderer iterations were not benchmarked concurrently with builds or browser tests.

| Version | Average FPS | 1% low FPS | p95 ms | p99 ms | Inactive frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| Existing v2, first run | 231.1 | 110.3 | 7.707 | 8.256 | 0 |
| Existing v2, repeat | 222.4 | 109.3 | 7.933 | 8.302 | 0 |
| Intermediate per-glyph atlas | 218.6 | 111.1 | 7.965 | 8.320 | 0 |
| Rejected native TTF experiment | 231.5 | 87.8 | 9.213 | 10.528 | 0 |
| Batched smooth atlas | 311.4 | 91.9 | 9.955 | 10.389 | 0 |
| Final packaged version | 307.7 | 91.2 | 9.869 | 10.352 | 0 |

The two smooth batched-atlas runs average approximately 308–311 FPS, versus 222–231 for the previous UI. However, 1% low falls from 109–110 to 91–92, with p99 increasing to about 10.4 ms. This is a real unresolved frame-pacing regression in this test, not a comprehensive performance win. The test cannot establish the renderer as the sole cause; world simulation, runtime scheduling and thermal state are not fully controlled. Do not claim superiority over OneClient, Lunar, Feather or vanilla from these runs. A closed-panel static scene does not substitute for multiplayer, chunk-traversal or large-modpack testing.

Average FPS is total frames / measured time. 1% low is 1000 / the mean duration of the slowest 1% of frames. Raw frame times, settings and JAR hashes are retained per run. Experimental runs are included so selection of the final renderer remains reviewable. They are not shipped.

The final application contains the JAR hash recorded by `shift-v3-release-performance/client-build.json`; the same JAR passed the 60-assertion game UI regression in `design/exports/in-game-v3/ui-smoke.json`.
