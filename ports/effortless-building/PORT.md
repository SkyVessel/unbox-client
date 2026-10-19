# effortless-building: unofficial Unbox native port

Original repository and exact revision: `UPSTREAM`. Original copyright and license
files are retained. Port modifications dated 2026-10-18 are distributed under
LGPL-3.0. This project is not endorsed by the upstream author.

Backported the 26.1.2 branch to native Minecraft 26.1 / NeoForge 26.1.0.19-beta. Unbox enforces Creative-only access in its separate adapter; the upstream engine stays in its own JAR.

Build using JDK 25 from this directory:

```sh
./gradlew :fabric:build :neoforge:build
```

On Windows use `gradlew.bat`. The first build downloads public dependencies.
See the root `scripts/package-client-ports.py` for the binary and source packaging
map. Source archives contain this directory, including the wrapper and build files.
No account credentials or Unbox proprietary source is required by this Gradle build.
