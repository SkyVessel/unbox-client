# Look Behind: native 1.21.11 Fabric adaptation

Author/source/commit and changes are recorded in `UPSTREAM.json`. GPL-3.0-or-later license is retained in `LICENSE`; adapted source is included in the output JAR under `META-INF/unbox-source`.

The author's public 1.0.2 release declares 1.21.11 compatibility but crashes during Camera mixin application: it targets the old BlockView/BlockGetter setup signature. This source build uses Mojang's native `Camera.setup(Level, Entity, boolean, boolean, float)` mapping. No runtime JAR rewriting or compatibility loader is used.

The original tick hook also assigned HUD visibility every tick, including when idle. This adaptation preserves the player's HUD visibility and clears the held look-behind state while unfocused/in menus. The default R binding remains editable in Minecraft Controls. It does not turn the player or send rotation packets.

Build from the workspace root:

```sh
./client-mod/gradlew -p ports/look-behind-1.21.11 build
```

After intentionally changing the build, update the local artifact's hashes in `client-mod/pvp-lock-1.21.11-fabric.json`. The normal prepare script checks those hashes. Do not install the original and adapted JAR together.

Camera movement feel is reserved for the user to test. Startup/mixin compatibility is a separate check.
