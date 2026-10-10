# Map performance check — 2026-10-08

Local macOS, Fabric Minecraft 26.1, same copied benchmark world, fixed camera, 2560×1440 framebuffer, render distance 12. Each run has 30 seconds warm-up and 45 seconds sampling; inactive frames are zero. Runs are sequential, not simultaneous.

| Sampler | Map | Average FPS | 1% low FPS | p99 frame time |
| --- | --- | ---: | ---: | ---: |
| Initial | Off | 766.208 | 251.204 | 3.721 ms |
| Initial | On | 743.821 | 209.049 | 3.886 ms |
| Bounded/reused samples | On | 763.133 | 218.205 | 4.256 ms |
| Bounded/reused samples | Off | 757.949 | 204.765 | 3.889 ms |

The final sampler reuses chunk/color-tile lookups and a mutable position, limits work to 250 microseconds per tick (checked every 32 samples), and waits one second between stationary scans. No new chunk generation is requested. The averages and 1% lows vary between runs; the map-on p99 remains higher in this pair. These short static measurements do not establish a performance advantage or zero overhead. Traversal, large modpacks, other hardware, Windows, and NeoForge performance have not been benchmarked here.

JSON summaries are included. Raw frame CSVs and exact build hashes remain in local `.cache/benchmarks/friends-map-v5-*/logs` and `client-build.json`. These summaries contain no account credentials or friend-list data.
