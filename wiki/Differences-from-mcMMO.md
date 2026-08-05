# Differences from upstream mcMMO

This is a **port**, not a fork that tracks upstream. It targets single-player, and a large amount of mcMMO exists only to serve a multiplayer server. That part was removed rather than disabled.

---

## Added

Six skills upstream doesn't have, plus a restructure.

### The movement group

**Acrobatics was renamed Agility** and turned into a **child skill** — it owns ten sub-skills but earns no XP of its own. Its level is the mean of three brand-new primary skills:

| New skill | Trained by |
|---|---|
| **Parkour** | Sprinting on land (also fall/roll/dodge) |
| **Swimming** | Swimming |
| **Flying** | Elytra gliding |

Six of Agility's ten sub-skills are new: **Fleet Footed, Athlete, Smash, Lead Lungs, Glide, Lake Raider, Solar Wings**, plus the **Second Wind** super ability. Full details: [Movement Skills](Movement-Skills).

### Stealth

Trained by sneaking. **Padfoot**, **Assassin** (backstab), **Smoke Bomb** super ability. Full details: [Stealth](Stealth).

### Unarmored

Trained by taking damage with every armour slot empty. **Iron Skin** (real armour points at four tiers) and **Thorny Skin**. Full details: [Unarmored](Unarmored).

### The speed-normalised XP model

Movement and sneak XP are paid **per second** of travel with distance clamped at a reference speed, rather than per block. This is a genuinely new XP model, and it exists so that speed buffs aren't XP multipliers and so a movement skill can't accelerate its own levelling. [Explanation](Movement-Skills#how-movement-xp-works).

---

## Changed

### Combat XP is per hit, not per kill

A deliberate ruling that shifts the whole combat XP rate. A tanky mob you hit fifteen times pays fifteen times.

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
- **Limit Break** — see below
- **Spears' super ability** — a registered placeholder, like the Tridents and Maces ones (see below)

---

## ⚠️ Dead enums — present but doing nothing

These appear in `/mcstats` and in the config files, and have full rank ladders. **No code reads them.** They are listed here so you don't waste time levelling toward them.

### The eight Limit Breaks

`ArcheryLimitBreak`, `AxesLimitBreak`, `CrossbowsLimitBreak`, `MacesLimitBreak`, `SpearsLimitBreak`, `SwordsLimitBreak`, `TridentsLimitBreak`, `UnarmedLimitBreak`.

### Five unimplemented super abilities

**Explosive Shot** (Archery), **Super Shotgun** (Crossbows), and the **Tridents**, **Maces** and **Spears** abilities. All are registered placeholders with no behaviour.

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
