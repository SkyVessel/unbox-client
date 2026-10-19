# resourcify: unofficial Unbox native port

Original repository and exact revision: `UPSTREAM`. Original copyright and license
files are retained. Port modifications dated 2026-10-18 are distributed under
LGPL-3.0. This project is not endorsed by the upstream author.

Added a native 26.3 NeoForge target using NeoForge 26.3.0.86, KotlinForForge 6.4.0 and the upstream UniversalCraft preprocessing approach. Sources are otherwise retained.

Build using JDK 25 from this directory:

```sh
./gradlew :26.3-neoforge:build
```

On Windows use `gradlew.bat`. The first build downloads public dependencies.
See the root `scripts/package-client-ports.py` for the binary and source packaging
map. Source archives contain this directory, including the wrapper and build files.
No account credentials or Unbox proprietary source is required by this Gradle build.
