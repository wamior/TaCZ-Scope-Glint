"""Validate embedded versions, target loaders, Java levels and packaging before publication."""

import argparse
from pathlib import Path
import sys
import zipfile
import publish


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    version = publish.properties(root / "gradle.properties")["mod_version"]
    items = publish.artifacts(root, version, [args.target] if args.target else None)
    for item in items:
        with zipfile.ZipFile(root / item["file"]) as jar:
            names = jar.namelist()
            if any(n.startswith("com/tacz/") or "PlayerRenderDistanceMixin" in n or n.endswith(".jar") for n in names):
                raise ValueError("Bundled dependency or obsolete render mixin: " + item["filename"])
            if "logo.png" not in names:
                raise ValueError("Missing icon")
            if jar.read("logo.png") != (root / "src/main/resources/logo.png").read_bytes():
                raise ValueError("Stale project icon: " + item["filename"])
            for name in names:
                if name.endswith(".class"):
                    data = jar.read(name)
                    major = int.from_bytes(data[6:8], "big")
                    if major > 44 + int(item["java"]):
                        raise ValueError(f"Wrong Java class level: {name}: {major}")
                    if item["loader"] == "forge" and b"net/neoforged/" in data:
                        raise ValueError("NeoForge reference in a Forge jar: " + name)
            meta_path = "META-INF/neoforge.mods.toml" if item["loader"] == "neoforge" else "META-INF/mods.toml"
            metadata = publish.tomllib.loads(jar.read(meta_path).decode("utf-8"))
            deps = metadata["dependencies"]["taczscopeglint"]
            if not any(d["modId"] == "tacz" and d["side"] == "CLIENT" for d in deps):
                raise ValueError("Missing client-side TaCZ dependency")
        print("Verified:", item["filename"], item["sha256"])


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, publish.PublishError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        sys.exit(1)
