"""Prepare, verify and tag a release. The GitHub Actions tag workflow publishes all artifacts."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from publish import properties
from verify_release import verify

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run(args, cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", nargs="?")
    parser.add_argument("changelog", nargs="?")
    parser.add_argument("--dry-run", action="store_true", help="Build and validate, then restore version files; do not commit or push")
    parser.add_argument("--prepare-only", action="store_true", help="Keep prepared version files, but do not commit or push")
    args = parser.parse_args()
    props = properties(ROOT / "gradle.properties")
    current = props["mod_version"]
    version = (args.version or input(f"New version (current {current}): ")).removeprefix("v").strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version) or tuple(map(int, version.split("."))) <= tuple(map(int, current.split("."))):
        raise ValueError("Use a new version greater than " + current)
    changelog = args.changelog or input("Release notes: ").strip()
    if not changelog:
        raise ValueError("Release notes must not be empty")
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip()
    if branch != "main":
        raise ValueError("Release from main")
    if not args.dry_run and subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip():
        raise ValueError("Commit current changes before releasing")
    tag = "v" + version
    if subprocess.run(["git", "rev-parse", "--verify", "refs/tags/" + tag], cwd=ROOT,
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        raise ValueError("Tag already exists: " + tag)
    paths = [ROOT / name for name in ("gradle.properties", "update.json", "CHANGELOG.md")]
    originals = {path: path.read_bytes() for path in paths}
    complete = False
    try:
        paths[0].write_text(re.sub(r"(?m)^mod_version=\S+", "mod_version=" + version,
                                  originals[paths[0]].decode("utf-8")), encoding="utf-8")
        update = json.loads(originals[paths[1]])
        for target in props["targets"].split(","):
            mc = properties(ROOT / "targets" / target / "gradle.properties")["minecraft_version"]
            update["promos"][mc + "-latest"] = version
            update["promos"][mc + "-recommended"] = version
            update.setdefault(mc, {})[version] = changelog
        paths[1].write_text(json.dumps(update, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        paths[2].write_text(f"# TaCZ Scope Glint {version}\n\n{changelog}\n", encoding="utf-8")
        run(sys.executable, str(ROOT / "scripts/download_dependencies.py"))
        run(str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")), "buildAll", "--console=plain")
        run(sys.executable, str(ROOT / "scripts/verify_artifacts.py"))
        run(sys.executable, str(ROOT / "scripts/publish.py"), "--dry-run", "--changelog-file", str(paths[2]))
        verify(ROOT, tag)
        complete = True
    finally:
        if args.dry_run or not complete:
            for path, data in originals.items():
                path.write_bytes(data)
    if args.dry_run or args.prepare_only:
        print("Prepared all four builds. No commits, tags or uploads were made.")
        return
    run("git", "add", "gradle.properties", "update.json", "CHANGELOG.md")
    run("git", "commit", "-m", "Release " + tag)
    run("git", "tag", "-a", tag, "-m", "TaCZ Scope Glint " + version)
    run("git", "push", "--atomic", "origin", "main", tag)
    print("Release pipeline started: https://github.com/wamior/TaCZ-Scope-Glint/actions")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        sys.exit(1)
