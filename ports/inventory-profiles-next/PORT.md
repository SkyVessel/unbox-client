# inventory-profiles-next: unofficial Unbox native port

Original repository and exact revision: `UPSTREAM`. Original copyright and license
files are retained. Port modifications dated 2026-10-18 are distributed under
AGPL-3.0-or-later. This project is not endorsed by the upstream author.

Backport target is Minecraft 26.1 with NeoForge 26.1.0.19-beta; historical source-directory names remain unchanged. Build the matching libIPN port first.

Build using JDK 25 from this directory:

```sh
./gradlew :platforms:neoforge-26.1.2:shadowJar -x :platforms:neoforge-26.1.2:neoFormJoined26.1-1DownloadAssets
```

On Windows use `gradlew.bat`. The first build downloads public dependencies.
See the root `scripts/package-client-ports.py` for the binary and source packaging
map. Source archives contain this directory, including the wrapper and build files.
No account credentials or Unbox proprietary source is required by this Gradle build.
