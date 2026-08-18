# Hunter

**New in this port** — Hunter does not exist in upstream mcMMO.

**How you train it:** kill things. Any things, with anything.

> **Hunter is not a weapon skill.** Swords, Axes, Archery and the rest care *what you swung*. Hunter only cares **what died**. You can level it with a netherite axe, a bow, a bare fist or a fall you set up — the weapon never enters into it.

> ⚠️ **Code-complete, never play-tested.** Every number on this page is a starting estimate, and the open balance question at the [bottom of the page](#the-open-balance-question) has not been settled. Balance feedback is genuinely wanted.

---

## Two axes, and they are independent

Hunter has an ordinary vertical axis — a level, from XP — and a **horizontal** one that no other skill has.

| | Vertical | Horizontal |
|---|---|---|
| Measures | Total kills, weighted by danger | Kills of **one specific creature** |
| Shown as | Your Hunter level | Mob Mastery tiers, per creature |
| Rewards | Trophy Hunter ranks | +1.0 / +2.0 / +3.0 damage against that creature |
| Caps at | Level 1000 | 10,000 kills, per creature, forever |

A Hunter 1000 who has never fought a blaze does no extra damage to blazes. A level-40 Hunter with 10,000 zombie kills hits zombies for +3.0 and everything else for nothing. **Both are working as designed.**

---

## Earning XP

`experience.yml` → `Experience_Values.Hunter`. **Paid per kill, not per hit.**

| Tier | XP | What's in it | Kills to max on that tier alone |
|---|---|---|---|
| **T1** | 100 | Anything that isn't a monster — chicken, cow, sheep, horse | 110,100 |
| **T2** | 300 | Ordinary monsters — zombie, skeleton, creeper, spider | 36,700 |
| **T3** | 800 | Dangerous monsters — blaze, guardian, enderman, ravager | 13,762 |
| **T4** | 1,500 | Bosses — wither, ender dragon, warden | 7,340 |

Every other combat skill pays `damage × multiplier` **per hit** ([why](XP-and-Levelling#combat-xp-is-per-hit)). Hunter can't: its subject is *which creature died*, which no single hit can answer, and per-hit would make a 500-health warden worth twenty-five zombies for reasons that have nothing to do with the tier being paid for. **The tier already prices the danger.**

At a sustained hand-killing rate of about 6 kills/min, the ~306 XP an average kill needs to hit the 100-hour target is what puts common hostiles — the overwhelming majority of what anyone actually kills — at 300.

### How a creature's tier is decided

**There is no mob table, and that is the design.** The tier is worked out from the creature itself:

```
not hostile                              -> T1   (T2 if >= 60 health)
hostile, >= 150 health                   -> T4
hostile, >= 30 health OR >= 6 attack     -> T3
any other hostile                        -> T2
```

A hand-written ~90-row mob table goes stale the moment Mojang adds a creature or you install a mod, and its failure mode is **silent** — the unlisted mob resolves to zero and pays nothing forever. That has already happened three times in this port. A derived tier cannot go stale, and it **fails low** rather than high, which is the safe direction for the axis a farm attacks.

The health and damage figures are read from the creature's **type**, not from the individual you killed — so a zombie holding a sword, a horse that rolled high health, and a Hard-difficulty mob are all still exactly the tier their species is. Tier is a fact about the species that a player can learn once.

**Modded mobs are handled for free**, since Fabric writes into the same attribute registry.

#### Overrides

`advanced.yml` → `Skills.Hunter.Tiers.Overrides` names only the exceptions. Two ship, and both fail the derivation the same way — **their danger is not in their attributes**:

| Mob | Forced to | Why the rule gets it wrong |
|---|---|---|
| `Ghast` | T3 | 10 health and *no* attack-damage attribute at all. It never melees — it throws fireballs across a lava sea. |
| `Wither_Skeleton` | T3 | Its attack-damage attribute is the inherited default 2.0, identical to a plain skeleton's. The stone sword and the wither effect do the work, and neither is an attribute. |

Keys use the same form as `experience.yml`'s combat multiplier table — `Wither_Skeleton`, not `minecraft:wither_skeleton`. A value outside 1–4 is **refused with a warning** and the derived tier used instead, rather than being quietly clamped.

> The **witch** looks like it should need an override and doesn't — 26 health puts it below the T3 line, which is correct.

---

## What counts as a kill

Four gates, all four required, for **XP, mastery and Trophy Hunter alike**.

| # | Gate | What it excludes |
|---|---|---|
| 1 | The killer is you | Fall, lava, suffocation and drowning farms have no attacker at all. Also excludes your wolf's kills — those are Taming's. A projectile resolves back to its shooter, so bow kills count. |
| 2 | `Enabled_For_PVE` / `Enabled_For_PVP` | The standard combat switches. |
| 3 | Not something you manufactured | Snow and copper golems never count. An iron golem counts only if it wasn't player-built. Summoned pets don't count. |
| 4 | The creature's **spawn origin** qualifies | See below. |

### The spawn-origin gate

Every mob is marked at creation with how it came into the world. These origins **never count**:

- **Spawners** and **trial spawners**
- **Bred** animals
- **Spawn eggs**, `/summon`, and dispenser-placed mobs
- **Structure spawns** (a nether portal's zombified piglins, and structure one-offs)

The marker is persisted, so it **survives a world reload** — a spawner farm does not quietly start counting again after a restart. It also survives **conversion**: a drowned farm is a zombie spawner over water, and the drowned inherits the zombie's disqualification rather than arriving fresh.

### Deliberately left counting

Raids, patrols, an evoker's vexes, zombie reinforcements, and mobs you released from a bucket. Each is farmable to some degree, but **a defended village raid is about the most legitimate combat in the game**, and excluding it takes more from honest play than it saves.

**Withers count too**, even though every wither is player-built — a wither costs three skulls at ~2.5 % drop, so the farm is rate-limited long before 1,500 XP/kill matters, and excluding them would empty T4 down to the warden.

**One known leak:** a skeleton riding a spawner-spawned spider arrives tagged `JOCKEY`, not `SPAWNER`, so it escapes the gate its mount doesn't.

### `Diminished_Returns.Threshold.Hunter` is not decorative

Every other skill in that table sits far out of the throttle's reach. Hunter doesn't: a T4 boss at ~1,500 XP means **fourteen kills in ten minutes** trips the 20,000 threshold. Unreachable by hand, trivial for a farm. Don't "tidy" it to match its neighbours.

---

## Sub-skills

### Mob Mastery

**Kill enough of one creature and you learn how to kill it.**

| Kills of one creature | Tier | Bonus damage against that creature |
|---|---|---|
| 500 | Mastery 1 | **+1.0** |
| 2,500 | Mastery 2 | **+2.0** |
| 10,000 | Mastery 3 | **+3.0** |

Permanent, per creature, and it stacks with everything else. You get a chat + action-bar notification each time you cross a threshold.

**The thresholds and the bonuses are not configurable, on purpose.** They are three numbers a player is meant to learn once and have mean the same thing for every creature in the game — that is the whole point of the horizontal axis.

Mob Mastery has **no rank ladder and no `skillranks.yml` entry**, because it doesn't unlock on a level. That is why it doesn't appear in the ranks list on `/mcstats`.

#### What the bonus applies to

A **melee swing** or a **player-owned arrow, bolt or thrown trident**. Not lit TNT, not a Blast Mining charge, not a lingering potion cloud, not Thorns, and not your wolf's bite (Taming's Sharpened Claws and Gore already own that damage).

It is applied **last** in the damage chain — after Parkour's Sprint Smash and Stealth's Assassin — because Assassin multiplies the whole melee total. A ×3 backstab on a 10-damage hit with top-tier mastery is `10×3 + 3 = 33`, not `(10+3)×3 = 39`.

`advanced.yml` → `Skills.Hunter.MobMastery.Ranged_Damage_Multiplier` (default `1.0`) exists as cheap insurance. A melee bonus is already throttled by the vanilla attack-cooldown charge, so spam-clicking pays less than a charged swing — a bow shot has no equivalent throttle and collects the whole +3.0 every time. If ranged mastery turns out over-tuned, `0.5` halves it on projectiles and leaves melee exactly as ruled. Negative values are clamped to 0: earned mastery must never become a penalty.

> **Spawn origin gates the kill, not the hit.** A spawner zombie still takes your full +3.0 and still banks nothing toward the counter. Re-checking the marker in the damage path would close no farm — the farm banks nothing either way — while making the damage you see depend on an invisible property of your target.

### Trophy Hunter — 4 ranks

**Take a second trophy from what you kill.** `ChanceMax: 50.0`

A chance that a creature you kill **rolls its own loot table a second time**. Not a bespoke bonus table and not a rare-slot bias: a second roll respects Looting, needs no per-mob data to go stale, and gives "more of what that creature drops" — more gunpowder and ender pearls, but also more rotten flesh. The two rolls are genuinely independent, not a copy of the first.

**The rank number *is* the mob tier.** Rank N unlocks the bonus roll against tier-N creatures and nothing above them:

| Rank | Unlocks at | Reaches |
|---|---|---|
| 1 | 100 | T1 — anything that isn't a monster |
| 2 | 300 | T2 — ordinary monsters |
| 3 | 600 | T3 — dangerous monsters |
| 4 | 900 | T4 — bosses |

There is no fifth rank and there never will be; the tier scale is 1–4.

50 % rather than the 100 % that Herbalism's and Mining's double drops use, because those are **blocks** and this is the **mob economy** — what a grinder attacks — and at rank 4 it applies to bosses, so 100 would mean two nether stars from every wither.

There is **no proc message**. It fires on roughly half of your kills at max level; like mcMMO's own double drops, it stays silent. You see the items.

### Quarry Sense — 1 rank, unlocks at 1

**Crouch and strike a creature with a bone to read your hunt log against it.**

| Requirement | |
|---|---|
| A **bone** in your main hand | |
| **Crouching** | |
| Hit any living creature | Armour stands excluded. |

The readout tells you how many of that creature you've slain, which mastery tier that is and what it's worth, how many more the next tier wants, the creature's own tier, and whether Trophy Hunter reaches it yet. The blow itself is cancelled.

This is Taming's **Beast Lore** extended to every creature. Two differences are deliberate:

1. **Beast Lore's gate is "is it tameable"** — wolves, cats, horses, parrots. Every creature Hunter actually counts is excluded by it, so simply appending to Beast Lore's readout would have shipped a feature that only worked on the four creatures nobody has 500 kills of.
2. **Quarry Sense requires crouching; Beast Lore doesn't.** A bone is a skeleton's own drop, and Beast Lore cancels the blow — so without the crouch requirement, picking up a bone mid-fight would mean **you can't swing back at anything** and have no idea why. Crouching can't happen in a panic. To read a wolf you need not crouch; to read a zombie you must.

Unlocked at level 1 in both modes, mirroring Beast Lore. The mastery counters are invisible from the very first kill and this is the only place in the world you can see one — gating it behind a level would recreate exactly the problem it exists to solve: 499 kills of nothing appearing to happen.

> An unknown mob id (from a mod that's since been removed) is shown as its raw id, never mis-labelled.

---

## `/mcstats hunter`

The other window onto the horizontal axis, answering "how is my mastery going overall" rather than "what do I know about *this* creature":

- creatures hunted, and creatures mastered
- your **top 3** by kill count
- Trophy Hunter's current chance, and the highest mob tier it reaches

A brand-new Hunter gets a sentence rather than an empty block — a first-time player is exactly who needs telling the counters are already live.

Your kill counts live in `<world save>/mcmmo/players/<uuid>.yml` under `kills:`, keyed on the full registry id and capped at 4,096 distinct creatures.

---

## Not shipped

**Field Dressing** — biasing the bonus roll toward a creature's rare drops rather than rolling the flat table. It reuses Trophy Hunter's exact seam and needs loot-table introspection, so it's the designated upgrade path once the base version has been played.

---

## The open balance question

**No spawn origin can close a nether-wastes piglin farm, a dark-room hostile farm, an End enderman farm or an ocean-monument guardian farm.** Those mobs are `NATURAL`, and legitimately so.

What excludes most such farms today is gate 1 — most of them kill by fall, lava or suffocation, which has no player attacker. **A grinder you stand in and swing at is excluded by nothing.**

The reserved backstop is a rolling per-mob-per-hour cap, **deliberately not built yet**: it's an additive throttle that would apply to honest play too, and it should be sized on measured numbers rather than guesses. The play-test plan measures the three worst farms by name. If you build one, the rate you get is exactly the data this needs.

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Hunter.Tier_1..4` — what each tier pays |
| `experience.yml` | `Diminished_Returns.Threshold.Hunter` — the anti-farm throttle |
| `advanced.yml` | `Skills.Hunter.Tiers` — which tier a creature is *in*, and the override table |
| `advanced.yml` | `Skills.Hunter.MobMastery.Ranged_Damage_Multiplier` |
| `advanced.yml` | `Skills.Hunter.TrophyHunter.ChanceMax` |
| `skillranks.yml` | `Hunter.TrophyHunter`, `Hunter.QuarrySense` |
| `config.yml` | `Skills.Hunter.Level_Cap`, `Enabled_For_PVE`, `Enabled_For_PVP` |

> ⚠️ `Enabled_For_PVP: false` does not merely mute a bonus — Hunter's whole subject is the target's identity, so turning it off means player kills stop feeding the skill at all. That's the right answer for a single-player port, and it's stated here so nobody reads it as an oversight.

The house split: **`experience.yml` prices things, `advanced.yml` decides mechanics.** Which tier a mob is in is a mechanic; what the tier is worth is a price.
