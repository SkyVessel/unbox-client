#!/usr/bin/env python3
"""Exercise the production bounded profiler accumulator without launching Minecraft."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='unbox-performance-') as work:
    subprocess.run(['javac','-d',work,str(root/'client-mod/src/main/java/dev/unbox/client/PerformanceSamples.java'),str(root/'tests/PerformanceSamplesTest.java'),str(root/'client-mod/src/main/java/dev/unbox/client/AsyncSettingsFile.java'),str(root/'tests/AsyncSettingsFileTest.java')],check=True)
    subprocess.run(['java','-cp',work,'dev.unbox.client.PerformanceSamplesTest'],check=True)
    subprocess.run(['java','-cp',work,'dev.unbox.client.AsyncSettingsFileTest',str(Path(work)/'data')],check=True)
