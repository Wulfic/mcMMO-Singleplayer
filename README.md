# mcMMO‑SP — Single‑Player Fabric Port

A port of **[mcMMO](https://github.com/mcMMO-Dev/mcMMO)** from a Bukkit/Spigot server plugin to a
standalone **Fabric mod**, rebuilt around single‑player. RPG skills, leveling, sub‑skills and active
super abilities for vanilla Minecraft — no server, no database, no plugin platform.

| | |
|---|---|
| **Minecraft** | 1.21.11 |
| **Mod loader** | Fabric Loader ≥ 0.19.3 |
| **Required dependency** | Fabric API |
| **Java** | 21+ |
| **License** | GPL‑3.0‑only (inherited from upstream mcMMO) |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods/`.
3. Drop the mcMMO jar from [Releases](../../releases) into `.minecraft/mods/`.
4. Launch. Configs are generated on first world load.

The mod runs on both sides (`"environment": "*"`) and works in single‑player, on LAN, and on a
dedicated Fabric server — but the multiplayer feature set (parties, chat channels, scoreboards,
admin broadcasts, MySQL) was **removed** during the port, so a server install is just "everyone has
their own skills."

---

## Commands

All commands are Brigadier‑registered, so tab‑completion works everywhere.

### Player commands

| Command | What it does |
|---|---|
| `/mcmmo` | Mod + version banner. |
| `/mcstats` | Level, current XP and XP‑to‑next for every skill, plus your **power level**. |
| `/mcstats <skill>` | The full per‑skill screen — XP‑gain methods, sub‑skill ranks, and the *live computed values* of every sub‑skill effect at your current level (chance to activate, damage bonus, duration, drop rates…). This is the port's equivalent of legacy mcMMO's `/mining`, `/swords`, `/archery`, … commands. |
| `/mcability` | Toggle whether super abilities may be readied/activated at all. Useful when you're building and don't want Super Breaker firing. |
| `/mcrefresh` | Clear all of your super‑ability cooldowns and cancel any active ability. |

### Admin commands

Require **permission level 2** (op level 2, or "Allow Cheats" in a single‑player world).

| Command | What it does |
|---|---|
| `/addlevels <skill\|all> <amount>` | Grant skill levels directly. |
| `/addxp <skill\|all> <amount>` | Grant raw XP through the real gain pipeline (so level‑ups, milestones and the XP bar all fire normally). |

`<skill>` accepts any skill name, lowercased (`mining`, `woodcutting`, `tridents`, …); `all` targets
every non‑child skill.

> **Not ported:** `/party`, `/ptp`, `/mcchat`, `/mcscoreboard`, `/mmoedit`, `/mcgod`, `/inspect`,
> `/mcconvert` and the rest of the multiplayer/admin tree. They were cut with the multiplayer layer.

---

## Skills

**17 primary skills** plus **2 child skills** whose level is derived from their parents.

| Category | Skills |
|---|---|
| **Gathering** | Mining, Woodcutting, Herbalism, Excavation, Fishing |
| **Combat** | Swords, Axes, Unarmed, Archery, Crossbows, Tridents, Maces, Spears |
| **Misc** | Acrobatics, Taming, Repair, Alchemy |
| **Child skills** | **Salvage** (avg. of Repair + Fishing), **Smelting** (avg. of Mining + Repair) |

**RetroMode is on by default** — levels scale 1–1000 rather than 1–100, and every level requirement
in the configs is multiplied by 10. Turn it off in `config.yml` under `General.RetroMode.Enabled`.

---

## Super abilities

Super abilities use the classic **two‑step gesture**:

1. **Ready** — hold the skill's tool and **right‑click** (on an activatable block, or in the air).
   You get the "you ready your tool" message and a short arming window.
2. **Activate** — **left‑click** a block the ability affects while the tool is readied.

| Ability | Skill | Tool |
|---|---|---|
| Super Breaker | Mining | Pickaxe |
| Giga Drill Breaker | Excavation | Shovel |
| Tree Feller | Woodcutting | Axe |
| Green Terra | Herbalism | Hoe |
| Berserk | Unarmed | Empty hand |
| Serrated Strikes | Swords | Sword |
| Skull Splitter | Axes | Axe |
| Blast Mining | Mining | Right‑click TNT with the detonator (flint & steel) |

Combat abilities (Serrated Strikes, Skull Splitter, Berserk) also arm on a right‑click and then fire
on your next hit.

**Call of the Wild** (Taming) is a **sneak + left‑click a block** while holding the summon item.
Sneak‑left‑clicking *air* is the one gesture that isn't wired — Fabric has no left‑click‑air
callback.

---

## In‑game feedback

- **XP boss bar** — a fading, per‑skill XP bar appears above the hotbar as you train. Configure or
  disable it in `experience.yml` under `Experience_Bars` (`Enable`, `Hide_Delay_Seconds`, default
  `10`).
- **Milestone advancements** — hidden vanilla advancements are granted on round levels, rank
  unlocks, maxing a skill, and power‑level tiers (500 / 1 000 / 2 000 / 3 500 / 5 000 / 10 000).
  These render as normal advancement toasts, or as custom plaques if you also run the client‑side
  [Advancement Plaques](https://modrinth.com/mod/advancement-plaques) mod. mcMMO does **not** depend
  on it. Toggle in `config.yml` → `General.Milestone_Advancements`.
- **Action‑bar + chat notifications** and **sound cues** for ability start/stop, level‑ups and
  sub‑skill procs.

---

## Configuration

Configs are plain YAML, written on first load to:

```
.minecraft/config/mcmmo/
```

| File | Controls |
|---|---|
| `config.yml` | Master switches: RetroMode, milestone advancements, ability durations, bonus‑drop lists, anti‑exploit toggles, per‑command enables. |
| `advanced.yml` | The numbers behind every sub‑skill — activation chances, damage bonuses, max levels, caps. |
| `experience.yml` | XP curve, per‑skill XP tables, XP bars, diminishing returns, exploit fixes. |
| `skillranks.yml` | The level at which each sub‑skill rank unlocks (standard **and** RetroMode ladders). |
| `coreskills.yml` | Enable/disable whole skills and individual sub‑skills. |
| `treasures.yml` / `fishing_treasures.yml` | Excavation & Fishing loot tables, Hylian Luck, shake drops. |
| `repair.vanilla.yml` / `salvage.vanilla.yml` | Repairable/salvageable items and their materials. |
| `potions.yml` | Alchemy brewing tree and custom potion concoctions. |
| `sounds.yml` | Per‑event sound and volume/pitch tuning. |
| `hidden.yml` | Rarely‑touched internals. |

> ⚠️ **Editing defaults in the jar does not update an existing config.** New keys are back‑filled on
> load, but keys already present on disk are left alone. To pick up a changed default, delete the key
> (or the file) and let it regenerate.

### In‑game config editor (optional)

Install **[Mod Menu](https://modrinth.com/mod/modmenu)** + **[Cloth
Config](https://modrinth.com/mod/cloth-config)** to get a settings screen from the mod list. Neither
is bundled and neither is required — without them mcMMO just falls back to editing YAML by hand.
Edits are written to disk and applied on the **next world load**.

---

## Save data

Skill data is stored **per world**, not globally:

```
<world save>/mcmmo/players/
```

Each player gets their own flat file. Copying a world copies its skills; deleting the `mcmmo`
folder resets progression for that world only.

---

## Building from source

```bash
./gradlew build
```

Requires JDK 21. The remapped mod jar lands in `build/libs/`. The JUnit suite runs as part of
`build`, so a failing test fails the build. Useful targets:

```bash
./gradlew test        # unit tests only
./gradlew runServer   # headless dev server
./gradlew runClient   # dev client
```

Releases are built and published automatically by [`.github/workflows/release.yml`](.github/workflows/release.yml)
on every push to `master` or an `mc/**` branch, keeping one "latest" release per Minecraft line.

---

## Port status & known gaps

The port is feature‑complete against upstream mcMMO's single‑player‑relevant surface and boots
clean, but it is **young** — expect rough edges and please file issues.

Deliberately **not** ported (and not coming back):

- Parties, party chat, teleport, XP sharing
- Admin chat, scoreboards, MOTD/broadcast systems
- MySQL and database conversion tooling
- Chimaera Wing, Limit Break, permission‑node integrations
- **Spears**' super ability — the sub‑skill depends on a custom item and a `spear` damage type that
  do not exist in 1.21.11

---

## License & attribution

**Project:** mcMMO‑SP (Single‑Player Port)
**Based on:** [mcMMO](https://github.com/mcMMO-Dev/mcMMO) by the mcMMO team
**Original license:** GNU General Public License v3.0 (GPL‑3.0) — see [LICENSE](LICENSE).

This repository contains a single‑player port and related changes to adapt mcMMO for
local/single‑player use. All original code from mcMMO remains under GPL‑3.0, and this fork's
modifications are likewise released under **GPL‑3.0**.

**How to obtain source** — the full source for every distributed binary is available in this
repository and in the [Releases](../../releases) section. Binary downloads include a link to this
source and the LICENSE file.

**Attribution and credits** — this project is a fork of mcMMO. Original authors and contributors
retain copyright. The full contributor list is preserved in this repository's git history and on the
[upstream contributors page](https://github.com/mcMMO-Dev/mcMMO/graphs/contributors); mcMMO was
created by **nossr50** and maintained by the mcMMO team.
