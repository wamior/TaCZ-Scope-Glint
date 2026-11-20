"""Fetch the exact upstream TaCZ jars used for compilation; never bundle them in the addon."""

import argparse
import hashlib
import json
import io
from pathlib import Path
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def runtime_libraries(path):
    """Loom removes nested jars, including libraries nested inside SimpleBedrockModel."""
    destination = ROOT / "libs" / ".runtime" / path.stem
    seen = {}

    def unpack(data):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for entry in archive.namelist():
                if not entry.startswith("META-INF/jarjar/") or not entry.endswith(".jar"):
                    continue
                name = Path(entry).name
                nested = archive.read(entry)
                digest = hashlib.sha512(nested).hexdigest()
                if name in seen:
                    if seen[name] != digest:
                        raise ValueError("Conflicting nested dependency: " + name)
                    continue
                seen[name] = digest
                with zipfile.ZipFile(io.BytesIO(nested)) as library:
                    mod = any(meta in library.namelist() for meta in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"))
                target = destination / ("mods" if mod else "libraries") / name
                target.parent.mkdir(parents=True, exist_ok=True)
                if not target.is_file() or hashlib.sha512(target.read_bytes()).hexdigest() != digest:
                    target.write_bytes(nested)
                unpack(nested)
    unpack(path.read_bytes())


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#") and "=" in line)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", help="Download one Gradle target's dependency instead of all four")
    parser.add_argument("--check", action="store_true", help="Verify local files without downloading")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "libs/manifest.json").read_text(encoding="utf-8"))
    if args.target:
        targets = properties(ROOT / "gradle.properties")["targets"].split(",")
        if args.target not in targets:
            raise ValueError("Unknown build target")
        name = properties(ROOT / "targets" / args.target / "gradle.properties")["tacz_jar"]
        manifest = {name: manifest[name]}
    for name, record in manifest.items():
        if Path(name).name != name or not record["url"].startswith("https://cdn.modrinth.com/data/"):
            raise ValueError("Invalid dependency manifest")
        path = ROOT / "libs" / name
        if path.is_file() and hashlib.sha512(path.read_bytes()).hexdigest() == record["sha512"]:
            print("Verified:", name)
            runtime_libraries(path)
            continue
        if args.check:
            raise ValueError("Missing or modified dependency: " + name)
        request = urllib.request.Request(record["url"], headers={"User-Agent": "wamior/TaCZ-Scope-Glint build"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if hashlib.sha512(data).hexdigest() != record["sha512"]:
            raise ValueError("SHA-512 mismatch: " + name)
        partial = path.with_suffix(".jar.part")
        partial.write_bytes(data)
        partial.replace(path)
        runtime_libraries(path)
        print("Downloaded and verified:", name)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        sys.exit(1)
