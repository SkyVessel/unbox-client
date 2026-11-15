#!/usr/bin/env python3
"""Run existing real TCP/UDP regressions with the Java 17 backport workers."""
import argparse
import os
from pathlib import Path
import subprocess
from port1201 import java17

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--gson', type=Path, required=True)
    parser.add_argument('--slf4j', type=Path, required=True)
    args = parser.parse_args()
    build = ROOT / '.cache/1.20.1/java17-network-test'
    source, classes = build / 'src', build / 'classes'
    source.mkdir(parents=True, exist_ok=True)
    classes.mkdir(exist_ok=True)
    generated = []
    for original in (ROOT / 'client-mod/src/main/java/dev/unbox/client/VoxLinkTransport.java', ROOT / 'tests/VoxLinkTransportTest.java'):
        dest = source / original.name
        dest.write_text(java17(original.read_text()))
        generated.append(dest)
    sources = [*generated, *sorted((ROOT / 'ports/voxlink/src').rglob('*.java')),
               ROOT / 'client-mod/src/1.20.1/java/LegacyThreads.java', ROOT / 'tests/LegacyThreadsTest.java']
    classpath = os.pathsep.join(str(p.resolve()) for p in (args.gson, args.slf4j, classes))
    subprocess.run([str(args.java_home / 'bin/javac'), '--release', '17', '-proc:none', '-cp', classpath,
                    '-d', str(classes), *map(str, sources)], check=True)
    for test in ('LegacyThreadsTest', 'VoxLinkTransportTest'):
        subprocess.run([str(args.java_home / 'bin/java'), '-cp', classpath, 'dev.unbox.client.' + test], check=True, timeout=180)


if __name__ == '__main__':
    main()
