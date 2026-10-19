# easyshulkerboxes: unofficial Unbox native port

Source and pinned commit: `UPSTREAM`. Code and port changes: MPL-2.0.
Target: Minecraft 26.1, NeoForge 26.1.0.19-beta. This is not an upstream release.

Build with JDK 25. Extract all three Fuzs source archives together, preserving
`ports/puzzleslib`, `ports/iteminteractions`, `ports/easyshulkerboxes` as siblings.
Run in this order from the extracted root:

```sh
./ports/puzzleslib/gradlew -p ports/puzzleslib :Common:jar :NeoForge:jar
./ports/iteminteractions/gradlew -p ports/iteminteractions :Common:jar :NeoForge:jar
./ports/easyshulkerboxes/gradlew -p ports/easyshulkerboxes :NeoForge:jar
```

Use gradlew.bat on Windows. Builds download public dependencies, without Unbox
private source or credentials. Item Interactions is distributed separately instead
of embedding an incompatible upstream JAR.

Puzzles Lib adapts break and tag events, retains server registry context for reload
callbacks, and supplies dynamic registry lookup while binding item components.
Item Interactions uses the original storage/network behavior with simple original
rectangles and text for inventory previews. Upstream reserved PNG artwork is omitted
from the port and source archives; `LICENSE-ASSETS.md` is retained as provenance.
Optional pack logos are omitted. Original code copyrights and MPL license remain.
