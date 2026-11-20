"""Copy local publishing credentials to GitHub Actions secrets without logging them."""

import json
import os
from pathlib import Path
import subprocess
from envfile import load_env

ROOT = Path(__file__).resolve().parents[1]
REPO = "wamior/TaCZ-Scope-Glint"


def main():
    load_env(ROOT / ".env")
    config = json.loads((ROOT / "publish.json").read_text(encoding="utf-8"))
    values = {}
    for platform in ("modrinth", "curseforge"):
        token_name = platform.upper() + "_TOKEN"
        token = os.environ.get(token_name)
        project = config[platform]["project_id"]
        if not token or not project:
            raise ValueError("Fill .env and publish.json for " + platform + " first")
        values[platform] = (token_name, token, project)
    subprocess.run(["gh", "repo", "view", REPO], check=True, stdout=subprocess.DEVNULL)
    for platform, (name, token, project) in values.items():
        subprocess.run(["gh", "secret", "set", name, "--repo", REPO], input=token, text=True, check=True)
        subprocess.run(["gh", "variable", "set", platform.upper() + "_PROJECT_ID", "--repo", REPO,
                        "--body", str(project)], check=True)
        print("Configured GitHub Actions:", platform)


if __name__ == "__main__":
    main()
