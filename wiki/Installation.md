# Installation

## Requirements

| | |
|---|---|
| **Minecraft** | 1.21 – 1.21.11 — see [Supported versions](#supported-versions) |
| **Fabric Loader** | ≥ 0.19.3 |
| **Fabric API** | required |
| **Java** | 21 or newer |

## Supported versions

mcMMO-SP ships **one build per version band**. A band is a run of Minecraft versions across which the mod's Minecraft-facing surface is identical, so a single jar covers all of them.

| Minecraft | Jar file | Fabric API | Mod Menu | Cloth Config |
|---|---|---|---|---|
| **1.21.11** | `mcmmo-<version>+mc1.21.11.jar` | `0.141.4+1.21.11` | `17.0.0` | `21.11.153` |
| **1.21.9 – 1.21.10** | `mcmmo-<version>+mc1.21.9-1.21.10.jar` | `0.138.4+1.21.10` | `16.0.1` | `20.0.149` |
| **1.21.6 – 1.21.8** | `mcmmo-<version>+mc1.21.6-1.21.8.jar` | `0.136.1+1.21.8` | `15.0.2` | `19.0.147` |
| **1.21.5** | `mcmmo-<version>+mc1.21.5.jar` | `0.128.2+1.21.5` | `14.0.2` | `18.0.145` |
| **1.21.4** | `mcmmo-<version>+mc1.21.4.jar` | `0.119.4+1.21.4` | `13.0.4` | `17.0.144` |
| **1.21.2 – 1.21.3** | `mcmmo-<version>+mc1.21.2-1.21.3.jar` | `0.114.1+1.21.3` | `12.0.1` | `16.0.143` |
| **1.21 – 1.21.1** | `mcmmo-<version>+mc1.21-1.21.1.jar` | `0.116.15+1.21.1` | `11.0.4` | `15.0.140` |

Every band needs Fabric Loader ≥ 0.19.3 and Java 21+. The `+mc…` suffix on the filename is the band that jar serves — a single version (`+mc1.21.5`) or a range written out at both ends (`+mc1.21.6-1.21.8`, which covers `1.21.7` too). On the [Releases page](https://github.com/Wulfic/mcMMO-Singleplayer/releases), pick the jar whose label contains your Minecraft version; the filename is the whole answer, so there is nothing to cross-reference.

That label is only the half you read. Each jar also declares the same band as a dependency range in `fabric.mod.json`, which is the half Fabric Loader **enforces** — a mismatched install is stopped at startup with a clear message rather than misbehaving quietly. A test keeps the two in agreement, so a filename promising a version the loader would refuse fails the build instead of reaching this page.

**Minecraft 1.20.6 and older are not supported**, and neither is the `26.x` line yet.

### What differs between bands

The mod is the same on every band. Only two features depend on what your Minecraft version actually contains:

| Feature | Needs | On older bands |
|---|---|---|
| **The [Spears](Skills#spears) skill** | **1.21.11+** | Spear items don't exist below 1.21.11, so the skill is **switched off** — no XP, no procs, no XP bar and no `/mcstats` line. `/mcstats spears` tells you it's the Minecraft version rather than a config setting, so you aren't sent to edit a `coreskills.yml` key that cannot help. |
| **Copper gear** — [Repair](Skills#repair) and [Salvage](Skills#salvage-child) of copper equipment, copper gear as [Fishing](Skills#fishing) treasure, `copper_nugget` from Hylian Luck, and [Smelting](Skills#smelting-child)'s copper nugget XP row | **1.21.9+** | Those config rows find nothing to match and sit inert. Everything else in Repair, Salvage, Fishing and Smelting works normally. |

Nothing else is version-gated. mcMMO asks the game's own registries what exists rather than carrying a table of version numbers, so no band has to be told what it can furnish — which is also why a skill that *is* available never needs a per-version config tweak.

## Steps

1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.19.3) for your Minecraft version.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods/`.
3. Drop the mcMMO jar for your band from the [Releases page](https://github.com/Wulfic/mcMMO-Singleplayer/releases) into `.minecraft/mods/` — the one whose `+mc…` label contains your Minecraft version. See [Supported versions](#supported-versions).
4. Launch the game. Configs are generated on first world load.

That's it. mcMMO has no other hard dependencies, and nothing needs configuring before you play.

Optionally add **Mod Menu + Cloth Config** for an in-game settings screen, and **Advancement Plaques** for fancier milestone popups — see [Optional Integrations](Optional-Integrations). None of them are bundled and none are required.

---

## Where things go

### Configuration — global, one copy

```
.minecraft/config/mcmmo/
```

Written on first load. See [Configuration](Configuration) for what each file controls.

### Save data — per world

```
<world save>/mcmmo/players/
```

Skill data is stored **per world, not globally**. Each player gets their own flat file.

This has consequences worth knowing:

- **Copying a world copies its skills.** Backing up a world backs up your progression.
- **Deleting the `mcmmo` folder resets progression for that world only.** Other worlds are untouched.
- **A new world starts you at zero.** There is no global profile.

The same folder also holds `placed_blocks.dat`, the anti-exploit record of blocks you placed yourself (so you can't farm XP by placing and re-breaking the same block, across restarts).

---

## Single-player, LAN and servers

The mod declares `"environment": "*"` and runs on both sides, so it works in single-player, on an opened-to-LAN world, and on a dedicated Fabric server.

But the **multiplayer feature set was removed during the port**, not merely disabled — no parties, no party chat or teleport, no XP sharing, no scoreboards, no admin broadcast tree, no MySQL. On a server, mcMMO-SP is simply "everyone has their own skills, independently."

If you want the full multiplayer experience, use [upstream mcMMO](https://github.com/mcMMO-Dev/mcMMO) on a Paper/Spigot server instead. That is what it is for.

---

## Upgrading

Replace the jar. Config files are **not** overwritten.

> ⚠️ **Editing defaults in the jar does not update an existing config.** On load, mcMMO back-fills keys that are *absent* from your config file, but keys already present on disk are left exactly as they are — including ones you never touched. So if a new release changes a *default*, your existing config keeps the old value silently.
>
> To pick up a changed default, delete the key (or the whole file) and let it regenerate.

If a skill was renamed between releases, mcMMO logs a warning naming the old and new config section rather than rewriting your file for you. Your config is yours; a silent rewriter is a worse failure mode than a log line.
