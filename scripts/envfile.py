"""Read local publishing credentials without executing shell code."""

import os
from pathlib import Path


TOKEN_NAMES = {"MODRINTH_TOKEN", "CURSEFORGE_TOKEN"}


def load_env(path: Path):
    if not path.is_file():
        return
    for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, sep, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not sep or key not in TOKEN_NAMES:
            raise ValueError(f"Invalid publishing variable in {path.name}, line {number}")
        if value.startswith(("'", '"')):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f"Unclosed quote in {path.name}, line {number}")
            value = value[1:-1]
        if value and not os.environ.get(key):
            os.environ[key] = value
