# TaCZ Scope Glint

TaCZ Scope Glint adds a bright glint to magnified optical scopes while a player aims, giving opponents a chance to spot a sniper. Red-dot and holographic sights do not glint, even when their ADS view zooms in.

The glint appears near the shooter's scope when they aim toward you. By default, you must also be aiming or using a spyglass to see it. Solid cover blocks the effect; glass and foliage can let it through.

## Scopes and gunpacks

The mod reads TaCZ's attachment data, so third-party gunpacks work without a separate list of supported items. Both installed scopes and built-in scope attachments are supported.

On TaCZ 1.2.0, hybrid sights use the selected view and its optical lens nodes. Switching to a red dot disables the glint even if that mode has a high ADS zoom. Older TaCZ builds without this lens metadata exclude ambiguous hybrid sights.

Gunpack authors must mark their attachments correctly. A collimator incorrectly marked as an optical scope can still be treated as one. Missing or ambiguous hybrid lens data disables the effect.

## Configuration

The `taczscopeglint-common.toml` file controls:

- Whether the observer must be aiming or using a spyglass. Default: **enabled**.
- The minimum current scope zoom, which must be strictly exceeded. Default: **1.5×**.
- The maximum glint distance. Default: **128 blocks**.
- The viewing angle from the shooter's aim direction. Default: **45°**.
- The glint size. Default: **1.0**.
- Clickable update notices, shown once per game launch. Default: **enabled**.

The mod uses the players already tracked by Minecraft. It does not increase player render distance or server tracking range.

## Requirements

Install TaCZ Scope Glint on the **client**, alongside TaCZ for the same Minecraft version. The server does not need this addon.

| Minecraft | Loader | TaCZ build used for compilation |
|---|---|---|
| 1.18.2 | Forge | 1.1.4-hotfix2 |
| 1.19.2 | Forge | 1.1.4-hotfix2 |
| 1.20.1 | Forge | 1.2.0-hotfix |
| 1.21.1 | NeoForge | 1.2.0-hotfix |

## Modpacks and support

You can include unmodified release builds in modpacks on CurseForge or Modrinth without asking permission.

Report bugs or suggest changes on [GitHub Issues](https://github.com/wamior/TaCZ-Scope-Glint/issues).
