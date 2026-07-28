# Installation

## Requirements

| | |
|---|---|
| **Minecraft** | 1.21.11 |
| **Fabric Loader** | ≥ 0.19.3 |
| **Fabric API** | required |
| **Java** | 21 or newer |

## Steps

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods/`.
3. Drop the mcMMO jar from the [Releases page](https://github.com/Wulfic/mcMMO-Singleplayer/releases) into `.minecraft/mods/`.
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
