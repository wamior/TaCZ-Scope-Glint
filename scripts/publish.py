"""Publish the already-built TaCZ Scope Glint jars. Python 3.11+, standard library only.

Without --upload or --check this writes an offline preview and makes no requests.
GitHub releases and update.json are handled by release.py and Actions, never by this script.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from envfile import load_env


ROOT = Path(__file__).resolve().parents[1]
APIS = {
    "curseforge": "https://minecraft.curseforge.com/api",
    "modrinth": "https://api.modrinth.com/v2",
}
TOKEN_VARS = {"curseforge": "CURSEFORGE_TOKEN", "modrinth": "MODRINTH_TOKEN"}
LOADERS = {"forge": "Forge", "neoforge": "NeoForge"}
DEPENDENCIES = {
    "tacz": ("timeless-and-classics-zero", "SzzJttH8"),
}


class PublishError(Exception):
    pass


class HTTPRejection(PublishError):
    def __init__(self, message, status):
        super().__init__(message)
        self.status = status


def properties(path):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def artifacts(root, version, selected=None):
    props = properties(root / "gradle.properties")
    targets = props["targets"].split(",")
    if selected:
        unknown = set(selected) - set(targets)
        if unknown:
            raise PublishError("Unknown targets: " + ", ".join(sorted(unknown)))
        targets = [t for t in targets if t in selected]
    result = []
    for target in targets:
        cfg = properties(root / "targets" / target / "gradle.properties")
        mc, loader = cfg["minecraft_version"], cfg["loader"]
        name = f"TaCZ-Scope-Glint-{loader}-{mc}-{version}.jar"
        path = root / "build" / "libs" / version / mc / name
        if not path.is_file():
            raise PublishError(f"Missing {path}. Build this version first (gradlew buildAll).")
        # Reject source/dev jars and stale artifacts before sending anything.
        with zipfile.ZipFile(path) as jar:
            if loader == "fabric":
                meta = json.loads(jar.read("fabric.mod.json"))
                actual = meta["version"]
                mod_id = meta["id"]
            else:
                meta_path = "META-INF/neoforge.mods.toml" if "META-INF/neoforge.mods.toml" in jar.namelist() else "META-INF/mods.toml"
                meta = tomllib.loads(jar.read(meta_path).decode("utf-8"))
                mod = next(m for m in meta["mods"] if m["modId"] == "taczscopeglint")
                actual, mod_id = mod["version"], mod["modId"]
            if actual != version or mod_id != "taczscopeglint":
                raise PublishError(f"{name}: embedded mod version is {actual}, expected {version}.")
        data = path.read_bytes()
        deps = [("tacz", "required")]
        result.append({
            "target": target, "file": path.relative_to(root).as_posix(),
            "filename": name, "loader": loader,
            "game_versions": cfg.get("mc_versions", mc).split(","),
            "java": cfg["java_release"], "version_number": f"{version}+{mc}-{loader}",
            "name": f"TaCZ Scope Glint {version} - {LOADERS[loader]} {mc}",
            "sha256": hashlib.sha256(data).hexdigest(),
            "sha512": hashlib.sha512(data).hexdigest(), "dependencies": deps,
        })
    return result


def metadata(platform, item, project, changelog, release_type):
    if platform == "modrinth":
        return {
            "project_id": project, "name": item["name"],
            "version_number": item["version_number"], "changelog": changelog,
            "game_versions": item["game_versions"], "loaders": [item["loader"]],
            "version_type": release_type, "featured": False, "status": "listed",
            "environment": "client_only",
            "file_parts": ["file"], "primary_file": "file",
            "dependencies": [{"project_id": DEPENDENCIES[d][1], "dependency_type": kind}
                             for d, kind in item["dependencies"]],
        }
    result = {
        "displayName": item["name"], "changelog": changelog,
        "changelogType": "markdown", "releaseType": release_type,
        "gameVersionNames": item["game_versions"] + [LOADERS[item["loader"]], f"Java {item['java']}", "Client"],
    }
    if item["dependencies"]:
        result["relations"] = {"projects": [{"slug": DEPENDENCIES[d][0], "type": kind + "Dependency"}
                                           for d, kind in item["dependencies"]]}
    return result


def multipart(field, meta, filename, data):
    boundary = "taczscopeglint-" + uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\n'
        'Content-Type: application/json\r\n\r\n'
    ).encode() + json.dumps(meta, ensure_ascii=False).encode("utf-8")
    body += (
        f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        'Content-Type: application/java-archive\r\n\r\n'
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    # Tokens must never be forwarded to another host, including on a POST redirect.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class API:
    def __init__(self, platform, token):
        self.platform, self.token = platform, token
        self.opener = urllib.request.build_opener(NoRedirect)

    def request(self, path, body=None, content_type=None):
        if body is None:
            print(f"  Checking {self.platform}: {path}", flush=True)
        headers = {"User-Agent": "wamior/TaCZ-Scope-Glint-publisher (https://github.com/wamior/TaCZ-Scope-Glint)",
                   "Accept": "application/json"}
        headers["X-Api-Token" if self.platform == "curseforge" else "Authorization"] = self.token
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(APIS[self.platform] + path, data=body, headers=headers)
        try:
            with self.opener.open(req, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as exc:
            hint = " Check project ID and access (draft projects may require the owner's token)." if exc.code == 404 else ""
            # Only selected JSON error fields, with credentials redacted. Never
            # log raw response bodies, HTML or request headers.
            detail = ""
            try:
                payload = json.loads(exc.read(65536))
                fields = {key: payload[key] for key in ("error", "description", "message", "errorMessage", "errorCode", "errors") if key in payload}
                if fields:
                    detail = " " + json.dumps(fields, ensure_ascii=False)
                    for token in [self.token] + [os.environ.get(var) for var in TOKEN_VARS.values()]:
                        if token:
                            detail = detail.replace(token, "[redacted]")
                    detail = detail[:1500]
            except (ValueError, TypeError, OSError):
                pass
            raise HTTPRejection(f"{self.platform} {path}: HTTP {exc.code}.{hint}{detail}", exc.code) from None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError):
            raise PublishError(f"{self.platform} {path}: network error or invalid JSON response.") from None


def curseforge_tag_id(name, kind, catalog, types):
    # The Minecraft endpoint includes Bukkit and addon tags with identical names.
    # Only the Minecraft release-family type is valid for mod artifacts. In newer
    # families the display name may omit "Minecraft", so use the stable type slug.
    type_slug = "minecraft-" + "-".join(name.split(".")[:2]) if kind == "minecraft" else kind
    type_ids = {int(t["id"]) for t in types if t["slug"].casefold() == type_slug}
    if len(type_ids) != 1:
        raise PublishError(f"CurseForge tag type '{type_slug}' is missing or ambiguous. No files uploaded.")
    matches = {int(entry["id"]) for entry in catalog
               if entry["name"].strip().casefold() == name.casefold()
               and entry.get("gameVersionTypeID") is not None
               and int(entry["gameVersionTypeID"]) in type_ids}
    if len(matches) != 1:
        raise PublishError(f"CurseForge tag '{type_slug}:{name}' is missing or ambiguous ({len(matches)} IDs). No files uploaded; use --targets to select supported targets.")
    return matches.pop()


def prepare(platform, config, items, changelog, release_type, api):
    project = str(config[platform]["project_id"])
    remote = []
    if platform == "modrinth":
        project = api.request("/project/" + urllib.parse.quote(project, safe=""))["id"]
        versions = {v["version"] for v in api.request("/tag/game_version")}
        loaders = {v["name"] for v in api.request("/tag/loader")}
        for item in items:
            missing = set(item["game_versions"]) - versions
            if missing or item["loader"] not in loaders:
                raise PublishError(f"Modrinth has no tags for {item['target']}: {sorted(missing)} / {item['loader']}. Use --targets to select supported targets.")
        remote = api.request(f"/project/{project}/version")
    jobs = [metadata(platform, item, project, changelog, release_type) for item in items]
    if platform == "curseforge":
        catalog = api.request("/game/versions")
        types = api.request("/game/version-types")
        for item, meta in zip(items, jobs):
            tags = [(name, "minecraft") for name in item["game_versions"]]
            tags += [(LOADERS[item["loader"]], "modloader"), (f"Java {item['java']}", "java"),
                     ("Client", "environment")]
            meta.pop("gameVersionNames")
            meta["gameVersions"] = [curseforge_tag_id(name, kind, catalog, types) for name, kind in tags]
    return project, jobs, remote


def remote_match(item, versions):
    for version in versions:
        for file in version["files"]:
            if file["hashes"].get("sha512") == item["sha512"]:
                return version["id"]
        if version["version_number"] == item["version_number"]:
            raise PublishError(f"Modrinth already has {item['version_number']} with different bytes. Use a new version.")
    return None


def state_key(platform, project, item):
    return f"{platform}:{project}:{item['filename']}"


def existing(state, key, item):
    record = state.get(key)
    if record and record["sha256"] != item["sha256"]:
        raise PublishError(f"{item['filename']} changed since a previous upload attempt. Use a new version.")
    return record


def upload(root, prepared, items, state, state_path, retry_uncertain=False):
    # Inspect all conflicts before the first POST, including attempts with a lost response.
    work = []
    for platform, api, project, jobs, remote in prepared:
        for item, meta in zip(items, jobs):
            key = state_key(platform, project, item)
            record = existing(state, key, item)
            remote_id = remote_match(item, remote) if platform == "modrinth" else None
            if remote_id:
                state[key] = {"sha256": item["sha256"], "id": remote_id, "status": "done"}
                write_json(state_path, state)
            if remote_id or (record and record["status"] == "done"):
                print(f"Skip {platform}: {item['filename']} (already uploaded)")
                continue
            if record and record["status"] != "rejected" and not retry_uncertain:
                raise PublishError(f"Uncertain previous upload: {key}. Check the site's Files page; only if absent, repeat with --retry-uncertain. Keep .publish/state.json.")
            work.append((platform, api, project, item, meta, key))
    for platform, api, project, item, meta, key in work:
        data = (root / item["file"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != item["sha256"]:
            raise PublishError(f"{item['filename']} changed after validation. Stop the build and retry.")
        body, content_type = multipart("metadata" if platform == "curseforge" else "data", meta, item["filename"], data)
        state[key] = {"sha256": item["sha256"], "status": "uncertain"}
        write_json(state_path, state)
        print(f"Upload {platform}: {item['filename']}", flush=True)
        path = f"/projects/{project}/upload-file" if platform == "curseforge" else "/version"
        try:
            response = api.request(path, body, content_type)
        except HTTPRejection as exc:
            # A rejected validation/auth/rate-limit request is different from a
            # timeout or 5xx response whose upload outcome can be unknown.
            if exc.status in (400, 401, 403, 404, 405, 409, 413, 415, 422, 429):
                state[key].update(status="rejected", http_status=exc.status)
                write_json(state_path, state)
            raise
        if not response.get("id"):
            raise PublishError(f"{platform}: missing uploaded file ID; check the site before retrying.")
        state[key] = {"sha256": item["sha256"], "id": response["id"], "status": "done"}
        write_json(state_path, state)
        print(f"  File/version ID: {response['id']}", flush=True)


def main(argv=None):
    load_env(ROOT / ".env")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Defaults to mod_version in gradle.properties")
    parser.add_argument("--platforms", nargs="+", choices=list(APIS), default=list(APIS))
    parser.add_argument("--targets", help="Comma-separated Gradle target names; default: all configured targets")
    changes = parser.add_mutually_exclusive_group()
    changes.add_argument("--changelog")
    changes.add_argument("--changelog-file", type=Path)
    parser.add_argument("--release-type", choices=["release", "beta", "alpha"], default="release")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--upload", action="store_true", help="Actually send files")
    mode.add_argument("--check", action="store_true", help="Validate credentials, project and tags, without uploading")
    mode.add_argument("--dry-run", action="store_true", help="Offline preview (also the default)")
    parser.add_argument("--retry-uncertain", action="store_true", help="Retry after manually confirming an uncertain upload is absent")
    args = parser.parse_args(argv)
    args.platforms = list(dict.fromkeys(args.platforms))
    version = args.version or properties(ROOT / "gradle.properties")["mod_version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?", version):
        raise PublishError("Invalid version")
    config = json.loads((ROOT / "publish.json").read_text(encoding="utf-8-sig"))
    for platform in args.platforms:
        override = os.environ.get(platform.upper() + "_PROJECT_ID")
        if override:
            config[platform]["project_id"] = override
    for platform in args.platforms:
        project = str(config[platform]["project_id"])
        if (args.upload or args.check) and (not config[platform].get("project_id") or not re.fullmatch(r"[0-9]+" if platform == "curseforge" else r"[A-Za-z0-9_-]+", project)):
            raise PublishError(f"Invalid {platform} project_id in publish.json")
    selected = [s.strip() for s in args.targets.split(",")] if args.targets else None
    items = artifacts(ROOT, version, selected)
    if args.changelog_file:
        changelog = args.changelog_file.read_text(encoding="utf-8-sig").strip()
    else:
        changelog = args.changelog
    if changelog is None:
        notes = ROOT / "build" / f"release-notes-{version}.md"
        changelog = notes.read_text(encoding="utf-8-sig").strip() if notes.exists() else ""
    if (args.upload or args.check) and not changelog:
        raise PublishError("Provide --changelog or --changelog-file (or use release.bat -Publish).")
    plan = {"version": version, "artifacts": items, "platforms": {
        p: [metadata(p, i, config[p]["project_id"], changelog, args.release_type) for i in items]
        for p in args.platforms}}
    plan_path = ROOT / "build" / f"publish-plan-{version}.json"
    write_json(plan_path, plan)
    print(f"Plan: {plan_path} ({len(items)} jars, {', '.join(args.platforms)})", flush=True)
    if not args.upload and not args.check:
        print("Offline preview only. Use --check to validate APIs or --upload to publish.")
        return
    missing = [TOKEN_VARS[p] for p in args.platforms if not os.environ.get(TOKEN_VARS[p])]
    if missing:
        raise PublishError("Set environment variables (never commit tokens): " + ", ".join(missing))
    prepared = []
    for platform in args.platforms:
        api = API(platform, os.environ[TOKEN_VARS[platform]])
        project, jobs, remote = prepare(platform, config, items, changelog, args.release_type, api)
        prepared.append((platform, api, project, jobs, remote))
    print("API credentials and all selected platform tags validated.")
    if args.check:
        return
    state_path = ROOT / ".publish" / "state.json"
    state_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path = state_path.parent / "upload.lock"
    try:
        lock = lock_path.open("x")
    except FileExistsError:
        raise PublishError("Another publisher is running. After a crash, remove .publish/upload.lock before retrying.") from None
    try:
        with lock:
            lock.write(str(os.getpid()))
        state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.exists() else {}
        upload(ROOT, prepared, items, state, state_path, args.retry_uncertain)
    finally:
        lock_path.unlink()
    print("Done. Platform moderation may still be pending.")


if __name__ == "__main__":
    try:
        main()
    except (PublishError, OSError, ValueError, KeyError, StopIteration, zipfile.BadZipFile) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
