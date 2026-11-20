import argparse
import json
from pathlib import Path
from publish import properties


def verify(root, tag):
    props = properties(root / "gradle.properties")
    version = props["mod_version"]
    if tag != "v" + version:
        raise ValueError("Tag must match mod_version: v" + version)
    data = json.loads((root / "update.json").read_text(encoding="utf-8"))
    for target in props["targets"].split(","):
        mc = properties(root / "targets" / target / "gradle.properties")["minecraft_version"]
        for channel in ("latest", "recommended"):
            if data["promos"][mc + "-" + channel] != version:
                raise ValueError("update.json does not match release: " + mc)
        if version not in data[mc]:
            raise ValueError("Missing update changelog: " + mc)
    if not (root / "CHANGELOG.md").read_text(encoding="utf-8").startswith("# TaCZ Scope Glint " + version + "\n"):
        raise ValueError("CHANGELOG.md does not match release")
    print("Release metadata verified:", tag)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    verify(Path(__file__).resolve().parents[1], parser.parse_args().tag)
