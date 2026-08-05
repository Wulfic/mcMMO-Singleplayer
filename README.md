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

📖 **[Full documentation is on the Wiki](../../wiki)** — per‑skill pages, every sub‑skill's numbers,
the complete config reference and a troubleshooting guide. This README is the short version.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods/`.
3. Drop the mcMMO jar from [Releases](../../releases) into `.minecraft/mods/`.
4. Launch. Configs are generated on first world load.

Optionally add **Mod Menu + Cloth Config** (in‑game settings screen) and **Advancement Plaques**
(fancy milestone popups) — see [Optional mod integrations](#optional-mod-integrations).

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

### Admin commands

Require **permission level 2** (op level 2, or "Allow Cheats" in a single‑player world).

| Command | What it does |
|---|---|
| `/mcrefresh` | Clear all of your super‑ability cooldowns and cancel any active ability. |
| `/addlevels <skill\|all> <amount>` | Grant skill levels directly. |
| `/addxp <skill\|all> <amount>` | Grant raw XP through the real gain pipeline (so level‑ups, milestones and the XP bar all fire normally). |

`<skill>` accepts any skill name, lowercased (`mining`, `woodcutting`, `tridents`, …); `all` targets
every non‑child skill.

> **Not ported:** `/party`, `/ptp`, `/mcchat`, `/mcscoreboard`, `/mmoedit`, `/mcgod`, `/inspect`,
> `/mcconvert` and the rest of the multiplayer/admin tree. They were cut with the multiplayer layer.

---

## Skills

**26 skills** — **23 primary** skills that earn XP directly, plus **3 child skills** whose level is
the average of their parents and which earn no XP of their own.

| Category | Skills |
|---|---|
| **Gathering** | Mining, Woodcutting, Herbalism, Excavation, Fishing, **Husbandry** |
| **Combat** | Swords, Axes, Unarmed, Archery, Crossbows, Tridents, Maces, Spears, Taming, **Hunter** |
| **Movement** | **Parkour**, **Swimming**, **Flying** |
| **Misc** | **Stealth**, **Unarmored**, Repair, Alchemy |
| **Child skills** | **Agility** (avg. of Parkour + Swimming + Flying), **Salvage** (avg. of Repair + Fishing), **Smelting** (avg. of Mining + Repair) |

### New in this port

Eight skills that upstream mcMMO does not have. **Acrobatics was renamed to Agility** and
restructured: it now owns ten movement sub-skills but earns no XP itself — instead it is the mean of
three new primary skills, one per medium you travel through.

| Skill | How you train it | What it gives you |
|---|---|---|
| **Parkour** | Sprinting and falling on land | Feeds Agility. Owns **Roll** (negate fall damage; hold sneak on landing for a Graceful Roll at double odds) and **Snow Walker** (cross powder snow without sinking). |
| **Swimming** | Swimming | Feeds Agility. |
| **Flying** | Elytra gliding | Feeds Agility. |
| **Agility** *(child)* | — derived — | Dodge, Fleet Footed, Athlete, Smash, Lead Lungs, Glide, Lake Raider, Solar Wings, and the **Second Wind** super ability. |
| **Stealth** | Sneaking under your own power | **Padfoot** (sneak nearly at walking speed), **Assassin** (backstab damage), **Smoke Bomb** super ability. |
| **Unarmored** | Taking damage with **every armour slot empty** | **Iron Skin** (real armour points at four tiers — leather/gold/iron/diamond) and **Thorny Skin** (reflect a sting at melee attackers). |
| **Husbandry** | Breeding, taming, shearing, milking, feeding and robbing hives | Nine sub-skills across six XP verbs — **Multi-Breed**, **Twins**, **Selective Breeding**, **Accelerated Growth**, **Brood**, **Bountiful Harvest**, **Hidden Bounty**, **Beekeeper** and the **Herdsman's Call** super ability. |
| **Hunter** | Killing creatures — **not** a weapon skill | Two independent axes. **Mob Mastery**: kill 500 / 2,500 / 10,000 of *one* creature for +1.0 / +2.0 / +3.0 damage against **that creature only**, forever. **Trophy Hunter**: a second roll of a kill's own loot table, unlocked one mob tier per rank. **Quarry Sense**: crouch and hit a creature with a bone to read your hunt log against it. Farmed creatures — spawner, bred, player-placed — count for nothing. |

Movement and sneak XP are **speed-normalised**: you are paid per *second* of travel, with each tick's
distance clamped at that medium's reference speed. Travelling faster than the reference pays no more,
so speed buffs, elytra rockets and ice boats are not XP multipliers. Standing still pays nothing,
walking pays nothing, and being *carried* pays nothing — Stealth reads your actual server-side
movement input, so a taped-down shift key in a water current earns zero.

> **Still planned:** Cooking — the design is drafted in [`plans/new-skills/`](plans/new-skills/), no
> code yet. Husbandry and Hunter are **code-complete but not yet play-tested**, along with the six
> movement/stealth skills above; see [`PLAYTEST_G.md`](PLAYTEST_G.md).

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

### Item‑triggered abilities

The two Pass‑2 abilities are **not** readied with a tool — they fire immediately on right‑click while
holding a configured item, which is **never consumed**:

| Ability | Skill | Trigger item | Effect |
|---|---|---|---|
| **Second Wind** | Agility | `FEATHER` | One ability, three bodies, chosen by how you are moving — a forward **lunge** on land, a **water buff** while swimming, a **speed burst** while gliding. Rank 1 unlocks land, 2 water, 3 air. |
| **Smoke Bomb** | Stealth | `GUNPOWDER` | Vanilla Invisibility for 5 s. Note that vanilla invisibility does **not** hide armour or held items. |

Both items are configurable in `config.yml` (`Skills.Agility.Second_Wind_Item`,
`Skills.Stealth.Smoke_Bomb_Item`) and **must differ from each other** — they listen on the same
use‑item event, so sharing an item fires one and prints the other's refusal message.

**Call of the Wild** (Taming) is a **sneak + left‑click a block** while holding the summon item.
Sneak‑left‑clicking *air* is the one gesture that isn't wired — Fabric has no left‑click‑air
callback.

---

## In‑game feedback

- **XP boss bar** — a fading, per‑skill XP bar appears above the hotbar as you train. Configure or
  disable it in `experience.yml` under `Experience_Bars` (`Enable`, `Hide_Delay_Seconds` default
  `10`, `Max_Visible` default `3`). Bars stack downward over the hotbar, so the count is capped —
  sprinting through a forest while mining can easily have five skills live at once, and the least
  recently trained bar is hidden to make room.
- **Milestone advancements** — hidden vanilla advancements are granted on round levels, rank
  unlocks, maxing a skill, and power‑level tiers. Optionally rendered as plaques — see
  [Optional mod integrations](#optional-mod-integrations).
- **Action‑bar + chat notifications** and **sound cues** for ability start/stop, level‑ups and
  sub‑skill procs.

---

## Optional mod integrations

mcMMO works fully standalone. These mods are **purely optional** — none are bundled, none are
declared as dependencies, and mcMMO detects each at runtime and degrades gracefully if it's absent.

| Mod | Side | What you get without it | What you get with it |
|---|---|---|---|
| **[Mod Menu](https://modrinth.com/mod/modmenu)** + **[Cloth Config](https://modrinth.com/mod/cloth-config)** | Client | Edit the YAML files by hand. | An in‑game **settings screen** for mcMMO, reachable from the mod list. |
| **[Advancement Plaques](https://modrinth.com/mod/advancement-plaques)** | Client | Milestones show as normal vanilla advancement toasts. | Milestones show as large animated **plaques**. |

### Mod Menu + Cloth Config — in‑game config editor

Install **both** (Cloth Config builds the widgets, Mod Menu provides the entry point) and mcMMO gains
a config screen from the mod list. Versions targeting MC 1.21.11: Mod Menu `17.0.0`, Cloth Config
`21.11.153`.

Edits are written straight back to the YAML on disk and take effect on the **next world load** — not
instantly, since most values are read once at load time.

With **Mod Menu but no Cloth Config**, the button still works but opens a small info screen with an
*Open Config Folder* shortcut instead of the editor. With **neither**, nothing is lost — hand‑editing
YAML remains the way in, and the mod runs identically.

### Advancement Plaques — milestone plaques

Advancement Plaques has **no API**, so there is nothing to hook. Instead mcMMO grants *hidden vanilla
advancements* at each milestone, which Advancement Plaques picks up and renders on its own. That
means **zero dependency in either direction**: with the mod you get plaques, without it you get the
ordinary toast, and the advancements are granted identically either way.

Milestones that fire a plaque:

| Milestone | Trigger |
|---|---|
| **Round level** | A skill crosses a multiple of `Level_Interval` (default `100`). |
| **Rank unlock** | Any sub‑skill of a skill reaches a new rank. |
| **Skill maxed** | A skill hits its level cap. |
| **Power tier** | Total power level crosses 500 / 1 000 / 2 000 / 3 500 / 5 000 / 10 000. |

Configure or switch the whole system off in `config.yml`:

```yaml
General:
    Milestone_Advancements:
        Enabled: true
        Level_Interval: 100
```

Because the advancements are hidden, they never clutter the vanilla advancement tree.

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
| `coreskills.yml` | The per‑skill master switch — turn a whole skill off (no XP, no procs, no super, no XP bar, no `/mcstats`, no plaques). |
| `treasures.yml` / `fishing_treasures.yml` | Excavation & Fishing loot tables, Hylian Luck, shake drops. |
| `repair.vanilla.yml` / `salvage.vanilla.yml` | Repairable/salvageable items and their materials. |
| `potions.yml` | Alchemy brewing tree and custom potion concoctions. |
| `sounds.yml` | Per‑event sound and volume/pitch tuning. |
| `hidden.yml` | Rarely‑touched internals. |

> ⚠️ **Editing defaults in the jar does not update an existing config.** New keys are back‑filled on
> load, but keys already present on disk are left alone. To pick up a changed default, delete the key
> (or the file) and let it regenerate.

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

> ⚠️ **The six new skills are code‑complete but lightly play‑tested.** Parkour, Swimming, Flying,
> the restructured Agility, Stealth and Unarmored all pass the unit suite and boot clean, but their
> XP rates and reference speeds are **starting estimates, not measured numbers** — the tuning
> comments in `experience.yml` say so explicitly. Balance feedback on these is especially welcome.
> The in‑game verification plan lives in [`PLAYTEST_G.md`](PLAYTEST_G.md).

> ⚠️ **Existing Acrobatics progress does not carry over.** Acrobatics was renamed Agility, and
> Agility then became a *child* skill — child skills have no save key at all, their level is
> recomputed from their parents on every load. There is nothing for old progress to migrate *to*, so
> it is deliberately allowed to zero out; train Parkour, Swimming and Flying instead. If you had
> tuned an `Acrobatics:` section in a config, mcMMO logs a warning telling you to rename it to
> `Agility:` rather than silently rewriting your file.

Deliberately **not** ported (and not coming back):

- Parties, party chat, teleport, XP sharing
- Admin chat, scoreboards, MOTD/broadcast systems
- MySQL and database conversion tooling
- Chimaera Wing, Limit Break, permission‑node integrations
- The **Spears**, **Maces**, **Tridents** and **Crossbows** super abilities — registered placeholders
  with no behaviour, upstream included. The skills themselves are fully playable.

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
