# ColourScroller: native shape-cycling port

MIT; upstream commit in UPSTREAM. This port adapts ColourScroller.getNextScrollable / createStack for Mojang mappings and keeps cycling in a separate native mod JAR. It provides only the single-stack cycle engine; Unbox supplies its keybind and settings. Upstream colour/hotbar-row editors are not included. The supplied preset cycles shapes within the same wood/stone material, as requested, rather than changing material. Invalid block-specific data is not copied across shapes.

Both the adapter and vanilla creative inventory packets enforce Creative mode. This library uses the game's own registry to exclude nonexistent variants. Build with python3 scripts/build-colour-scroller.py after preparing the six native Unbox build runtimes. Full corresponding source and build script are included in colour-scroller-sources.zip.

Independent build (no Unbox source or cache required), JDK 25:

```sh
./gradlew build -Pminecraft=26.1
./gradlew build -Pminecraft=26.1.2
./gradlew build -Pminecraft=26.3
```

Use gradlew.bat on Windows. Each build produces separate Fabric and NeoForge JARs.
Only vanilla Minecraft classes are referenced by the shared cycle engine.
