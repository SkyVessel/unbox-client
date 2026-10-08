# Shared-mod sync performance recheck, 2026-10-08

Same Mac, pinned optimized Fabric 26.1 stack, 2560×1440 framebuffer, render distance 12, simulation distance 8, VSync off, fixed camera, isolated copies of the same world, 30-second warmup + 45-second capture. DynamicFPS excluded consistently. No concurrent game tests or builds during the four performance runs. This is normal play after initialization, **not physical mouse input or active file-transfer performance**.

Order: old → new → new → old. Build hashes and raw samples are retained per run.

| Run | Average FPS | 1% low FPS | p99 ms | Inactive frames |
|---|---:|---:|---:|---:|
| sharing-perf-before | 557.1 | 155.9 | 5.777 | 0 |
| sharing-perf-after | 549.3 | 149.4 | 6.056 | 0 |
| sharing-perf-after-repeat | 580.2 | 158.1 | 5.634 | 0 |
| sharing-perf-before-repeat | 590.1 | 162.5 | 5.562 | 0 |

The new measurements are roughly 1.4–1.7% lower in average FPS and 2.7–4.1% lower in 1% low than the adjacent old runs. Variation between repetitions is larger than the mean gap, but these data do not demonstrate a performance improvement or establish that synchronization has no overhead. No cause has been isolated. Avoid interpreting two repetitions as statistical equivalence. The reported hardware-mouse stutter was not reproduced or ruled out by this fixed-camera test; no input smoothing/filter was added.

After these measurements, transfer cancellation/admission guards and test fixtures changed; rendering, HUD and mouse paths did not. Game integration was retested with the final bundled JAR; do not claim these captures are a fresh benchmark of every later byte-identical build.
