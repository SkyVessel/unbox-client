#!/usr/bin/env python3
"""Exercise real manifest/metadata code; stub only Minecraft version and loader identity."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
gson = ROOT / '.cache/runtime-1.21.11/libraries/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'
with tempfile.TemporaryDirectory(prefix='unbox-environment-test-') as tmp:
    work = Path(tmp)
    (work / 'Platform.java').write_text('package dev.unbox.client; public class Platform { public static String loader(){return "fabric";} public static String loaderVersion(){return "0.19.5";} }')
    (work / 'SharedConstants.java').write_text('package net.minecraft; public class SharedConstants { public record Version(String id){} public static Version getCurrentVersion(){return new Version(System.getProperty("test.minecraft"));} }')
    sources = [work / 'Platform.java', work / 'SharedConstants.java', ROOT / 'client-mod/src/main/java/dev/unbox/client/EnvironmentManifest.java', ROOT / 'client-mod/src/fabric/java/dev/unbox/client/ModMetadata.java', ROOT / 'tests/EnvironmentManifestTest.java', ROOT / 'tests/EnvironmentPreparationTest.java']
    subprocess.run(['javac', '--release', '21', '-cp', str(gson), '-d', str(work), *map(str, sources)], check=True)
    for version in ('26.3', '1.21.11'):
        runtime_gson = ROOT / ('.cache/runtime-26.3/libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar' if version == '26.3' else str(gson.relative_to(ROOT)))
        for test in ('EnvironmentManifestTest', 'EnvironmentPreparationTest'):
            print(version, test, flush=True)
            subprocess.run(['java', '-Dtest.minecraft=' + version, '-cp', os.pathsep.join(map(str, (work, runtime_gson))), test], check=True)
