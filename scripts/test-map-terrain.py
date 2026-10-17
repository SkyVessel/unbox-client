#!/usr/bin/env python3
"""Test the production terrain cache using a temporary JVM build and storage root.

Run: python3 scripts/test-map-terrain.py
Requires Java 21 or newer. Does not build a client JAR or access player data.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    with tempfile.TemporaryDirectory(prefix="unbox-map-terrain-test-") as temporary:
        work = Path(temporary)
        sources = work / "dev/unbox/client"
        sources.mkdir(parents=True)
        shutil.copyfile(
            ROOT / "client-mod/src/main/java/dev/unbox/client/MapTerrainStore.java",
            sources / "MapTerrainStore.java",
        )
        shutil.copyfile(ROOT / "tests/MapTerrainStoreTest.java", sources / "MapTerrainStoreTest.java")
        (sources / "Platform.java").write_text(
            'package dev.unbox.client; final class Platform {'
            'static java.nio.file.Path gameDir() {'
            'throw new AssertionError("Tests must provide an explicit temporary storage root");}}\n'
        )
        subprocess.run(
            ["javac", "-d", str(work), *map(str, sorted(sources.glob("*.java")))],
            cwd=work, check=True, timeout=30,
        )
        subprocess.run(
            ["java", "-cp", str(work), "dev.unbox.client.MapTerrainStoreTest", str(work / "data")],
            cwd=work, check=True, timeout=30,
        )


if __name__ == "__main__":
    main()
