# Differences from upstream mcMMO

This is a **port**, not a fork that tracks upstream. It targets single-player, and a large amount of mcMMO exists only to serve a multiplayer server. That part was removed rather than disabled.

---

## Added

**Eight primary skills upstream doesn't have**, plus a restructure.

### The movement group

**Acrobatics is replaced by three brand-new primary skills**, one per medium you travel through:

| New skill | Trained by |
|---|---|
| **Parkour** | Sprinting on land (also fall/roll/dodge) |
| **Swimming** | Swimming |
| **Flying** | Elytra gliding |

Seven sub-skills are new: **Fleet Footed, Athlete, Smash, Lead Lungs, Glide, Lake Raider, Solar Wings**, plus the **Second Wind** super ability.

**Every perk is gated on the skill that pays for it.** Two of them — Fleet Footed and Second Wind — work in all three mediums, so each of the three skills gets its own copy rather than all three sharing one gated on the average. That average was the problem: it made some perks literally unreachable for a player who committed to one medium. Full details: [Movement Skills](Movement-Skills#why-every-perk-sits-on-the-skill-that-earns-it).

### Stealth

Trained by sneaking. **Padfoot**, **Assassin** (backstab), **Smoke Bomb** super ability. Full details: [Stealth](Stealth).

### Unarmored

Trained by taking damage with every armour slot empty. **Iron Skin** (real armour points at four tiers) and **Thorny Skin**. Full details: [Unarmored](Unarmored).

### Husbandry

The livestock lifecycle — six XP verbs (breed, raise, feed a baby, shear, harvest a hive, milk or brush), eight passives and the **Herdsman's Call** super ability. Its boundary against Taming is **the verb, never the species**: Taming pays once for making an animal yours, Husbandry pays repeatedly for what you do with it afterwards. Full details: [Husbandry](Husbandry).

### Hunter

A combat skill that is **not a weapon skill** — it cares only what died, never what you swung. It is the only skill in the mod that progresses on two independent axes: an ordinary XP level, and a per-creature kill counter that grants permanent bonus damage against *that* creature. It is also the only combat skill paid **per kill** rather than per hit. Full details: [Hunter](Hunter).

### Cooking

**Smelting's other half.** The two skills share the furnace and split it by input — ore pays Smelting, food pays Cooking, never both. Three passives (**Master Chef**, **Power Cook**, **Kitchen Efficiency**), no super ability, and a single anti-farm gate (`Max_Cooks_Per_Hour`) because an item carries no record of where it came from. Full details: [Cooking](Cooking).

### The speed-normalised XP model

Movement and sneak XP are paid **per second** of travel with distance clamped at a reference speed, rather than per block. This is a genuinely new XP model, and it exists so that speed buffs aren't XP multipliers and so a movement skill can't accelerate its own levelling. [Explanation](Movement-Skills#how-movement-xp-works).

---

## Changed

### Combat XP is per hit, not per kill

A deliberate ruling that shifts the whole combat XP rate. A tanky mob you hit fifteen times pays fifteen times.

### Limit Break ships off, and off is invisible

All eight Limit Break sub-skills **are** implemented here. They ship **disabled**.

Upstream gates the mechanic behind `Skills.General.LimitBreak.AllowPVE`, which effectively means "PVP only". Single-player has no PVP, so leaving that gate shut would make it unreachable and opening it by default would be a large unannounced buff: against mobs the bonus is *not* nerfed the way it is against a lightly-armoured player. Rank N grants a flat **+N damage**, from +1 at level 100 up to **+10 at level 1000** — more than a diamond sword's base damage.

So it is a deliberate opt-in. **While it is off it is completely invisible**: no damage, no `/mcstats` entry, no rank plaques, nothing nagging you about a mechanic you aren't using. Turn it on in **Settings → Abilities → Limit Break**, or set `AllowPVE: true` in `advanced.yml`.

> ⚠️ Once on, the bonus applies to **every non-player entity**. If you run mods that add humanoid NPCs, those NPCs take the full bonus too.

### `/mcstats <skill>` replaced the per-skill commands

Legacy's `/mining`, `/swords`, `/archery`, … all collapsed into one command that shows strictly more: not just your ranks, but the **live computed value of every sub-skill effect at your current level**.

### Skill data is per world

Stored in `<world save>/mcmmo/players/`, not in a global profile or a database. Copying a world copies its skills.

### Behaviour differences worth knowing

| Change | Detail |
|---|---|
| **Horseback harvesting pays nothing** | `Prevent_AFK_Leveling` is shipped **on** (upstream ships the key but never consults it). |
| **A chorus tree pays for every block** | The multi-block traversal now actually runs. |
| **Sub-100 Repair destroys all enchantments** | Faithful to upstream, but a harsh surprise. Get an Arcane Forging rank first. |
| **Placed blocks are tracked across restarts** | Place → quit → reopen → mine still pays nothing. |
| **Old Acrobatics progress zeroes out** | Child skills have no save key, so there's nothing to migrate to. See [Movement Skills](Movement-Skills). |
| **Tamed pets follow you through a teleport** | An override of *vanilla*, not of upstream mcMMO — a pet outside your simulation distance stops being ticked and never runs its follow goal. Same world only; sitting pets stay. `Skills.Taming.Pets_Follow_Teleport: false` restores vanilla. |

---

## Not ported

### Everything multiplayer

- Parties, party chat, party teleport (`/ptp`), party XP sharing
- Admin chat, scoreboards, MOTD and broadcast systems
- MySQL, and all database conversion tooling
- Permission-node integrations (single-player collapses these to op level / config)

### Commands

`/party`, `/ptp`, `/mcchat`, `/mcscoreboard`, `/mmoedit`, `/mcgod`, `/inspect`, `/mctop`, `/mcconvert`, `/skillreset` and the rest of the multiplayer/admin tree. See [Commands](Commands) for what does exist.

### Features

- **Chimaera Wing**
- **Flux Mining** — never ported. Its config knobs were culled rather than left as switches over nothing.
- **Five super abilities** — see below.

---

## The five unimplemented super abilities

**Explosive Shot** (Archery), **Super Shotgun** (Crossbows), and the **Tridents**, **Maces** and **Spears** abilities are registered `SuperAbilityType` constants with **no behaviour**. Upstream never shipped any of them either — this is not a Fabric limitation.

They are listed here for completeness rather than as a warning, because **there is nothing to level toward and nothing to see**: they have no rank ladders in `skillranks.yml`, no tuning block in `advanced.yml`, and no `/mcstats` line. Apart from their locale strings, the constants are invisible from inside the game.

---

## Blocked, not dropped

**Alchemy Concoctions tier gating.** The custom-ingredient tree in `potions.yml` loads and works, but the level gate that should restrict which tier you can brew has no seam to hook in 1.21.11 — the relevant vanilla method is static and carries no block position, so there's no way to find out whose brewing stand it is.

---

## Deliberately kept

Some upstream behaviour is surprising but was kept on purpose for faithfulness:

- **Sub-100 Repair strips every enchantment.** Harsh, faithful, flagged rather than fixed.
- **A flower pot is consumed on a failed Hylian Luck roll.** A legacy quirk, preserved.

---

## Relationship to upstream

mcMMO is GPL-3.0 and so is this. Original authors retain copyright; the contributor list is preserved in git history and on the [upstream contributors page](https://github.com/mcMMO-Dev/mcMMO/graphs/contributors).

**If you want the full multiplayer mcMMO experience, use [upstream mcMMO](https://github.com/mcMMO-Dev/mcMMO) on a Paper/Spigot server.** That is what it is for, and it is actively maintained. This port exists for people who want the skill system in single-player Fabric.
