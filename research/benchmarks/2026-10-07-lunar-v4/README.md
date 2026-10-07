# v4 UI / HUD performance regression — 2026-10-07

Same Mac, Minecraft 26.1 with the pinned optimization stack, isolated copies of one saved world, 2560×1440 framebuffer, render distance 12, simulation distance 8, fixed camera, VSync off. FPS/CPS/coordinates/armor enabled, new Keystrokes disabled. DynamicFPS excluded. Each run warms up 30 seconds and measures 45 seconds. No concurrent game, build or browser test. All samples stayed foreground. The original save was not modified.

Order: old → new → new → old. Old is the saved v3 JAR; new is the final v4 JAR. Per-run build hashes and raw frame times are included.

| Run | Average FPS | 1% low FPS | p95 ms | p99 ms | Inactive frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| lunar-v4-perf-old | 339.637 | 138.802 | 6.306 | 6.620 | 0 |
| lunar-v4-perf-new | 335.100 | 130.813 | 6.338 | 6.698 | 0 |
| lunar-v4-perf-new-repeat | 332.815 | 133.483 | 6.296 | 6.676 | 0 |
| lunar-v4-perf-old-repeat | 340.728 | 138.478 | 6.358 | 6.683 | 0 |

The new build measures 333–335 FPS versus 340–341 for v3, about 1.3–2.3% lower. The new 1% low is 131–133 versus 138–139, about 3.6–5.8% lower. This is a small observed regression, not a performance win. Reversing run order did not remove it. Causes have not been isolated; hardware state, world scheduling and changed HUD layout work remain possible contributors. Treat frame pacing as unresolved.

These closed-menu static-scene measurements do not establish performance in large modpacks, chunk traversal, particle-heavy combat, other platforms or multiplayer. No competitor was benchmarked. Historical v3 test results differed from today's v3 rerun; cross-day numbers are not interchangeable.
