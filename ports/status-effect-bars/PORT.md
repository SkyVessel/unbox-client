# Status Effect Bars — native NeoForge port

Original author: A5b84 and the contributors credited in the retained Fabric metadata.
Original source and exact revision: `UPSTREAM`. Original license texts: `COPYING`
and `COPYING.LESSER`; all port changes are offered under LGPL-3.0.

This is an unofficial Unbox-maintained port. Changes dated 2026-10-18:
- Native NeoForge client entry point, without a Fabric compatibility loader.
- Build-time Gui/Hud adaptation for Minecraft 26.1, 26.1.2 and 26.3.
- NeoForge effect rejection and inventory rendering hooks are adapted without disabling effect bars.
- Cloth Config remains an external dependency; the Fabric Mod Menu adapter is not compiled.

Build independently with JDK 25 from this source directory:

```sh
./gradlew build -Pminecraft=26.1
./gradlew build -Pminecraft=26.1.2
./gradlew build -Pminecraft=26.3
```

Use `gradlew.bat` on Windows. Gradle downloads the public NeoForge/game and Cloth
Config dependencies; no Unbox source, cached runtime or account is needed.
The equivalent repository packaging helper is `scripts/build-status-effect-bars.py`.
Publication includes this complete directory, wrapper, build files and license texts.
A local build is not a claim that an upstream release or public fork exists.
