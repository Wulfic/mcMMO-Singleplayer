# mcMMO‑SP — Single‑Player Fabric Port

A port of **[mcMMO](https://github.com/mcMMO-Dev/mcMMO)** from a Bukkit/Spigot server plugin to a
standalone **Fabric mod**, rebuilt around single‑player. RPG skills, leveling, sub‑skills and active
super abilities for vanilla Minecraft — no server, no database, no plugin platform.

| | |
|---|---|
| **Minecraft** | 1.21.2 – 1.21.11 — one build per version band, see [Supported versions](#supported-versions) |
| **Mod loader** | Fabric Loader ≥ 0.19.3 |
| **Required dependency** | Fabric API |
| **Java** | 21+ |
| **License** | GPL‑3.0‑only (inherited from upstream mcMMO) |

📖 **[Full documentation is on the Wiki](../../wiki)** — per‑skill pages, every sub‑skill's numbers,
the complete config reference and a troubleshooting guide. This README is the short version.

---

## Supported versions

mcMMO‑SP ships **one build per version band**. A band is a run of Minecraft versions across which
the mod's Minecraft‑facing surface is identical, so a single jar covers all of them.

| Minecraft | Jar file | Fabric API | Mod Menu | Cloth Config |
|---|---|---|---|---|
| **1.21.11** | `mcmmo-<version>+mc1.21.11.jar` | `0.141.4+1.21.11` | `17.0.0` | `21.11.153` |
| **1.21.9 – 1.21.10** | `mcmmo-<version>+mc1.21.9-1.21.10.jar` | `0.138.4+1.21.10` | `16.0.1` | `20.0.149` |
| **1.21.6 – 1.21.8** | `mcmmo-<version>+mc1.21.6-1.21.8.jar` | `0.136.1+1.21.8` | `15.0.2` | `19.0.147` |
| **1.21.5** | `mcmmo-<version>+mc1.21.5.jar` | `0.128.2+1.21.5` | `14.0.2` | `18.0.145` |
| **1.21.4** | `mcmmo-<version>+mc1.21.4.jar` | `0.119.4+1.21.4` | `13.0.4` | `17.0.144` |
| **1.21.2 – 1.21.3** | `mcmmo-<version>+mc1.21.2-1.21.3.jar` | `0.114.1+1.21.3` | `12.0.1` | `16.0.143` |

Every band needs **Fabric Loader ≥ 0.19.3** and **Java 21+**. The `+mc…` suffix on the filename is
the band the jar serves: a single version (`+mc1.21.5`) or a range written out at both ends
(`+mc1.21.6-1.21.8`, which covers `1.21.7` as well). Pick the jar whose label contains your Minecraft
version — the name is the whole answer, so there is nothing to cross-reference.

That label is only the half you read. Each jar also declares the same band as a dependency range in
`fabric.mod.json`, which is the half Fabric Loader **enforces** — a mismatched install is stopped at
startup with a clear message instead of misbehaving quietly. The two are kept in agreement by a test
(`BandVersionLabelTest`), so a filename that promises a version the loader would refuse fails the
build rather than reaching a download page.

Minecraft **1.20.6 and older are not supported**, and neither is the `26.x` line yet.

### What differs between bands

The mod is the same on every band — same skills, same sub‑skills, same numbers. Only two things
depend on what your Minecraft version actually contains:

| Feature | Needs | On older bands |
|---|---|---|
| **The Spears skill** | **1.21.11+** | Spear items don't exist below 1.21.11, so the skill is **switched off** — no XP, no procs, no XP bar, and no `/mcstats` line. `/mcstats spears` tells you it's the Minecraft version rather than a config setting. |
| **Copper gear** — Repair, Salvage, copper equipment as Fishing treasure, `copper_nugget` from Hylian Luck, and Smelting's copper nugget row | **1.21.9+** | Those config rows simply find nothing to match. Everything else in Repair, Salvage, Fishing and Smelting works normally. |

Nothing else in the mod is version‑gated. mcMMO asks the game's own registries what exists rather
than carrying a table of version numbers, so a band never has to be told what it can furnish.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.19.3) for your Minecraft version.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods/`.
3. Drop the mcMMO jar whose `+mc…` label contains your Minecraft version, from [Releases](../../releases), into `.minecraft/mods/` — see
   [Supported versions](#supported-versions) for the exact filename.
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

**25 skills** — **23 primary** skills that earn XP directly, plus **2 child skills** whose level is
the average of their parents and which earn no XP of their own.

| Category | Skills |
|---|---|
| **Gathering** | Mining, Woodcutting, Herbalism, Excavation, Fishing, **Husbandry** |
| **Combat** | Swords, Axes, Unarmed, Archery, Crossbows, Tridents, Maces, Spears\*, Taming, **Hunter** |
| **Movement** | **Parkour**, **Swimming**, **Flying** |
| **Misc** | **Stealth**, **Unarmored**, Repair, Alchemy, **Cooking** |
| **Child skills** | **Salvage** (avg. of Repair + Fishing), **Smelting** (avg. of Mining + Repair) |

\* **Spears needs Minecraft 1.21.11+** — spear items don't exist below it, so on older bands the
skill is switched off entirely and does not appear in `/mcstats`. See
[Supported versions](#supported-versions).

### New in this port

Eight primary skills that upstream mcMMO does not have. **Acrobatics is replaced by three movement
skills**, one per medium you travel through. Each earns its own XP and owns the perks belonging to
its medium.

| Skill | How you train it | What it gives you |
|---|---|---|
| **Parkour** | Sprinting and falling on land | **Dodge**, **Roll** (hold sneak on landing for a Graceful Roll at double odds), **Athlete** (sprinting costs less hunger), **Smash** (harder sprint attacks) and **Snow Walker**. |
| **Swimming** | Swimming | **Lead Lungs** (far longer breath) and **Lake Raider** (underwater digging turns up treasure). |
| **Flying** | Elytra gliding | **Glide** (descend more slowly) and **Solar Wings** (a worn elytra mends in daylight). |
| **Stealth** | Sneaking under your own power | **Padfoot** (sneak nearly at walking speed), **Assassin** (backstab damage), **Smoke Bomb** super ability. |
| **Unarmored** | Taking damage with **every armour slot empty** | **Iron Skin** (real armour points at four tiers — leather/gold/iron/diamond) and **Thorny Skin** (reflect a sting at melee attackers). |
| **Husbandry** | Breeding, taming, shearing, milking, feeding and robbing hives | Nine sub-skills across six XP verbs — **Multi-Breed**, **Twins**, **Selective Breeding**, **Accelerated Growth**, **Brood**, **Bountiful Harvest**, **Hidden Bounty**, **Beekeeper** and the **Herdsman's Call** super ability. |
| **Hunter** | Killing creatures — **not** a weapon skill | Two independent axes. **Mob Mastery**: kill 500 / 2,500 / 10,000 of *one* creature for +1.0 / +2.0 / +3.0 damage against **that creature only**, forever. **Trophy Hunter**: a second roll of a kill's own loot table, unlocked one mob tier per rank. **Quarry Sense**: crouch and hit a creature with a bone to read your hunt log against it. Farmed creatures — spawner, bred, player-placed — count for nothing. |
| **Cooking** | Cooking food in a furnace, smoker, blast furnace or campfire, and crafting food at a bench | **Smelting's other half** — the two share the furnace and split it by input, ore paying Smelting and food paying Cooking, never both. **Master Chef** (a second helping out of a finished cook), **Power Cook** (cooked food carries a lingering effect when eaten, always amplifier 0), **Kitchen Efficiency** (fuel burns up to 4× longer on food). An item has no spawn origin, so its only anti-farm gate is a cap of 1,200 paid items per hour. |

All three movement skills additionally get their **own** copy of **Fleet Footed** (a movement-speed
bonus that scales with the skill) and their own body of the **Second Wind** super ability. Fleet
Footed unlocks at level **1** and Second Wind at **250**, separately in each of the three.

> **This is deliberately a buff for specialists.** Those two perks used to be gated on the *average*
> of Parkour, Swimming and Flying, which meant a player who only flew could never reach them at all —
> flying alone capped that average at 333, and the air ranks sat at 400 and 750. Now a pure flier
> gets air Fleet Footed at Flying 1 and the air Second Wind at Flying 250, and a pure swimmer the
> same in water. Being an all-rounder still earns *more* — all six perks instead of two — but it is
> no longer a **gate** on any one of them. Second Wind's strength and duration follow the medium you
> are actually moving through, and Dodge, Roll, Fall XP and Fleet Footed's scaling all read the skill
> that earns them rather than a three-skill average.

Movement and sneak XP are **speed-normalised**: you are paid per *second* of travel, with each tick's
distance clamped at that medium's reference speed. Travelling faster than the reference pays no more,
so speed buffs, elytra rockets and ice boats are not XP multipliers. Standing still pays nothing,
walking pays nothing, and being *carried* pays nothing — Stealth reads your actual server-side
movement input, so a taped-down shift key in a water current earns zero.

> **All eight are shipped and code‑complete**, including Cooking. What they are still short on is
> *play* — see the play‑test caveat under [Port status & known gaps](#port-status--known-gaps). Each
> skill's design document is in [`plans/completed/`](plans/completed/), and the in‑game verification
> plan is [`plans/PLAYTEST_G.md`](plans/PLAYTEST_G.md).

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
| **Second Wind** | Parkour / Swimming / Flying | `FEATHER` | One ability, three bodies, chosen by how you are moving — a forward **lunge** on land, a **water buff** while swimming, a **speed burst** while gliding. One cooldown shared across all three. Each medium unlocks its own body at level **250** in that skill, independently of the other two. |
| **Smoke Bomb** | Stealth | `GUNPOWDER` | Vanilla Invisibility for 5 s. Note that vanilla invisibility does **not** hide armour or held items. |

Both items are configurable in `config.yml` (`Skills.Movement.Second_Wind_Item`,
`Skills.Stealth.Smoke_Bomb_Item`) and **must differ from each other** — they listen on the same
use‑item event, so sharing an item fires one and prints the other's refusal message.

**Call of the Wild** (Taming) is a **sneak + left‑click a block** while holding the summon item.
Sneak‑left‑clicking *air* is the one gesture that isn't wired — Fabric has no left‑click‑air
callback.

**Pet combat stance** (Taming) is a **sneak + right‑click on a pet you own** while holding a bone.
It flips your pets between *passive* (they fight only what you fight) and *aggressive* (idle pets
pick the nearest hostile to you). The stance is **player‑wide** — the animal you click only proves
it's yours. ⚠️ The documented cost: while a bone is in your main hand that gesture changes the
stance **instead of sitting the pet**; a plain right‑click, or any other item, still sits it. See
[Taming](../../wiki/Skills#taming).

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
a config screen from the mod list. Both mods are versioned per Minecraft release — the pair that
matches each band is in the [Supported versions](#supported-versions) table.

The screen carries a tab per area — including a **Skills** tab with a master switch for every skill,
so turning one off no longer means finding and hand‑editing a YAML file.

Edits are written straight back to the YAML on disk and take effect on the **next world load** — not
instantly, since most values are read once at load time. That includes the skill switches: one turned
off from the pause menu keeps paying XP until you reload the world.

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
| `coreskills.yml` | The per‑skill master switch — turn a whole skill off (no XP, no procs, no super, no XP bar, no `/mcstats`, no plaques). Also the **Skills** tab of the in‑game settings screen (Mod Menu + Cloth Config). |
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

### Branches and releases

Each version band is its own branch. `master` **is** the newest supported band; `mc/**` branches
exist only for older ones.

| Branch | Band |
|---|---|
| `master` | 1.21.11 |
| `mc/1.21.10` | 1.21.9 – 1.21.10 |
| `mc/1.21.8` | 1.21.6 – 1.21.8 |
| `mc/1.21.5` | 1.21.5 |
| `mc/1.21.4` | 1.21.4 |
| `mc/1.21.3` | 1.21.2 – 1.21.3 |

A branch pins its own `minecraft_version` and `yarn_mappings` in `gradle.properties` and its own
band range in `fabric.mod.json`, so checking one out and running `./gradlew build` produces that
band's jar with no further configuration.

Fixes land on `master` first and are propagated to the band branches, each propagation commit
carrying a `Backport-of: <sha>` trailer naming the `master` commit it came from. Releases are
published per band and tagged `mc<minecraft version>-v<mod version>`. The tag deliberately differs
from the jar name: the jar is what a player reads, the tag is what the release automation keys on —
its `mc<version>-v` prefix is how each Minecraft line finds and retires its own previous release.

---

## Port status & known gaps

The port is feature‑complete against upstream mcMMO's single‑player‑relevant surface and boots
clean, but it is **young** — expect rough edges and please [file issues](https://github.com/Wulfic/mcMMO-Singleplayer/issues).

> ⚠️ **File them on *this* repo, not upstream mcMMO.** This is a fork; upstream
> maintains the Bukkit/Spigot plugin and cannot act on a single‑player Fabric bug.

> ⚠️ **The five new skills are code‑complete but lightly play‑tested.** Parkour, Swimming, Flying,
> Stealth and Unarmored all pass the unit suite and boot clean, but their XP rates and reference
> speeds are **starting estimates, not measured numbers** — the tuning
> comments in `experience.yml` say so explicitly. Balance feedback on these is especially welcome.
> The in‑game verification plan lives in [`plans/PLAYTEST_G.md`](plans/PLAYTEST_G.md).

> ⚠️ **Existing Acrobatics or Agility progress does not carry over.** Movement is three skills now —
> Parkour, Swimming and Flying — and neither of the names that came before them has a save key left
> to read, so old progress is deliberately allowed to zero out. Train the three instead. Your
> *tuning* is a different matter and is not thrown away: mcMMO relocates a stranded `Agility:` block
> to whichever movement skill now owns each setting, on the next load, and logs what it moved. For
> anything it cannot move safely — and for an older `Acrobatics:` section — it logs a warning naming
> where the values belong rather than silently rewriting a file you own. See
> [Troubleshooting](../../wiki/Troubleshooting) for the full picture.

Deliberately **not** ported (and not coming back):

- Parties, party chat, teleport, XP sharing
- Admin chat, scoreboards, MOTD/broadcast systems
- MySQL and database conversion tooling
- Chimaera Wing, Flux Mining, permission‑node integrations
- The **Archery**, **Crossbows**, **Tridents**, **Maces** and **Spears** super abilities — five
  registered placeholders with no behaviour, upstream included. They have no rank ladders, no config
  and no `/mcstats` line, so there is nothing to level toward. The skills themselves are fully
  playable.

**Limit Break** *is* implemented, for all eight weapons, but ships **off** — and off is invisible: no
damage, no `/mcstats` entry, no rank plaques. Upstream's `AllowPVE` gate means "PVP only", which
single-player cannot reach, and against mobs the bonus is not nerfed the way it is against an
armoured player (+10 at level 1000 is more than a diamond sword's base damage). Enable it in
**Settings → Abilities → Limit Break**.

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
