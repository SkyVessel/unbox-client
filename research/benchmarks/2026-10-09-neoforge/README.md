# NeoForge 0.2.5 local verification

2026-10-09, macOS / Apple M3 Max / 36 GiB RAM. These are local development results, not Windows or public release certification.

The UI reports cover 116 checks per loader. Private-world reports use two local game processes and a local Cloudflare-compatible authentication fixture. The relay report deliberately removes LAN candidates and traverses the real public e4mc relay. The sync report transfers a host-only gameplay fixture, prepares a separate NeoForge profile, restarts the guest and verifies its actual item registry.

## Performance method

Both runs use NeoForge 26.1.0.19-beta / Minecraft 26.1, the same copied world, fixed camera, 2560×1440 framebuffer, render distance 12, simulation distance 8, no VSync, 30 s warmup and 45 s measurement. Sodium, Lithium, FerriteCore and ImmediatelyFast are installed. Dynamic FPS is deliberately excluded from this foreground measurement. FPS/CPS/coordinates/armor HUD remain enabled. `perf-map` additionally enables minimap and latency; `perf-off` disables both. Java heap maximum is 4 GiB. Only one benchmark game process runs at a time.

| Run | Average FPS | 1% low FPS | p95 frame ms | p99 frame ms |
| --- | ---: | ---: | ---: | ---: |
| Map/latency off | 648.465 | 133.235 | 3.726 | 4.639 |
| Map/latency on | 709.520 | 121.043 | 3.518 | 4.195 |

Both reports record zero inactive frames. 1% low is the reciprocal of the mean duration of the slowest 1% of frames, not the reciprocal of p99. One sequential pair cannot isolate map overhead: the on run has higher mean FPS and lower 1% low. Treat this as a smoke benchmark only, not evidence of a speedup, zero overhead, large-modpack performance or superiority over other clients. Moving terrain generation, long sessions and two remote home networks remain to be measured.

`build-audit.json` records module hashes and shared class comparison. Account fixtures, credentials and raw user screenshots are not included here.
