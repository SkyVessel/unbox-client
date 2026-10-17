#!/usr/bin/env python3
"""Headless adapter regression against both pinned native VeinMiner Hotkey JARs.

Uses real Minecraft key mappings and upstream toggle/disconnect code. Only loader
presence, authentication and Options.save are replaced. No window/world is opened.
Requires the project's existing client compilation for unrelated dependencies.
"""
from pathlib import Path
import os
import subprocess
import shutil
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(*args):
    subprocess.run(args, cwd=ROOT, check=True)

with tempfile.TemporaryDirectory(prefix="unbox-vein-toggle-") as temporary:
    work = Path(temporary)
    shutil.copyfile(ROOT / "src-tauri/resources/unbox-client.jar", work / "unbox-dependencies.jar")
    kotlin = ROOT / ".cache/survival-mods/fabric-language-kotlin-1.14.2+kotlin.2.4.21.jar"
    with zipfile.ZipFile(kotlin) as archive:
        for name in archive.namelist():
            if name.endswith(".jar"):
                (work / Path(name).name).write_bytes(archive.read(name))
    for version, client, common in [
        ("Fabric 2.10.3", "nested/veinminer-client-common-2.10.3.jar", "nested/veinminer-common-2.10.3.jar"),
        ("NeoForge 2.10.1", "veinminer-client-neoforge-2.10.1.jar", "veinminer-neoforge-2.10.1.jar"),
    ]:
        classes = work / version.replace(" ", "-")
        classes.mkdir()
        if version.startswith("Fabric"):
            with zipfile.ZipFile(ROOT / ".cache/survival-mods/veinminer-fabric-2.10.3.jar") as archive:
                (classes / "core.jar").write_bytes(archive.read("META-INF/jars/core-2.10.3.jar"))
        cp = os.pathsep.join(map(str, [classes, classes / "core.jar", ROOT / ".cache/survival-mods" / client,
            ROOT / ".cache/survival-mods" / common, work / "*", ROOT / ".cache/client-26.1.jar",
            ROOT / ".cache/libs/*", ROOT / ".cache/fabric-loader.jar", work / "unbox-dependencies.jar"]))
        sources = list((ROOT / "tests/fixtures/vein-toggle").rglob("*.java")) + [
            ROOT / "tests/VeinMinerToggleTest.java",
            ROOT / "client-mod/src/main/java/dev/unbox/client/SurvivalHostMods.java",
            ROOT / "client-mod/src/main/java/dev/unbox/client/VeinMinerClientGateMixin.java"]
        # Compile production against full cached Platform, then shadow only runtime discovery.
        real_sources = sources[-2:]
        run("javac", "-proc:none", "-cp", cp, "-d", str(classes), *map(str, real_sources))
        run("javac", "-proc:none", "-cp", cp, "-d", str(classes), *map(str, sources[:-2]))
        print(version, flush=True)
        run("java", "-cp", cp, "dev.unbox.client.VeinMinerToggleTest")
        run("java", "-cp", cp, "dev.unbox.client.VeinMinerToggleTest", "existing-toggle")
