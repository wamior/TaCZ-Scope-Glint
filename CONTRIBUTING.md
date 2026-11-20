# Build and release

Use JDK 21 and Python 3.11 or later. Forge outputs target Java 17; the NeoForge output targets Java 21.

```sh
python scripts/download_dependencies.py
./gradlew buildAll
python scripts/verify_artifacts.py
python -m unittest discover -s scripts
```

On Windows, use `gradlew.bat`. All release jars appear under `build/libs/<mod version>/<Minecraft version>/`.
The four TaCZ dependencies are downloaded from upstream Modrinth files and verified against SHA-512 hashes in `libs/manifest.json`. They are compile/runtime dependencies only and are not bundled or committed.

To build a single target:

```sh
./gradlew -Pbuild_targets=forge-1.20.1 :forge-1.20.1:build
```

Quote those arguments when invoking Gradle directly from PowerShell.

## Publishing configuration

1. Create the project's pages on Modrinth and CurseForge.
2. Enter their project IDs in `publish.json`. On Modrinth, the project slug is also accepted.
3. Put the owner/upload credentials in local `.env`:

```dotenv
MODRINTH_TOKEN=
CURSEFORGE_TOKEN=
```

`.env` is ignored by Git. `.env.example` contains empty placeholders. Environment variables, including GitHub Actions secrets, take precedence over the file. Tokens are read literally and never executed as shell code.

For local uploads, `publish.bat` defaults to an **offline preview**. Run `publish.bat --changelog-file CHANGELOG.md --check` to verify credentials and platform metadata without uploading; add `--upload` to send the files.

To configure GitHub Actions from the local `.env` and `publish.json`, run:

```sh
python scripts/setup_publishing.py
```

This sets the repository secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`, and the variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`. GitHub CLI must be logged in as a repository administrator. Tokens go through standard input, not command arguments or logs.

## Releasing

From a clean `main` branch:

```bat
release.bat 1.1.1 "Describe the changes here"
```

The script updates `gradle.properties`, `update.json` and `CHANGELOG.md`, builds and tests all four targets, verifies their contents, commits these three version files and atomically pushes `main` and the annotated version tag. The tag starts the release workflow.

Use `--dry-run` to build and validate without commits, tags or uploads; version files are restored afterward. Use `--prepare-only` to leave the prepared version files for review without pushing.

The release workflow:

1. Verifies that the tag, mod version, changelog and update metadata agree.
2. Builds and tests each Minecraft version independently.
3. Publishes all four validated jars to GitHub Releases.
4. Uploads the **same jars** to Modrinth and CurseForge with TaCZ as a required dependency.

If platform IDs or credentials are missing, the platform job fails with an explanation; the GitHub release remains available. After configuring them, rerun the failed job. The `publish-state` workflow artifact preserves upload receipts across job retries. For local uploads, keep `.publish/state.json`. An uncertain CurseForge upload requires checking the project's Files page before using `--retry-uncertain`; identical Modrinth files are detected by hash and skipped.

The publication APIs follow the official [Modrinth version API](https://docs.modrinth.com/api/operations/createversion/) and [CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api).

## Update notifications

`updateJSONURL` in both loader metadata files points to this repository's `update.json`. Forge/NeoForge performs its built-in asynchronous check; the client waits for its result and shows a clickable chat notice at most once per game launch. Disable `updateNotices` in the common config to hide notices. No update is installed automatically.

`update.json` only promotes the current mod version for the four supported Minecraft versions. Future releases update it together with their version and changelog.
