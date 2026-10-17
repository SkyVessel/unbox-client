#!/usr/bin/env python3
"""Run grave write/read in separate JVMs against one disposable copied world.

Usage:
  python3 scripts/test-grave-persistence.py WORLD_DIRECTORY RUN_NAME TEMPLATE_GAME_DIR

All fixture inputs and outputs must be inside .cache/benchmarks. The template
must contain test-launch-args.json, mods/unbox-client.jar and options.txt from an
existing isolated launch. Refresh its client JAR before invoking this runner.
No download, compilation or access to normal player saves is performed.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
BENCHMARKS = (ROOT / ".cache/benchmarks").resolve()


def fixture_path(value, label):
    path = Path(value).expanduser().resolve()
    if not path.is_relative_to(BENCHMARKS) or path == BENCHMARKS:
        raise ValueError(f"{label} must be strictly inside {BENCHMARKS}")
    if not path.is_dir():
        raise ValueError(f"{label} directory does not exist: {path}")
    return path


def reject_symlinks(path):
    if path.is_symlink():
        raise ValueError(f"Fixture symlinks are not allowed: {path}")
    if path.is_dir():
        for entry in path.rglob("*"):
            if entry.is_symlink():
                raise ValueError(f"Fixture symlinks are not allowed: {entry}")


def option_value(arguments, option):
    if arguments.count(option) != 1:
        raise ValueError(f"Template must have exactly one {option}")
    index = arguments.index(option)
    if index + 1 >= len(arguments):
        raise ValueError(f"Template {option} has no value")
    return arguments[index + 1]


def launch_arguments(template, game):
    path = template / "test-launch-args.json"
    reject_symlinks(path)
    args = json.loads(path.read_text())
    if not isinstance(args, list) or not args or not all(isinstance(arg, str) for arg in args):
        raise ValueError("test-launch-args.json must be a nonempty string array")
    if Path(option_value(args, "--gameDir")).resolve() != template:
        raise ValueError("Template --gameDir does not identify TEMPLATE_GAME_DIR")
    if option_value(args, "--accessToken") != "0":
        raise ValueError("Only offline test-launch arguments with accessToken 0 are accepted")
    if not Path(args[0]).is_file() or Path(args[0]).name != "java":
        raise ValueError("Template must use an existing Java executable")
    # Strip every Unbox fixture property, including all *Smoke and benchmark flags,
    # shared-cache overrides and other automatic test entry points.
    result = [arg for arg in args if not arg.startswith("-Dunbox.")]
    for option in ("--quickPlaySingleplayer", "--quickPlayMultiplayer", "--quickPlayRealms", "--quickPlayPath"):
        while option in result:
            index = result.index(option)
            if index + 1 >= len(result):
                raise ValueError(f"Template {option} has no value")
            del result[index:index + 2]
    result[result.index("--gameDir") + 1] = str(game)
    # Keep JVM libraries/assets read-only at their existing paths; all Minecraft
    # writable data is routed to the newly created game directory and world.
    result += ["--quickPlaySingleplayer", "Benchmark"]
    return result


def copy_fixture(source, template, game):
    for path in (source, template / "mods", template / "options.txt"):
        if not path.exists():
            raise ValueError(f"Missing fixture input: {path}")
        reject_symlinks(path)
    if not (source / "level.dat").is_file():
        raise ValueError("Source fixture world must contain level.dat")
    if not (template / "mods/unbox-client.jar").is_file():
        raise ValueError("Template must contain the newly built mods/unbox-client.jar")
    # mkdir's exist_ok=False is also the final race-safe check against overwrites.
    game.mkdir(parents=False, exist_ok=False)
    (game / "saves").mkdir()
    (game / "logs").mkdir()
    shutil.copytree(source, game / "saves/Benchmark", ignore=shutil.ignore_patterns("session.lock"))
    shutil.copytree(template / "mods", game / "mods")
    shutil.copy2(template / "options.txt", game / "options.txt")
    for folder in ("config", "resourcepacks", "shaderpacks"):
        origin = template / folder
        if origin.exists():
            reject_symlinks(origin)
            shutil.copytree(origin, game / folder)
    # These are display/audio settings for the isolated copy only.
    options = (game / "options.txt").read_text().splitlines()
    replacements = {"soundCategory_master": "0.0", "pauseOnLostFocus": "false", "fullscreen": "false"}
    options = [line for line in options if line.partition(":")[0] not in replacements]
    options += [f"{key}:{value}" for key, value in replacements.items()]
    (game / "options.txt").write_text("\n".join(options) + "\n")


def run_phase(mode, arguments, game, timeout):
    args = [arguments[0], f"-Dunbox.gravePersistence={mode}", *arguments[1:]]
    (game / f"test-launch-args-{mode}.json").write_text(json.dumps(args, indent=2))
    log_path = game / f"logs/grave-persistence-{mode}-launch.log"
    start = time.monotonic()
    environment = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        environment.pop(key, None)
    timed_out = False
    with log_path.open("w") as log:
        process = subprocess.Popen(args, cwd=game, stdout=log, stderr=subprocess.STDOUT, env=environment)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.terminate()
            try:
                code = process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                code = process.wait()
    result_path = game / f"logs/grave-persistence-{mode}.json"
    report = None
    if result_path.is_file():
        try:
            report = json.loads(result_path.read_text())
        except (ValueError, OSError):
            pass
    return {
        "mode": mode,
        "ok": not timed_out and code == 0 and isinstance(report, dict) and report.get("ok") is True,
        "exitCode": code,
        "timedOut": timed_out,
        "seconds": round(time.monotonic() - start, 2),
        "report": report,
        "launchLog": str(log_path),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source_world")
    parser.add_argument("run_name")
    parser.add_argument("template_game_dir")
    parser.add_argument("--timeout", type=int, default=300, help="Seconds per JVM, including normal shutdown (default: 300)")
    options = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", options.run_name):
        raise ValueError("Run name must be 1–80 letters/digits/underscores/hyphens, beginning with a letter or digit")
    if not 30 <= options.timeout <= 1800:
        raise ValueError("Timeout must be between 30 and 1800 seconds")
    source = fixture_path(options.source_world, "Source world")
    template = fixture_path(options.template_game_dir, "Template game directory")
    game = BENCHMARKS / options.run_name
    if game.exists() or game.is_symlink():
        raise ValueError("Run already exists; choose a fresh name. Existing data is never overwritten")
    args = launch_arguments(template, game)
    copy_fixture(source, template, game)
    (game / "test-launch-args.json").write_text(json.dumps(args, indent=2))
    summary = {"ok": False, "gameDirectory": str(game), "sourceWorld": str(source), "template": str(template), "phases": []}
    summary_path = game / "logs/grave-persistence-summary.json"
    try:
        for mode in ("write", "read"):
            print(f"Grave persistence {mode}: {game}", flush=True)
            phase = run_phase(mode, args, game, options.timeout)
            summary["phases"].append(phase)
            summary_path.write_text(json.dumps(summary, indent=2))
            if not phase["ok"]:
                raise RuntimeError(f"{mode} did not report success and exit normally; inspect {phase['launchLog']}")
            if mode == "write" and not (game / "logs/grave-persistence-manifest.json").is_file():
                raise RuntimeError("Write process exited without a manifest; read phase was not launched")
        summary["ok"] = True
        summary_path.write_text(json.dumps(summary, indent=2))
        print(f"Both isolated JVM phases passed: {summary_path}", flush=True)
        return 0
    except Exception as error:
        summary["error"] = str(error)
        summary_path.write_text(json.dumps(summary, indent=2))
        raise


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, RuntimeError) as error:
        print(f"Grave persistence runner: {error}", file=sys.stderr)
        sys.exit(1)
