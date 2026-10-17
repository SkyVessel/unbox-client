#!/usr/bin/env python3
"""Fetch pinned native mods into the repository cache; never select an active profile.

Without --output-dir this only prepares .cache/survival-mods. An explicitly supplied
output directory receives a copy, and an existing different file is never replaced.
The launcher, not this helper, manages profile updates and gameplay settings.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def verified(path, item):
    if not path.is_file() or path.is_symlink():
        return False
    data = path.read_bytes()
    return all(hashlib.new(algorithm, data).hexdigest() == item[algorithm]
               for algorithm in ("sha1", "sha512"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("loader", choices=("fabric", "neoforge"))
    parser.add_argument("version", choices=("26.1", "26.1.2", "26.3"))
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    lock = json.loads((ROOT / "src-tauri/resources/survival-mods-lock.json").read_text())
    selected = next(target for target in lock["targets"]
                    if target["loader"] == args.loader and target["minecraft"] == args.version)
    for unavailable in selected.get("unavailableFeatures", []):
        print(f"Unavailable {unavailable['feature']}: {unavailable['reason']}")
    cache = ROOT / ".cache/survival-mods"
    cache.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    names = set()
    # Fabric API is included for standalone preparation, using the same performance pin.
    for item in selected["mods"]:
        name = item["filename"]
        if Path(name).name != name or name in names:
            raise SystemExit("Unsafe or duplicate dependency filename")
        names.add(name)
        if args.output_dir:
            dest = args.output_dir / name
            if (dest.exists() or dest.is_symlink()) and not verified(dest, item):
                raise SystemExit(f"Preserved conflicting file: {dest}")
    for item in selected["mods"]:
        cached = cache / item["filename"]
        if not verified(cached, item):
            if cached.is_symlink():
                raise SystemExit(f"Refusing symlink: {cached}")
            request = urllib.request.Request(item["url"], headers={"User-Agent": "UnboxClient dependency preparation"})
            with opener.open(request, timeout=180) as response:
                data = response.read()
            if not all(hashlib.new(algorithm, data).hexdigest() == item[algorithm]
                       for algorithm in ("sha1", "sha512")):
                raise SystemExit(f"Dependency checksum mismatch: {item['slug']}")
            temporary = cached.with_suffix(".jar.part")
            temporary.write_bytes(data)
            temporary.replace(cached)
        if args.output_dir:
            args.output_dir.mkdir(parents=True, exist_ok=True)
            dest = args.output_dir / item["filename"]
            if not dest.exists():
                # Exclusive creation also preserves a file created after the preflight.
                with dest.open("xb") as target, cached.open("rb") as source:
                    shutil.copyfileobj(source, target)
        print(f"Prepared {item['slug']} {item['version']} ({args.loader} / {args.version})")


if __name__ == "__main__":
    main()
