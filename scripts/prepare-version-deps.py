#!/usr/bin/env python3
"""Prepare pinned compilation dependencies in .cache/26.3 only.

Usage: python3 scripts/prepare-version-deps.py 26.3 [fabric|neoforge|all]
Verified downloads in .cache/26.3/mods or .cache/benchmark-mods are reused.
Existing different files are preserved and reported instead of overwritten.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "fabric": [("fabric-api", "fabric-api.jar", "fabric-api"),
               ("freelook", "freelook.jar", None),
               ("e4mc", "e4mc.jar", "e4mc-deps")],
    "neoforge": [("e4mc", "e4mc-neoforge.jar", "e4mc-neoforge-deps")],
}


def basename(name):
    if not isinstance(name, str) or not name.endswith(".jar") or name in (".", "..") \
            or Path(name).name != name or "/" in name or "\\" in name:
        raise SystemExit(f"Unsafe dependency filename: {name}")
    return name


def verified(data, item):
    return all(hashlib.new(algorithm, data).hexdigest() == item[algorithm]
               for algorithm in ("sha1", "sha512"))


def safe_path(path):
    for part in (path, *path.parents):
        if part == ROOT:
            return
        if part.is_symlink():
            raise SystemExit(f"Refusing symlink: {part}")
    raise SystemExit(f"Path outside repository: {path}")


def store(path, data):
    safe_path(path)
    if path.exists():
        if not path.is_file() or path.read_bytes() != data:
            raise SystemExit(f"Preserved conflicting file: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as output:
        output.write(data)


def prepare(version, loader):
    cache = ROOT / ".cache" / version
    prefix = "neoforge-" if loader == "neoforge" else ""
    lock = json.loads((ROOT / "src-tauri/resources" /
                       f"{prefix}performance-lock-{version}.json").read_text())
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for slug, alias, nested_dir in TARGETS[loader]:
        matches = [item for item in lock if item["slug"] == slug]
        if len(matches) != 1 or version not in matches[0]["gameVersions"]:
            raise SystemExit(f"Missing unique {loader} / {version} pin: {slug}")
        item = matches[0]
        name = basename(item["filename"])
        data = None
        for candidate in (cache / "mods" / name, ROOT / ".cache/benchmark-mods" / name):
            safe_path(candidate)
            if candidate.is_file():
                existing = candidate.read_bytes()
                if verified(existing, item):
                    data = existing
                    break
        if data is None:
            request = urllib.request.Request(item["url"], headers={
                "User-Agent": "UnboxClient version dependency preparation"})
            with opener.open(request, timeout=180) as response:
                data = response.read()
            if not verified(data, item):
                raise SystemExit(f"Dependency checksum mismatch: {slug}")
        store(cache / "mods" / name, data)
        store(cache / basename(alias), data)
        if nested_dir:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                names = set()
                for entry in archive.infolist():
                    if not entry.filename.startswith("META-INF/jars/") or not entry.filename.endswith(".jar"):
                        continue
                    nested_name = basename(entry.filename.removeprefix("META-INF/jars/"))
                    if nested_name in names:
                        raise SystemExit(f"Duplicate nested dependency: {nested_name}")
                    names.add(nested_name)
                    store(cache / nested_dir / nested_name, archive.read(entry))
                if not names:
                    raise SystemExit(f"Missing nested compilation dependencies: {slug}")
        print(f"Prepared {slug} {item['version']} ({loader} / {version})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", choices=("26.3",))
    parser.add_argument("loader", nargs="?", default="all", choices=("fabric", "neoforge", "all"))
    args = parser.parse_args()
    for loader in TARGETS if args.loader == "all" else (args.loader,):
        prepare(args.version, loader)


if __name__ == "__main__":
    main()
